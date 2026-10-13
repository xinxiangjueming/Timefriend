package com.example.timefriend.viewmodel

import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord

/** Navigation & UI preference state (owned by MainViewModel). */
data class MainState(
    val currentTab: Int = 0,                    // 0 = Today, 1 = History
    val showSettings: Boolean = false,          // whether settings screen is visible
    val blurLevel: Float = 10f                  // nav bar blur radius
)

/** Timer state (owned by TimerViewModel). */
data class TimerUiState(
    val todayRecords: List<TimeRecord> = emptyList(),
    val activeCategory: TimeCategory? = null,   // currently recording category
    val activeStartTime: Long = 0L,             // when timer started
    val timerElapsed: Long = 0L,                // live elapsed ms for display
    val sleepDurationMinutes: Int = 0           // manually entered sleep minutes
)

/** One day of aggregated pomodoro time (sum of session rows for that date). */
data class DailyPomodoro(
    val date: String,           // yyyy-MM-dd
    val focusMinutes: Int,      // total focus (investment) minutes
    val restMinutes: Int        // total rest (break) minutes
)

/** History data state (owned by HistoryViewModel, auto-refreshed via Room flows). */
data class HistoryUiState(
    val selectedDate: String = "",              // yyyy-MM-dd for history
    val historyRecords: List<TimeRecord> = emptyList(),
    val weekRecords: List<TimeRecord> = emptyList(), // records for the week containing selectedDate
    val monthRecords: List<TimeRecord> = emptyList(), // records for the month containing selectedDate
    val recordDates: Set<String> = emptySet()   // dates with data (calendar dots)
)

/** WebDAV config & sync state (owned by WebDavViewModel). */
data class WebDavUiState(
    val webDavServer: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val syncMessage: String? = null
)

enum class SyncStatus { IDLE, LOADING, SUCCESS, ERROR }
