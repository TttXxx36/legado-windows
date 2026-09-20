package io.legado.desktop

import io.legado.desktop.data.model.Book
import io.legado.desktop.engine.search.SearchRelevanceEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchRelevanceEngineTest {

    @Test
    fun testExactTitleMatchRanksFirst() {
        val b1 = Book(name = "斗破苍穹之无限升级", author = "天蚕土豆", bookUrl = "http://b1")
        val b2 = Book(name = "斗破苍穹", author = "天蚕土豆", bookUrl = "http://b2")
        val b3 = Book(name = "斗破苍穹之大主宰", author = "天蚕土豆", bookUrl = "http://b3")
        val b4 = Book(name = "完美世界", author = "辰东", bookUrl = "http://b4")

        val sorted = SearchRelevanceEngine.sortSearchResults(listOf(b1, b4, b3, b2), "斗破苍穹")

        assertEquals("斗破苍穹", sorted.first().name)
        assertEquals(4, sorted.size)
    }

    @Test
    fun testPunctuationStrippedMatch() {
        val b1 = Book(name = "《斗破苍穹》", author = "天蚕土豆", bookUrl = "http://b1")
        val b2 = Book(name = "斗破苍穹（精校全本）", author = "天蚕土豆", bookUrl = "http://b2")
        val b3 = Book(name = "新斗破苍穹", author = "某作者", bookUrl = "http://b3")

        val sorted = SearchRelevanceEngine.sortSearchResults(listOf(b2, b3, b1), "斗破苍穹")

        // 《斗破苍穹》 with brackets stripped is exact match
        assertEquals("《斗破苍穹》", sorted.first().name)
    }

    @Test
    fun testAuthorMatchBoost() {
        val b1 = Book(name = "武炼巅峰", author = "莫默", bookUrl = "http://b1")
        val b2 = Book(name = "元尊", author = "天蚕土豆", bookUrl = "http://b2")

        val sorted = SearchRelevanceEngine.sortSearchResults(listOf(b1, b2), "天蚕土豆")

        assertEquals("元尊", sorted.first().name)
    }

    @Test
    fun testScoreCalculationDirectly() {
        val exactBook = Book(
            name = "凡人修仙传",
            author = "忘语",
            bookUrl = "http://test",
            intro = "一个普通的山村穷小子...",
            coverUrl = "http://cover.jpg",
            latestChapterTitle = "第两千四百四十章 飞升之门"
        )
        val scoreExact = SearchRelevanceEngine.calculateScore(exactBook, "凡人修仙传")

        val partialBook = Book(
            name = "凡人修仙传之仙界篇",
            author = "忘语",
            bookUrl = "http://test2"
        )
        val scorePartial = SearchRelevanceEngine.calculateScore(partialBook, "凡人修仙传")

        assertTrue(scoreExact > scorePartial, "Exact title match score ($scoreExact) must exceed partial match ($scorePartial)")
        assertTrue(scoreExact > 100000, "Exact match score should be >= 100000")
    }
}
