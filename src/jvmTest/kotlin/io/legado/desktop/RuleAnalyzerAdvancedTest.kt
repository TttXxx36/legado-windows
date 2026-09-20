package io.legado.desktop

import io.legado.desktop.engine.rule.RuleAnalyzer
import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleAnalyzerAdvancedTest {

    @Test
    fun testDirectAttrExtraction() {
        val html = """
            <div id="wrapper">
                <a href="/book/123.html" title="斗破苍穹">点击阅读</a>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://www.example.com")
        val linkEl = doc.selectFirst("a")!!

        val href = RuleAnalyzer.extractString(linkEl, "href", "https://www.example.com")
        val text = RuleAnalyzer.extractString(linkEl, "text")

        assertEquals("https://www.example.com/book/123.html", href)
        assertEquals("点击阅读", text)
    }

    @Test
    fun testAlternativeRuleFallback() {
        val html = """
            <div class="main-content">
                <div class="novel-intro">这是一部宏大的玄幻小说。</div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        // First rule class.wrong-intro will be empty, fallback to novel-intro
        val result = RuleAnalyzer.extractString(doc, "class.wrong-intro@text||class.novel-intro@text")
        assertEquals("这是一部宏大的玄幻小说。", result)
    }

    @Test
    fun testAtChainAndDirectAttributes() {
        val html = """
            <div class="chapter-list">
                <ul>
                    <li><a href="/chap/1.html">第一章 陨落的天才</a></li>
                    <li><a href="/chap/2.html">第二章 斗之气三段</a></li>
                    <li><a href="/chap/3.html">第三章 药老现身</a></li>
                </ul>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://book.test")

        val items = RuleAnalyzer.extractList(doc, "class.chapter-list@ul@li@a")
        assertEquals(3, items.size)

        val firstTitle = RuleAnalyzer.extractString(items[0], "text")
        val firstUrl = RuleAnalyzer.extractString(items[0], "href", "https://book.test")

        assertEquals("第一章 陨落的天才", firstTitle)
        assertEquals("https://book.test/chap/1.html", firstUrl)
    }

    @Test
    fun testPositionalIndexAndNegativeIndex() {
        val html = """
            <div class="crumbs">
                <a href="/">首页</a>
                <a href="/category/xuanhuan">玄幻小说</a>
                <a href="/book/100">斗破苍穹</a>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        // Last link: -1
        val lastText = RuleAnalyzer.extractString(doc, "class.crumbs@tag.a.-1@text")
        assertEquals("斗破苍穹", lastText)

        // First link: 0
        val firstText = RuleAnalyzer.extractString(doc, "class.crumbs@tag.a.0@text")
        assertEquals("首页", firstText)
    }

    @Test
    fun testRegexReplacement() {
        val html = """
            <div id="content">
                萧炎深吸了一口气。
                本章未完，请点击下一页继续阅读！
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html)

        val cleaned = RuleAnalyzer.extractString(doc, "id.content@text##本章未完.*")
        assertTrue(!cleaned.contains("本章未完"))
        assertTrue(cleaned.contains("萧炎深吸了一口气。"))
    }

    @Test
    fun testResolveUrl() {
        val base = "https://www.example.com/dir/index.html"

        assertEquals("https://www.example.com/chapter1.html", RuleAnalyzer.resolveUrl(base, "/chapter1.html"))
        assertEquals("https://www.example.com/dir/chapter2.html", RuleAnalyzer.resolveUrl(base, "chapter2.html"))
        assertEquals("http://other.com/a.html", RuleAnalyzer.resolveUrl(base, "http://other.com/a.html"))
        assertEquals("https://other.com/b.html", RuleAnalyzer.resolveUrl(base, "//other.com/b.html"))
    }
}
