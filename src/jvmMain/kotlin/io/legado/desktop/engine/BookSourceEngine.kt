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
            HttpHelper.smartRequest(source.bookSourceUrl, finalUrl)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        if (html.isBlank()) return@withContext emptyList()

        val doc = Jsoup.parse(html, source.bookSourceUrl)
        val bookElements = RuleAnalyzer.extractList(doc, ruleSearch.bookList)

        bookElements.mapNotNull { el ->
            val name = RuleAnalyzer.extractString(el, ruleSearch.name, source.bookSourceUrl)
            if (name.isBlank()) return@mapNotNull null

            val author = RuleAnalyzer.extractString(el, ruleSearch.author, source.bookSourceUrl)
            var bookUrl = RuleAnalyzer.extractString(el, ruleSearch.bookUrl, source.bookSourceUrl)
            if (bookUrl.isBlank()) {
                bookUrl = el.attr("href").ifEmpty { el.selectFirst("a")?.attr("href") ?: "" }
            }
            bookUrl = RuleAnalyzer.resolveUrl(source.bookSourceUrl, bookUrl)

            val coverUrl = RuleAnalyzer.extractString(el, ruleSearch.coverUrl, source.bookSourceUrl)
            val intro = RuleAnalyzer.extractString(el, ruleSearch.intro, source.bookSourceUrl)
            val latestChapter = RuleAnalyzer.extractString(el, ruleSearch.lastChapter, source.bookSourceUrl)
            val kind = RuleAnalyzer.extractString(el, ruleSearch.kind, source.bookSourceUrl)

            Book(
                bookUrl = if (bookUrl.isNotBlank()) bookUrl else "${source.bookSourceUrl}#${name}_$author",
                name = name.trim().removeSurrounding("《", "》"),
                author = author.trim(),
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
        var tocUrl = book.tocUrl

        // Step 1: Resolve tocUrl from ruleBookInfo if book.tocUrl is empty
        if (tocUrl.isBlank() && !source.ruleBookInfo?.tocUrl.isNullOrBlank()) {
            val bookDetailUrl = RuleAnalyzer.resolveUrl(source.bookSourceUrl, book.bookUrl)
            try {
                val detailHtml = HttpHelper.smartRequest(source.bookSourceUrl, bookDetailUrl)
                if (detailHtml.isNotBlank()) {
                    val detailDoc = Jsoup.parse(detailHtml, bookDetailUrl)
                    val extractedToc = RuleAnalyzer.extractString(detailDoc, source.ruleBookInfo?.tocUrl, bookDetailUrl)
                    if (extractedToc.isNotBlank()) {
                        tocUrl = RuleAnalyzer.resolveUrl(bookDetailUrl, extractedToc)
                        book.tocUrl = tocUrl
                    }
                }
            } catch (_: Exception) {}
        }

        if (tocUrl.isBlank()) {
            tocUrl = book.bookUrl
        }
        val finalTocUrl = RuleAnalyzer.resolveUrl(source.bookSourceUrl, tocUrl)

        val html = try {
            HttpHelper.smartRequest(source.bookSourceUrl, finalTocUrl)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        if (html.isBlank()) return@withContext emptyList()

        val doc = Jsoup.parse(html, finalTocUrl)
        var chapterElements = RuleAnalyzer.extractList(doc, tocRule.chapterList)
        if (chapterElements.isEmpty()) {
            chapterElements = doc.select("#list dd a, .catalog a, #chapter-list a, .mulu a, #readerlist li a, .listmain dd a, #list-chapterAll dd a")
        }

        chapterElements.mapIndexedNotNull { idx, el ->
            var title = RuleAnalyzer.extractString(el, tocRule.chapterName, finalTocUrl)
            if (title.isBlank()) {
                title = el.text()
            }
            if (title.isBlank()) return@mapIndexedNotNull null

            var rawUrl = RuleAnalyzer.extractString(el, tocRule.chapterUrl, finalTocUrl)
            if (rawUrl.isBlank()) {
                rawUrl = el.attr("href").ifEmpty { el.selectFirst("a")?.attr("href") ?: "" }
            }
            val finalChapterUrl = RuleAnalyzer.resolveUrl(finalTocUrl, rawUrl)

            BookChapter(
                url = if (finalChapterUrl.isNotBlank()) finalChapterUrl else "$finalTocUrl#$idx",
                title = title.trim(),
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
        val baseRef = if (book.tocUrl.isNotBlank()) book.tocUrl else book.bookUrl
        val finalUrl = RuleAnalyzer.resolveUrl(baseRef.ifBlank { source.bookSourceUrl }, chapter.url)

        val html = try {
            HttpHelper.smartRequest(baseRef.ifBlank { source.bookSourceUrl }, finalUrl)
        } catch (e: Exception) {
            return@withContext "加载正文失败: ${e.message}"
        }

        if (html.isBlank()) return@withContext "加载正文失败: 目标网站返回空内容"

        val doc = Jsoup.parse(html, finalUrl)
        var content = RuleAnalyzer.extractString(doc, contentRule.content, finalUrl)
        if (content.isBlank()) {
            content = doc.selectFirst("#content, .content, #chaptercontent, .read-content, #htmlContent, .showtxt, .article-content, #ChapterContents, .textarticle, .con")?.html() ?: ""
        }

        // Apply replaceRegex if present
        if (!contentRule.replaceRegex.isNullOrBlank()) {
            val parts = contentRule.replaceRegex!!.split("##")
            if (parts.size >= 2) {
                val pattern = parts[1]
                val replacement = if (parts.size >= 3) parts[2] else ""
                if (pattern.isNotEmpty()) {
                    try {
                        content = content.replace(Regex(pattern), replacement)
                    } catch (_: Exception) {}
                }
            }
        }

        // Clean HTML tags and entities for crisp typography
        content = content
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</p>"""), "\n\n")
            .replace(Regex("""(?i)<p[^>]*>"""), "　　")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()

        if (content.isBlank()) {
            "（本章正文内容提取为空，可能由于源站内容加密或防盗链，建议尝试切换书源）"
        } else {
            content
        }
    }
}
