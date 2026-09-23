package io.legado.desktop.engine.script

import io.legado.desktop.engine.network.HttpHelper
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

class JsHost(private val baseUrl: String = "") {
    private val memoryStore = ConcurrentHashMap<String, String>()

    fun ajax(url: String): String {
        return runBlocking {
            try {
                val fullUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else if (baseUrl.isNotEmpty()) {
                    java.net.URI(baseUrl).resolve(url).toString()
                } else {
                    url
                }
                val request = Request.Builder().url(fullUrl).build()
                HttpHelper.client.newCall(request).execute().use { response ->
                    response.body?.string() ?: ""
                }
            } catch (e: Exception) {
                ""
            }
        }
    }

    fun put(key: String, value: String): String {
        memoryStore[key] = value
        return value
    }

    fun get(key: String): String {
        return memoryStore[key] ?: ""
    }

    fun base64Decode(str: String): String {
        return try {
            String(Base64.getDecoder().decode(str), Charsets.UTF_8)
        } catch (e: Exception) {
            str
        }
    }

    fun base64Encode(str: String): String {
        return try {
            Base64.getEncoder().encodeToString(str.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            str
        }
    }

    fun md5Encode(str: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(str.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            str
        }
    }

    fun getString(key: String): String = get(key)

    fun getString(jsonOrStr: Any?, rule: String): String {
        if (jsonOrStr == null) return ""
        val text = jsonOrStr.toString().trim()
        val cleanRule = rule.trim().removePrefix("$.").removePrefix("@json:").removePrefix("$")
        return try {
            val element = json.parseToJsonElement(text)
            if (element is JsonObject) {
                var current: JsonElement? = element
                for (part in cleanRule.split(".").filter { it.isNotBlank() }) {
                    current = (current as? JsonObject)?.get(part)
                }
                (current as? JsonPrimitive)?.content ?: current?.toString()?.removeSurrounding("\"") ?: ""
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun log(msg: Any?): Any? {
        println("[JsHost Log]: $msg")
        return msg
    }

    @JvmOverloads
    fun timeFormat(timestamp: Any?, format: String = "yyyy-MM-dd HH:mm:ss"): String {
        return try {
            val millis = when (timestamp) {
                is Number -> timestamp.toLong()
                is String -> timestamp.toLongOrNull() ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
            val dtf = java.time.format.DateTimeFormatter.ofPattern(format)
            val instant = java.time.Instant.ofEpochMilli(millis)
            val zoneId = java.time.ZoneId.systemDefault()
            instant.atZone(zoneId).format(dtf)
        } catch (_: Exception) {
            timestamp.toString()
        }
    }

    fun encodeURI(str: String): String {
        return try {
            java.net.URLEncoder.encode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
    }

    fun decodeURI(str: String): String {
        return try {
            java.net.URLDecoder.decode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
    }
}

object JsEngine {
    fun eval(
        script: String,
        result: Any? = null,
        baseUrl: String = "",
        variables: Map<String, Any?> = emptyMap()
    ): Any? {
        val cx = Context.enter()
        return try {
            cx.optimizationLevel = -1 // Interpret mode
            val scope: Scriptable = cx.initStandardObjects()

            // Bind Legado host object: java.*
            val host = JsHost(baseUrl)
            val wrappedHost = Context.javaToJS(host, scope)
            ScriptableObject.putProperty(scope, "java", wrappedHost)

            // Bind result and baseUrl
            ScriptableObject.putProperty(scope, "result", Context.javaToJS(result, scope))
            ScriptableObject.putProperty(scope, "baseUrl", Context.javaToJS(baseUrl, scope))

            // Bind additional variables
            variables.forEach { (k, v) ->
                ScriptableObject.putProperty(scope, k, Context.javaToJS(v, scope))
            }

            val evalResult = cx.evaluateString(scope, script, "LegadoRuleScript", 1, null)
            Context.jsToJava(evalResult, Any::class.java)
        } catch (e: Exception) {
            System.err.println("JS Eval Error: ${e.message} in script: $script")
            result
        } finally {
            Context.exit()
        }
    }
}
