package io.legado.desktop.engine.network

import io.legado.desktop.engine.rule.RuleAnalyzer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    /**
     * Smart request handler that supports Legado 3.0 URL format:
     * - Automatic relative URL resolution against baseUrl
     * - Parse option JSON suffix: url, { "method": "POST", "body": "...", "headers": {...} }
     */
    suspend fun smartRequest(
        baseUrl: String,
        rawUrlWithOption: String,
        defaultHeaders: Map<String, String> = emptyMap()
    ): String {
        val trimmed = rawUrlWithOption.trim()
        if (trimmed.isEmpty()) return ""

        var targetUrl = trimmed
        var method = "GET"
        var body: String? = null
        val reqHeaders = defaultHeaders.toMutableMap()
        var contentType = "application/x-www-form-urlencoded; charset=utf-8"

        val commaIndex = trimmed.indexOf(",{")
        val altCommaIndex = if (commaIndex == -1) trimmed.indexOf(", {") else commaIndex

        if (altCommaIndex != -1) {
            targetUrl = trimmed.substring(0, altCommaIndex).trim()
            val optionJson = trimmed.substring(trimmed.indexOf('{')).trim()
            try {
                val jsonObj = Json.parseToJsonElement(optionJson).jsonObject
                jsonObj["method"]?.jsonPrimitive?.contentOrNull?.let { method = it.uppercase() }
                jsonObj["body"]?.jsonPrimitive?.contentOrNull?.let { body = it }
                jsonObj["headers"]?.jsonObject?.forEach { (k, v) ->
                    v.jsonPrimitive.contentOrNull?.let { reqHeaders[k] = it }
                }
                if (body != null && body!!.trim().startsWith("{")) {
                    contentType = "application/json; charset=utf-8"
                }
            } catch (_: Exception) {}
        }

        val finalUrl = RuleAnalyzer.resolveUrl(baseUrl, targetUrl)
        if (finalUrl.isEmpty() || (!finalUrl.startsWith("http://") && !finalUrl.startsWith("https://"))) {
            return ""
        }

        return if (method == "POST") {
            post(finalUrl, body ?: "", contentType, reqHeaders)
        } else {
            get(finalUrl, reqHeaders)
        }
    }
}
