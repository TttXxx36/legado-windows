package io.legado.desktop.engine.script

import io.legado.desktop.engine.network.HttpHelper
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

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
