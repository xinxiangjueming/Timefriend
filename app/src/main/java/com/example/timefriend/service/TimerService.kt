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
import com.example.timefriend.data.TimeCategory
import com.example.timefriend.data.TimeRecord
import com.example.timefriend.data.TimeRepository
import kotlinx.coroutines.*

class TimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1002

        const val ACTION_START = "com.example.timefriend.TIMER_START"
        const val ACTION_STOP  = "com.example.timefriend.TIMER_STOP"
        const val ACTION_QUERY = "com.example.timefriend.TIMER_QUERY"

        const val ACTION_STATE_UPDATE = "com.example.timefriend.TIMER_STATE"
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_ELAPSED_MS = "elapsed_ms"
        const val EXTRA_TOO_SHORT = "too_short"
    }

    private var activeCategory: TimeCategory? = null
    private var startTimeMs: Long = 0L
    private var elapsedMs: Long = 0L
    private var tickJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private lateinit var repo: TimeRepository

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_START -> {
                    val name = intent.getStringExtra(EXTRA_CATEGORY) ?: return
                    val cat = TimeCategory.valueOf(name)
                    startTimer(cat)
                }
                ACTION_STOP  -> stopTimer()
                ACTION_QUERY -> broadcastState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = TimeRepository(application)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter().apply {
            addAction(ACTION_START)
            addAction(ACTION_STOP)
            addAction(ACTION_QUERY)
        }
        ContextCompat.registerReceiver(this, controlReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val name = intent.getStringExtra(EXTRA_CATEGORY)
                if (name != null) startTimer(TimeCategory.valueOf(name))
            }
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        tickJob?.cancel()
        scope.cancel()
        try { unregisterReceiver(controlReceiver) } catch (_: Exception) {}
    }

    // ── Timer logic ──

    private fun startTimer(category: TimeCategory) {
        tickJob?.cancel()
        activeCategory = category
        startTimeMs = System.currentTimeMillis()
        elapsedMs = 0L
        broadcastState()
        updateNotification()
        startTicking()
    }

    private fun stopTimer() {
        tickJob?.cancel()
        val cat = activeCategory
        val tooShort: Boolean
        if (cat != null) {
            val durationMs = System.currentTimeMillis() - startTimeMs
            if (durationMs >= 3 * 60 * 1000L) {
                val record = TimeRecord(
                    category = cat,
                    startTime = startTimeMs,
                    endTime = startTimeMs + durationMs,
                    date = repo.todayDateString()
                )
                scope.launch { repo.saveRecord(record) }
                tooShort = false
            } else {
                tooShort = true
            }
        } else {
            tooShort = false
        }
        // Clear state first, then broadcast once with category=null (triggers data reload in ViewModel).
        // For tooShort, send a separate broadcast before clearing so ViewModel receives the flag.
        if (tooShort) {
            broadcastState(tooShort = true)
        }
        activeCategory = null
        startTimeMs = 0L
        elapsedMs = 0L
        broadcastState()
        updateNotification()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (activeCategory != null) {
                delay(1000)
                elapsedMs = System.currentTimeMillis() - startTimeMs
                broadcastState()
                updateNotification()
            }
        }
    }

    private fun broadcastState(tooShort: Boolean = false) {
        val intent = Intent(ACTION_STATE_UPDATE).apply {
            setPackage(packageName)
            activeCategory?.let { putExtra(EXTRA_CATEGORY, it.name) }
            putExtra(EXTRA_ELAPSED_MS, elapsedMs)
            putExtra(EXTRA_TOO_SHORT, tooShort)
        }
        sendBroadcast(intent)
    }

    // ── Notification ──

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.timer_channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        val cat = activeCategory
        if (cat != null) {
            // Active timer: show category name and elapsed time
            val h = elapsedMs / 3_600_000
            val m = (elapsedMs % 3_600_000) / 60_000
            val s = (elapsedMs % 60_000) / 1_000
            val timeStr = String.format("%02d:%02d:%02d", h, m, s)
            val catName = getString(cat.displayNameRes)

            builder
                .setContentTitle(catName)
                .setContentText(timeStr)

            // Stop action button
            val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
            val stopPi = PendingIntent.getService(
                this, 200, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.timer_notif_stop), stopPi).build()
            )
        } else {
            // Idle: three category start buttons
            builder.setContentTitle(getString(R.string.timer_notif_idle_title))
            TimeCategory.entries
                .filter { it != TimeCategory.SLEEP }
                .forEach { category ->
                    val pi = PendingIntent.getService(
                        this, category.ordinal + 100,
                        Intent(this, TimerService::class.java).apply {
                            action = ACTION_START
                            putExtra(EXTRA_CATEGORY, category.name)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(
                        Notification.Action.Builder(null, getString(category.displayNameRes), pi).build()
                    )
                }
        }

        // MIUI Super Island
        try {
            val extras = Bundle()
            val title = if (cat != null) getString(cat.displayNameRes) else getString(R.string.timer_notif_idle_title)
            val content = if (cat != null) {
                val h = elapsedMs / 3_600_000
                val m = (elapsedMs % 3_600_000) / 60_000
                val s = (elapsedMs % 60_000) / 1_000
                String.format("%02d:%02d:%02d", h, m, s)
            } else getString(R.string.timer_notif_tap_start)
            val focusParam = org.json.JSONObject().apply {
                put("ticker", "$title $content")
                put("baseInfo", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply { put("type", "text"); put("content", title) })
                    put(org.json.JSONObject().apply { put("type", "timer"); put("content", content) })
                })
                put("hintInfo", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("type", "text")
                        put("content", if (cat != null) getString(R.string.timer_notif_timing) else getString(R.string.timer_notif_idle_title))
                    })
                })
            }
            extras.putString("miui.focus.param", focusParam.toString())
            builder.addExtras(extras)
        } catch (_: Exception) {}

        // API 34+ promoted notification
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
