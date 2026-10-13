package com.example.timefriend.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One pomodoro cycle (focus + rest), written by [com.example.timefriend.service.PomodoroService]
 * at phase transitions — independent of the UI being composed, so sessions are
 * recorded reliably even when the app is in the background.
 *
 * [focusMinutes] is the actual focused minutes (net countdown time; paused time
 * is excluded). The row is inserted when the focus phase completes (rest = 0),
 * and [restMinutes] is updated in place when the rest phase ends.
 */
@Entity(
    tableName = "pomodoro_sessions",
    indices = [
        Index(value = ["date"])
    ]
)
data class PomodoroSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** yyyy-MM-dd, the date the focus phase started on. */
    val date: String,
    val focusMinutes: Int,
    val restMinutes: Int,
    val createdAt: Long
)
