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

    val HEAD_FORBIDDEN_CHARS = setOf(
        '，', '。', '、', '；', '：', '？', '！',
        '）', ']', '}', '’', '”', '』', '」', '〉', '》', '】', '〕',
        ',', '.', ';', ':', '?', '!', ')', '\'', '"', '%', '…', '—'
    )

    val TAIL_FORBIDDEN_CHARS = setOf(
        '（', '[', '{', '‘', '“', '『', '「', '〈', '《', '【', '〔',
        '(', '\'', '"'
    )

    /**
     * Splits a single paragraph into lines that fit within maxVisualWidthUnits,
     * with optional Chinese Kinsoku Shori (punctuation head/tail avoidance and hanging).
     */
    fun wrapParagraph(
        paragraph: String,
        maxVisualWidthUnits: Float,
        enableKinsoku: Boolean = true
    ): List<String> {
        if (paragraph.isBlank()) return listOf("")
        if (!enableKinsoku) {
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

        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()
        var currentWeight = 0f
        val chars = paragraph.toCharArray()
        var i = 0

        while (i < chars.size) {
            val ch = chars[i]
            val w = getCharWeight(ch)

            // Check if current character exceeds line width
            if (currentWeight + w > maxVisualWidthUnits && currentLine.isNotEmpty()) {
                // Rule 1: Punctuation Hanging (标点悬挂)
                // If current character is a head-forbidden punctuation, allow it to hang at the end of current line
                if (ch in HEAD_FORBIDDEN_CHARS && currentWeight <= maxVisualWidthUnits + 0.1f) {
                    currentLine.append(ch)
                    lines.add(currentLine.toString())
                    currentLine.clear()
                    currentWeight = 0f
                    i++
                    continue
                }

                // Rule 2: Tail-forbidden check on the end of currentLine (e.g. '《', '“')
                var poppedChar: Char? = null
                if (currentLine.isNotEmpty() && currentLine.last() in TAIL_FORBIDDEN_CHARS) {
                    poppedChar = currentLine.last()
                    currentLine.deleteCharAt(currentLine.length - 1)
                }

                // Rule 3: Head-forbidden check for next line's first char
                if (poppedChar == null && ch in HEAD_FORBIDDEN_CHARS && currentLine.length > 1) {
                    poppedChar = currentLine.last()
                    currentLine.deleteCharAt(currentLine.length - 1)
                }

                lines.add(currentLine.toString())
                currentLine.clear()
                currentWeight = 0f

                if (poppedChar != null) {
                    currentLine.append(poppedChar)
                    currentWeight += getCharWeight(poppedChar)
                }

                currentLine.append(ch)
                currentWeight += w
                i++
                continue
            }

            currentLine.append(ch)
            currentWeight += w
            i++
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
        firstLineIndent: Boolean = true,
        enableKinsoku: Boolean = true
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

            val wrappedLines = wrapParagraph(formattedPara, maxUnitsPerLine, enableKinsoku)

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

    /**
     * Calculates dual-page spreads with cross-chapter seamless stitching
     */
    fun createDualSpreads(
        currentChapterPages: List<String>,
        currentChapterTitle: String,
        nextChapterFirstPage: String? = null,
        nextChapterTitle: String? = null,
        nextChapterTotalPages: Int? = null
    ): List<DualSpreadPage> {
        if (currentChapterPages.isEmpty()) {
            return listOf(
                DualSpreadPage(
                    leftPageText = "",
                    leftChapterTitle = currentChapterTitle,
                    leftPageIndex = 0,
                    leftTotalPages = 1,
                    rightPageText = null,
                    rightChapterTitle = null,
                    rightPageIndex = null,
                    rightTotalPages = null
                )
            )
        }

        val spreads = mutableListOf<DualSpreadPage>()
        val totalCurrentPages = currentChapterPages.size
        var i = 0
        while (i < totalCurrentPages) {
            val leftText = currentChapterPages[i]
            val leftPageIdx = i
            val rightPageIdx = i + 1

            if (rightPageIdx < totalCurrentPages) {
                // Both pages belong to current chapter
                spreads.add(
                    DualSpreadPage(
                        leftPageText = leftText,
                        leftChapterTitle = currentChapterTitle,
                        leftPageIndex = leftPageIdx,
                        leftTotalPages = totalCurrentPages,
                        rightPageText = currentChapterPages[rightPageIdx],
                        rightChapterTitle = currentChapterTitle,
                        rightPageIndex = rightPageIdx,
                        rightTotalPages = totalCurrentPages,
                        isCrossChapter = false
                    )
                )
                i += 2
            } else {
                // Odd last page: stitch with next chapter's first page if available
                if (nextChapterFirstPage != null && nextChapterTitle != null) {
                    spreads.add(
                        DualSpreadPage(
                            leftPageText = leftText,
                            leftChapterTitle = currentChapterTitle,
                            leftPageIndex = leftPageIdx,
                            leftTotalPages = totalCurrentPages,
                            rightPageText = nextChapterFirstPage,
                            rightChapterTitle = nextChapterTitle,
                            rightPageIndex = 0,
                            rightTotalPages = nextChapterTotalPages ?: 1,
                            isCrossChapter = true
                        )
                    )
                } else {
                    spreads.add(
                        DualSpreadPage(
                            leftPageText = leftText,
                            leftChapterTitle = currentChapterTitle,
                            leftPageIndex = leftPageIdx,
                            leftTotalPages = totalCurrentPages,
                            rightPageText = null,
                            rightChapterTitle = null,
                            rightPageIndex = null,
                            rightTotalPages = null,
                            isCrossChapter = false,
                            isEndOfBook = true
                        )
                    )
                }
                i += 1
            }
        }
        return spreads
    }

    /**
     * Finds matches of query inside chapter content and maps them to corresponding virtual page index
     */
    fun findMatches(content: String, query: String, pages: List<String> = emptyList()): List<SearchMatch> {
        if (query.isBlank() || content.isBlank()) return emptyList()
        val results = mutableListOf<SearchMatch>()
        var startIndex = 0
        while (startIndex < content.length) {
            val idx = content.indexOf(query, startIndex, ignoreCase = true)
            if (idx < 0) break
            val snippetStart = (idx - 15).coerceAtLeast(0)
            val snippetEnd = (idx + query.length + 15).coerceAtMost(content.length)
            val snippet = content.substring(snippetStart, snippetEnd).replace("\n", " ")

            var targetPage = 0
            if (pages.isNotEmpty()) {
                var cumChars = 0
                for (pIdx in pages.indices) {
                    val pText = pages[pIdx]
                    cumChars += pText.length
                    if (idx < cumChars) {
                        targetPage = pIdx
                        break
                    }
                    targetPage = pIdx
                }
            }

            results.add(
                SearchMatch(
                    matchIndex = idx,
                    length = query.length,
                    snippet = snippet,
                    pageIndex = targetPage
                )
            )
            startIndex = idx + query.length.coerceAtLeast(1)
        }
        return results
    }
}

/**
 * Data structure representing a search hit inside chapter content
 */
data class SearchMatch(
    val matchIndex: Int,
    val length: Int,
    val snippet: String,
    val pageIndex: Int = 0
)

/**
 * Data structure representing a dual-page spread on wide desktop displays
 */
data class DualSpreadPage(
    val leftPageText: String,
    val leftChapterTitle: String,
    val leftPageIndex: Int,
    val leftTotalPages: Int,
    val rightPageText: String?,
    val rightChapterTitle: String?,
    val rightPageIndex: Int?,
    val rightTotalPages: Int?,
    val isCrossChapter: Boolean = false,
    val isEndOfBook: Boolean = false
)

/**
 * Helper for mouse wheel physical damping and debounce accumulation
 */
object MouseWheelDampingHelper {
    const val DEFAULT_THRESHOLD = 1.0f
    const val DEBOUNCE_TIMEOUT_MS = 250L

    fun processScroll(
        currentAccumulator: Float,
        lastScrollTimestamp: Long,
        scrollDeltaY: Float,
        currentTimeMs: Long,
        threshold: Float = DEFAULT_THRESHOLD,
        debounceTimeoutMs: Long = DEBOUNCE_TIMEOUT_MS
    ): Pair<Float, Int> {
        val resetAccumulator = if (currentTimeMs - lastScrollTimestamp > debounceTimeoutMs) 0f else currentAccumulator
        val newAccumulator = resetAccumulator + scrollDeltaY

        return when {
            newAccumulator >= threshold -> Pair(0f, 1) // Next page
            newAccumulator <= -threshold -> Pair(0f, -1) // Prev page
            else -> Pair(newAccumulator, 0)
        }
    }
}
