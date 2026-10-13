package com.example.timefriend.data

import android.app.Application
import androidx.room.Room
import com.example.timefriend.data.db.AppDatabase
import com.example.timefriend.wearable.WearableLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Simple manual dependency container (no DI framework).
 * Owns the process-wide singletons shared by ViewModels and Services:
 * the OkHttpClient (connection pool reuse), the Room database and the
 * single [TimeRepository] instance (eliminates the previous dual-instance
 * Mutex race between MainViewModel and TimerService).
 */
class AppContainer(private val app: Application) {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(app, AppDatabase::class.java, "timefriend.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val repository: TimeRepository by lazy {
        TimeRepository(app, database, database.timeRecordDao())
    }

    /** 小米 Vela interconnect 手机端接收器：监听手表，把番茄钟周期写入 Room。 */
    val wearableLink: WearableLink by lazy {
        WearableLink.get(app, repository).also { it.start() }
    }

    /** One-time migration of legacy SharedPreferences records into Room (idempotent). */
    fun migrateLegacyRecords() {
        appScope.launch { repository.migrateFromSharedPreferences() }
        // 启动手表数据通道（会话由本端主导建立，失败仅记日志不阻断主流程）
        runCatching { wearableLink }.onFailure { /* 无 xms-wearable AAR 时安全跳过 */ }
    }
}
