package com.example.timefriend.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimeRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("timefriend_records", Context.MODE_PRIVATE)

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    /** Protects read-modify-write cycles on per-date records. */
    private val writeMutex = Mutex()

    fun todayDateString(): String = LocalDate.now().format(dateFormat)

    suspend fun getRecords(date: String): List<TimeRecord> = withContext(Dispatchers.IO) {
        val json = prefs.getString(date, null) ?: return@withContext emptyList()
        try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                TimeRecord(
                    id = obj.getLong("id"),
                    category = TimeCategory.valueOf(obj.getString("category")),
                    startTime = obj.getLong("startTime"),
                    endTime = obj.getLong("endTime"),
                    date = obj.getString("date")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveRecord(record: TimeRecord) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = getRecordsSync(record.date).toMutableList()
            existing.add(record)
            saveRecordsSync(record.date, existing)
        }
    }

    suspend fun replaceRecord(record: TimeRecord) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = getRecordsSync(record.date)
                .filter { it.category != record.category }
                .toMutableList()
            existing.add(record)
            saveRecordsSync(record.date, existing)
        }
    }

    suspend fun getRecordDates(): Set<String> = withContext(Dispatchers.IO) {
        prefs.all.keys
    }

    /**
     * Export all record data as a JSON string (excludes non-record keys).
     */
    suspend fun exportAllJson(): String = withContext(Dispatchers.IO) {
        val obj = JSONObject()
        for ((key, value) in prefs.all) {
            if (value is String && key.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                obj.put(key, value)
            }
        }
        obj.toString()
    }

    /**
     * Import record data from a JSON string, merging into existing data.
     * For each date, the imported records replace existing ones.
     */
    suspend fun importAllJson(json: String) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val obj = JSONObject(json)
            val editor = prefs.edit()
            for (key in obj.keys()) {
                editor.putString(key, obj.getString(key))
            }
            editor.apply()
        }
    }

    // Internal synchronous helpers — only called from within withContext(Dispatchers.IO)
    private fun getRecordsSync(date: String): List<TimeRecord> {
        val json = prefs.getString(date, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                TimeRecord(
                    id = obj.getLong("id"),
                    category = TimeCategory.valueOf(obj.getString("category")),
                    startTime = obj.getLong("startTime"),
                    endTime = obj.getLong("endTime"),
                    date = obj.getString("date")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveRecordsSync(date: String, records: List<TimeRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("category", r.category.name)
                put("startTime", r.startTime)
                put("endTime", r.endTime)
                put("date", r.date)
            })
        }
        prefs.edit().putString(date, arr.toString()).apply()
    }
}
