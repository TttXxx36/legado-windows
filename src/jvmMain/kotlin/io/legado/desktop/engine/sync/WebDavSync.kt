package io.legado.desktop.engine.sync

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.*
import io.legado.desktop.engine.network.HttpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object WebDavSync {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun getBaseUrl(config: WebDavConfig): String {
        val root = config.url.trimEnd('/')
        val dir = config.rootDir.trim('/')
        return if (dir.isEmpty()) root else "$root/$dir"
    }

    private fun getAuthHeader(config: WebDavConfig): String {
        return Credentials.basic(config.username, config.password)
    }

    suspend fun testConnection(config: WebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = getBaseUrl(config)
            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", null)
                .header("Authorization", getAuthHeader(config))
                .header("Depth", "0")
                .build()

            HttpHelper.client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 207) {
                    Result.success("WebDAV 连接成功！")
                } else if (response.code == 404) {
                    // Try creating directory via MKCOL
                    val mkcol = Request.Builder()
                        .url(url)
                        .method("MKCOL", null)
                        .header("Authorization", getAuthHeader(config))
                        .build()
                    HttpHelper.client.newCall(mkcol).execute().use { mkcolRes ->
                        if (mkcolRes.isSuccessful || mkcolRes.code == 201) {
                            Result.success("WebDAV 连接成功，已自动初始化根目录！")
                        } else {
                            Result.failure(Exception("连接成功但无法创建根目录 (HTTP ${mkcolRes.code})"))
                        }
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun backupAll(config: WebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl(config)
            val auth = getAuthHeader(config)

            val books = AppDatabase.getAllBooks()
            val sources = AppDatabase.getAllBookSources()
            val bookmarks = AppDatabase.getBookmarks()
            val rules = AppDatabase.getReplaceRules()

            uploadFile(baseUrl, "bookshelf.json", json.encodeToString(books), auth)
            uploadFile(baseUrl, "bookSource.json", json.encodeToString(sources), auth)
            uploadFile(baseUrl, "bookmark.json", json.encodeToString(bookmarks), auth)
            uploadFile(baseUrl, "replaceRule.json", json.encodeToString(rules), auth)

            Result.success("全量数据已成功备份至 WebDAV 云端！\n共同步 ${books.size} 本书、${sources.size} 个书源、${bookmarks.size} 条书签。")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreAll(config: WebDavConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getBaseUrl(config)
            val auth = getAuthHeader(config)

            var restoredBooks = 0
            var restoredSources = 0
            var restoredRules = 0
            var restoredBookmarks = 0

            // 1. Bookshelf
            val shelfJson = downloadFile(baseUrl, "bookshelf.json", auth)
            if (!shelfJson.isNullOrBlank()) {
                val books = json.decodeFromString<List<Book>>(shelfJson)
                for (b in books) AppDatabase.insertOrUpdateBook(b)
                restoredBooks = books.size
            }

            // 2. Book Sources
            val sourcesJson = downloadFile(baseUrl, "bookSource.json", auth)
            if (!sourcesJson.isNullOrBlank()) {
                val sources = json.decodeFromString<List<BookSource>>(sourcesJson)
                AppDatabase.insertBookSources(sources)
                restoredSources = sources.size
            }

            // 3. Replace Rules
            val rulesJson = downloadFile(baseUrl, "replaceRule.json", auth)
            if (!rulesJson.isNullOrBlank()) {
                val rules = json.decodeFromString<List<ReplaceRule>>(rulesJson)
                for (r in rules) AppDatabase.insertOrUpdateReplaceRule(r)
                restoredRules = rules.size
            }

            // 4. Bookmarks
            val bookmarkJson = downloadFile(baseUrl, "bookmark.json", auth)
            if (!bookmarkJson.isNullOrBlank()) {
                val bookmarks = json.decodeFromString<List<Bookmark>>(bookmarkJson)
                for (bm in bookmarks) AppDatabase.insertBookmark(bm)
                restoredBookmarks = bookmarks.size
            }

            Result.success("云端恢复完成！\n已恢复：$restoredBooks 本书、$restoredSources 个书源、$restoredRules 条规则、$restoredBookmarks 条书签。")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun uploadFile(baseUrl: String, fileName: String, content: String, auth: String) {
        val url = "$baseUrl/$fileName"
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val request = Request.Builder()
            .url(url)
            .put(content.toRequestBody(mediaType))
            .header("Authorization", auth)
            .build()

        HttpHelper.client.newCall(request).execute().use { res ->
            if (!res.isSuccessful && res.code != 201 && res.code != 204) {
                throw Exception("上传 $fileName 失败 (HTTP ${res.code})")
            }
        }
    }

    private fun downloadFile(baseUrl: String, fileName: String, auth: String): String? {
        val url = "$baseUrl/$fileName"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", auth)
            .build()

        return try {
            HttpHelper.client.newCall(request).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
