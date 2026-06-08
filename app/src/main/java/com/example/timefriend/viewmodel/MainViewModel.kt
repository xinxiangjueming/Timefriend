package com.example.timefriend.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord
import com.example.timefriend.data.TimeRepository
import com.example.timefriend.data.WebDavHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TimeRepository(application)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _state = MutableStateFlow(MainState(selectedDate = dateFormat.format(Date())))
    val state: StateFlow<MainState> = _state.asStateFlow()
    private val currentState: MainState get() = _state.value

    private val _effect = Channel<MainEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private var timerJob: Job? = null

    init {
        val today = dateFormat.format(Date())
        viewModelScope.launch {
            loadToday()
            loadRecordDates()
            loadHistory(today)
            loadWeekRecords(today)
            loadMonthRecords(today)
        }
    }

    fun handleIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.SwitchTab -> {
                if (currentState.showSettings) saveWebDavSettings()
                setState { copy(currentTab = intent.tabIndex, showSettings = false, syncMessage = null, syncStatus = SyncStatus.IDLE) }
            }
            is MainIntent.StartTimer -> startTimer(intent.category)
            is MainIntent.StopTimer -> stopTimer()
            is MainIntent.SaveSleep -> saveSleep(intent.minutes)
            is MainIntent.SelectHistoryDate -> selectHistoryDate(intent.date)
            is MainIntent.UpdateTimerElapsed -> setState { copy(timerElapsed = intent.elapsed) }
            is MainIntent.SavePomodoroRecord -> savePomodoroRecord(intent.category, intent.minutes)
            is MainIntent.ShowSettings -> { loadWebDavSettings(); setState { copy(showSettings = true) } }
            is MainIntent.HideSettings -> { saveWebDavSettings(); setState { copy(showSettings = false, syncMessage = null, syncStatus = SyncStatus.IDLE) } }
            is MainIntent.UpdateWebDavServer -> setState { copy(webDavServer = intent.value) }
            is MainIntent.UpdateWebDavUsername -> setState { copy(webDavUsername = intent.value) }
            is MainIntent.UpdateWebDavPassword -> setState { copy(webDavPassword = intent.value) }
            is MainIntent.UpdateWebDavPath -> setState { copy(webDavPath = intent.value) }
            is MainIntent.WebDavBackup -> webDavBackup()
            is MainIntent.WebDavSync -> webDavSync()
        }
    }

    private fun savePomodoroRecord(category: TimeCategory, minutes: Int) {
        val now = System.currentTimeMillis()
        val record = TimeRecord(
            category = category,
            startTime = now - minutes * 60_000L,
            endTime = now,
            date = repo.todayDateString()
        )
        viewModelScope.launch {
            repo.saveRecord(record)
            loadToday()
            loadRecordDates()
            loadHistory(currentState.selectedDate)
        }
    }

    private fun startTimer(category: TimeCategory) {
        if (category == TimeCategory.SLEEP) return
        if (currentState.activeCategory != null) return

        val now = System.currentTimeMillis()
        setState { copy(activeCategory = category, activeStartTime = now, timerElapsed = 0L) }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val elapsed = System.currentTimeMillis() - currentState.activeStartTime
                setState { copy(timerElapsed = elapsed) }
            }
        }
    }

    private fun stopTimer() {
        val category = currentState.activeCategory ?: return
        val startTime = currentState.activeStartTime
        val endTime = System.currentTimeMillis()

        timerJob?.cancel()
        timerJob = null

        val durationMs = endTime - startTime
        if (durationMs < 3 * 60 * 1000L) {
            setState { copy(activeCategory = null, activeStartTime = 0L, timerElapsed = 0L) }
            viewModelScope.launch { _effect.send(MainEffect.TooShort) }
            return
        }

        val record = TimeRecord(
            category = category,
            startTime = startTime,
            endTime = endTime,
            date = repo.todayDateString()
        )

        setState { copy(activeCategory = null, activeStartTime = 0L, timerElapsed = 0L) }

        viewModelScope.launch {
            repo.saveRecord(record)
            loadToday()
            loadRecordDates()
            loadHistory(currentState.selectedDate)
        }
    }

    private fun saveSleep(minutes: Int) {
        val now = System.currentTimeMillis()
        val record = TimeRecord(
            category = TimeCategory.SLEEP,
            startTime = now - minutes * 60_000L,
            endTime = now,
            date = repo.todayDateString()
        )

        setState { copy(sleepDurationMinutes = minutes) }

        viewModelScope.launch {
            repo.replaceRecord(record)
            loadToday()
            loadRecordDates()
            loadHistory(currentState.selectedDate)
        }
    }

    private fun selectHistoryDate(date: String) {
        setState { copy(selectedDate = date) }
        viewModelScope.launch {
            loadHistory(date)
            loadWeekRecords(date)
            loadMonthRecords(date)
        }
    }

    private suspend fun loadToday() {
        val records = repo.getRecords(repo.todayDateString())
        setState { copy(todayRecords = records) }
    }

    private suspend fun loadWeekRecords(dateStr: String) {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        cal.time = sdf.parse(dateStr) ?: Date()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val weekRecords = mutableListOf<TimeRecord>()
        repeat(7) {
            weekRecords.addAll(repo.getRecords(sdf.format(cal.time)))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        setState { copy(weekRecords = weekRecords) }
    }

    private suspend fun loadMonthRecords(dateStr: String) {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        cal.time = sdf.parse(dateStr) ?: Date()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthRecords = mutableListOf<TimeRecord>()
        repeat(daysInMonth) {
            monthRecords.addAll(repo.getRecords(sdf.format(cal.time)))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        setState { copy(monthRecords = monthRecords) }
    }

    private suspend fun loadHistory(date: String) {
        val records = repo.getRecords(date)
        setState { copy(historyRecords = records) }
    }

    private suspend fun loadRecordDates() {
        val dates = repo.getRecordDates()
        setState { copy(recordDates = dates) }
    }

    private fun setState(reducer: MainState.() -> MainState) {
        _state.value = currentState.reducer()
    }

    // ── WebDAV settings persistence ──

    private val webDavPrefs =
        application.getSharedPreferences("webdav_prefs", android.content.Context.MODE_PRIVATE)

    private fun loadWebDavSettings() {
        setState {
            copy(
                webDavServer = webDavPrefs.getString("server", "") ?: "",
                webDavUsername = webDavPrefs.getString("username", "") ?: "",
                webDavPassword = webDavPrefs.getString("password", "") ?: "",
                webDavPath = webDavPrefs.getString("path", "timefriend_backup.json") ?: "timefriend_backup.json"
            )
        }
    }

    private fun saveWebDavSettings() {
        webDavPrefs.edit()
            .putString("server", currentState.webDavServer)
            .putString("username", currentState.webDavUsername)
            .putString("password", currentState.webDavPassword)
            .putString("path", currentState.webDavPath)
            .apply()
    }

    // ── WebDAV backup / sync ──

    private fun webDavBackup() {
        val s = currentState
        if (s.webDavServer.isBlank() || s.webDavUsername.isBlank() || s.webDavPath.isBlank()) {
            setState { copy(syncStatus = SyncStatus.ERROR, syncMessage = "请填写完整信息") }
            return
        }
        setState { copy(syncStatus = SyncStatus.LOADING, syncMessage = null) }
        viewModelScope.launch {
            try {
                val json = repo.exportAllJson()
                WebDavHelper(s.webDavServer, s.webDavUsername, s.webDavPassword, s.webDavPath)
                    .upload(json)
                setState { copy(syncStatus = SyncStatus.SUCCESS, syncMessage = "备份成功") }
                loadToday(); loadRecordDates(); loadHistory(currentState.selectedDate)
            } catch (e: Exception) {
                setState { copy(syncStatus = SyncStatus.ERROR, syncMessage = "网络错误：${e.message}") }
            }
        }
    }

    private fun webDavSync() {
        val s = currentState
        if (s.webDavServer.isBlank() || s.webDavUsername.isBlank() || s.webDavPath.isBlank()) {
            setState { copy(syncStatus = SyncStatus.ERROR, syncMessage = "请填写完整信息") }
            return
        }
        setState { copy(syncStatus = SyncStatus.LOADING, syncMessage = null) }
        viewModelScope.launch {
            try {
                val json = WebDavHelper(s.webDavServer, s.webDavUsername, s.webDavPassword, s.webDavPath)
                    .download()
                if (json != null) {
                    repo.importAllJson(json)
                    setState { copy(syncStatus = SyncStatus.SUCCESS, syncMessage = "同步成功") }
                } else {
                    setState { copy(syncStatus = SyncStatus.ERROR, syncMessage = "云端无备份文件") }
                }
                loadToday(); loadRecordDates(); loadHistory(currentState.selectedDate)
            } catch (e: Exception) {
                setState { copy(syncStatus = SyncStatus.ERROR, syncMessage = "网络错误：${e.message}") }
            }
        }
    }
}
