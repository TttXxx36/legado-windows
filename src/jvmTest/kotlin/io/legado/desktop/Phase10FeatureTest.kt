package io.legado.desktop

import androidx.compose.ui.graphics.Color
import io.legado.desktop.engine.SearchMatch
import io.legado.desktop.engine.TextPagingEngine
import io.legado.desktop.ui.ReadTheme
import io.legado.desktop.ui.parseHexColor
import org.junit.Assert.*
import org.junit.Test

class Phase10FeatureTest {

    @Test
    fun testReadThemeMatrixAndIds() {
        assertEquals(ReadTheme.DAY, ReadTheme.fromId("day"))
        assertEquals(ReadTheme.PARCHMENT, ReadTheme.fromId("parchment"))
        assertEquals(ReadTheme.BEAN_GREEN, ReadTheme.fromId("bean_green"))
        assertEquals(ReadTheme.TWILIGHT, ReadTheme.fromId("twilight"))
        assertEquals(ReadTheme.OCEAN_BLUE, ReadTheme.fromId("ocean_blue"))
        assertEquals(ReadTheme.E_INK, ReadTheme.fromId("e_ink"))
        assertEquals(ReadTheme.OLED_BLACK, ReadTheme.fromId("oled_black"))
        assertEquals(ReadTheme.GREEN, ReadTheme.fromId("green"))
        assertEquals(ReadTheme.CUSTOM, ReadTheme.fromId("custom"))

        // Fallback for unknown ID
        assertEquals(ReadTheme.DAY, ReadTheme.fromId("non_existing_theme"))

        // Ensure distinct background colors across all 8 preset themes
        val presets = listOf(
            ReadTheme.DAY, ReadTheme.PARCHMENT,
            ReadTheme.BEAN_GREEN, ReadTheme.TWILIGHT,
            ReadTheme.OCEAN_BLUE, ReadTheme.E_INK,
            ReadTheme.OLED_BLACK, ReadTheme.GREEN
        )
        val bgSet = presets.map { it.bg }.toSet()
        assertEquals("8个精调主题应该具备各不相同的背景色", 8, bgSet.size)
    }

    @Test
    fun testParseHexColorHelper() {
        val fallback = Color.Black

        // 6-character hex with hash
        val color1 = parseHexColor("#FAF7F2", fallback)
        assertEquals(Color(0xFFFAF7F2), color1)

        // 6-character hex without hash
        val color2 = parseHexColor("C7EDCC", fallback)
        assertEquals(Color(0xFFC7EDCC), color2)

        // 8-character hex with hash (with alpha)
        val color3 = parseHexColor("#FF1E2638", fallback)
        assertEquals(Color(0xFF1E2638), color3)

        // Invalid hex strings return fallback
        assertEquals(fallback, parseHexColor("not_a_color", fallback))
        assertEquals(fallback, parseHexColor("#123", fallback))
        assertEquals(fallback, parseHexColor("", fallback))
    }

    @Test
    fun testInChapterSearchSinglePage() {
        val chapterText = """
            白日依山尽，黄河入海流。
            欲穷千里目，更上一层楼。
            千里之行，始于足下。
        """.trimIndent()

        val matches = TextPagingEngine.findMatches(chapterText, "千里")
        assertEquals(2, matches.size)

        assertEquals(chapterText.indexOf("千里"), matches[0].matchIndex)
        assertEquals(2, matches[0].length)
        assertTrue(matches[0].snippet.contains("千里"))

        val secondIdx = chapterText.indexOf("千里", matches[0].matchIndex + 1)
        assertEquals(secondIdx, matches[1].matchIndex)
        assertEquals(2, matches[1].length)
        assertTrue(matches[1].snippet.contains("千里"))
    }

    @Test
    fun testInChapterSearchMultiPageDistribution() {
        val page0 = "第一页：江南可采莲，莲叶何田田。鱼戏莲叶间。"
        val page1 = "第二页：鱼戏莲叶东，鱼戏莲叶西，鱼戏莲叶南，鱼戏莲叶北。"
        val page2 = "第三页：长风破浪会有时，直挂云帆济沧海。"

        val pages = listOf(page0, page1, page2)
        val fullContent = "$page0\n$page1\n$page2"

        val matches = TextPagingEngine.findMatches(fullContent, "莲叶", pages)
        assertTrue("莲叶在文章中出现多次", matches.size >= 5)

        // First occurrence is on Page 0
        assertEquals("第一处莲叶应位于第0页", 0, matches[0].pageIndex)

        // Subsequent occurrences located in Page 1
        val page1Matches = matches.filter { it.pageIndex == 1 }
        assertTrue("第二页应该有多个莲叶匹配项", page1Matches.size >= 4)
    }

    @Test
    fun testInChapterSearchCaseInsensitiveAndEdgeCases() {
        val content = "Kotlin is concise. KOTLIN is powerful. Loving kotlin so much!"
        val matches = TextPagingEngine.findMatches(content, "kotlin")
        assertEquals(3, matches.size)

        // Blank query returns empty list
        assertTrue(TextPagingEngine.findMatches(content, "").isEmpty())
        assertTrue(TextPagingEngine.findMatches(content, "   ").isEmpty())

        // Blank content returns empty list
        assertTrue(TextPagingEngine.findMatches("", "kotlin").isEmpty())

        // Non-existing query returns empty list
        assertTrue(TextPagingEngine.findMatches(content, "Swift").isEmpty())
    }
}
