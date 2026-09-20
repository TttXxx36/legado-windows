package io.legado.desktop.engine

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class PagedChapter(
    val pages: List<String>,
    val totalCharCount: Int,
    val totalPages: Int = pages.size
)

enum class PageTurnMode(val id: String, val title: String) {
    SCROLL("scroll", "垂直平滑滚动"),
    SLIDE_PAGING("slide_paging", "平移仿真分页");

    companion object {
        fun fromId(id: String): PageTurnMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SCROLL
        }
    }
}

object TextPagingEngine {

    /**
     * Measure visual width of a character: CJK/Full-width is 1.0, Half-width is 0.55
     */
    private fun getCharWeight(c: Char): Float {
        return if (c.code in 0..127) 0.55f else 1.0f
    }

    /**
     * Splits a single paragraph into lines that fit within maxVisualWidthUnits
     */
    fun wrapParagraph(paragraph: String, maxVisualWidthUnits: Float): List<String> {
        if (paragraph.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()
        var currentWeight = 0f

        for (ch in paragraph) {
            val w = getCharWeight(ch)
            if (currentWeight + w > maxVisualWidthUnits && currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine.clear()
                currentWeight = 0f
            }
            currentLine.append(ch)
            currentWeight += w
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    /**
     * Splits chapter content into pages based on viewport dimensions and typography parameters
     */
    fun paginate(
        content: String,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        fontSizePx: Float,
        lineSpacingMultiplier: Float = 1.75f,
        paragraphSpacingPx: Float = 16f,
        firstLineIndent: Boolean = true
    ): PagedChapter {
        val totalChars = content.length
        if (content.isBlank()) {
            return PagedChapter(pages = listOf(""), totalCharCount = 0)
        }

        val effectiveWidth = viewportWidthPx.coerceAtLeast(300f)
        val effectiveHeight = viewportHeightPx.coerceAtLeast(400f)

        // Visual width capacity in full-width character units
        val maxUnitsPerLine = max(10f, effectiveWidth / fontSizePx.coerceAtLeast(12f))

        // Vertical capacity in lines
        val lineHeightPx = fontSizePx * lineSpacingMultiplier.coerceIn(1.2f, 2.5f)
        val maxLinesPerPage = max(6, floor(effectiveHeight / lineHeightPx).toInt())
        val paragraphExtraLines = (paragraphSpacingPx / lineHeightPx).coerceIn(0f, 2f)

        val paragraphs = content.lines()
        val pages = mutableListOf<String>()
        val currentPageText = StringBuilder()
        var currentLinesOnPage = 0f

        for (rawPara in paragraphs) {
            val trimmed = rawPara.trim()
            if (trimmed.isEmpty()) continue

            val formattedPara = if (firstLineIndent) {
                if (!trimmed.startsWith("　　") && !trimmed.startsWith("  ")) {
                    "　　$trimmed"
                } else trimmed
            } else {
                trimmed
            }

            val wrappedLines = wrapParagraph(formattedPara, maxUnitsPerLine)

            for (i in wrappedLines.indices) {
                val line = wrappedLines[i]
                val isLastLineOfPara = (i == wrappedLines.size - 1)
                val lineCost = 1f + (if (isLastLineOfPara) paragraphExtraLines else 0f)

                if (currentLinesOnPage + lineCost > maxLinesPerPage && currentPageText.isNotEmpty()) {
                    pages.add(currentPageText.toString().trimEnd())
                    currentPageText.clear()
                    currentLinesOnPage = 0f
                }

                currentPageText.append(line).append("\n")
                if (isLastLineOfPara && paragraphSpacingPx > 4f) {
                    currentPageText.append("\n")
                }
                currentLinesOnPage += lineCost
            }
        }

        if (currentPageText.isNotEmpty()) {
            pages.add(currentPageText.toString().trimEnd())
        }

        if (pages.isEmpty()) {
            pages.add(content)
        }

        return PagedChapter(pages = pages, totalCharCount = totalChars)
    }
}
