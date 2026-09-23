package io.legado.desktop

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.engine.BookSourceDiagnosticEngine
import io.legado.desktop.engine.BookSourceEngine
import io.legado.desktop.engine.DiagnosticStatus
import io.legado.desktop.engine.script.JsEngine
import io.legado.desktop.engine.search.AggregatedBook
import io.legado.desktop.engine.search.SearchRelevanceEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Phase14FeatureTest {

    @Test
    fun testDiagnosticStatusClassification() {
        val healthy = DiagnosticStatus.Healthy(150L)
        val timeout = DiagnosticStatus.Timeout
        val error = DiagnosticStatus.Error("Connection refused")
        val empty = DiagnosticStatus.Empty
        val untested = DiagnosticStatus.Untested
        val checking = DiagnosticStatus.Checking

        assertFalse(healthy.isFailed())
        assertTrue(timeout.isFailed())
        assertTrue(error.isFailed())
        assertFalse(empty.isFailed())
        assertFalse(untested.isFailed())
        assertFalse(checking.isFailed())
        assertEquals(150L, healthy.latencyMs)
        assertEquals("Connection refused", error.message)
    }

    @Test
    fun testDiagnosticEngineBatchDisableAndDelete() {
        runBlocking {
            val s1 = BookSource(
                bookSourceUrl = "https://source1.example.com",
                bookSourceName = "测试书源1",
                enabled = true
            )
        val s2 = BookSource(
            bookSourceUrl = "https://source2.example.com",
            bookSourceName = "测试书源2",
            enabled = true
        )
        val s3 = BookSource(
            bookSourceUrl = "https://source3.example.com",
            bookSourceName = "测试书源3",
            enabled = true
        )

        AppDatabase.insertBookSources(listOf(s1, s2, s3))

        // Mark s1 as Timeout, s2 as Error, s3 as Healthy
        BookSourceDiagnosticEngine.setStatus(s1.bookSourceUrl, DiagnosticStatus.Timeout)
        BookSourceDiagnosticEngine.setStatus(s2.bookSourceUrl, DiagnosticStatus.Error("404 Not Found"))
        BookSourceDiagnosticEngine.setStatus(s3.bookSourceUrl, DiagnosticStatus.Healthy(85L))

        // Batch disable failed
        val disabledCount = BookSourceDiagnosticEngine.disableFailedSources(listOf(s1, s2, s3))
        assertEquals("Should disable 2 failed sources", 2, disabledCount)

        val updatedSources = AppDatabase.getAllBookSources()
        val updatedS1 = updatedSources.find { it.bookSourceUrl == s1.bookSourceUrl }
        val updatedS2 = updatedSources.find { it.bookSourceUrl == s2.bookSourceUrl }
        val updatedS3 = updatedSources.find { it.bookSourceUrl == s3.bookSourceUrl }

        assertNotNull(updatedS1)
        assertFalse("s1 should be disabled", updatedS1!!.enabled)
        assertNotNull(updatedS2)
        assertFalse("s2 should be disabled", updatedS2!!.enabled)
        assertNotNull(updatedS3)
        assertTrue("s3 should remain enabled", updatedS3!!.enabled)

        // Batch delete failed
        val deletedCount = BookSourceDiagnosticEngine.deleteFailedSources(listOf(s1, s2, s3))
        assertEquals("Should delete 2 failed sources", 2, deletedCount)

        val remainingSources = AppDatabase.getAllBookSources()
        assertNull("s1 should be deleted from DB", remainingSources.find { it.bookSourceUrl == s1.bookSourceUrl })
        assertNull("s2 should be deleted from DB", remainingSources.find { it.bookSourceUrl == s2.bookSourceUrl })
        assertNotNull("s3 should remain in DB", remainingSources.find { it.bookSourceUrl == s3.bookSourceUrl })

        // Clean up s3
        AppDatabase.deleteBookSource(s3.bookSourceUrl)
        }
    }

    @Test
    fun testSearchRelevanceAggregation() {
        val rawBooks = listOf(
            Book(
                bookUrl = "https://a.com/b1",
                name = "雪中悍刀行",
                author = "烽火戏诸侯",
                origin = "sourceA",
                originName = "笔趣阁",
                latestChapterTitle = "番外 第十章"
            ),
            Book(
                bookUrl = "https://b.com/b2",
                name = "《雪中悍刀行》",
                author = "烽火戏诸侯",
                origin = "sourceB",
                originName = "新笔趣阁",
                latestChapterTitle = "番外 第九章"
            ),
            Book(
                bookUrl = "https://c.com/b3",
                name = "雪中悍刀行",
                author = "烽火戏诸侯 ",
                origin = "sourceC",
                originName = "顶点小说",
                latestChapterTitle = "终章"
            ),
            Book(
                bookUrl = "https://d.com/b4",
                name = "剑来",
                author = "烽火戏诸侯",
                origin = "sourceA",
                originName = "笔趣阁",
                latestChapterTitle = "第1200章"
            )
        )

        val aggregated = SearchRelevanceEngine.aggregateSearchResults(rawBooks, "雪中悍刀行")

        assertEquals("Should cluster into 2 distinct titles", 2, aggregated.size)

        val xuezhongCluster = aggregated.firstOrNull { it.primaryBook.name.contains("雪中悍刀行") }
        assertNotNull(xuezhongCluster)
        assertEquals("雪中悍刀行 cluster should aggregate all 3 candidate sources", 3, xuezhongCluster!!.sourceCount)
        assertEquals(3, xuezhongCluster.candidateSources.size)

        val jianlaiCluster = aggregated.firstOrNull { it.primaryBook.name == "剑来" }
        assertNotNull(jianlaiCluster)
        assertEquals("剑来 cluster should have 1 source", 1, jianlaiCluster!!.sourceCount)

        // Exact match should be ranked first
        assertEquals("雪中悍刀行", aggregated.first().primaryBook.name)
    }

    @Test
    fun testExtendedJsEngineFunctions() {
        // Test md5Encode
        val md5 = JsEngine.eval("java.md5Encode('legado')") as? String
        assertNotNull(md5)
        assertEquals("bbd6a62a8a291b19a802e4ad64547fff", md5)

        // Test encodeURI & decodeURI
        val encoded = JsEngine.eval("java.encodeURI('雪')") as? String
        assertNotNull(encoded)
        assertTrue(encoded!!.contains("%E9%9B%AA") || encoded.contains("%e9%9b%aa"))

        val decoded = JsEngine.eval("java.decodeURI('%E9%9B%AA')") as? String
        assertEquals("雪", decoded)

        // Test timeFormat
        val formattedTime = JsEngine.eval("java.timeFormat(1700000000000)") as? String
        assertNotNull(formattedTime)
        assertTrue("Time format should yield a non-empty date string", formattedTime!!.isNotBlank())

        // Test getString (JSONPath / fast extraction)
        val jsonValue = JsEngine.eval("java.getString('{\"title\":\"庆余年\"}', '$.title')") as? String
        assertEquals("庆余年", jsonValue)
    }

    @Test
    fun testBookSourceHeaderParsing() {
        val validJson = """{"User-Agent": "LegadoDesktop/1.8", "Cookie": "token=xyz"}"""
        val headerMap = BookSourceEngine.parseHeaderMap(validJson)

        assertNotNull(headerMap)
        assertEquals("LegadoDesktop/1.8", headerMap["User-Agent"])
        assertEquals("token=xyz", headerMap["Cookie"])

        assertTrue(BookSourceEngine.parseHeaderMap(null).isEmpty())
        assertTrue(BookSourceEngine.parseHeaderMap("").isEmpty())
        assertTrue(BookSourceEngine.parseHeaderMap("    ").isEmpty())
        assertTrue(BookSourceEngine.parseHeaderMap("invalid-non-json").isEmpty())
    }
}
