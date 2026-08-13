package com.example.timefriend.data

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.example.timefriend.data.db.AppDatabase
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
    context: Context,
    private val db: AppDatabase,
    private val dao: TimeRecordDao
) {

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
}
