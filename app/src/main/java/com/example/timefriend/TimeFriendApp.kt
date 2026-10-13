package com.example.timefriend

import android.app.Application
import android.util.Log
import com.example.timefriend.data.AppContainer
import com.example.timefriend.wearable.WearableLink

class TimeFriendApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.migrateLegacyRecords()

        // 手表同步服务：安全口径同 Sportlink——每次冷启动强制复位为「关闭」，
        // 不按上次保存的状态自动唤起表端。开启状态仅对当前进程有效，
        // 需要同步时由用户在「设置 → 手表同步服务」手动开启（开启即唤起表端建立会话）。
        // 这样打开 App 不会自动弹出手表端同步页。
        val prefs = getSharedPreferences(WearableLink.PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(WearableLink.KEY_WATCH_SYNC_ENABLED, false)) {
            prefs.edit().putBoolean(WearableLink.KEY_WATCH_SYNC_ENABLED, false).apply()
            Log.i("TimeFriendApp", "Watch sync service force-disabled on cold start (security policy)")
        }
    }
}
