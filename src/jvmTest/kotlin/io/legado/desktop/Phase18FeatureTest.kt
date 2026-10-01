package io.legado.desktop

import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.engine.analytics.ReadingAnalyticsEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class Phase18FeatureTest {

    @Test
    fun testCalculateStreak_consecutiveDays() {
        val today = LocalDate.of(2026, 9, 27)
        val activeDates = setOf(
            "2026-09-27",
            "2026-09-26",
            "2026-09-25",
            "2026-09-24",
            "2026-09-23"
        )
        val streak = ReadingAnalyticsEngine.calculateStreak(activeDates, today)
        assertEquals(5, streak)
    }

    @Test
    fun testCalculateStreak_yesterdayActiveTodayNotYet() {
        val today = LocalDate.of(2026, 9, 27)
        // Today not in set, but yesterday is active
        val activeDates = setOf(
            "2026-09-26",
            "2026-09-25",
            "2026-09-24"
        )
        val streak = ReadingAnalyticsEngine.calculateStreak(activeDates, today)
        assertEquals(3, streak)
    }

    @Test
    fun testCalculateStreak_brokenStreak() {
        val today = LocalDate.of(2026, 9, 27)
        // Missing yesterday (2026-09-26)
        val activeDates = setOf(
            "2026-09-25",
            "2026-09-24"
        )
        val streak = ReadingAnalyticsEngine.calculateStreak(activeDates, today)
        assertEquals(0, streak)
    }

    @Test
    fun testCalculateStreak_emptyDates() {
        val today = LocalDate.of(2026, 9, 27)
        val streak = ReadingAnalyticsEngine.calculateStreak(emptySet(), today)
        assertEquals(0, streak)
    }

    @Test
    fun testFormatDuration() {
        assertEquals("45秒", ReadingAnalyticsEngine.formatDuration(45))
        assertEquals("15分钟", ReadingAnalyticsEngine.formatDuration(900))
        assertEquals("1小时", ReadingAnalyticsEngine.formatDuration(3600))
        assertEquals("1小时30分", ReadingAnalyticsEngine.formatDuration(5400))
        assertEquals("2小时15分", ReadingAnalyticsEngine.formatDuration(8100))
    }

    @Test
    fun testFormatWords() {
        assertEquals("520字", ReadingAnalyticsEngine.formatWords(520L))
        assertEquals("48.5万字", ReadingAnalyticsEngine.formatWords(485_000L))
        assertEquals("1.25亿字", ReadingAnalyticsEngine.formatWords(125_000_000L))
    }

    @Test
    fun testSourceExportSerialization() {
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        val sources = listOf(
            BookSource(
                bookSourceName = "测试书源 A",
                bookSourceUrl = "https://example.com/sourceA",
                bookSourceGroup = "精品优质",
                searchUrl = "https://example.com/search?key={{key}}"
            ),
            BookSource(
                bookSourceName = "测试书源 B",
                bookSourceUrl = "https://example.com/sourceB",
                bookSourceGroup = "轻小说",
                searchUrl = "https://example.com/api?q={{key}}"
            )
        )

        val jsonStr = json.encodeToString(sources)
        assertTrue(jsonStr.contains("测试书源 A"))
        assertTrue(jsonStr.contains("https://example.com/sourceA"))
        assertTrue(jsonStr.contains("精品优质"))

        val parsed = json.decodeFromString<List<BookSource>>(jsonStr)
        assertEquals(2, parsed.size)
        assertEquals("测试书源 A", parsed[0].bookSourceName)
        assertEquals("测试书源 B", parsed[1].bookSourceName)
        assertEquals("轻小说", parsed[1].bookSourceGroup)
    }

    @Test
    fun testChapterReverseSort() {
        val chapters = listOf(
            BookChapter(url = "http://a/1", title = "第1章 初入江湖", bookUrl = "http://a", index = 0),
            BookChapter(url = "http://a/2", title = "第2章 剑意凌霄", bookUrl = "http://a", index = 1),
            BookChapter(url = "http://a/3", title = "第3章 风云聚变", bookUrl = "http://a", index = 2)
        )

        val reversed = chapters.reversed()
        assertEquals("第3章 风云聚变", reversed[0].title)
        assertEquals("第2章 剑意凌霄", reversed[1].title)
        assertEquals("第1章 初入江湖", reversed[2].title)

        // Filter + Reversed
        val filtered = chapters.filter { it.title.contains("章") }
        val filteredReversed = filtered.reversed()
        assertEquals(3, filteredReversed.size)
        assertEquals(2, filteredReversed[0].index)
    }
}
