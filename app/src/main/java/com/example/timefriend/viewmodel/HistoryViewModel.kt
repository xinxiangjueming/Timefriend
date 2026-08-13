package com.example.timefriend.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timefriend.TimeFriendApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
