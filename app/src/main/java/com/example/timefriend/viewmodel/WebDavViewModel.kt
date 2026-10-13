package com.example.timefriend.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import java.io.File
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.timefriend.R
import com.example.timefriend.TimeFriendApp
import com.example.timefriend.data.WebDavErrorKind
import com.example.timefriend.data.WebDavException
import com.example.timefriend.data.WebDavHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages WebDAV configuration (encrypted persistence) and backup/sync.
 * Uses the shared OkHttpClient singleton from the AppContainer.
 */
class WebDavViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()
    private val container get() = (getApplication<Application>() as TimeFriendApp).container
    private val repo get() = container.repository

    private val _state = MutableStateFlow(WebDavUiState())
    val state: StateFlow<WebDavUiState> = _state.asStateFlow()
    private val currentState: WebDavUiState get() = _state.value

    /** 旧版自定义备份路径（磁盘保留值），用于同步时的兼容回退。 */
    private var legacyWebDavPath: String? = null

    /**
     * 加密配置懒加载：首次访问才做 MasterKey(KeyStore)/Tink 初始化，
     * 避免冷启动时在 ViewModel 构造（主线程）上白白消耗 100~300ms
     * （这正是"刚打开 App 时切换界面掉帧生硬"的元凶之一）。
     * [warmUpWebDavPrefs] 会在 IO 线程提前预热，打开设置页时通常已就绪。
     */
    private val webDavPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(getApplication<Application>())
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            getApplication<Application>(),
            "webdav_prefs_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { encrypted ->
            // One-time migration: copy any existing plaintext prefs into encrypted storage
            val legacy = getApplication<Application>().getSharedPreferences("webdav_prefs", Context.MODE_PRIVATE)
            if (legacy.all.isNotEmpty()) {
                encrypted.edit().apply {
                    legacy.all.forEach { (key, value) ->
                        if (value is String && !encrypted.contains(key)) putString(key, value)
                    }
                    apply()
                }
                legacy.edit().clear().apply()
            }
        }
    }

    init {
        // 后台预热加密配置，避免首次打开设置页时卡顿（lazy 线程安全，重复访问无副作用）
        viewModelScope.launch(Dispatchers.IO) { webDavPrefs }
    }

    fun loadSettings() {
        // 旧版自定义备份路径不再向 UI 暴露，仅读取到内存供同步回退
        legacyWebDavPath = webDavPrefs.getString("path", null)
        _state.value = WebDavUiState(
            webDavServer = webDavPrefs.getString("server", "") ?: "",
            webDavUsername = webDavPrefs.getString("username", "") ?: "",
            webDavPassword = webDavPrefs.getString("password", "") ?: ""
        )
    }

    fun saveSettings() {
        webDavPrefs.edit()
            .putString("server", currentState.webDavServer)
            .putString("username", currentState.webDavUsername)
            .putString("password", currentState.webDavPassword)
            // 不再写入 "path"：磁盘旧值保留，供 legacyWebDavPath 回退使用
            .apply()
    }

    fun resetStatus() {
        _state.value = currentState.copy(syncStatus = SyncStatus.IDLE, syncMessage = null)
    }

    fun updateServer(value: String) {
        _state.value = currentState.copy(webDavServer = value)
    }

    fun updateUsername(value: String) {
        _state.value = currentState.copy(webDavUsername = value)
    }

    fun updatePassword(value: String) {
        _state.value = currentState.copy(webDavPassword = value)
    }

    fun backup() {
        val s = currentState
        if (s.webDavServer.isBlank() || s.webDavUsername.isBlank()) {
            _state.value = s.copy(syncStatus = SyncStatus.ERROR, syncMessage = ctx.getString(R.string.webdav_error_empty))
            return
        }
        _state.value = s.copy(syncStatus = SyncStatus.LOADING, syncMessage = null)
        viewModelScope.launch {
            try {
                // 空库保护：本地无任何记录则不覆盖云端
                if (repo.isEmpty()) {
                    _state.value = _state.value.copy(
                        syncStatus = SyncStatus.ERROR,
                        syncMessage = ctx.getString(R.string.webdav_no_local_data)
                    )
                    return@launch
                }
                val zst = repo.backupRoomDb()
                try {
                    val helper = WebDavHelper(
                        s.webDavServer, s.webDavUsername, s.webDavPassword,
                        WebDavHelper.REMOTE_DB_RELPATH, container.okHttpClient
                    )
                    helper.ensureDirectory() // 自动在云端创建 Timefriend 目录（幂等）
                    helper.uploadFile(WebDavHelper.REMOTE_DB_RELPATH, zst)
                    _state.value = _state.value.copy(
                        syncStatus = SyncStatus.SUCCESS,
                        syncMessage = ctx.getString(R.string.webdav_backup_success)
                    )
                    // History refresh is automatic via Room flows.
                } finally {
                    zst.delete()
                }
            } catch (e: WebDavException) {
                _state.value = _state.value.copy(syncStatus = SyncStatus.ERROR, syncMessage = webDavErrorMessage(e))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    syncStatus = SyncStatus.ERROR,
                    syncMessage = ctx.getString(R.string.webdav_error_network, e.message)
                )
            }
        }
    }

    fun sync() {
        val s = currentState
        if (s.webDavServer.isBlank() || s.webDavUsername.isBlank()) {
            _state.value = s.copy(syncStatus = SyncStatus.ERROR, syncMessage = ctx.getString(R.string.webdav_error_empty))
            return
        }
        _state.value = s.copy(syncStatus = SyncStatus.LOADING, syncMessage = null)
        viewModelScope.launch {
            try {
                // 1) 优先从 zstd 压缩库恢复（整体下载 + 按主键并入，只补缺不覆盖）
                val dbHelper = WebDavHelper(
                    s.webDavServer, s.webDavUsername, s.webDavPassword,
                    WebDavHelper.REMOTE_DB_RELPATH, container.okHttpClient
                )
                val zst = File(ctx.cacheDir, "timefriend_restore.db.zst")
                if (dbHelper.downloadFile(WebDavHelper.REMOTE_DB_RELPATH, zst)) {
                    try {
                        repo.restoreRoomDbFrom(zst)
                        _state.value = _state.value.copy(
                            syncStatus = SyncStatus.SUCCESS,
                            syncMessage = ctx.getString(R.string.webdav_sync_success)
                        )
                    } finally {
                        zst.delete()
                    }
                    return@launch
                }
                // 2) 兼容回退：老用户的旧版 JSON 备份
                val helper = WebDavHelper(
                    s.webDavServer, s.webDavUsername, s.webDavPassword,
                    WebDavHelper.REMOTE_FILE_PATH, container.okHttpClient
                )
                var json = helper.download() // 新固定路径
                val legacy = legacyWebDavPath
                if (json == null && !legacy.isNullOrBlank() && legacy != WebDavHelper.REMOTE_FILE_PATH) {
                    // 老用户旧版自定义路径
                    json = WebDavHelper(s.webDavServer, s.webDavUsername, s.webDavPassword, legacy, container.okHttpClient)
                        .download()
                }
                if (json != null) {
                    repo.importAllJson(json)
                    _state.value = _state.value.copy(
                        syncStatus = SyncStatus.SUCCESS,
                        syncMessage = ctx.getString(R.string.webdav_sync_success)
                    )
                } else {
                    _state.value = _state.value.copy(
                        syncStatus = SyncStatus.ERROR,
                        syncMessage = ctx.getString(R.string.webdav_no_backup)
                    )
                }
                // History refresh is automatic via Room flows.
            } catch (e: WebDavException) {
                _state.value = _state.value.copy(syncStatus = SyncStatus.ERROR, syncMessage = webDavErrorMessage(e))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    syncStatus = SyncStatus.ERROR,
                    syncMessage = ctx.getString(R.string.webdav_error_network, e.message)
                )
            }
        }
    }

    private fun webDavErrorMessage(e: WebDavException): String = when (e.kind) {
        WebDavErrorKind.MKCOL -> ctx.getString(R.string.webdav_error_mkcol, e.message ?: "")
        WebDavErrorKind.AUTH -> ctx.getString(R.string.webdav_error_auth)
        WebDavErrorKind.UPLOAD -> ctx.getString(R.string.webdav_error_upload, e.message ?: "")
        WebDavErrorKind.DOWNLOAD -> ctx.getString(R.string.webdav_error_network, e.message ?: "")
    }
}
