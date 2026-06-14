package com.example.timefriend.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class WebDavHelper(
    private val server: String,
    private val username: String,
    private val password: String,
    private val remotePath: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val credentials = Credentials.basic(username, password)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun fullUrl(): String {
        val base = server.trimEnd('/')
        val path = remotePath.trimStart('/')
        return "$base/$path"
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
                throw RuntimeException("Upload failed: ${response.code} ${response.message}")
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
                else -> throw RuntimeException("Download failed: ${response.code} ${response.message}")
            }
        }
    }
}
