package io.legado.desktop

import io.legado.desktop.data.model.ClickZoneAction
import kotlin.test.Test
import kotlin.test.assertEquals

class ClickZoneEngineTest {

    @Test
    fun testClickZoneActionFromId() {
        assertEquals(ClickZoneAction.PAGE_PREV, ClickZoneAction.fromId("page_prev"))
        assertEquals(ClickZoneAction.PAGE_NEXT, ClickZoneAction.fromId("page_next"))
        assertEquals(ClickZoneAction.TOGGLE_MENU, ClickZoneAction.fromId("toggle_menu"))
        assertEquals(ClickZoneAction.OPEN_TOC, ClickZoneAction.fromId("open_toc"))
        assertEquals(ClickZoneAction.NONE, ClickZoneAction.fromId("invalid_id", ClickZoneAction.NONE))
    }

    @Test
    fun testClickZoneCoordinatePartition() {
        val totalWidth = 1000f

        // Ratio 0: 25% : 50% : 25%
        val leftBorderClassic = totalWidth * 0.25f
        val rightBorderClassic = totalWidth * (1f - 0.25f)

        fun resolveClassicAction(x: Float): String {
            return when {
                x < leftBorderClassic -> "LEFT"
                x > rightBorderClassic -> "RIGHT"
                else -> "CENTER"
            }
        }

        assertEquals("LEFT", resolveClassicAction(100f))
        assertEquals("LEFT", resolveClassicAction(249f))
        assertEquals("CENTER", resolveClassicAction(251f))
        assertEquals("CENTER", resolveClassicAction(500f))
        assertEquals("CENTER", resolveClassicAction(749f))
        assertEquals("RIGHT", resolveClassicAction(751f))
        assertEquals("RIGHT", resolveClassicAction(950f))

        // Ratio 1: 33.3% : 33.4% : 33.3%
        val leftBorderEqual = totalWidth * 0.333f
        val rightBorderEqual = totalWidth * (1f - 0.333f)

        fun resolveEqualAction(x: Float): String {
            return when {
                x < leftBorderEqual -> "LEFT"
                x > rightBorderEqual -> "RIGHT"
                else -> "CENTER"
            }
        }

        assertEquals("LEFT", resolveEqualAction(200f))
        assertEquals("CENTER", resolveEqualAction(400f))
        assertEquals("RIGHT", resolveEqualAction(800f))
    }
}
