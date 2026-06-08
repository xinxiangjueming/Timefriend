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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.timefriend.MainActivity
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
    }

    private var remainingSeconds = 0
    private var isFocusPhase = true
    private var isPaused = false
    private var focusMinutes = 25
    private var restMinutes = 5
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PAUSE -> pause()
                ACTION_RESUME -> resume()
                ACTION_STOP -> stop()
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
                startTimer()
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
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

    private fun stop() {
        timerJob?.cancel()
        remainingSeconds = 0
        sendStateUpdate()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            sendStateUpdate()
            updateNotification()
            startCountdown()
        } else {
            sendStateUpdate()
            stop()
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
            "番茄钟",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "番茄钟倒计时通知"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val mm = remainingSeconds / 60
        val ss = remainingSeconds % 60
        val timeText = String.format("%02d:%02d", mm, ss)
        val phaseText = if (isFocusPhase) "番茄钟" else "休息"

        // Open app intent
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PomodoroService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            null, "停止", stopIntent
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
                        put("content", if (isPaused) "已暂停" else "进行中")
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
