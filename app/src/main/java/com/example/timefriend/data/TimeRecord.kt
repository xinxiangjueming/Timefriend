package com.example.timefriend.data

import android.content.Context
import com.example.timefriend.R

data class TimeRecord(
    val id: Long = System.currentTimeMillis(),
    val category: TimeCategory,
    val startTime: Long,  // epoch millis
    val endTime: Long,    // epoch millis
    val date: String      // yyyy-MM-dd
) {
    val durationMillis: Long get() = endTime - startTime
    val durationMinutes: Long get() = durationMillis / 60_000
    val durationHours: Long get() = durationMillis / 3_600_000

    fun displayDuration(context: Context): String {
        val hours = durationMinutes / 60
        val mins = durationMinutes % 60
        return if (hours > 0) {
            context.getString(R.string.duration_hours_minutes, hours, mins)
        } else {
            context.getString(R.string.duration_minutes, mins)
        }
    }
}
