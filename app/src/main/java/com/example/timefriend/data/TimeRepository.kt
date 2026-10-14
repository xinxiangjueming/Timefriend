package com.example.timefriend.data

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import java.io.File
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.db.AppDatabase
import com.example.timefriend.data.db.PomodoroSessionEntity
import com.example.timefriend.data.db.TimeRecordDao
import com.example.timefriend.data.db.TimeRecordEntity
import com.example.timefriend.data.db.toEntity
import com.example.timefriend.data.db.toRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Backed by Room. Single instance owned by [AppContainer], shared by all
 * ViewModels and Services — Room serializes writes, eliminating the previous
 * dual-instance Mutex race. Public API signatures are kept compatible with
 * the legacy SharedPreferences implementation.
 */
class TimeRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val dao: TimeRecordDao
) {

    private val pomodoroDao = db.pomodoroSessionDao()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("timefriend_records", Context.MODE_PRIVATE)

    private val dateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    private val dateKeyRegex = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun todayDateString(): String = LocalDate.now().format(dateFormat)

    // ── Legacy API (kept for compatibility) ──

    suspend fun getRecords(date: String): List<TimeRecord> =
        dao.getByDate(date).map { it.toRecord() }

    suspend fun saveRecord(record: TimeRecord) {
        dao.insert(record.toEntity())
    }

    /** Replace all records of the same (date, category) — used for sleep records. */
    suspend fun replaceRecord(record: TimeRecord) {
        db.withTransaction {
            dao.deleteByDateAndCategory(record.date, record.category.name)
            dao.insert(record.toEntity())
        }
    }

    suspend fun getRecordDates(): Set<String> =
        dao.getDistinctDates().toSet()

    /**
     * Export all record data as a JSON string (WebDAV backup format):
     * top-level object { "yyyy-MM-dd": "JSONArray-string", ... }.
     * Kept byte-compatible with the legacy format.
     */
    suspend fun exportAllJson(): String {
        val obj = JSONObject()
        dao.getAll()
            .groupBy { it.date }
            .toSortedMap()
            .forEach { (date, entities) ->
                obj.put(date, serializeEntities(entities).toString())
            }
        return obj.toString()
    }

    /**
     * Incremental merge of a WebDAV backup JSON into the local database.
     * Records already present locally (same record [TimeRecord.id]) are kept
     * untouched; only cloud records that are missing locally get inserted.
     * Local data is never overwritten or deleted — this is a union merge,
     * not a replace.
     */
    suspend fun importAllJson(json: String) {
        val remote = parseAllJson(json)
        if (remote.isEmpty()) return
        db.withTransaction {
            val localIds = dao.getAll().map { it.id }.toSet()
            val toInsert = remote.filter { it.id !in localIds }
            if (toInsert.isNotEmpty()) dao.insertAll(toInsert.map { it.toEntity() })
        }
    }

    // ── Reactive API (auto-refresh via Room invalidation) ──

    fun observeRecords(date: String): Flow<List<TimeRecord>> =
        dao.observeByDate(date).map { list -> list.map { it.toRecord() } }

    fun observeDateRange(startDate: String, endDate: String): Flow<List<TimeRecord>> =
        dao.observeByDateRange(startDate, endDate).map { list -> list.map { it.toRecord() } }

    fun observeRecordDates(): Flow<Set<String>> =
        dao.observeDistinctDates().map { it.toSet() }

    // ── Pomodoro sessions (per-cycle focus/rest, written by PomodoroService) ──

    /** Observe pomodoro sessions in an inclusive date range (auto-refreshes). */
    fun observePomodoroRange(startDate: String, endDate: String): Flow<List<PomodoroSessionEntity>> =
        pomodoroDao.observeByDateRange(startDate, endDate)

    /**
     * Insert a session row for a completed focus phase. [date] defaults to today.
     * Returns the generated row id (used later to fill in rest minutes).
     */
    suspend fun insertPomodoroSession(
        focusMinutes: Int,
        restMinutes: Int = 0,
        date: String = todayDateString()
    ): Long = pomodoroDao.insert(
        PomodoroSessionEntity(
            date = date,
            focusMinutes = focusMinutes,
            restMinutes = restMinutes,
            createdAt = System.currentTimeMillis()
        )
    )

    /** Fill in the rest minutes of a session after its rest phase ends. */
    suspend fun updatePomodoroSessionRest(id: Long, restMinutes: Int) {
        if (id > 0) pomodoroDao.updateRest(id, restMinutes)
    }

    /**
     * 幂等写入手表（Vela interconnect）同步来的番茄钟周期。
     * 按 (date|createdAt|focusMinutes|restMinutes) 去重，重复同步不会产生重复行；
     * 每个手表周期的 [PomodoroSessionEntity.createdAt] 是完成时刻的稳定时间戳，天然幂等键。
     *
     * 同时派生 INVESTMENT/FIXED 两条 [TimeRecord]（对齐 [com.example.timefriend.service.
     * PomodoroService.recordFocus/recordRest] 的本地行为）：柱状图吃 pomodoro_sessions、
     * 总结饼图吃 records，只写前者会导致手表同步的数据永远进不了总结图
     * （2026-10-14 用户反馈）。派生带重叠守卫（见 [deriveMissingPomodoroRecords]），
     * 重复同步与本地周期均不会重复计。
     *
     * @return 实际新插入的周期行数（供同步回执携带）
     */
    suspend fun upsertWatchPomodoroSessions(sessions: List<PomodoroSessionEntity>): Int {
        if (sessions.isEmpty()) return 0
        return db.withTransaction {
            val existing = pomodoroDao.getAll().map { pomKey(it) }.toSet()
            val toInsert = sessions.filter { pomKey(it) !in existing }
            if (toInsert.isNotEmpty()) pomodoroDao.insertAll(toInsert)
            deriveMissingPomodoroRecords(sessions)
            toInsert.size
        }
    }

    /**
     * 冷启动补齐（2026-10-14）：修复版发布前同步的存量周期只落了 pomodoro_sessions、
     * 没派生时间记录 → 柱状图有数据、总结饼图空缺。App 启动时对全部周期重跑派生。
     * 重叠守卫保证幂等与安全：
     *  - 手表周期（无任何记录）→ 派生窗口与已有同类别记录不重叠 → 插入；
     *  - 手机本地周期（PomodoroService 已写记录，结束时刻 = createdAt）→ 自身记录
     *    与派生窗口重叠 → 跳过，不会重复计；
     *  - 已派生过的手表周期 → 精确同窗口记录存在（重叠）→ 跳过。
     */
    suspend fun backfillPomodoroTimeRecords() {
        val sessions = pomodoroDao.getAll()
        if (sessions.isEmpty()) return
        db.withTransaction { deriveMissingPomodoroRecords(sessions) }
    }

    /**
     * 从番茄钟周期派生缺失的 INVESTMENT/FIXED 时间记录（同事务调用）。
     * 时间轴：createdAt = 周期（专注+休息）完成时刻；休息 = [createdAt-rest, createdAt]，
     * 专注紧随休息前 = [focusEnd-focus, focusEnd]，focusEnd = createdAt-rest。
     * 守卫：同日期同类别已有记录与派生窗口 [start,end) 重叠即跳过（幂等 + 防本地重复计）。
     */
    private suspend fun deriveMissingPomodoroRecords(sessions: List<PomodoroSessionEntity>) {
        val recsByDate = sessions.map { it.date }.distinct().associateWith { date ->
            dao.getByDate(date).filter {
                it.category == TimeCategory.INVESTMENT.name || it.category == TimeCategory.FIXED.name
            }
        }
        val added = mutableSetOf<String>()
        val derived = mutableListOf<TimeRecordEntity>()
        for (s in sessions) {
            val dateRecs = recsByDate[s.date] ?: emptyList()
            val restEnd = s.createdAt
            if (s.restMinutes > 0) {
                addRecordIfAbsent(
                    derived, added, dateRecs, TimeCategory.FIXED,
                    restEnd - s.restMinutes * 60_000L, restEnd, s.date
                )
            }
            if (s.focusMinutes > 0) {
                val focusEnd = restEnd - s.restMinutes * 60_000L
                addRecordIfAbsent(
                    derived, added, dateRecs, TimeCategory.INVESTMENT,
                    focusEnd - s.focusMinutes * 60_000L, focusEnd, s.date
                )
            }
        }
        if (derived.isNotEmpty()) dao.insertAll(derived)
    }

    private fun addRecordIfAbsent(
        out: MutableList<TimeRecordEntity>,
        added: MutableSet<String>,
        existing: List<TimeRecordEntity>,
        category: TimeCategory,
        startTime: Long,
        endTime: Long,
        date: String
    ) {
        val key = "$date|${category.name}|$startTime|$endTime"
        if (key in added) return
        // 重叠守卫：已有同类别记录覆盖了 [startTime, endTime) 的任意部分即视为已存在
        if (existing.any { it.startTime < endTime && startTime < it.endTime }) return
        added += key
        out += TimeRecordEntity(
            id = endTime, // 兼容列（uid 才是主键）；取结束时刻，与本地记录的毫秒时间戳形态一致
            category = category.name,
            startTime = startTime,
            endTime = endTime,
            date = date
        )
    }

    // ── Legacy SharedPreferences → Room migration (idempotent) ──

    /**
     * One-time migration of records stored in the legacy SharedPreferences
     * ("timefriend_records") into Room. Idempotent: re-runs delete-and-reinsert
     * each date, and clears the legacy prefs only after a successful transaction.
     */
    suspend fun migrateFromSharedPreferences() {
        val data = prefs.all.filterKeys { it.matches(dateKeyRegex) }
        if (data.isEmpty()) return
        db.withTransaction {
            data.forEach { (date, raw) ->
                if (raw is String) {
                    val entities = parseJsonArray(raw).map { it.toEntity() }
                    dao.deleteByDate(date)
                    if (entities.isNotEmpty()) dao.insertAll(entities)
                }
            }
        }
        prefs.edit().clear().apply()
    }

    // ── Internal helpers ──

    /** Parse the whole backup object { "date": "JSONArray-string" } into flat records. */
    private fun parseAllJson(json: String): List<TimeRecord> = try {
        val obj = JSONObject(json)
        val result = mutableListOf<TimeRecord>()
        for (key in obj.keys()) {
            if (dateKeyRegex.matches(key)) {
                result.addAll(parseJsonArray(obj.getString(key)))
            }
        }
        result
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseJsonArray(json: String): List<TimeRecord> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            TimeRecord(
                id = o.getLong("id"),
                category = TimeCategory.valueOf(o.getString("category")),
                startTime = o.getLong("startTime"),
                endTime = o.getLong("endTime"),
                date = o.getString("date")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun serializeEntities(entities: List<TimeRecordEntity>): JSONArray {
        val arr = JSONArray()
        entities.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("category", e.category)
                put("startTime", e.startTime)
                put("endTime", e.endTime)
                put("date", e.date)
            })
        }
        return arr
    }

    // ── Room 库文件备份 / 恢复（zstd 压缩，WebDAV 通道）──

    /**
     * 备份本地 Room 库为 zstd 压缩单文件并返回该文件（位于 cache 目录）。
     * 流程：WAL checkpoint(TRUNCATE) 把 WAL 并入主库 → 拷贝主库文件 → zstd 压缩。
     * 压缩产物字节确定，便于后续做"库未变就跳过上传"的增量判断。
     */
    suspend fun backupRoomDb(): File {
        // 1) WAL checkpoint，保证主库文件包含全部已提交数据
        db.openHelper.writableDatabase
            .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)"))
            .use { /* 消费游标 */ }
        // 2) 主库文件路径
        val srcDb = File(
            db.openHelper.writableDatabase.path
                ?: throw IllegalStateException("无法获取数据库文件路径")
        )
        if (!srcDb.exists()) throw IllegalStateException("数据库文件不存在: ${srcDb.absolutePath}")
        // 3) 拷贝 + zstd 压缩
        val cacheDir = context.cacheDir
        val tmpDb = File(cacheDir, "timefriend_backup_tmp.db")
        val zst = File(cacheDir, "timefriend.db.zst")
        try {
            srcDb.copyTo(tmpDb, overwrite = true)
            ZstdUtil.compress(tmpDb, zst, level = 12)
        } finally {
            tmpDb.delete()
        }
        return zst
    }

    /**
     * 从云端下载的 .zst 恢复：整体解压后**按主键/业务键并入本地**——
     * 仅插入本地缺失的记录，绝不覆盖或删除本地已有数据（并集合并）。
     *
     * 去重键：records 用整行 (id|category|startTime|endTime|date)，
     * pomodoro 用整行 (date|createdAt|focusMinutes|restMinutes)，
     * 比单纯 id 更稳健，避免毫秒级时间戳碰撞导致漏插。
     */
    suspend fun restoreRoomDbFrom(zstFile: File) {
        val cacheDir = context.cacheDir
        val tmpDb = File(cacheDir, "timefriend_restore_tmp.db")
        try {
            ZstdUtil.decompress(zstFile, tmpDb)
            val remote = SQLiteDatabase.openDatabase(tmpDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val remoteRecords = readRecordsFromDb(remote)
                val remotePomodoros = readPomodorosFromDb(remote)
                db.withTransaction {
                    val localRecKeys = dao.getAll().map { recKey(it) }.toSet()
                    val toInsertRec = remoteRecords.filter { recKey(it) !in localRecKeys }
                    if (toInsertRec.isNotEmpty()) dao.insertAll(toInsertRec)

                    val localPomKeys = pomodoroDao.getAll().map { pomKey(it) }.toSet()
                    val toInsertPom = remotePomodoros.filter { pomKey(it) !in localPomKeys }
                    if (toInsertPom.isNotEmpty()) pomodoroDao.insertAll(toInsertPom)
                }
            } finally {
                remote.close()
            }
        } finally {
            tmpDb.delete()
        }
    }

    /** 本地是否完全没有记录（备份前空库保护，避免用空库覆盖云端）。 */
    suspend fun isEmpty(): Boolean = dao.getAll().isEmpty() && pomodoroDao.getAll().isEmpty()

    // ── 内部：从裸 SQLite 库读取备份数据 ──

    private fun readRecordsFromDb(db: SQLiteDatabase): List<TimeRecordEntity> {
        val list = mutableListOf<TimeRecordEntity>()
        db.rawQuery("SELECT id, category, startTime, endTime, date FROM records", null).use { c ->
            while (c.moveToNext()) {
                list += TimeRecordEntity(
                    uid = 0,
                    id = c.getLong(0),
                    category = c.getString(1),
                    startTime = c.getLong(2),
                    endTime = c.getLong(3),
                    date = c.getString(4)
                )
            }
        }
        return list
    }

    private fun readPomodorosFromDb(db: SQLiteDatabase): List<PomodoroSessionEntity> {
        val list = mutableListOf<PomodoroSessionEntity>()
        db.rawQuery("SELECT id, date, focusMinutes, restMinutes, createdAt FROM pomodoro_sessions", null).use { c ->
            while (c.moveToNext()) {
                list += PomodoroSessionEntity(
                    id = 0,
                    date = c.getString(1),
                    focusMinutes = c.getInt(2),
                    restMinutes = c.getInt(3),
                    createdAt = c.getLong(4)
                )
            }
        }
        return list
    }

    private fun recKey(e: TimeRecordEntity): String =
        "${e.id}|${e.category}|${e.startTime}|${e.endTime}|${e.date}"

    private fun pomKey(e: PomodoroSessionEntity): String =
        "${e.date}|${e.createdAt}|${e.focusMinutes}|${e.restMinutes}"
}
