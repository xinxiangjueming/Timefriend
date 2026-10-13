package com.example.timefriend.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** WebDAV 错误分类，用于向用户展示不同提示。 */
enum class WebDavErrorKind { MKCOL, UPLOAD, DOWNLOAD, AUTH }

class WebDavException(val kind: WebDavErrorKind, message: String) : RuntimeException(message)

/**
 * WebDAV 客户端。使用 [client] 共享 OkHttpClient（进程级单例，复用连接池）。
 */
class WebDavHelper(
    private val server: String,
    private val username: String,
    private val password: String,
    remotePath: String,
    private val client: OkHttpClient
) {
    /** 规范化后的相对路径（无首尾斜杠、过滤空段）。 */
    private val remotePath: String = normalizePath(remotePath)

    private val credentials = Credentials.basic(username, password)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    companion object {
        const val DIRECTORY_NAME = "Timefriend"
        const val BACKUP_FILE_NAME = "timefriend_backup.json"
        /** 云端固定相对路径：Timefriend/timefriend_backup.json */
        const val REMOTE_FILE_PATH = "$DIRECTORY_NAME/$BACKUP_FILE_NAME"

        /** 云端 Room 库压缩包相对路径（zstd 单文件备份）。 */
        const val REMOTE_DB_RELPATH = "$DIRECTORY_NAME/timefriend.db.zst"

        /** 规范化相对路径：去首尾 '/'，过滤空段。 */
        fun normalizePath(path: String): String =
            path.trim().trim('/').split('/').filter { it.isNotBlank() }.joinToString("/")

        /** 拼接完整 URL：server 尾斜杠与 path 前导斜杠均安全。 */
        fun buildUrl(server: String, remotePath: String): String =
            server.trimEnd('/') + "/" + normalizePath(remotePath)
    }

    private fun fullUrl(path: String = remotePath): String = buildUrl(server, path)

    /**
     * 确保云端目标目录存在（幂等）。
     * MKCOL 只能创建单级集合，故把文件的父目录段拆开逐级创建。
     */
    suspend fun ensureDirectory() = withContext(Dispatchers.IO) {
        val dirSegments = remotePath.split('/').dropLast(1)
        var current = ""
        for (seg in dirSegments) {
            current = if (current.isEmpty()) seg else "$current/$seg"
            mkcolIfNeeded(fullUrl(current))
        }
    }

    /** MKCOL 单级目录；2xx/405 = 已就绪，409 = 探测区分，401/403 = 认证失败。 */
    private fun mkcolIfNeeded(url: String) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .method("MKCOL", null)
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                in 200..299, 405 -> Unit // 创建成功，或目录已存在（RFC 4918: 405）
                409 -> {
                    // 可能是父级缺失，也可能部分实现把"已存在"报为 409，用 PROPFIND 区分
                    if (!probeExists(url)) {
                        throw WebDavException(
                            WebDavErrorKind.MKCOL,
                            "Cannot create directory $url on server: parent directory missing or server address invalid"
                        )
                    }
                }
                in 401..403 -> throw WebDavException(WebDavErrorKind.AUTH, "认证失败 (HTTP ${response.code})")
                else -> throw WebDavException(
                    WebDavErrorKind.MKCOL,
                    "MKCOL failed: ${response.code} ${response.message}"
                )
            }
        }
    }

    /** PROPFIND Depth:0 探测集合是否存在。 */
    private fun probeExists(url: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .header("Depth", "0")
            .method("PROPFIND", null)
            .build()
        client.newCall(request).execute().use { response ->
            return response.code in 200..299
        }
    }

    /**
     * Upload JSON data to the WebDAV server (PUT request).
     */
    suspend fun upload(json: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(fullUrl())
            .header("Authorization", credentials)
            .put(json.toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val kind = if (response.code in 401..403) WebDavErrorKind.AUTH else WebDavErrorKind.UPLOAD
                throw WebDavException(kind, "Upload failed: ${response.code} ${response.message}")
            }
        }
    }

    /**
     * Download JSON data from the WebDAV server (GET request).
     * Returns null if file doesn't exist (404).
     */
    suspend fun download(): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(fullUrl())
            .header("Authorization", credentials)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                response.code == 404 -> null
                response.code in 401..403 -> throw WebDavException(WebDavErrorKind.AUTH, "认证失败 (HTTP ${response.code})")
                else -> throw WebDavException(WebDavErrorKind.DOWNLOAD, "Download failed: ${response.code} ${response.message}")
            }
        }
    }

    /**
     * Upload a binary file to the WebDAV server (PUT). [remotePath] is the
     * relative path including the "Timefriend/" prefix (e.g. [REMOTE_DB_RELPATH]).
     */
    suspend fun uploadFile(remotePath: String, file: File) = withContext(Dispatchers.IO) {
        val url = buildUrl(server, remotePath)
        val body = file.asRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .put(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val kind = if (response.code in 401..403) WebDavErrorKind.AUTH else WebDavErrorKind.UPLOAD
                throw WebDavException(kind, "Upload failed: ${response.code} ${response.message}")
            }
        }
    }

    /**
     * Download a binary file (GET) into [target].
     * Returns false when the remote file does not exist (HTTP 404, idempotent);
     * any other failure throws [WebDavException].
     */
    suspend fun downloadFile(remotePath: String, target: File): Boolean = withContext(Dispatchers.IO) {
        val url = buildUrl(server, remotePath)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credentials)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> {
                    target.parentFile?.mkdirs()
                    response.body?.byteStream()?.use { input ->
                        target.outputStream().use { out -> input.copyTo(out) }
                    }
                    true
                }
                response.code == 404 -> false
                response.code in 401..403 -> throw WebDavException(WebDavErrorKind.AUTH, "认证失败 (HTTP ${response.code})")
                else -> throw WebDavException(WebDavErrorKind.DOWNLOAD, "Download failed: ${response.code} ${response.message}")
            }
        }
    }
}
