package io.legado.desktop.engine.network

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object HttpHelper {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): String {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", headers["User-Agent"] ?: DEFAULT_USER_AGENT)

        headers.forEach { (k, v) ->
            if (!k.equals("User-Agent", ignoreCase = true)) {
                requestBuilder.header(k, v)
            }
        }

        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: ""
        }
    }

    suspend fun post(
        url: String,
        body: String,
        contentType: String = "application/x-www-form-urlencoded; charset=utf-8",
        headers: Map<String, String> = emptyMap()
    ): String {
        val mediaType = contentType.toMediaTypeOrNull()
        val requestBody = body.toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("User-Agent", headers["User-Agent"] ?: DEFAULT_USER_AGENT)

        headers.forEach { (k, v) ->
            if (!k.equals("User-Agent", ignoreCase = true)) {
                requestBuilder.header(k, v)
            }
        }

        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string() ?: ""
        }
    }
}
