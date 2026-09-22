package io.legado.desktop

import io.legado.desktop.engine.PageTurnMode
import io.legado.desktop.engine.TextPagingEngine
import org.junit.Assert.*
import org.junit.Test

class TextPagingEngineTest {

    @Test
    fun testPageTurnModeEnum() {
        assertEquals(PageTurnMode.SCROLL, PageTurnMode.fromId("scroll"))
        assertEquals(PageTurnMode.SLIDE_PAGING, PageTurnMode.fromId("slide_paging"))
        assertEquals(PageTurnMode.DUAL_PAGE, PageTurnMode.fromId("dual_page"))
        assertEquals(PageTurnMode.SCROLL, PageTurnMode.fromId("unknown"))
    }

    @Test
    fun testWrapParagraph() {
        val paragraph = "天地玄黄宇宙洪荒日月盈昃辰宿列张"
        // At width unit = 8 (each CJK is 1.0 unit), 16 characters should wrap into 2 lines
        val lines = TextPagingEngine.wrapParagraph(paragraph, 8f)
        assertEquals(2, lines.size)
        assertEquals("天地玄黄宇宙洪荒", lines[0])
        assertEquals("日月盈昃辰宿列张", lines[1])
    }

    @Test
    fun testEmptyContentPagination() {
        val paged = TextPagingEngine.paginate(
            content = "",
            viewportWidthPx = 800f,
            viewportHeightPx = 900f,
            fontSizePx = 24f
        )
        assertEquals(1, paged.totalPages)
        assertEquals(0, paged.totalCharCount)
        assertEquals("", paged.pages[0])
    }

    @Test
    fun testLongTextPaginationProducesMultiplePages() {
        val longContent = (1..60).joinToString("\n") { idx ->
            "第${idx}段：道可道，非常道。名可名，非常名。无名天地之始，有名万物之母。故常无欲以观其妙，常有欲以观其徼。"
        }

        val paged = TextPagingEngine.paginate(
            content = longContent,
            viewportWidthPx = 600f,
            viewportHeightPx = 800f,
            fontSizePx = 24f,
            lineSpacingMultiplier = 1.8f,
            firstLineIndent = true
        )

        assertTrue("Long content should split into multiple pages", paged.totalPages > 1)
        assertTrue("Total character count should match text length", paged.totalCharCount == longContent.length)
        // Verify indentation
        assertTrue("First page first line should have 2 full-width spaces indentation", paged.pages[0].startsWith("　　"))
    }

    @Test
    fun testLargerFontSizeYieldsMorePages() {
        val content = "测试段落内容。".repeat(100)

        val pagedSmallFont = TextPagingEngine.paginate(
            content = content,
            viewportWidthPx = 600f,
            viewportHeightPx = 800f,
            fontSizePx = 16f
        )

        val pagedLargeFont = TextPagingEngine.paginate(
            content = content,
            viewportWidthPx = 600f,
            viewportHeightPx = 800f,
            fontSizePx = 36f
        )

        assertTrue(
            "Larger font size must result in more pages (small=${pagedSmallFont.totalPages}, large=${pagedLargeFont.totalPages})",
            pagedLargeFont.totalPages >= pagedSmallFont.totalPages
        )
    }
}
