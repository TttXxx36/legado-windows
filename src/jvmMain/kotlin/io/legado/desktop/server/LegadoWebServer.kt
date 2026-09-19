package io.legado.desktop.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.engine.BookSourceEngine
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

object LegadoWebServer {
    private var server: HttpServer? = null
    var isRunning: Boolean = false
        private set
    var port: Int = 1122
        private set

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun start(customPort: Int = 1122): Boolean {
        if (isRunning) return true
        port = customPort
        return try {
            val s = HttpServer.create(InetSocketAddress(port), 0)
            s.executor = Executors.newFixedThreadPool(4)

            // Web Dashboard
            s.createContext("/", DashboardHandler())

            // API Endpoints
            s.createContext("/api/books", BooksHandler())
            s.createContext("/api/sources", SourcesHandler())
            s.createContext("/api/chapters", ChaptersHandler())
            s.createContext("/api/content", ContentHandler())

            s.start()
            server = s
            isRunning = true
            true
        } catch (e: Exception) {
            System.err.println("Failed to start Legado Web Server: ${e.message}")
            isRunning = false
            false
        }
    }

    fun stop() {
        try {
            server?.stop(0)
        } catch (e: Exception) {
            // Ignore
        }
        server = null
        isRunning = false
    }

    fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.address.size == 4) {
                        return addr.hostAddress
                    }
                }
            }
            "127.0.0.1"
        } catch (e: Exception) {
            "127.0.0.1"
        }
    }

    private fun sendResponse(exchange: HttpExchange, code: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "$contentType; charset=utf-8")
        exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        exchange.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { os -> os.write(bytes) }
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            val k = URLDecoder.decode(parts[0], "UTF-8")
            val v = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            k to v
        }
    }

    private class DashboardHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val html = """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Legado Windows - 局域网服务</title>
                    <style>
                        :root {
                            --bg: #121415;
                            --surface: #1C1F20;
                            --primary: #82D3E0;
                            --on-primary: #00363D;
                            --text: #E1E3E3;
                            --text-muted: #8E9293;
                            --border: #2E3335;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body { font-family: system-ui, -apple-system, sans-serif; background: var(--bg); color: var(--text); padding: 24px; line-height: 1.6; }
                        header { margin-bottom: 24px; border-bottom: 1px solid var(--border); padding-bottom: 16px; }
                        h1 { color: var(--primary); font-size: 24px; margin-bottom: 4px; }
                        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 16px; margin-bottom: 16px; }
                        .book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; margin-top: 16px; }
                        .book-card { background: #232729; border-radius: 8px; padding: 12px; border: 1px solid var(--border); }
                        .book-title { font-weight: bold; font-size: 16px; margin-bottom: 4px; }
                        .book-author { color: var(--text-muted); font-size: 13px; }
                        button, .btn { background: var(--primary); color: var(--on-primary); border: none; padding: 8px 16px; border-radius: 6px; font-weight: bold; cursor: pointer; text-decoration: none; display: inline-block; }
                        textarea { width: 100%; height: 100px; background: #232729; border: 1px solid var(--border); color: var(--text); padding: 8px; border-radius: 6px; margin: 8px 0; font-family: monospace; }
                    </style>
                </head>
                <body>
                    <header>
                        <h1>Legado Windows 局域网 Web 传书与控制台</h1>
                        <p style="color: var(--text-muted);">本页面由 Legado Windows 原生端提供支持，可在手机或同局域网设备上管理与查阅。</p>
                    </header>

                    <div class="card">
                        <h2>📚 书架图书</h2>
                        <div id="shelf" class="book-grid">正在读取书架...</div>
                    </div>

                    <div class="card">
                        <h2>📥 远程导入 Legado 3.0 书源</h2>
                        <p style="color: var(--text-muted); font-size: 14px;">在手机上复制书源 JSON，粘贴至下方直接导入到 Windows 端：</p>
                        <textarea id="sourceJson" placeholder="[ { 'bookSourceName': '...', ... } ]"></textarea>
                        <button onclick="importSources()">导入书源到桌面端</button>
                        <span id="importResult" style="margin-left: 12px; font-size: 14px;"></span>
                    </div>

                    <script>
                        async function loadBooks() {
                            try {
                                const res = await fetch('/api/books');
                                const books = await res.json();
                                const shelf = document.getElementById('shelf');
                                if (books.length === 0) {
                                    shelf.innerHTML = '<p style="color: var(--text-muted);">书架暂无书籍</p>';
                                    return;
                                }
                                shelf.innerHTML = books.map(b => `
                                    <div class="book-card">
                                        <div class="book-title">${'$'}{b.name}</div>
                                        <div class="book-author">${'$'}{b.author || '未知作者'}</div>
                                        <div style="font-size: 12px; color: var(--primary); margin-top: 6px;">最新: ${'$'}{b.latestChapterTitle || '未知'}</div>
                                    </div>
                                `).join('');
                            } catch(e) {
                                document.getElementById('shelf').innerText = '加载失败: ' + e;
                            }
                        }

                        async function importSources() {
                            const val = document.getElementById('sourceJson').value.trim();
                            const resEl = document.getElementById('importResult');
                            if (!val) return;
                            resEl.innerText = '正在导入...';
                            try {
                                const res = await fetch('/api/sources', {
                                    method: 'POST',
                                    headers: { 'Content-Type': 'application/json' },
                                    body: val
                                });
                                const json = await res.json();
                                resEl.innerText = json.message || '导入成功！';
                                document.getElementById('sourceJson').value = '';
                            } catch(e) {
                                resEl.innerText = '导入失败: ' + e;
                            }
                        }

                        loadBooks();
                    </script>
                </body>
                </html>
            """.trimIndent()
            sendResponse(exchange, 200, "text/html", html)
        }
    }

    private class BooksHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val books = runBlocking { AppDatabase.getAllBooks() }
            val response = json.encodeToString(books)
            sendResponse(exchange, 200, "application/json", response)
        }
    }

    private class SourcesHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
                val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
                val parsed = BookSourceEngine.parseBookSources(body)
                if (parsed.isEmpty()) {
                    sendResponse(exchange, 400, "application/json", """{"status":"error","message":"未能识别有效的书源 JSON"}""")
                } else {
                    runBlocking { AppDatabase.insertBookSources(parsed) }
                    sendResponse(exchange, 200, "application/json", """{"status":"success","message":"成功导入 ${parsed.size} 个书源！"}""")
                }
            } else {
                val sources = runBlocking { AppDatabase.getAllBookSources() }
                val response = json.encodeToString(sources)
                sendResponse(exchange, 200, "application/json", response)
            }
        }
    }

    private class ChaptersHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val params = parseQueryParams(exchange.requestURI.query)
            val bookUrl = params["bookUrl"]
            if (bookUrl.isNullOrBlank()) {
                sendResponse(exchange, 400, "application/json", """{"error":"缺少 bookUrl 参数"}""")
                return
            }
            val chapters = runBlocking { AppDatabase.getChapters(bookUrl) }
            sendResponse(exchange, 200, "application/json", json.encodeToString(chapters))
        }
    }

    private class ContentHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val params = parseQueryParams(exchange.requestURI.query)
            val bookUrl = params["bookUrl"] ?: ""
            val idxStr = params["chapterIndex"] ?: "0"
            val index = idxStr.toIntOrNull() ?: 0

            val content = runBlocking {
                val chapters = AppDatabase.getChapters(bookUrl)
                if (chapters.isNotEmpty() && index in chapters.indices) {
                    val books = AppDatabase.getAllBooks()
                    val book = books.firstOrNull { it.bookUrl == bookUrl }
                    val sources = AppDatabase.getAllBookSources()
                    val source = sources.firstOrNull { it.bookSourceUrl == book?.origin }
                    if (source != null && book != null) {
                        BookSourceEngine.getContent(source, book, chapters[index])
                    } else {
                        "（本地章节内容）"
                    }
                } else {
                    "章节未找到"
                }
            }

            sendResponse(exchange, 200, "application/json", """{"content": ${json.encodeToString(content)}}""")
        }
    }
}
