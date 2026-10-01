package io.legado.desktop

import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.engine.align.ChapterAlignmentEngine
import io.legado.desktop.ui.SourceHistoryRecord
import org.junit.Assert.*
import org.junit.Test

class Phase20FeatureTest {

    @Test
    fun testParseChineseNumber() {
        assertEquals(1, ChapterAlignmentEngine.parseChineseNumber("一"))
        assertEquals(2, ChapterAlignmentEngine.parseChineseNumber("二"))
        assertEquals(2, ChapterAlignmentEngine.parseChineseNumber("两"))
        assertEquals(10, ChapterAlignmentEngine.parseChineseNumber("十"))
        assertEquals(12, ChapterAlignmentEngine.parseChineseNumber("十二"))
        assertEquals(20, ChapterAlignmentEngine.parseChineseNumber("二十"))
        assertEquals(99, ChapterAlignmentEngine.parseChineseNumber("九十九"))
        assertEquals(100, ChapterAlignmentEngine.parseChineseNumber("一百"))
        assertEquals(105, ChapterAlignmentEngine.parseChineseNumber("一百零五"))
        assertEquals(105, ChapterAlignmentEngine.parseChineseNumber("百零五"))
        assertEquals(1234, ChapterAlignmentEngine.parseChineseNumber("一千两百三十四"))
        assertEquals(10005, ChapterAlignmentEngine.parseChineseNumber("一万零五"))
    }

    @Test
    fun testExtractChapterNumber() {
        assertEquals(105, ChapterAlignmentEngine.extractChapterNumber("第105章 决战天门山"))
        assertEquals(105, ChapterAlignmentEngine.extractChapterNumber("第一百零五章 决战天门山"))
        assertEquals(105, ChapterAlignmentEngine.extractChapterNumber("105. 决战天门山"))
        assertEquals(105, ChapterAlignmentEngine.extractChapterNumber("105 决战天门山"))
        assertEquals(12, ChapterAlignmentEngine.extractChapterNumber("第十二回 宝钗扑蝶"))
        assertEquals(1, ChapterAlignmentEngine.extractChapterNumber("第1卷 序章"))
        assertNull(ChapterAlignmentEngine.extractChapterNumber("引子：无题传说"))
    }

    @Test
    fun testCleanSubtitle() {
        assertEquals("决战天门山", ChapterAlignmentEngine.cleanSubtitle("第105章 决战天门山 (求月票)"))
        assertEquals("决战天门山", ChapterAlignmentEngine.cleanSubtitle("第一卷 正文卷 第105章 决战天门山【修】"))
        assertEquals("决战天门山", ChapterAlignmentEngine.cleanSubtitle("105. 决战天门山（加更）"))
        assertEquals("宝钗扑蝶", ChapterAlignmentEngine.cleanSubtitle("第十二回 宝钗扑蝶"))
    }

    @Test
    fun testCalculateStringSimilarity() {
        assertEquals(1.0f, ChapterAlignmentEngine.calculateStringSimilarity("决战天门山", "决战天门山"), 0.001f)
        val highSim = ChapterAlignmentEngine.calculateStringSimilarity("决战天门山", "决战天门山峰")
        assertTrue(highSim >= 0.8f)

        val lowSim = ChapterAlignmentEngine.calculateStringSimilarity("决战天门山", "完全无关的内容")
        assertTrue(lowSim < 0.3f)
    }

    @Test
    fun testChapterAlignment_exactAndNumeralEquivalence() {
        val candidates = listOf(
            BookChapter(url = "u1", title = "第103章 序幕初开", bookUrl = "b1", index = 0),
            BookChapter(url = "u2", title = "第104章 风暴酝酿", bookUrl = "b1", index = 1),
            BookChapter(url = "u3", title = "第一百零五章 决战天门山 (修)", bookUrl = "b1", index = 2),
            BookChapter(url = "u4", title = "第106章 胜负已分", bookUrl = "b1", index = 3)
        )

        // Target: "第105章 决战天门山"
        val result = ChapterAlignmentEngine.alignChapter(
            currentTitle = "第105章 决战天门山",
            currentIndex = 2,
            candidates = candidates
        )

        assertEquals(2, result.index)
        assertEquals("第一百零五章 决战天门山 (修)", result.chapter.title)
        assertTrue(result.confidence >= 0.90f)
        assertTrue(result.strategy.contains("105"))
    }

    @Test
    fun testChapterAlignment_leadingDigitsAndPunctuation() {
        val candidates = listOf(
            BookChapter(url = "u1", title = "104. 风暴", bookUrl = "b1", index = 0),
            BookChapter(url = "u2", title = "105. 决战天门山", bookUrl = "b1", index = 1),
            BookChapter(url = "u3", title = "106. 终曲", bookUrl = "b1", index = 2)
        )

        // Target: "第105章 决战天门山"
        val result = ChapterAlignmentEngine.alignChapter(
            currentTitle = "第105章 决战天门山",
            currentIndex = 1,
            candidates = candidates
        )

        assertEquals(1, result.index)
        assertEquals("105. 决战天门山", result.chapter.title)
        assertTrue(result.confidence >= 0.95f)
    }

    @Test
    fun testChapterAlignment_fuzzySubtitleWithoutNumbers() {
        val candidates = listOf(
            BookChapter(url = "u1", title = "引子：天地玄黄", bookUrl = "b1", index = 0),
            BookChapter(url = "u2", title = "决战天门山之巅", bookUrl = "b1", index = 1),
            BookChapter(url = "u3", title = "尾声：归隐红尘", bookUrl = "b1", index = 2)
        )

        // Target without numbers
        val result = ChapterAlignmentEngine.alignChapter(
            currentTitle = "决战天门山",
            currentIndex = 1,
            candidates = candidates
        )

        assertEquals(1, result.index)
        assertEquals("决战天门山之巅", result.chapter.title)
        assertTrue(result.confidence >= 0.75f)
    }

    @Test
    fun testChapterAlignment_emptyCandidatesFallback() {
        val result = ChapterAlignmentEngine.alignChapter(
            currentTitle = "任意章节",
            currentIndex = 0,
            candidates = emptyList()
        )
        assertEquals(0, result.index)
        assertEquals(0.0f, result.confidence, 0.001f)
    }

    @Test
    fun testSourceHistoryRecordModel() {
        val record = SourceHistoryRecord(
            origin = "https://source1.com",
            originName = "优选源",
            tocUrl = "https://source1.com/toc",
            chapterIndex = 42,
            chapterTitle = "第43章 突破"
        )
        assertEquals("https://source1.com", record.origin)
        assertEquals("优选源", record.originName)
        assertEquals(42, record.chapterIndex)
        assertEquals("第43章 突破", record.chapterTitle)
        assertTrue(record.timestamp > 0L)
    }
}
