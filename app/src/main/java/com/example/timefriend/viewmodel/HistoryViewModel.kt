package com.example.timefriend.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timefriend.TimeFriendApp
import com.example.timefriend.data.db.PomodoroSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Manages history data: selected date, day/week/month records and the calendar
 * dot dates. Everything is derived from Room flows, so any write (timer stop,
 * sleep, pomodoro, WebDAV import) refreshes the UI automatically.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo get() = (getApplication<Application>() as TimeFriendApp).container.repository
    private val dateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    private val selectedDate = MutableStateFlow(repo.todayDateString())

    val state: StateFlow<HistoryUiState> =
        combine(
            selectedDate,
            selectedDate.flatMapLatest { repo.observeRecords(it) },
            selectedDate.flatMapLatest { repo.observeDateRange(weekStart(it), weekEnd(it)) },
            selectedDate.flatMapLatest { repo.observeDateRange(monthStart(it), monthEnd(it)) },
            repo.observeRecordDates()
        ) { d, history, week, month, dates ->
            HistoryUiState(
                selectedDate = d,
                historyRecords = history,
                weekRecords = week,
                monthRecords = month,
                recordDates = dates
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    fun selectDate(date: String) {
        selectedDate.value = date
    }

    // ── Pomodoro week (Monday-anchored current week: Mon..Sun) ──
    // 与卡片文案（"本周投入/休息时间"）及饼图周模式（weekStart = previousOrSame MONDAY）
    // 的"周"定义一致；原"今天为锚点的最近 7 天"窗口会导致星期标签从周中开始
    //（如今天周二 → 三四五六日一二），与"周统计"语义不符（用户 2026-10-13 反馈）。

    private val pomodoroWindowStart: String =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(dateFormat)
    private val pomodoroWindowEnd: String =
        LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).format(dateFormat)

    /** Aggregated focus/rest minutes per day for the current week (Mon..Sun), oldest first. */
    val pomodoroWeek: StateFlow<List<DailyPomodoro>> =
        repo.observePomodoroRange(pomodoroWindowStart, pomodoroWindowEnd)
            .map { sessions -> buildPomodoroWeek(sessions) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun buildPomodoroWeek(sessions: List<PomodoroSessionEntity>): List<DailyPomodoro> {
        val byDate = sessions.groupBy { it.date }
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0..6).map { offset ->
            val date = monday.plusDays(offset.toLong()).format(dateFormat)
            val daySessions = byDate[date]
            DailyPomodoro(
                date = date,
                focusMinutes = daySessions?.sumOf { it.focusMinutes } ?: 0,
                restMinutes = daySessions?.sumOf { it.restMinutes } ?: 0
            )
        }
    }

    private fun weekStart(dateStr: String): String {
        val date = LocalDate.parse(dateStr, dateFormat)
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).format(dateFormat)
    }

    private fun weekEnd(dateStr: String): String {
        val date = LocalDate.parse(dateStr, dateFormat)
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).format(dateFormat)
    }

    private fun monthStart(dateStr: String): String {
        val month = YearMonth.from(LocalDate.parse(dateStr, dateFormat))
        return month.atDay(1).format(dateFormat)
    }

    private fun monthEnd(dateStr: String): String {
        val month = YearMonth.from(LocalDate.parse(dateStr, dateFormat))
        return month.atEndOfMonth().format(dateFormat)
    }
}
