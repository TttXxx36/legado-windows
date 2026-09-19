package io.legado.desktop

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.data.model.SearchRule
import io.legado.desktop.engine.BookSourceEngine
import io.legado.desktop.engine.rule.RuleAnalyzer
import io.legado.desktop.engine.script.JsEngine
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import kotlin.test.*

class BookSourceEngineTest {

    @Test
    fun testParseLegado30SourceJson() {
        val json = """
            [
              {
                "bookSourceUrl": "https://example.com/source",
                "bookSourceName": "测试书源",
                "bookSourceType": 0,
                "enabled": true,
                "searchUrl": "https://example.com/search?q={{key}}",
                "ruleSearch": {
                  "bookList": "div.book-item",
                  "name": "h3.title@text",
                  "author": "span.author@text",
                  "bookUrl": "a@href"
                }
              }
            ]
        """.trimIndent()

        val sources = BookSourceEngine.parseBookSources(json)
        assertEquals(1, sources.size)
        val source = sources[0]
        assertEquals("测试书源", source.bookSourceName)
        assertEquals("https://example.com/source", source.bookSourceUrl)
        assertEquals("h3.title@text", source.ruleSearch?.name)
    }

    @Test
    fun testRuleAnalyzerCssAndRegex() {
        val html = """
            <div class="book-item">
                <h3 class="title">诡秘之主（精校版）</h3>
                <span class="author">作者：爱潜水的乌贼</span>
                <a href="/book/12345">阅读</a>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://example.com")
        val item = doc.selectFirst("div.book-item")!!

        val title = RuleAnalyzer.extractString(item, "h3.title@text##（.*）##")
        assertEquals("诡秘之主", title)

        val author = RuleAnalyzer.extractString(item, "span.author@text##作者：##")
        assertEquals("爱潜水的乌贼", author)

        val link = RuleAnalyzer.extractString(item, "a@href", baseUrl = "https://example.com")
        assertEquals("https://example.com/book/12345", link)
    }

    @Test
    fun testJsEngineHostMethods() {
        // Test java.put and java.get
        val script1 = """
            java.put("token", "my_secret_token");
            java.get("token");
        """.trimIndent()
        val result1 = JsEngine.eval(script1)
        assertEquals("my_secret_token", result1)

        // Test java.base64Decode
        val script2 = """
            java.base64Decode("TGVnYWRvIFdpbmRvd3M=");
        """.trimIndent()
        val result2 = JsEngine.eval(script2)
        assertEquals("Legado Windows", result2)
    }

    @Test
    fun testDatabaseCrud() = runBlocking {
        val testBook = Book(
            bookUrl = "https://test.com/book/1",
            name = "道诡异仙",
            author = "狐尾的笔",
            origin = "test-source",
            originName = "测试书源"
        )

        AppDatabase.insertOrUpdateBook(testBook)
        val books = AppDatabase.getAllBooks()
        assertTrue(books.any { it.bookUrl == testBook.bookUrl && it.name == "道诡异仙" })

        val chapters = listOf(
            BookChapter(url = "https://test.com/c1", title = "第1章 幻觉", bookUrl = testBook.bookUrl, index = 0),
            BookChapter(url = "https://test.com/c2", title = "第2章 药", bookUrl = testBook.bookUrl, index = 1)
        )
        AppDatabase.saveChapters(testBook.bookUrl, chapters)
        val loadedChapters = AppDatabase.getChapters(testBook.bookUrl)
        assertEquals(2, loadedChapters.size)
        assertEquals("第1章 幻觉", loadedChapters[0].title)

        // Clean up
        AppDatabase.deleteBook(testBook.bookUrl)
        val remaining = AppDatabase.getAllBooks()
        assertFalse(remaining.any { it.bookUrl == testBook.bookUrl })
    }
}
