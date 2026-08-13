package com.example.timefriend.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timefriend.TimeFriendApp
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord
import com.example.timefriend.service.TimerService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TimerEffect {
    data object TooShort : TimerEffect
}

/**
 * Manages timer state: the foreground TimerService lifecycle (started on demand,
 * never at app launch), live timer broadcast updates, today's records
 * (auto-refreshed via Room flow) and manual sleep/pomodoro record saving.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()
    private val repo get() = (getApplication<Application>() as TimeFriendApp).container.repository

    private val today = repo.todayDateString()

    private val _ui = MutableStateFlow(TimerUiState())

    val state: StateFlow<TimerUiState> =
        combine(_ui, repo.observeRecords(today)) { ui, todayRecords ->
            ui.copy(todayRecords = todayRecords)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerUiState())

    private val _effect = Channel<TimerEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val catName = intent.getStringExtra(TimerService.EXTRA_CATEGORY)
            val elapsed = intent.getLongExtra(TimerService.EXTRA_ELAPSED_MS, 0L)
            val tooShort = intent.getBooleanExtra(TimerService.EXTRA_TOO_SHORT, false)

            if (catName != null) {
                val cat = TimeCategory.valueOf(catName)
                _ui.value = _ui.value.copy(activeCategory = cat, timerElapsed = elapsed)
            } else {
                _ui.value = _ui.value.copy(activeCategory = null, timerElapsed = 0L)
            }

            if (tooShort) {
                viewModelScope.launch { _effect.send(TimerEffect.TooShort) }
            }
            // Records refresh is automatic via the Room flow (no manual reload).
        }
    }

    init {
        // Register timer broadcast receiver and request current state in case the
        // service is already running. Deliberately does NOT start the service.
        val filter = IntentFilter(TimerService.ACTION_STATE_UPDATE)
        ContextCompat.registerReceiver(ctx, timerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ctx.sendBroadcast(Intent(TimerService.ACTION_QUERY).setPackage(ctx.packageName))
    }

    override fun onCleared() {
        super.onCleared()
        try { ctx.unregisterReceiver(timerReceiver) } catch (_: Exception) {}
    }

    fun startTimer(category: TimeCategory) {
        if (category == TimeCategory.SLEEP) return
        if (_ui.value.activeCategory != null) return

        // Eagerly update UI; the service sends precise state via broadcast.
        _ui.value = _ui.value.copy(activeCategory = category, timerElapsed = 0L)

        ContextCompat.startForegroundService(
            ctx, Intent(ctx, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_CATEGORY, category.name)
            }
        )
    }

    fun stopTimer() {
        if (_ui.value.activeCategory == null) return

        // Use startForegroundService uniformly: the service may have been
        // recycled by the system while the UI still shows an active timer.
        ContextCompat.startForegroundService(
            ctx, Intent(ctx, TimerService::class.java).apply {
                action = TimerService.ACTION_STOP
            }
        )
    }

    fun saveSleep(minutes: Int) {
        val now = System.currentTimeMillis()
        val record = TimeRecord(
            category = TimeCategory.SLEEP,
            startTime = now - minutes * 60_000L,
            endTime = now,
            date = repo.todayDateString()
        )
        _ui.value = _ui.value.copy(sleepDurationMinutes = minutes)
        viewModelScope.launch { repo.replaceRecord(record) }
    }

    fun savePomodoroRecord(category: TimeCategory, minutes: Int) {
        val now = System.currentTimeMillis()
        val record = TimeRecord(
            category = category,
            startTime = now - minutes * 60_000L,
            endTime = now,
            date = repo.todayDateString()
        )
        viewModelScope.launch { repo.saveRecord(record) }
    }
}
