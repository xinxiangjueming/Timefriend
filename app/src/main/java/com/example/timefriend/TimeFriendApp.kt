package com.example.timefriend

import android.app.Application
import com.example.timefriend.data.AppContainer

class TimeFriendApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.migrateLegacyRecords()
    }
}
