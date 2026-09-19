package io.legado.desktop

import io.legado.desktop.server.LegadoWebServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.*

class Phase3FeatureTest {

    @Test
    fun testWebServerLifecycleAndEndpoints() {
        val testPort = 11223
        val started = LegadoWebServer.start(testPort)
        assertTrue(started, "Web server should start successfully")
        assertTrue(LegadoWebServer.isRunning, "Web server state should be running")

        val client = OkHttpClient()

        // 1. Test Dashboard HTML endpoint
        val htmlReq = Request.Builder().url("http://127.0.0.1:$testPort/").build()
        client.newCall(htmlReq).execute().use { res ->
            assertEquals(200, res.code)
            val body = res.body?.string() ?: ""
            assertTrue(body.contains("Legado Windows 局域网 Web 传书与控制台"))
        }

        // 2. Test Books API endpoint
        val apiReq = Request.Builder().url("http://127.0.0.1:$testPort/api/books").build()
        client.newCall(apiReq).execute().use { res ->
            assertEquals(200, res.code)
            assertEquals("application/json; charset=utf-8", res.header("Content-Type"))
        }

        // 3. Stop server
        LegadoWebServer.stop()
        assertFalse(LegadoWebServer.isRunning, "Web server state should be stopped")
    }

    @Test
    fun testComicImageExtraction() {
        val chapterHtml = """
            <p>第一话 觉醒</p>
            <img src="https://img.comic.com/chapter1/01.jpg" />
            <img src="https://img.comic.com/chapter1/02.webp" />
            <img src="https://img.comic.com/chapter1/03.png" />
        """.trimIndent()

        val imgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val extracted = imgRegex.findAll(chapterHtml).map { it.groupValues[1] }.toList()

        assertEquals(3, extracted.size)
        assertEquals("https://img.comic.com/chapter1/01.jpg", extracted[0])
        assertEquals("https://img.comic.com/chapter1/02.webp", extracted[1])
        assertEquals("https://img.comic.com/chapter1/03.png", extracted[2])
    }
}
