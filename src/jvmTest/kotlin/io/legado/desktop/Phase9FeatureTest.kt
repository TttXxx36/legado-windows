package io.legado.desktop

import io.legado.desktop.engine.MouseWheelDampingHelper
import io.legado.desktop.engine.TextPagingEngine
import org.junit.Assert.*
import org.junit.Test

class Phase9FeatureTest {

    @Test
    fun testDualPageSpreadLayoutSplittingEvenPages() {
        val pages = listOf(
            "第一页内容：混沌初开，天地分明。",
            "第二页内容：大道无形，生育天地。",
            "第三页内容：大道无情，运行日月。",
            "第四页内容：大道无名，长养万物。"
        )

        val spreads = TextPagingEngine.createDualSpreads(
            currentChapterPages = pages,
            currentChapterTitle = "第一章 序章",
            nextChapterFirstPage = null,
            nextChapterTitle = null
        )

        assertEquals("4页应该恰好划分为2个双页对开", 2, spreads.size)

        // Spread 0
        assertEquals(0, spreads[0].leftPageIndex)
        assertEquals(1, spreads[0].rightPageIndex)
        assertEquals(pages[0], spreads[0].leftPageText)
        assertEquals(pages[1], spreads[0].rightPageText)
        assertFalse("非跨章对开", spreads[0].isCrossChapter)
        assertFalse(spreads[0].isEndOfBook)

        // Spread 1
        assertEquals(2, spreads[1].leftPageIndex)
        assertEquals(3, spreads[1].rightPageIndex)
        assertEquals(pages[2], spreads[1].leftPageText)
        assertEquals(pages[3], spreads[1].rightPageText)
        assertFalse(spreads[1].isCrossChapter)
        assertFalse(spreads[1].isEndOfBook)
    }

    @Test
    fun testDualPageCrossChapterSeamlessStitching() {
        val curPages = listOf(
            "第一章 页面一",
            "第一章 页面二",
            "第一章 页面三（奇数尾页）"
        )
        val nextFirstPage = "第二章 页面一（下一章序言）"

        val spreads = TextPagingEngine.createDualSpreads(
            currentChapterPages = curPages,
            currentChapterTitle = "第一章",
            nextChapterFirstPage = nextFirstPage,
            nextChapterTitle = "第二章 乘风归来",
            nextChapterTotalPages = 5
        )

        assertEquals("3页正文应产生2个对开页", 2, spreads.size)

        // Spread 1 must have left = curPages[2] and right = nextFirstPage
        val lastSpread = spreads[1]
        assertEquals("第一章 页面三（奇数尾页）", lastSpread.leftPageText)
        assertEquals(2, lastSpread.leftPageIndex)
        assertEquals("第一章", lastSpread.leftChapterTitle)

        assertEquals("第二章 页面一（下一章序言）", lastSpread.rightPageText)
        assertEquals(0, lastSpread.rightPageIndex)
        assertEquals("第二章 乘风归来", lastSpread.rightChapterTitle)
        assertEquals(5, lastSpread.rightTotalPages)
        assertTrue("最后一页无缝拼接下一章第1页", lastSpread.isCrossChapter)
        assertFalse(lastSpread.isEndOfBook)
    }

    @Test
    fun testDualPageEndOfBookWhenOddPages() {
        val curPages = listOf("全书最后一章 独页")

        val spreads = TextPagingEngine.createDualSpreads(
            currentChapterPages = curPages,
            currentChapterTitle = "最终章 大结局",
            nextChapterFirstPage = null,
            nextChapterTitle = null
        )

        assertEquals(1, spreads.size)
        val finalSpread = spreads[0]
        assertEquals("全书最后一章 独页", finalSpread.leftPageText)
        assertNull("无下一章时右页为空", finalSpread.rightPageText)
        assertFalse(finalSpread.isCrossChapter)
        assertTrue("标记为全书完结", finalSpread.isEndOfBook)
    }

    @Test
    fun testMouseWheelDampingAccumulator() {
        var acc = 0f
        var lastTime = 1000L

        // Tick 1: scroll down 0.4f -> no trigger
        val step1 = MouseWheelDampingHelper.processScroll(acc, lastTime, 0.4f, 1050L)
        acc = step1.first
        assertEquals(0, step1.second)
        assertEquals(0.4f, acc, 0.001f)

        // Tick 2: scroll down 0.4f -> no trigger (total 0.8f)
        val step2 = MouseWheelDampingHelper.processScroll(acc, 1050L, 0.4f, 1100L)
        acc = step2.first
        assertEquals(0, step2.second)
        assertEquals(0.8f, acc, 0.001f)

        // Tick 3: scroll down 0.3f -> triggers NEXT (total 1.1f >= 1.0f) and resets to 0f
        val step3 = MouseWheelDampingHelper.processScroll(acc, 1100L, 0.3f, 1150L)
        acc = step3.first
        assertEquals("触发翻下一页", 1, step3.second)
        assertEquals(0f, acc, 0.001f)

        // Tick 4: scroll up -1.2f -> triggers PREV directly
        val step4 = MouseWheelDampingHelper.processScroll(acc, 1150L, -1.2f, 1200L)
        acc = step4.first
        assertEquals("触发翻上一页", -1, step4.second)
        assertEquals(0f, acc, 0.001f)

        // Tick 5: partial scroll + timeout reset
        val step5 = MouseWheelDampingHelper.processScroll(acc, 1200L, 0.6f, 1220L)
        acc = step5.first
        assertEquals(0.6f, acc, 0.001f)

        // 300ms later (> 250ms timeout)
        val step6 = MouseWheelDampingHelper.processScroll(acc, 1220L, 0.2f, 1600L)
        acc = step6.first
        assertEquals("超时后前序累积清空，仅保留本次 0.2f", 0.2f, acc, 0.001f)
        assertEquals(0, step6.second)
    }

    @Test
    fun testStatusBarProgressCalculation() {
        fun calcProgress(curIndex: Int, totalChapters: Int): Int {
            return if (totalChapters > 0) ((curIndex + 1) * 100 / totalChapters) else 0
        }

        assertEquals(0, calcProgress(0, 0))
        assertEquals(100, calcProgress(0, 1))
        assertEquals(50, calcProgress(49, 100))
        assertEquals(33, calcProgress(32, 100))
        assertEquals(100, calcProgress(99, 100))
    }
}
