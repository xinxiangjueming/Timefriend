package com.example.timefriend.viewmodel

import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord

data class MainState(
    val currentTab: Int = 0,                    // 0 = Today, 1 = History
    val todayRecords: List<TimeRecord> = emptyList(),
    val historyRecords: List<TimeRecord> = emptyList(),
    val selectedDate: String = "",              // yyyy-MM-dd for history
    val weekRecords: List<TimeRecord> = emptyList(), // records for the week containing selectedDate
    val monthRecords: List<TimeRecord> = emptyList(), // records for the month containing selectedDate
    val activeCategory: TimeCategory? = null,   // currently recording category
    val activeStartTime: Long = 0L,             // when timer started
    val timerElapsed: Long = 0L,                // live elapsed ms for display
    val sleepDurationMinutes: Int = 0,          // manually entered sleep minutes
    val recordDates: Set<String> = emptySet(),  // dates with data (calendar dots)
    val showSettings: Boolean = false,          // whether settings screen is visible
    // WebDAV settings
    val webDavServer: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val webDavPath: String = "timefriend_backup.json",
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val syncMessage: String? = null
)

enum class SyncStatus { IDLE, LOADING, SUCCESS, ERROR }

sealed interface MainIntent {
    data class SwitchTab(val tabIndex: Int) : MainIntent
    data class StartTimer(val category: TimeCategory) : MainIntent
    data object StopTimer : MainIntent
    data class SaveSleep(val minutes: Int) : MainIntent
    data class SelectHistoryDate(val date: String) : MainIntent
    data class UpdateTimerElapsed(val elapsed: Long) : MainIntent
    data class SavePomodoroRecord(val category: TimeCategory, val minutes: Int) : MainIntent
    data object ShowSettings : MainIntent
    data object HideSettings : MainIntent
    data class UpdateWebDavServer(val value: String) : MainIntent
    data class UpdateWebDavUsername(val value: String) : MainIntent
    data class UpdateWebDavPassword(val value: String) : MainIntent
    data class UpdateWebDavPath(val value: String) : MainIntent
    data object WebDavBackup : MainIntent
    data object WebDavSync : MainIntent
}

sealed interface MainEffect {
    data object TimerStarted : MainEffect
    data object TimerStopped : MainEffect
    data object SleepSaved : MainEffect
    data object TooShort : MainEffect
}
