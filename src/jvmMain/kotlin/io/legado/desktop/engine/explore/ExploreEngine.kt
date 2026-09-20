package io.legado.desktop.engine.explore

import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.engine.network.HttpHelper
import io.legado.desktop.engine.rule.RuleAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.net.URI

data class ExploreKind(
    val title: String,
    val url: String
)

object ExploreEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses the exploreUrl field of a Legado 3.0 book source into a structured list of ExploreKind.
     * Supports both JSON array syntax and standard "Title::URL" multi-line text syntax.
     */
    fun parseExploreKinds(exploreUrl: String?): List<ExploreKind> {
        if (exploreUrl.isNullOrBlank()) return emptyList()
        val trimmed = exploreUrl.trim()

        // 1. JSON Array format: [{"title": "玄幻", "url": "..."}, ...]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                val element = json.parseToJsonElement(trimmed)
                val list = mutableListOf<ExploreKind>()
                element.jsonArray.forEach { item ->
                    val obj = item.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content
                        ?: obj["name"]?.jsonPrimitive?.content
                        ?: "未知分类"
                    val url = obj["url"]?.jsonPrimitive?.content ?: ""
                    if (url.isNotBlank()) {
                        list.add(ExploreKind(title, url))
                    }
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // fallback to line-based parsing
            }
        }

        // 2. Multi-line text format: "Category Title::URL"
        val lines = trimmed.lines()
        val result = mutableListOf<ExploreKind>()

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isBlank()) continue

            if (cleanLine.contains("::")) {
                val parts = cleanLine.split("::", limit = 2)
                val title = parts[0].trim()
                var url = parts[1].trim()
                // Strip optional trailing JSON config: url&&{...}
                if (url.contains("&&{")) {
                    url = url.substringBefore("&&{").trim()
                }
                if (url.isNotBlank()) {
                    result.add(ExploreKind(title = title, url = url))
                }
            } else if (cleanLine.startsWith("http://") || cleanLine.startsWith("https://") || cleanLine.startsWith("/")) {
                result.add(ExploreKind(title = "全部推荐", url = cleanLine))
            }
        }

        return result
    }

    private data class CommonExploreRule(
        val bookList: String?,
        val name: String?,
        val author: String?,
        val bookUrl: String?,
        val coverUrl: String?,
        val intro: String?,
        val lastChapter: String?,
        val kind: String?
    )

    /**
     * Executes explore request against a book source for a selected explore category and page index.
     */
    suspend fun explore(
        source: BookSource,
        exploreUrlPattern: String,
        page: Int = 1
    ): List<Book> = withContext(Dispatchers.IO) {
        val rule = source.ruleExplore?.let {
            CommonExploreRule(it.bookList, it.name, it.author, it.bookUrl, it.coverUrl, it.intro, it.lastChapter, it.kind)
        } ?: source.ruleSearch?.let {
            CommonExploreRule(it.bookList, it.name, it.author, it.bookUrl, it.coverUrl, it.intro, it.lastChapter, it.kind)
        } ?: return@withContext emptyList()

        if (rule.bookList.isNullOrBlank()) return@withContext emptyList()

        // Substitute pagination variables
        var url = exploreUrlPattern
            .replace("{{page}}", page.toString())
            .replace("{{page+1}}", (page + 1).toString())
            .replace("{{page-1}}", (page - 1).coerceAtLeast(1).toString())

        // Resolve relative URL against source base URL
        val baseUrl = source.bookSourceUrl.trimEnd('/')
        if (url.startsWith("/")) {
            try {
                val uri = URI(baseUrl)
                url = "${uri.scheme}://${uri.authority}$url"
            } catch (_: Exception) {
                url = "$baseUrl$url"
            }
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "$baseUrl/$url"
        }

        val html = try {
            HttpHelper.get(url)
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        val doc = Jsoup.parse(html, url)
        val bookElements = RuleAnalyzer.extractList(doc, rule.bookList)

        bookElements.mapNotNull { el ->
            val name = RuleAnalyzer.extractString(el, rule.name, url)
            if (name.isBlank()) return@mapNotNull null

            val author = RuleAnalyzer.extractString(el, rule.author, url)
            val bookUrl = RuleAnalyzer.extractString(el, rule.bookUrl, url)
            val coverUrl = RuleAnalyzer.extractString(el, rule.coverUrl, url)
            val intro = RuleAnalyzer.extractString(el, rule.intro, url)
            val latestChapter = RuleAnalyzer.extractString(el, rule.lastChapter, url)
            val kind = RuleAnalyzer.extractString(el, rule.kind, url)

            Book(
                bookUrl = if (bookUrl.isNotBlank()) bookUrl else "$url#${name}_$author",
                name = name,
                author = author,
                kind = kind,
                coverUrl = coverUrl,
                intro = intro,
                latestChapterTitle = latestChapter,
                origin = source.bookSourceUrl,
                originName = source.bookSourceName
            )
        }
    }
}
