package io.legado.desktop.engine

import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.engine.network.HttpHelper
import io.legado.desktop.engine.rule.RuleAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.net.URLEncoder

object BookSourceEngine {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parseBookSources(jsonString: String): List<BookSource> {
        val trimmed = jsonString.trim()
        return try {
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<BookSource>>(trimmed)
            } else {
                listOf(json.decodeFromString<BookSource>(trimmed))
            }
        } catch (e: Exception) {
            System.err.println("Failed to parse book sources: ${e.message}")
            emptyList()
        }
    }

    suspend fun search(
        source: BookSource,
        keyword: String,
        page: Int = 1
    ): List<Book> = withContext(Dispatchers.IO) {
        val searchUrlRule = source.searchUrl ?: return@withContext emptyList()
        val ruleSearch = source.ruleSearch ?: return@withContext emptyList()

        val encodedKey = URLEncoder.encode(keyword, "UTF-8")
        val finalUrl = searchUrlRule
            .replace("{{key}}", encodedKey)
            .replace("{{page}}", page.toString())

        val html = try {
            HttpHelper.get(finalUrl)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        val doc = Jsoup.parse(html, finalUrl)
        val bookElements = RuleAnalyzer.extractList(doc, ruleSearch.bookList)

        bookElements.mapNotNull { el ->
            val name = RuleAnalyzer.extractString(el, ruleSearch.name, finalUrl)
            if (name.isBlank()) return@mapNotNull null

            val author = RuleAnalyzer.extractString(el, ruleSearch.author, finalUrl)
            val bookUrl = RuleAnalyzer.extractString(el, ruleSearch.bookUrl, finalUrl)
            val coverUrl = RuleAnalyzer.extractString(el, ruleSearch.coverUrl, finalUrl)
            val intro = RuleAnalyzer.extractString(el, ruleSearch.intro, finalUrl)
            val latestChapter = RuleAnalyzer.extractString(el, ruleSearch.lastChapter, finalUrl)
            val kind = RuleAnalyzer.extractString(el, ruleSearch.kind, finalUrl)

            Book(
                bookUrl = if (bookUrl.isNotBlank()) bookUrl else "$finalUrl#${name}_$author",
                name = name,
                author = author,
                coverUrl = coverUrl.ifBlank { null },
                intro = intro.ifBlank { null },
                latestChapterTitle = latestChapter.ifBlank { null },
                kind = kind.ifBlank { null },
                origin = source.bookSourceUrl,
                originName = source.bookSourceName
            )
        }
    }

    suspend fun getChapters(
        source: BookSource,
        book: Book
    ): List<BookChapter> = withContext(Dispatchers.IO) {
        val tocRule = source.ruleToc ?: return@withContext emptyList()
        val tocUrl = if (book.tocUrl.isNotBlank()) book.tocUrl else book.bookUrl

        val html = try {
            HttpHelper.get(tocUrl)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        val doc = Jsoup.parse(html, tocUrl)
        val chapterElements = RuleAnalyzer.extractList(doc, tocRule.chapterList)

        chapterElements.mapIndexedNotNull { idx, el ->
            val title = RuleAnalyzer.extractString(el, tocRule.chapterName, tocUrl)
            val url = RuleAnalyzer.extractString(el, tocRule.chapterUrl, tocUrl)
            if (title.isBlank()) return@mapIndexedNotNull null

            BookChapter(
                url = if (url.isNotBlank()) url else "$tocUrl#$idx",
                title = title,
                bookUrl = book.bookUrl,
                index = idx
            )
        }
    }

    suspend fun getContent(
        source: BookSource,
        book: Book,
        chapter: BookChapter
    ): String = withContext(Dispatchers.IO) {
        val contentRule = source.ruleContent ?: return@withContext "无正文规则"

        val html = try {
            HttpHelper.get(chapter.url)
        } catch (e: Exception) {
            return@withContext "加载正文失败: ${e.message}"
        }

        val doc = Jsoup.parse(html, chapter.url)
        var content = RuleAnalyzer.extractString(doc, contentRule.content, chapter.url)

        // Apply replaceRegex if present
        if (!contentRule.replaceRegex.isNullOrBlank()) {
            val parts = contentRule.replaceRegex.split("##")
            if (parts.size >= 3) {
                try {
                    content = content.replace(Regex(parts[1]), parts[2])
                } catch (e: Exception) {
                    // Ignore regex error
                }
            }
        }

        if (content.isBlank()) {
            "（本章正文内容为空）"
        } else {
            content
        }
    }
}
