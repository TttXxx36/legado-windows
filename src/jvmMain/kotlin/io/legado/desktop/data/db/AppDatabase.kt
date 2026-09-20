package io.legado.desktop.data.db

import io.legado.desktop.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

object AppDatabase {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val dbFile: File by lazy {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "LegadoDesktop").apply { if (!exists()) mkdirs() }
        File(dir, "legado.db")
    }

    private val url: String by lazy {
        "jdbc:sqlite:${dbFile.absolutePath}"
    }

    init {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (_: Throwable) {}
        initDatabase()
    }

    private fun getConnection(): Connection = DriverManager.getConnection(url)

    private fun initDatabase() {
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                // Books table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS books (
                        bookUrl TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        author TEXT,
                        kind TEXT,
                        coverUrl TEXT,
                        intro TEXT,
                        type INTEGER DEFAULT 0,
                        durChapterTitle TEXT,
                        durChapterIndex INTEGER DEFAULT 0,
                        durChapterPos INTEGER DEFAULT 0,
                        durChapterTime INTEGER DEFAULT 0,
                        totalChapterNum INTEGER DEFAULT 0,
                        latestChapterTitle TEXT,
                        origin TEXT,
                        originName TEXT,
                        tocUrl TEXT,
                        orderIndex INTEGER DEFAULT 0
                    )
                    """.trimIndent()
                )

                // Book Sources table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS book_sources (
                        bookSourceUrl TEXT PRIMARY KEY,
                        bookSourceName TEXT NOT NULL,
                        bookSourceGroup TEXT,
                        bookSourceType INTEGER DEFAULT 0,
                        enabled INTEGER DEFAULT 1,
                        weight INTEGER DEFAULT 0,
                        customOrder INTEGER DEFAULT 0,
                        searchUrl TEXT,
                        exploreUrl TEXT,
                        jsonData TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                // Book Chapters table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS book_chapters (
                        bookUrl TEXT NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        url TEXT NOT NULL,
                        title TEXT NOT NULL,
                        isVip INTEGER DEFAULT 0,
                        tag TEXT,
                        PRIMARY KEY (bookUrl, chapterIndex)
                    )
                    """.trimIndent()
                )

                // Bookmarks table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        id INTEGER PRIMARY KEY,
                        bookUrl TEXT NOT NULL,
                        bookName TEXT NOT NULL,
                        chapterIndex INTEGER NOT NULL,
                        chapterTitle TEXT NOT NULL,
                        content TEXT NOT NULL,
                        note TEXT,
                        time INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // Replace Rules table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS replace_rules (
                        id INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        ruleGroup TEXT,
                        pattern TEXT NOT NULL,
                        replacement TEXT DEFAULT '',
                        scope TEXT,
                        isEnabled INTEGER DEFAULT 1,
                        isRegex INTEGER DEFAULT 1,
                        ruleOrder INTEGER DEFAULT 0
                    )
                    """.trimIndent()
                )

                // Key-Value App Configuration table
                stmt.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS app_config (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }

    suspend fun getAllBooks(): List<Book> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Book>()
        getConnection().use { conn ->
            conn.prepareStatement("SELECT * FROM books ORDER BY durChapterTime DESC, orderIndex ASC").use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        Book(
                            bookUrl = rs.getString("bookUrl"),
                            name = rs.getString("name"),
                            author = rs.getString("author") ?: "",
                            kind = rs.getString("kind"),
                            coverUrl = rs.getString("coverUrl"),
                            intro = rs.getString("intro"),
                            type = rs.getInt("type"),
                            durChapterTitle = rs.getString("durChapterTitle"),
                            durChapterIndex = rs.getInt("durChapterIndex"),
                            durChapterPos = rs.getInt("durChapterPos"),
                            durChapterTime = rs.getLong("durChapterTime"),
                            totalChapterNum = rs.getInt("totalChapterNum"),
                            latestChapterTitle = rs.getString("latestChapterTitle"),
                            origin = rs.getString("origin") ?: "",
                            originName = rs.getString("originName") ?: "",
                            tocUrl = rs.getString("tocUrl") ?: "",
                            order = rs.getInt("orderIndex")
                        )
                    )
                }
            }
        }
        list
    }

    suspend fun insertOrUpdateBook(book: Book) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            val sql = """
                INSERT INTO books (
                    bookUrl, name, author, kind, coverUrl, intro, type,
                    durChapterTitle, durChapterIndex, durChapterPos, durChapterTime,
                    totalChapterNum, latestChapterTitle, origin, originName, tocUrl, orderIndex
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(bookUrl) DO UPDATE SET
                    name = excluded.name,
                    author = excluded.author,
                    kind = excluded.kind,
                    coverUrl = excluded.coverUrl,
                    intro = excluded.intro,
                    type = excluded.type,
                    durChapterTitle = excluded.durChapterTitle,
                    durChapterIndex = excluded.durChapterIndex,
                    durChapterPos = excluded.durChapterPos,
                    durChapterTime = excluded.durChapterTime,
                    totalChapterNum = excluded.totalChapterNum,
                    latestChapterTitle = excluded.latestChapterTitle,
                    origin = excluded.origin,
                    originName = excluded.originName,
                    tocUrl = excluded.tocUrl,
                    orderIndex = excluded.orderIndex
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, book.bookUrl)
                stmt.setString(2, book.name)
                stmt.setString(3, book.author)
                stmt.setString(4, book.kind)
                stmt.setString(5, book.coverUrl)
                stmt.setString(6, book.intro)
                stmt.setInt(7, book.type)
                stmt.setString(8, book.durChapterTitle)
                stmt.setInt(9, book.durChapterIndex)
                stmt.setInt(10, book.durChapterPos)
                stmt.setLong(11, book.durChapterTime)
                stmt.setInt(12, book.totalChapterNum)
                stmt.setString(13, book.latestChapterTitle)
                stmt.setString(14, book.origin)
                stmt.setString(15, book.originName)
                stmt.setString(16, book.tocUrl)
                stmt.setInt(17, book.order)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun deleteBook(bookUrl: String) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM books WHERE bookUrl = ?").use { stmt ->
                stmt.setString(1, bookUrl)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM book_chapters WHERE bookUrl = ?").use { stmt ->
                stmt.setString(1, bookUrl)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM bookmarks WHERE bookUrl = ?").use { stmt ->
                stmt.setString(1, bookUrl)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun getAllBookSources(): List<BookSource> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BookSource>()
        getConnection().use { conn ->
            conn.prepareStatement("SELECT jsonData FROM book_sources ORDER BY customOrder ASC, weight DESC").use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val rawJson = rs.getString("jsonData")
                    try {
                        val source = json.decodeFromString<BookSource>(rawJson)
                        list.add(source)
                    } catch (e: Exception) {
                        System.err.println("Failed to parse stored book source JSON: ${e.message}")
                    }
                }
            }
        }
        list
    }

    suspend fun insertOrUpdateBookSource(source: BookSource) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            val sql = """
                INSERT INTO book_sources (
                    bookSourceUrl, bookSourceName, bookSourceGroup, bookSourceType,
                    enabled, weight, customOrder, searchUrl, exploreUrl, jsonData
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(bookSourceUrl) DO UPDATE SET
                    bookSourceName = excluded.bookSourceName,
                    bookSourceGroup = excluded.bookSourceGroup,
                    bookSourceType = excluded.bookSourceType,
                    enabled = excluded.enabled,
                    weight = excluded.weight,
                    customOrder = excluded.customOrder,
                    searchUrl = excluded.searchUrl,
                    exploreUrl = excluded.exploreUrl,
                    jsonData = excluded.jsonData
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, source.bookSourceUrl)
                stmt.setString(2, source.bookSourceName)
                stmt.setString(3, source.bookSourceGroup)
                stmt.setInt(4, source.bookSourceType)
                stmt.setInt(5, if (source.enabled) 1 else 0)
                stmt.setInt(6, source.weight)
                stmt.setInt(7, source.customOrder)
                stmt.setString(8, source.searchUrl)
                stmt.setString(9, source.exploreUrl)
                stmt.setString(10, json.encodeToString(source))
                stmt.executeUpdate()
            }
        }
    }

    suspend fun insertBookSources(sources: List<BookSource>) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.autoCommit = false
            val sql = """
                INSERT INTO book_sources (
                    bookSourceUrl, bookSourceName, bookSourceGroup, bookSourceType,
                    enabled, weight, customOrder, searchUrl, exploreUrl, jsonData
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(bookSourceUrl) DO UPDATE SET
                    bookSourceName = excluded.bookSourceName,
                    bookSourceGroup = excluded.bookSourceGroup,
                    bookSourceType = excluded.bookSourceType,
                    enabled = excluded.enabled,
                    weight = excluded.weight,
                    customOrder = excluded.customOrder,
                    searchUrl = excluded.searchUrl,
                    exploreUrl = excluded.exploreUrl,
                    jsonData = excluded.jsonData
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                for (source in sources) {
                    stmt.setString(1, source.bookSourceUrl)
                    stmt.setString(2, source.bookSourceName)
                    stmt.setString(3, source.bookSourceGroup)
                    stmt.setInt(4, source.bookSourceType)
                    stmt.setInt(5, if (source.enabled) 1 else 0)
                    stmt.setInt(6, source.weight)
                    stmt.setInt(7, source.customOrder)
                    stmt.setString(8, source.searchUrl)
                    stmt.setString(9, source.exploreUrl)
                    stmt.setString(10, json.encodeToString(source))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.commit()
            conn.autoCommit = true
        }
    }

    suspend fun deleteBookSource(sourceUrl: String) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM book_sources WHERE bookSourceUrl = ?").use { stmt ->
                stmt.setString(1, sourceUrl)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun getChapters(bookUrl: String): List<BookChapter> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BookChapter>()
        getConnection().use { conn ->
            conn.prepareStatement("SELECT * FROM book_chapters WHERE bookUrl = ? ORDER BY chapterIndex ASC").use { stmt ->
                stmt.setString(1, bookUrl)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        BookChapter(
                            bookUrl = rs.getString("bookUrl"),
                            index = rs.getInt("chapterIndex"),
                            url = rs.getString("url"),
                            title = rs.getString("title"),
                            isVip = rs.getInt("isVip") == 1,
                            tag = rs.getString("tag")
                        )
                    )
                }
            }
        }
        list
    }

    suspend fun saveChapters(bookUrl: String, chapters: List<BookChapter>) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.autoCommit = false
            conn.prepareStatement("DELETE FROM book_chapters WHERE bookUrl = ?").use { stmt ->
                stmt.setString(1, bookUrl)
                stmt.executeUpdate()
            }
            val sql = "INSERT INTO book_chapters (bookUrl, chapterIndex, url, title, isVip, tag) VALUES (?, ?, ?, ?, ?, ?)"
            conn.prepareStatement(sql).use { stmt ->
                for (ch in chapters) {
                    stmt.setString(1, bookUrl)
                    stmt.setInt(2, ch.index)
                    stmt.setString(3, ch.url)
                    stmt.setString(4, ch.title)
                    stmt.setInt(5, if (ch.isVip) 1 else 0)
                    stmt.setString(6, ch.tag)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.commit()
            conn.autoCommit = true
        }
    }

    // --- Bookmarks CRUD ---
    suspend fun getBookmarks(bookUrl: String? = null): List<Bookmark> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Bookmark>()
        getConnection().use { conn ->
            val sql = if (bookUrl == null) {
                "SELECT * FROM bookmarks ORDER BY time DESC"
            } else {
                "SELECT * FROM bookmarks WHERE bookUrl = ? ORDER BY chapterIndex ASC, time DESC"
            }
            conn.prepareStatement(sql).use { stmt ->
                if (bookUrl != null) stmt.setString(1, bookUrl)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        Bookmark(
                            id = rs.getLong("id"),
                            bookUrl = rs.getString("bookUrl"),
                            bookName = rs.getString("bookName"),
                            chapterIndex = rs.getInt("chapterIndex"),
                            chapterTitle = rs.getString("chapterTitle"),
                            content = rs.getString("content"),
                            note = rs.getString("note"),
                            time = rs.getLong("time")
                        )
                    )
                }
            }
        }
        list
    }

    suspend fun insertBookmark(bookmark: Bookmark) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            val sql = "INSERT OR REPLACE INTO bookmarks (id, bookUrl, bookName, chapterIndex, chapterTitle, content, note, time) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, bookmark.id)
                stmt.setString(2, bookmark.bookUrl)
                stmt.setString(3, bookmark.bookName)
                stmt.setInt(4, bookmark.chapterIndex)
                stmt.setString(5, bookmark.chapterTitle)
                stmt.setString(6, bookmark.content)
                stmt.setString(7, bookmark.note)
                stmt.setLong(8, bookmark.time)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun deleteBookmark(id: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM bookmarks WHERE id = ?").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
        }
    }

    // --- Replace Rules CRUD ---
    suspend fun getReplaceRules(): List<ReplaceRule> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ReplaceRule>()
        getConnection().use { conn ->
            conn.prepareStatement("SELECT * FROM replace_rules ORDER BY ruleOrder ASC, id ASC").use { stmt ->
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    list.add(
                        ReplaceRule(
                            id = rs.getLong("id"),
                            name = rs.getString("name"),
                            group = rs.getString("ruleGroup"),
                            pattern = rs.getString("pattern"),
                            replacement = rs.getString("replacement") ?: "",
                            scope = rs.getString("scope"),
                            isEnabled = rs.getInt("isEnabled") == 1,
                            isRegex = rs.getInt("isRegex") == 1,
                            order = rs.getInt("ruleOrder")
                        )
                    )
                }
            }
        }
        list
    }

    suspend fun insertOrUpdateReplaceRule(rule: ReplaceRule) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            val sql = """
                INSERT INTO replace_rules (id, name, ruleGroup, pattern, replacement, scope, isEnabled, isRegex, ruleOrder)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    ruleGroup = excluded.ruleGroup,
                    pattern = excluded.pattern,
                    replacement = excluded.replacement,
                    scope = excluded.scope,
                    isEnabled = excluded.isEnabled,
                    isRegex = excluded.isRegex,
                    ruleOrder = excluded.ruleOrder
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, rule.id)
                stmt.setString(2, rule.name)
                stmt.setString(3, rule.group)
                stmt.setString(4, rule.pattern)
                stmt.setString(5, rule.replacement)
                stmt.setString(6, rule.scope)
                stmt.setInt(7, if (rule.isEnabled) 1 else 0)
                stmt.setInt(8, if (rule.isRegex) 1 else 0)
                stmt.setInt(9, rule.order)
                stmt.executeUpdate()
            }
        }
    }

    suspend fun deleteReplaceRule(id: Long) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            conn.prepareStatement("DELETE FROM replace_rules WHERE id = ?").use { stmt ->
                stmt.setLong(1, id)
                stmt.executeUpdate()
            }
        }
    }

    // --- WebDAV Config ---
    suspend fun getWebDavConfig(): WebDavConfig = withContext(Dispatchers.IO) {
        var config = WebDavConfig()
        getConnection().use { conn ->
            conn.prepareStatement("SELECT value FROM app_config WHERE key = 'webdav_config'").use { stmt ->
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    try {
                        config = json.decodeFromString<WebDavConfig>(rs.getString("value"))
                    } catch (e: Exception) {
                        // fallback
                    }
                }
            }
        }
        config
    }

    suspend fun saveWebDavConfig(config: WebDavConfig) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            val sql = "INSERT OR REPLACE INTO app_config (key, value) VALUES ('webdav_config', ?)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, json.encodeToString(config))
                stmt.executeUpdate()
            }
        }
    }

    // --- General Key-Value Configuration ---
    suspend fun getConfig(key: String, defaultValue: String = ""): String = withContext(Dispatchers.IO) {
        var result = defaultValue
        getConnection().use { conn ->
            conn.prepareStatement("SELECT value FROM app_config WHERE key = ?").use { stmt ->
                stmt.setString(1, key)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    result = rs.getString("value")
                }
            }
        }
        result
    }

    suspend fun setConfig(key: String, value: String) = withContext(Dispatchers.IO) {
        getConnection().use { conn ->
            val sql = "INSERT OR REPLACE INTO app_config (key, value) VALUES (?, ?)"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, value)
                stmt.executeUpdate()
            }
        }
    }
}
