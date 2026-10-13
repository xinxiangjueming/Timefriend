package com.example.timefriend.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.timefriend.MainActivity
import com.example.timefriend.R
import com.example.timefriend.TimeFriendApp
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord
import kotlinx.coroutines.*

class PomodoroService : Service() {

    companion object {
        const val CHANNEL_ID = "pomodoro_timer"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.timefriend.POMODORO_START"
        const val ACTION_PAUSE = "com.example.timefriend.POMODORO_PAUSE"
        const val ACTION_RESUME = "com.example.timefriend.POMODORO_RESUME"
        const val ACTION_STOP = "com.example.timefriend.POMODORO_STOP"
        const val ACTION_REQUEST_STATE = "com.example.timefriend.POMODORO_REQUEST_STATE"

        const val ACTION_STATE_UPDATE = "com.example.timefriend.POMODORO_STATE"
        const val EXTRA_REMAINING = "remaining"
        const val EXTRA_IS_FOCUS = "is_focus"
        const val EXTRA_IS_PAUSED = "is_paused"
        const val EXTRA_FOCUS_MIN = "focus_min"
        const val EXTRA_REST_MIN = "rest_min"
        const val EXTRA_CYCLES = "cycles"
    }

    private var remainingSeconds = 0
    private var isFocusPhase = true
    private var isPaused = false
    private var focusMinutes = 25
    private var restMinutes = 5
    // Multi-cycle: total focus+rest pairs requested at ACTION_START, and how
    // many remain (decremented each time a rest phase ends naturally).
    private var cyclesTotal = 1
    private var cyclesRemaining = 1
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    // Written by the Service at phase transitions (not by the UI), so pomodoro
    // sessions are recorded reliably even when no UI is composed in the foreground.
    // Row is inserted when focus completes; rest minutes are filled in later.
    @Volatile
    private var sessionId: Long = -1L
    private val repo get() = (application as TimeFriendApp).container.repository

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PAUSE -> pause()
                ACTION_RESUME -> resume()
                ACTION_STOP -> stop(manual = true)
                ACTION_REQUEST_STATE -> sendStateUpdate()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
            addAction(ACTION_STOP)
            addAction(ACTION_REQUEST_STATE)
        }
        ContextCompat.registerReceiver(this, controlReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                focusMinutes = intent.getIntExtra(EXTRA_FOCUS_MIN, 25)
                restMinutes = intent.getIntExtra(EXTRA_REST_MIN, 5)
                cyclesTotal = intent.getIntExtra(EXTRA_CYCLES, 1).coerceAtLeast(1)
                startTimer()
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop(manual = true)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        scope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(controlReceiver) } catch (_: Exception) {}
    }

    private fun startTimer() {
        isFocusPhase = true
        isPaused = false
        remainingSeconds = focusMinutes * 60
        cyclesRemaining = cyclesTotal
        sessionId = -1L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        sendStateUpdate()
        startCountdown()
    }

    private fun pause() {
        isPaused = true
        timerJob?.cancel()
        updateNotification()
        sendStateUpdate()
    }

    private fun resume() {
        isPaused = false
        updateNotification()
        sendStateUpdate()
        startCountdown()
    }

    private fun stop(manual: Boolean) {
        // Persist elapsed time BEFORE zeroing remainingSeconds. Net minutes =
        // configured minutes minus the not-yet-counted remainder (paused time
        // never decrements the countdown, so it is excluded automatically).
        if (manual) {
            if (isFocusPhase) {
                val elapsed = focusMinutes - (remainingSeconds + 59) / 60
                if (elapsed >= 3) recordFocus(elapsed)
            } else {
                val elapsed = restMinutes - (remainingSeconds + 59) / 60
                if (elapsed >= 3) recordRest(elapsed)
            }
        } else {
            // Natural end: only the rest phase reaches stop() (focus switches
            // to rest instead and is recorded in handlePhaseEnd).
            recordRest(restMinutes)
        }
        timerJob?.cancel()
        remainingSeconds = 0
        sendStateUpdate()
        // Lifecycle calls must run on main thread; handlePhaseEnd() may call from Dispatchers.Default
        mainHandler.post {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (remainingSeconds > 0 && !isPaused) {
                delay(1000)
                remainingSeconds--
                sendStateUpdate()
                updateNotification()
                if (remainingSeconds == 0) {
                    handlePhaseEnd()
                }
            }
        }
    }

    private fun handlePhaseEnd() {
        if (isFocusPhase) {
            isFocusPhase = false
            remainingSeconds = restMinutes * 60
            // Focus completed naturally: net focus time = focusMinutes (paused
            // time never decrements the countdown). Creates the session row;
            // rest minutes are filled in when the rest phase ends.
            recordFocus(focusMinutes)
            sendStateUpdate()
            updateNotification()
            startCountdown()
        } else {
            // Rest phase ended: count the finished focus+rest pair, then either
            // roll into the next focus phase (multi-cycle) or stop after the
            // final one (stop(manual=false) records the last rest itself).
            cyclesRemaining--
            if (cyclesRemaining > 0) {
                recordRest(restMinutes)
                isFocusPhase = true
                remainingSeconds = focusMinutes * 60
                sendStateUpdate()
                updateNotification()
                startCountdown()
            } else {
                sendStateUpdate()
                stop(manual = false)
            }
        }
    }

    // ── Recording (Room, async) ──

    /**
     * Persist a completed focus phase: the time record (INVESTMENT, feeds the
     * today pie chart) plus a new pomodoro_sessions row (rest = 0 for now).
     */
    private fun recordFocus(minutes: Int) {
        // Invalidate the previous cycle id up front: if the rest phase ends
        // before the async insert completes, rest falls back to inserting a
        // standalone row instead of updating a stale session.
        sessionId = -1L
        val date = repo.todayDateString()
        scope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                repo.saveRecord(
                    TimeRecord(
                        category = TimeCategory.INVESTMENT,
                        startTime = now - minutes * 60_000L,
                        endTime = now,
                        date = date
                    )
                )
                sessionId = repo.insertPomodoroSession(focusMinutes = minutes, restMinutes = 0, date = date)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Persist a completed rest phase: the time record (FIXED) plus filling in
     * the rest minutes of the session row created at focus end. If no session
     * row exists (insert still in flight), a rest-only row is inserted.
     */
    private fun recordRest(minutes: Int) {
        val sid = sessionId
        scope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                repo.saveRecord(
                    TimeRecord(
                        category = TimeCategory.FIXED,
                        startTime = now - minutes * 60_000L,
                        endTime = now,
                        date = repo.todayDateString()
                    )
                )
                if (sid > 0) repo.updatePomodoroSessionRest(sid, minutes)
                else repo.insertPomodoroSession(focusMinutes = 0, restMinutes = minutes)
            } catch (_: Exception) {
            }
        }
    }

    private fun sendStateUpdate() {
        val intent = Intent(ACTION_STATE_UPDATE).apply {
            putExtra(EXTRA_REMAINING, remainingSeconds)
            putExtra(EXTRA_IS_FOCUS, isFocusPhase)
            putExtra(EXTRA_IS_PAUSED, isPaused)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ── Notification ──

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.pomodoro_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.pomodoro_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val mm = remainingSeconds / 60
        val ss = remainingSeconds % 60
        val timeText = String.format("%02d:%02d", mm, ss)
        val phaseText = if (isFocusPhase) getString(R.string.pomodoro_notif_focus) else getString(R.string.pomodoro_notif_rest)

        // Open app intent
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopActionIntent = Intent(this, PomodoroService::class.java).apply { action = ACTION_STOP }
        val stopIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this, 1, stopActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this, 1, stopActionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val stopAction = Notification.Action.Builder(
            null, getString(R.string.pomodoro_notif_stop), stopIntent
        ).build()

        // Build with platform Notification.Builder (not NotificationCompat)
        val builder = Notification.Builder(this, CHANNEL_ID)

        builder
            .setContentTitle(timeText)
            .setContentText(phaseText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)
            .addAction(stopAction)

        // MIUI Super Island / Focus Notification
        try {
            val extras = Bundle()
            val focusParam = org.json.JSONObject().apply {
                put("ticker", "$phaseText $timeText")
                put("baseInfo", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("type", "text")
                        put("content", phaseText)
                    })
                    put(org.json.JSONObject().apply {
                        put("type", "timer")
                        put("content", timeText)
                    })
                })
                put("hintInfo", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("type", "text")
                        put("content", if (isPaused) getString(R.string.pomodoro_notif_paused) else getString(R.string.pomodoro_notif_running))
                    })
                })
            }
            extras.putString("miui.focus.param", focusParam.toString())
            builder.addExtras(extras)
        } catch (_: Exception) {}

        // API 34+ promoted notification (live update in status bar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val canPromote = notificationManager.javaClass
                    .getMethod("canPostPromotedNotifications")
                    .invoke(notificationManager) as? Boolean ?: false
                if (canPromote) {
                    val extras = Bundle()
                    extras.putBoolean("android.requestPromotedOngoing", true)
                    builder.addExtras(extras)
                }
            } catch (_: Exception) {}
        }

        return builder.build()
    }

    private fun updateNotification() {
        mainHandler.post {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }
}
