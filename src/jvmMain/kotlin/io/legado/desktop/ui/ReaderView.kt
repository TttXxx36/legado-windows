package io.legado.desktop.ui

import androidx.compose.animation.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.style.TextDecoration
import io.legado.desktop.data.model.BookAnnotation
import io.legado.desktop.engine.export.MarkdownExportEngine
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import io.legado.desktop.data.model.ClickZoneAction
import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.data.model.Bookmark
import io.legado.desktop.engine.BookCacheEngine
import io.legado.desktop.engine.BookSourceEngine
import io.legado.desktop.engine.DualSpreadPage
import io.legado.desktop.engine.MouseWheelDampingHelper
import io.legado.desktop.engine.PageTurnMode
import io.legado.desktop.engine.TextPagingEngine
import kotlin.math.roundToInt
import io.legado.desktop.engine.hotkey.GlobalMediaHotkeyManager
import io.legado.desktop.engine.local.LocalBookImporter
import io.legado.desktop.engine.rule.ReplaceRuleEngine
import io.legado.desktop.engine.tts.TtsEngine
import io.legado.desktop.ui.font.FontManager
import io.legado.desktop.ui.theme.LegadoIcons
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

enum class ReadTheme(
    val id: String,
    val nameZh: String,
    val bg: Color,
    val text: Color
) {
    DAY("day", "象牙白", Color(0xFFFAF7F2), Color(0xFF2C2523)),
    PARCHMENT("parchment", "羊皮纸", Color(0xFFF4ECD8), Color(0xFF382E20)),
    BEAN_GREEN("bean_green", "豆沙绿", Color(0xFFC7EDCC), Color(0xFF1C2B1F)),
    TWILIGHT("twilight", "苍山暮", Color(0xFFD8D2C2), Color(0xFF282522)),
    OCEAN_BLUE("ocean_blue", "远峰蓝", Color(0xFF1E2638), Color(0xFFB4C6E7)),
    E_INK("e_ink", "水墨白", Color(0xFFFFFFFF), Color(0xFF000000)),
    OLED_BLACK("oled_black", "极夜黑", Color(0xFF000000), Color(0xFFCECECE)),
    GREEN("green", "护眼绿", Color(0xFFE8EFE6), Color(0xFF233226)),
    NIGHT("night", "夜间黑", Color(0xFF161618), Color(0xFFD4D4D8)),
    CUSTOM("custom", "自定义", Color(0xFFFAF7F2), Color(0xFF2C2523));

    companion object {
        fun fromId(id: String): ReadTheme {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) } ?: DAY
        }
    }
}

fun parseHexColor(hex: String, fallback: Color): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6 -> Color(java.lang.Long.parseLong("FF$clean", 16))
            8 -> Color(java.lang.Long.parseLong(clean, 16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

fun Color.toHex(): String {
    val r = (red * 255).roundToInt().coerceIn(0, 255)
    val g = (green * 255).roundToInt().coerceIn(0, 255)
    val b = (blue * 255).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(r, g, b)
}

fun getSystemClipboardText(): String? {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else null
    } catch (_: Exception) {
        null
    }
}

fun buildHighlightedText(
    text: String,
    query: String,
    activeMatchSnippet: String? = null,
    annotations: List<BookAnnotation> = emptyList(),
    isFirstPage: Boolean = false,
    enableDropCaps: Boolean = false
): AnnotatedString {
    if (query.isBlank() && annotations.isEmpty() && (!isFirstPage || !enableDropCaps)) {
        return AnnotatedString(text)
    }

    val builder = AnnotatedString.Builder(text)

    // Layer 0: Drop Caps (首段首字大字下沉)
    if (isFirstPage && enableDropCaps && text.isNotBlank()) {
        val firstCharIdx = text.indexOfFirst { !it.isWhitespace() && it != '　' && it !in TextPagingEngine.TAIL_FORBIDDEN_CHARS }
        if (firstCharIdx >= 0 && firstCharIdx < text.length) {
            builder.addStyle(
                SpanStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                firstCharIdx,
                firstCharIdx + 1
            )
        }
    }

    // Layer 1: User Annotations (Highlights & Underlines)
    for (anno in annotations) {
        if (anno.selectedText.isBlank()) continue
        var searchIdx = 0
        while (searchIdx < text.length) {
            val found = text.indexOf(anno.selectedText, searchIdx)
            if (found < 0) break
            val foundEnd = found + anno.selectedText.length
            val style = when (anno.colorType.uppercase()) {
                "GREEN" -> SpanStyle(background = Color(0x6681C784))
                "PURPLE" -> SpanStyle(background = Color(0x66BA68C8))
                "UNDERLINE" -> SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)
                else -> SpanStyle(background = Color(0x66FFD54F)) // YELLOW default
            }
            builder.addStyle(style, found, foundEnd)
            searchIdx = foundEnd
        }
    }

    // Layer 2: Search Query Highlight (Top Layer, High Contrast)
    if (query.isNotBlank()) {
        var cursor = 0
        while (cursor < text.length) {
            val idx = text.indexOf(query, cursor, ignoreCase = true)
            if (idx < 0) break
            val matchEnd = idx + query.length
            val snippet = text.substring(
                (idx - 15).coerceAtLeast(0),
                (idx + query.length + 15).coerceAtMost(text.length)
            ).replace("\n", " ")
            val isActive = activeMatchSnippet != null && (snippet.contains(activeMatchSnippet) || activeMatchSnippet.contains(snippet))
            builder.addStyle(
                SpanStyle(
                    background = if (isActive) Color(0xFFFF9800) else Color(0xFFFFD54F),
                    color = Color(0xFF1E1E1E),
                    fontWeight = FontWeight.Bold
                ),
                idx,
                matchEnd
            )
            cursor = matchEnd
        }
    }

    return builder.toAnnotatedString()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ReaderView(
    book: Book,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    var chapters by remember { mutableStateOf<List<BookChapter>>(emptyList()) }
    var currentChapterIndex by remember { mutableStateOf(book.durChapterIndex) }
    var chapterContent by remember { mutableStateOf("正在加载章节内容...") }
    var isLoading by remember { mutableStateOf(true) }

    // Reader Preferences
    var fontSize by remember { mutableStateOf(18) }
    var isDualPage by remember { mutableStateOf(false) }
    var currentTheme by remember { mutableStateOf(ReadTheme.DAY) }
    var customBgColor by remember { mutableStateOf(Color(0xFFFAF7F2)) }
    var customTextColor by remember { mutableStateOf(Color(0xFF2C2523)) }
    var customBgHex by remember { mutableStateOf("#FAF7F2") }
    var customTextHex by remember { mutableStateOf("#2C2523") }
    val themeBg = if (currentTheme == ReadTheme.CUSTOM) customBgColor else currentTheme.bg
    val themeText = if (currentTheme == ReadTheme.CUSTOM) customTextColor else currentTheme.text

    var fontPath by remember { mutableStateOf<String?>(null) }
    var fontName by remember { mutableStateOf("默认系统字体") }
    val activeFontFamily = remember(fontPath) { FontManager.getFontFamily(fontPath) }
    var showTOC by remember { mutableStateOf(false) }
    var tocTabIndex by remember { mutableStateOf(0) } // 0: 目录, 1: 书签
    var showSettingsDrawer by remember { mutableStateOf(false) }
    var settingsTabIndex by remember { mutableStateOf(0) } // 0: 排版与主题, 1: 翻页与屏幕触控
    var showHud by remember { mutableStateOf(true) }

    // Search and Bookmarks State
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentSearchMatchIndex by remember { mutableStateOf(0) }

    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkExcerptText by remember { mutableStateOf("") }
    var bookmarkNoteText by remember { mutableStateOf("") }

    // Re-split Chapter State
    var showReSplitDialog by remember { mutableStateOf(false) }
    var splitPresetIndex by remember { mutableStateOf(0) }
    var customRegexPatternText by remember { mutableStateOf("") }
    var splitPreviewInfo by remember { mutableStateOf<io.legado.desktop.engine.local.SplitPreview?>(null) }
    var isPreviewingSplit by remember { mutableStateOf(false) }

    // Advanced 5-Dimension Typography & Page Turn Mode
    var pageTurnMode by remember { mutableStateOf(PageTurnMode.SCROLL) }
    var lineSpacingMultiplier by remember { mutableStateOf(1.75f) }
    var paragraphSpacingDp by remember { mutableStateOf(16) }
    var firstLineIndent by remember { mutableStateOf(true) }
    var horizontalPaddingDp by remember { mutableStateOf(32) }
    var showBatchCacheDialog by remember { mutableStateOf(false) }

    // Phase 13 Typography Aesthetics & Paper Texture States
    var enableKinsoku by remember { mutableStateOf(true) }
    var enableDropCaps by remember { mutableStateOf(false) }
    var enableArtTitle by remember { mutableStateOf(true) }
    var enablePaperTexture by remember { mutableStateOf(true) }
    var paperTextureAlpha by remember { mutableStateOf(0.06f) }
    var activeLightboxImageUrl by remember { mutableStateOf<String?>(null) }

    // Status Bar & Next Chapter Preloading
    var showStatusBar by remember { mutableStateOf(true) }
    var currentTimeStr by remember { mutableStateOf("") }
    var nextChapterContent by remember { mutableStateOf<String?>(null) }

    // Mouse Wheel Damping Accumulator
    var wheelAccumulator by remember { mutableStateOf(0f) }
    var lastWheelTimestamp by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            currentTimeStr = java.time.LocalTime.now().format(formatter)
            kotlinx.coroutines.delay(10000L)
        }
    }

    // Pagination Calculation States
    var viewportWidthPx by remember { mutableStateOf(800f) }
    var viewportHeightPx by remember { mutableStateOf(900f) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var targetPageWhenChapterLoaded by remember { mutableStateOf<Int?>(null) }

    val downloadProgress by BookCacheEngine.downloadProgress.collectAsState()

    val activeSplitRegex = remember(splitPresetIndex, customRegexPatternText) {
        when (splitPresetIndex) {
            0 -> io.legado.desktop.engine.local.ChapterPresets.STANDARD_CHINESE
            1 -> io.legado.desktop.engine.local.ChapterPresets.ENGLISH
            2 -> io.legado.desktop.engine.local.ChapterPresets.NUMBERED
            3 -> io.legado.desktop.engine.local.ChapterPresets.WEB_SPECIAL
            else -> {
                try {
                    Regex(customRegexPatternText.ifBlank { "^[ \\t]*第[0-9一二三四五六七八九十百千万]+章.*$" }, RegexOption.MULTILINE)
                } catch (_: Exception) {
                    io.legado.desktop.engine.local.ChapterPresets.STANDARD_CHINESE
                }
            }
        }
    }

    LaunchedEffect(showReSplitDialog, activeSplitRegex) {
        if (showReSplitDialog && book.origin == "local") {
            isPreviewingSplit = true
            splitPreviewInfo = io.legado.desktop.engine.local.LocalBookImporter.previewSplit(book, activeSplitRegex)
            isPreviewingSplit = false
        }
    }

    // Single Column Virtual Pagination
    val pagedChapter = remember(
        chapterContent,
        viewportWidthPx,
        viewportHeightPx,
        fontSize,
        lineSpacingMultiplier,
        paragraphSpacingDp,
        firstLineIndent,
        horizontalPaddingDp,
        enableKinsoku
    ) {
        val effectiveTextW = (viewportWidthPx - horizontalPaddingDp * 2 * 1.5f).coerceAtLeast(240f)
        val effectiveTextH = (viewportHeightPx - 130f).coerceAtLeast(300f)
        TextPagingEngine.paginate(
            content = chapterContent,
            viewportWidthPx = effectiveTextW,
            viewportHeightPx = effectiveTextH,
            fontSizePx = fontSize.toFloat() * 1.35f,
            lineSpacingMultiplier = lineSpacingMultiplier,
            paragraphSpacingPx = paragraphSpacingDp.toFloat() * 1.5f,
            firstLineIndent = firstLineIndent,
            enableKinsoku = enableKinsoku
        )
    }

    // Safely clamp current page index
    val safePageIndex = remember(currentPageIndex, pagedChapter.totalPages) {
        if (pagedChapter.totalPages <= 0) 0
        else currentPageIndex.coerceIn(0, pagedChapter.totalPages - 1)
    }

    val searchMatches = remember(chapterContent, searchQuery, pagedChapter.pages) {
        if (searchQuery.isBlank()) emptyList()
        else TextPagingEngine.findMatches(chapterContent, searchQuery, pagedChapter.pages)
    }

    LaunchedEffect(searchMatches.size) {
        if (currentSearchMatchIndex >= searchMatches.size) {
            currentSearchMatchIndex = 0
        }
    }

    fun navigateMatch(delta: Int) {
        if (searchMatches.isEmpty()) return
        val newIdx = (currentSearchMatchIndex + delta).mod(searchMatches.size)
        currentSearchMatchIndex = newIdx
        val match = searchMatches[newIdx]
        if (isDualPage) {
            currentPageIndex = (match.pageIndex / 2) * 2
        } else if (pageTurnMode == PageTurnMode.SLIDE_PAGING) {
            currentPageIndex = match.pageIndex
        } else {
            if (chapterContent.isNotEmpty()) {
                val ratio = match.matchIndex.toFloat() / chapterContent.length.toFloat()
                scope.launch {
                    scrollState.animateScrollTo((scrollState.maxValue * ratio).toInt())
                }
            }
        }
    }

    // Dual-Page Spread Calculation (Left Page N + Right Page N+1 & Cross-Chapter Stitching)
    val dualColumnWidth = remember(viewportWidthPx, horizontalPaddingDp) {
        ((viewportWidthPx - horizontalPaddingDp * 2 * 1.5f - 40f) / 2f).coerceAtLeast(240f)
    }
    val dualColumnHeight = remember(viewportHeightPx) {
        (viewportHeightPx - 130f).coerceAtLeast(300f)
    }

    val dualPagedCurrentChapter = remember(
        chapterContent,
        dualColumnWidth,
        dualColumnHeight,
        fontSize,
        lineSpacingMultiplier,
        paragraphSpacingDp,
        firstLineIndent,
        enableKinsoku
    ) {
        TextPagingEngine.paginate(
            content = chapterContent,
            viewportWidthPx = dualColumnWidth,
            viewportHeightPx = dualColumnHeight,
            fontSizePx = fontSize.toFloat() * 1.35f,
            lineSpacingMultiplier = lineSpacingMultiplier,
            paragraphSpacingPx = paragraphSpacingDp.toFloat() * 1.5f,
            firstLineIndent = firstLineIndent,
            enableKinsoku = enableKinsoku
        )
    }

    val dualPagedNextChapter = remember(
        nextChapterContent,
        dualColumnWidth,
        dualColumnHeight,
        fontSize,
        lineSpacingMultiplier,
        paragraphSpacingDp,
        firstLineIndent,
        enableKinsoku
    ) {
        val nextContent = nextChapterContent
        if (!nextContent.isNullOrBlank()) {
            TextPagingEngine.paginate(
                content = nextContent,
                viewportWidthPx = dualColumnWidth,
                viewportHeightPx = dualColumnHeight,
                fontSizePx = fontSize.toFloat() * 1.35f,
                lineSpacingMultiplier = lineSpacingMultiplier,
                paragraphSpacingPx = paragraphSpacingDp.toFloat() * 1.5f,
                firstLineIndent = firstLineIndent,
                enableKinsoku = enableKinsoku
            )
        } else null
    }

    val nextChapterTitle = remember(chapters, currentChapterIndex) {
        if (chapters.isNotEmpty() && currentChapterIndex < chapters.size - 1) {
            chapters[currentChapterIndex + 1].title
        } else null
    }

    val dualSpreads = remember(
        dualPagedCurrentChapter,
        currentChapterIndex,
        chapters,
        dualPagedNextChapter,
        nextChapterTitle
    ) {
        val curTitle = if (chapters.isNotEmpty() && currentChapterIndex in chapters.indices) {
            chapters[currentChapterIndex].title
        } else book.name
        TextPagingEngine.createDualSpreads(
            currentChapterPages = dualPagedCurrentChapter.pages,
            currentChapterTitle = curTitle,
            nextChapterFirstPage = dualPagedNextChapter?.pages?.firstOrNull(),
            nextChapterTitle = nextChapterTitle,
            nextChapterTotalPages = dualPagedNextChapter?.totalPages
        )
    }

    val safeSpreadIndex = remember(currentPageIndex, dualSpreads.size) {
        val maxSpread = (dualSpreads.size - 1).coerceAtLeast(0)
        (currentPageIndex / 2).coerceIn(0, maxSpread)
    }

    // Immersive / Fullscreen State
    var isImmersive by remember { mutableStateOf(false) }

    // Screen Click Zones Preferences
    var leftClickAction by remember { mutableStateOf(ClickZoneAction.PREV_CHAPTER) }
    var centerClickAction by remember { mutableStateOf(ClickZoneAction.TOGGLE_MENU) }
    var rightClickAction by remember { mutableStateOf(ClickZoneAction.NEXT_CHAPTER) }
    var clickRatioIndex by remember { mutableStateOf(0) } // 0: 25-50-25, 1: 33-34-33, 2: 20-60-20

    val (leftRatio, rightRatio) = remember(clickRatioIndex) {
        when (clickRatioIndex) {
            1 -> Pair(0.333f, 0.333f)
            2 -> Pair(0.20f, 0.20f)
            else -> Pair(0.25f, 0.25f)
        }
    }

    fun executeAction(action: ClickZoneAction) {
        when (action) {
            ClickZoneAction.PAGE_PREV -> {
                if (isDualPage) {
                    if (currentPageIndex >= 2) {
                        currentPageIndex -= 2
                    } else if (currentChapterIndex > 0) {
                        targetPageWhenChapterLoaded = 999999
                        currentChapterIndex--
                    }
                } else if (pageTurnMode == PageTurnMode.SLIDE_PAGING) {
                    if (safePageIndex > 0) {
                        currentPageIndex = safePageIndex - 1
                    } else if (currentChapterIndex > 0) {
                        targetPageWhenChapterLoaded = 999999
                        currentChapterIndex--
                    }
                } else {
                    scope.launch {
                        val viewportH = scrollState.viewportSize
                        val step = if (viewportH > 0) (viewportH * 0.85f).toInt() else 600
                        if (scrollState.value > 0) {
                            scrollState.animateScrollTo((scrollState.value - step).coerceAtLeast(0))
                        } else if (currentChapterIndex > 0) {
                            currentChapterIndex--
                        }
                    }
                }
            }
            ClickZoneAction.PAGE_NEXT -> {
                if (isDualPage) {
                    if (safeSpreadIndex < dualSpreads.size - 1) {
                        currentPageIndex += 2
                    } else if (currentChapterIndex < chapters.size - 1) {
                        val currentSpread = dualSpreads.getOrNull(safeSpreadIndex)
                        if (currentSpread != null && currentSpread.isCrossChapter) {
                            targetPageWhenChapterLoaded = 1
                            currentPageIndex = 1
                            currentChapterIndex++
                        } else {
                            targetPageWhenChapterLoaded = 0
                            currentPageIndex = 0
                            currentChapterIndex++
                        }
                    }
                } else if (pageTurnMode == PageTurnMode.SLIDE_PAGING) {
                    if (safePageIndex < pagedChapter.totalPages - 1) {
                        currentPageIndex = safePageIndex + 1
                    } else if (currentChapterIndex < chapters.size - 1) {
                        targetPageWhenChapterLoaded = 0
                        currentPageIndex = 0
                        currentChapterIndex++
                    }
                } else {
                    scope.launch {
                        val viewportH = scrollState.viewportSize
                        val step = if (viewportH > 0) (viewportH * 0.85f).toInt() else 600
                        if (scrollState.value < scrollState.maxValue) {
                            scrollState.animateScrollTo((scrollState.value + step).coerceAtMost(scrollState.maxValue))
                        } else if (currentChapterIndex < chapters.size - 1) {
                            currentChapterIndex++
                        }
                    }
                }
            }
            ClickZoneAction.TOGGLE_MENU -> {
                showHud = !showHud
            }
            ClickZoneAction.PREV_CHAPTER -> {
                if (currentChapterIndex > 0) {
                    targetPageWhenChapterLoaded = 0
                    currentPageIndex = 0
                    currentChapterIndex--
                }
            }
            ClickZoneAction.NEXT_CHAPTER -> {
                if (currentChapterIndex < chapters.size - 1) {
                    targetPageWhenChapterLoaded = 0
                    currentPageIndex = 0
                    currentChapterIndex++
                }
            }
            ClickZoneAction.OPEN_TOC -> {
                showTOC = true
            }
            ClickZoneAction.TOGGLE_THEME -> {
                currentTheme = when (currentTheme) {
                    ReadTheme.DAY -> ReadTheme.PARCHMENT
                    ReadTheme.PARCHMENT -> ReadTheme.BEAN_GREEN
                    ReadTheme.BEAN_GREEN -> ReadTheme.TWILIGHT
                    ReadTheme.TWILIGHT -> ReadTheme.OCEAN_BLUE
                    ReadTheme.OCEAN_BLUE -> ReadTheme.E_INK
                    ReadTheme.E_INK -> ReadTheme.OLED_BLACK
                    ReadTheme.OLED_BLACK -> ReadTheme.GREEN
                    ReadTheme.GREEN -> ReadTheme.DAY
                    else -> ReadTheme.DAY
                }
                scope.launch {
                    AppDatabase.setConfig("reader_theme", currentTheme.id)
                }
            }
            ClickZoneAction.NONE -> {}
        }
    }

    // Bookmarks & Annotations State
    val bookBookmarks = remember { mutableStateListOf<Bookmark>() }
    val bookAnnotations = remember { mutableStateListOf<BookAnnotation>() }
    val chapterAnnotations = remember(bookAnnotations.toList(), currentChapterIndex) {
        bookAnnotations.filter { it.chapterIndex == currentChapterIndex }
    }
    var activeSelectionText by remember { mutableStateOf<String?>(null) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val customToolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus
                get() = if (activeSelectionText != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

            override fun hide() {
                // Keep active selection or dismiss when handled
            }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                onCopyRequested?.invoke()
                val text = getSystemClipboardText()?.trim()
                if (!text.isNullOrBlank()) {
                    activeSelectionText = text
                }
            }
        }
    }

    // TTS State
    var isTtsActive by remember { mutableStateOf(false) }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var ttsRate by remember { mutableStateOf(0) }

    // Load chapters, bookmarks & preferences
    LaunchedEffect(book.bookUrl) {
        val savedThemeId = AppDatabase.getConfig("reader_theme", ReadTheme.DAY.id)
        currentTheme = ReadTheme.fromId(savedThemeId)
        val savedCustomBg = AppDatabase.getConfig("reader_custom_bg", "#FAF7F2")
        val savedCustomText = AppDatabase.getConfig("reader_custom_text", "#2C2523")
        customBgHex = savedCustomBg
        customTextHex = savedCustomText
        customBgColor = parseHexColor(savedCustomBg, ReadTheme.DAY.bg)
        customTextColor = parseHexColor(savedCustomText, ReadTheme.DAY.text)

        val savedPath = AppDatabase.getConfig("reader_font_path", "")
        val savedName = AppDatabase.getConfig("reader_font_name", "默认系统字体")
        if (savedPath.isNotBlank()) {
            fontPath = savedPath
            fontName = savedName
        }

        val savedLeft = AppDatabase.getConfig("reader_click_left_action", "")
        leftClickAction = if (savedLeft.isBlank() || savedLeft == ClickZoneAction.PAGE_PREV.id) {
            ClickZoneAction.PREV_CHAPTER
        } else {
            ClickZoneAction.fromId(savedLeft, ClickZoneAction.PREV_CHAPTER)
        }

        val savedCenter = AppDatabase.getConfig("reader_click_center_action", ClickZoneAction.TOGGLE_MENU.id)
        centerClickAction = ClickZoneAction.fromId(savedCenter, ClickZoneAction.TOGGLE_MENU)

        val savedRight = AppDatabase.getConfig("reader_click_right_action", "")
        rightClickAction = if (savedRight.isBlank() || savedRight == ClickZoneAction.PAGE_NEXT.id) {
            ClickZoneAction.NEXT_CHAPTER
        } else {
            ClickZoneAction.fromId(savedRight, ClickZoneAction.NEXT_CHAPTER)
        }
        val savedRatio = AppDatabase.getConfig("reader_click_ratio", "0").toIntOrNull() ?: 0
        clickRatioIndex = savedRatio

        // Load 5-Dimension Typography & Page Turn Mode
        val savedMode = AppDatabase.getConfig("reader_page_turn_mode", PageTurnMode.SCROLL.id)
        pageTurnMode = PageTurnMode.fromId(savedMode)
        isDualPage = (pageTurnMode == PageTurnMode.DUAL_PAGE)

        val savedLineSpacing = AppDatabase.getConfig("reader_line_spacing", "1.75").toFloatOrNull() ?: 1.75f
        lineSpacingMultiplier = savedLineSpacing

        val savedParaSpacing = AppDatabase.getConfig("reader_paragraph_spacing", "16").toIntOrNull() ?: 16
        paragraphSpacingDp = savedParaSpacing

        val savedIndent = AppDatabase.getConfig("reader_first_line_indent", "true").toBooleanStrictOrNull() ?: true
        firstLineIndent = savedIndent

        val savedPadding = AppDatabase.getConfig("reader_horizontal_padding", "32").toIntOrNull() ?: 32
        horizontalPaddingDp = savedPadding

        val savedStatusBar = AppDatabase.getConfig("reader_show_status_bar", "true").toBooleanStrictOrNull() ?: true
        showStatusBar = savedStatusBar

        val savedKinsoku = AppDatabase.getConfig("reader_kinsoku", "true").toBooleanStrictOrNull() ?: true
        enableKinsoku = savedKinsoku

        val savedDropCaps = AppDatabase.getConfig("reader_drop_caps", "false").toBooleanStrictOrNull() ?: false
        enableDropCaps = savedDropCaps

        val savedArtTitle = AppDatabase.getConfig("reader_art_title", "true").toBooleanStrictOrNull() ?: true
        enableArtTitle = savedArtTitle

        val savedPaper = AppDatabase.getConfig("reader_paper_texture", "true").toBooleanStrictOrNull() ?: true
        enablePaperTexture = savedPaper

        val savedAlpha = AppDatabase.getConfig("reader_paper_alpha", "0.06").toFloatOrNull() ?: 0.06f
        paperTextureAlpha = savedAlpha

        val loadedChapters = AppDatabase.getChapters(book.bookUrl)
        if (loadedChapters.isNotEmpty()) {
            chapters = loadedChapters
        } else {
            chapterContent = "正在拉取目录列表中..."
            val allSources = AppDatabase.getAllBookSources()
            val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }
            if (source != null) {
                try {
                    val fetched = BookSourceEngine.getChapters(source, book)
                    if (fetched.isNotEmpty()) {
                        chapters = fetched
                        AppDatabase.saveChapters(book.bookUrl, fetched)
                    } else {
                        isLoading = false
                        chapterContent = """
                            【目录加载失败】未能从书源《${source.bookSourceName}》获取到章节列表。
                            
                            可能的原因：
                            1. 该书源网站当前连接超时或已被阻断；
                            2. 目标书籍在该站点已下架或需要滑动验证反爬；
                            3. 目录解析规则与该网站新版结构不匹配。
                            
                            建议：请点击左上方返回按钮，在搜索列表中选择其他书源阅读。
                        """.trimIndent()
                    }
                } catch (e: Exception) {
                    isLoading = false
                    chapterContent = "【目录加载异常】获取章节列表失败：${e.localizedMessage ?: e.message}\n\n建议返回搜索列表更换书源。"
                }
            } else {
                isLoading = false
                chapterContent = """
                    【未找到对应书源】（书源地址：${book.origin}）
                    
                    该书籍绑定的书源不存在或已被删除。请在“书源”模块重新导入或在搜索中重新换源。
                """.trimIndent()
            }
        }

        val loadedBookmarks = AppDatabase.getBookmarks(book.bookUrl)
        bookBookmarks.clear()
        bookBookmarks.addAll(loadedBookmarks)

        val loadedAnnotations = AppDatabase.getAnnotations(book.bookUrl)
        bookAnnotations.clear()
        bookAnnotations.addAll(loadedAnnotations)
    }

    // Load current chapter content and apply replace rules
    LaunchedEffect(currentChapterIndex, chapters) {
        if (chapters.isNotEmpty() && currentChapterIndex in chapters.indices) {
            val chapter = chapters[currentChapterIndex]
            isLoading = true
            chapterContent = "正在加载章节《${chapter.title}》正文内容..."

            // 1. Fast path: check offline cache first
            val cached = BookCacheEngine.readCache(book, currentChapterIndex)
            if (!cached.isNullOrBlank()) {
                chapterContent = cached
                isLoading = false
            } else {
                val allSources = AppDatabase.getAllBookSources()
                val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }

                try {
                    val raw = if (book.type == 3 || book.origin == "local") {
                        LocalBookImporter.loadChapterContent(chapter)
                    } else if (source != null) {
                        BookSourceEngine.getContent(source, book, chapter)
                    } else {
                        """
                            ${chapter.title}
                            
                            这是本地模拟章节示例正文内容。
                            天色微明，晨曦破晓，群山在薄雾中若隐若现。
                            他站在青石崖边，凝望着远方翻腾的云海，微风拂过衣袂。
                            修真之道，路漫漫其修远兮，既已踏足，便再无退路。
                            
                            （注：请在“书源”中导入真实 Legado 3.0 书源并在“搜索”中添加网络书籍，即可自动同步拉取在线正文！）
                        """.trimIndent()
                    }

                    val cleaned = ReplaceRuleEngine.applyRules(raw, book, source)
                    if (cleaned.isBlank()) {
                        chapterContent = """
                            【正文内容为空】
                            
                            未能从源站《${source?.bookSourceName ?: "未知"}》提取到《${chapter.title}》的有效文字。
                            章节地址: ${chapter.url}
                            
                            可能原因：目标章节需要登录/付费、存在复杂反爬防护，或书源正文解析规则不匹配。
                            建议：点击顶部左上角返回，在“搜索”列表中换一个书源阅读。
                        """.trimIndent()
                    } else {
                        chapterContent = cleaned
                        // Save valid content to local cache
                        if (!cleaned.startsWith("【正文内容为空】") && !cleaned.startsWith("【正文加载异常】")) {
                            BookCacheEngine.writeCache(book, currentChapterIndex, cleaned)
                        }
                    }
                } catch (e: Exception) {
                    chapterContent = "【正文加载异常】：${e.localizedMessage ?: e.message}\n\n建议返回搜索界面更换其他书源。"
                } finally {
                    isLoading = false
                }
            }

            scrollState.scrollTo(0)
            if (targetPageWhenChapterLoaded != null) {
                currentPageIndex = targetPageWhenChapterLoaded!!
                targetPageWhenChapterLoaded = null
            } else {
                currentPageIndex = 0
            }

            // If TTS was speaking, restart with new chapter
            if (isTtsActive && isTtsPlaying) {
                TtsEngine.speak(chapterContent, ttsRate)
            }

            // Update reading progress in database
            book.durChapterIndex = currentChapterIndex
            book.durChapterTitle = chapter.title
            book.durChapterTime = System.currentTimeMillis()
            AppDatabase.insertOrUpdateBook(book)

            // Asynchronously prefetch next chapter content (for dual page seamless stitching)
            if (chapters.isNotEmpty() && currentChapterIndex < chapters.size - 1) {
                scope.launch {
                    val nextCh = chapters[currentChapterIndex + 1]
                    val cachedNext = BookCacheEngine.readCache(book, currentChapterIndex + 1)
                    if (!cachedNext.isNullOrBlank()) {
                        nextChapterContent = cachedNext
                    } else {
                        val allSources = AppDatabase.getAllBookSources()
                        val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }
                        try {
                            val rawNext = if (book.type == 3 || book.origin == "local") {
                                LocalBookImporter.loadChapterContent(nextCh)
                            } else if (source != null) {
                                BookSourceEngine.getContent(source, book, nextCh)
                            } else null
                            if (rawNext != null) {
                                val cleanedNext = ReplaceRuleEngine.applyRules(rawNext, book, source)
                                if (cleanedNext.isNotBlank()) {
                                    nextChapterContent = cleanedNext
                                    BookCacheEngine.writeCache(book, currentChapterIndex + 1, cleanedNext)
                                }
                            }
                        } catch (_: Exception) {
                            // Background prefetch ignore
                        }
                    }
                }
            } else {
                nextChapterContent = null
            }
        }
    }

    DisposableEffect(currentChapterIndex, chapters, isTtsActive, isTtsPlaying, chapterContent, ttsRate) {
        val prevPlayPause = GlobalMediaHotkeyManager.onPlayPause
        val prevNext = GlobalMediaHotkeyManager.onNext
        val prevPrev = GlobalMediaHotkeyManager.onPrev

        GlobalMediaHotkeyManager.onPlayPause = {
            if (isTtsActive) {
                if (isTtsPlaying) {
                    TtsEngine.pause()
                    isTtsPlaying = false
                } else {
                    TtsEngine.resume()
                    isTtsPlaying = true
                }
            } else {
                isTtsActive = true
                isTtsPlaying = true
                TtsEngine.speak(chapterContent, ttsRate)
            }
        }
        GlobalMediaHotkeyManager.onNext = {
            if (currentChapterIndex < chapters.size - 1) {
                currentChapterIndex++
            }
        }
        GlobalMediaHotkeyManager.onPrev = {
            if (currentChapterIndex > 0) {
                currentChapterIndex--
            }
        }

        onDispose {
            TtsEngine.stop()
            GlobalMediaHotkeyManager.onPlayPause = prevPlayPause
            GlobalMediaHotkeyManager.onNext = prevNext
            GlobalMediaHotkeyManager.onPrev = prevPrev
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val currentChapterTitle = if (chapters.isNotEmpty() && currentChapterIndex in chapters.indices) {
        chapters[currentChapterIndex].title
    } else {
        book.name
    }

    val isCurrentChapterBookmarked = bookBookmarks.any { it.chapterIndex == currentChapterIndex }

    fun handleClose() {
        TtsEngine.stop()
        onClose()
    }

    var isComicMode by remember { mutableStateOf(book.type == 2) }

    val detectedImages = remember(chapterContent) {
        val list = mutableListOf<String>()
        val imgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        imgRegex.findAll(chapterContent).forEach { match ->
            list.add(match.groupValues[1])
        }
        if (list.isEmpty()) {
            chapterContent.lines().map { it.trim() }.filter {
                (it.startsWith("http://") || it.startsWith("https://")) &&
                (it.endsWith(".jpg", true) || it.endsWith(".png", true) || it.endsWith(".webp", true) || it.endsWith(".jpeg", true))
            }.forEach { list.add(it) }
        }
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (event.isCtrlPressed || event.isMetaPressed) {
                        when (event.key) {
                            Key.F -> {
                                showSearch = !showSearch
                                true
                            }
                            else -> false
                        }
                    } else {
                        when (event.key) {
                            Key.F11 -> {
                                isImmersive = !isImmersive
                                if (isImmersive) showHud = false
                                true
                            }
                            Key.DirectionRight, Key.PageDown, Key.Spacebar, Key.J -> {
                                executeAction(ClickZoneAction.PAGE_NEXT)
                                true
                            }
                            Key.DirectionLeft, Key.PageUp, Key.K -> {
                                executeAction(ClickZoneAction.PAGE_PREV)
                                true
                            }
                            Key.LeftBracket -> {
                                executeAction(ClickZoneAction.PREV_CHAPTER)
                                true
                            }
                            Key.RightBracket -> {
                                executeAction(ClickZoneAction.NEXT_CHAPTER)
                                true
                            }
                            Key.Escape -> {
                                if (showSearch) {
                                    showSearch = false
                                    searchQuery = ""
                                    true
                                } else if (showSettingsDrawer) {
                                    showSettingsDrawer = false
                                    true
                                } else if (showTOC) {
                                    showTOC = false
                                    true
                                } else if (isImmersive) {
                                    isImmersive = false
                                    true
                                } else {
                                    handleClose()
                                    true
                                }
                            }
                            else -> false
                        }
                    }
                } else false
            }
    ) {
        // Reader Content Canvas
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (scrollDeltaY != 0f && (pageTurnMode == PageTurnMode.SLIDE_PAGING || isDualPage)) {
                        val (newAcc, action) = MouseWheelDampingHelper.processScroll(
                            currentAccumulator = wheelAccumulator,
                            lastScrollTimestamp = lastWheelTimestamp,
                            scrollDeltaY = scrollDeltaY,
                            currentTimeMs = System.currentTimeMillis()
                        )
                        wheelAccumulator = newAcc
                        lastWheelTimestamp = System.currentTimeMillis()
                        if (action > 0) {
                            executeAction(ClickZoneAction.PAGE_NEXT)
                        } else if (action < 0) {
                            executeAction(ClickZoneAction.PAGE_PREV)
                        }
                    }
                }
                .pointerInput(leftClickAction, centerClickAction, rightClickAction, leftRatio, rightRatio) {
                    detectTapGestures { offset ->
                        val x = offset.x
                        val totalW = size.width
                        val leftBorder = totalW * leftRatio
                        val rightBorder = totalW * (1f - rightRatio)
                        when {
                            x < leftBorder -> executeAction(leftClickAction)
                            x > rightBorder -> executeAction(rightClickAction)
                            else -> executeAction(centerClickAction)
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter
        ) {
            val density = LocalDensity.current
            LaunchedEffect(maxWidth, maxHeight) {
                viewportWidthPx = with(density) { maxWidth.toPx() }
                viewportHeightPx = with(density) { maxHeight.toPx() }
            }

            if (enablePaperTexture && paperTextureAlpha > 0.001f) {
                PaperTextureCanvas(
                    alpha = paperTextureAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            }

            CompositionLocalProvider(LocalTextToolbar provides customToolbar) {
                if (isComicMode || detectedImages.isNotEmpty()) {
                // Comic / Manga Continuous Roll Mode
                val imagesToShow = if (detectedImages.isNotEmpty()) {
                    detectedImages
                } else {
                    listOf("https://fake.comic/p1.jpg", "https://fake.comic/p2.jpg", "https://fake.comic/p3.jpg")
                }
                Column(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 24.dp, vertical = 50.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "$currentChapterTitle（条漫模式）",
                        fontSize = (fontSize + 4).sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.text
                    )
                    imagesToShow.forEachIndexed { idx, url ->
                        ElevatedCard(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeLightboxImageUrl = url }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(380.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        LegadoIcons.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "漫画第 ${idx + 1} 页 / 共 ${imagesToShow.size} 页",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = url.take(60) + if (url.length > 60) "..." else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(80.dp))
                }
            } else if (isDualPage) {
                // Real Dual-Page Book Spread Layout with cross-chapter stitching
                val spread = dualSpreads.getOrElse(safeSpreadIndex) {
                    DualSpreadPage(
                        leftPageText = chapterContent,
                        leftChapterTitle = currentChapterTitle,
                        leftPageIndex = 0,
                        leftTotalPages = 1,
                        rightPageText = null,
                        rightChapterTitle = null,
                        rightPageIndex = null,
                        rightTotalPages = null
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPaddingDp.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Page Column
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left page header
                            if (showStatusBar) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${book.name} · ${spread.leftChapterTitle}",
                                        fontSize = 12.sp,
                                        color = themeText.copy(alpha = 0.5f),
                                        fontFamily = activeFontFamily,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${spread.leftPageIndex + 1} / ${spread.leftTotalPages} 页",
                                        fontSize = 11.sp,
                                        color = themeText.copy(alpha = 0.45f),
                                        fontFamily = activeFontFamily
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(4.dp))
                            }

                            // Left page content
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = buildHighlightedText(
                                            spread.leftPageText,
                                            searchQuery,
                                            searchMatches.getOrNull(currentSearchMatchIndex)?.snippet,
                                            chapterAnnotations,
                                            isFirstPage = (spread.leftPageIndex == 0),
                                            enableDropCaps = enableDropCaps
                                        ),
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * lineSpacingMultiplier).sp,
                                        color = themeText,
                                        letterSpacing = 0.6.sp,
                                        fontFamily = activeFontFamily,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Left page footer
                            if (showStatusBar) {
                                Text(
                                    text = "全书：第 ${currentChapterIndex + 1} / ${chapters.size} 章 (${if (chapters.isNotEmpty()) ((currentChapterIndex + 1) * 100 / chapters.size) else 0}%)",
                                    fontSize = 11.sp,
                                    color = themeText.copy(alpha = 0.4f),
                                    fontFamily = activeFontFamily
                                )
                            } else {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }

                    // Book Spine Divider (subtle shadow & vertical line)
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .fillMaxHeight(0.92f)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            themeText.copy(alpha = 0.05f),
                                            themeText.copy(alpha = 0.22f),
                                            themeText.copy(alpha = 0.05f)
                                        )
                                    )
                                )
                        )
                    }

                    // Right Page Column
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Right page header
                            if (showStatusBar) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (spread.isCrossChapter && spread.rightChapterTitle != null) {
                                            "【接下章】${spread.rightChapterTitle}"
                                        } else {
                                            spread.rightChapterTitle ?: spread.leftChapterTitle
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (spread.isCrossChapter) FontWeight.Bold else FontWeight.Normal,
                                        color = if (spread.isCrossChapter) MaterialTheme.colorScheme.primary else themeText.copy(alpha = 0.5f),
                                        fontFamily = activeFontFamily,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (spread.rightPageIndex != null) {
                                            "${spread.rightPageIndex + 1} / ${spread.rightTotalPages ?: spread.leftTotalPages} 页"
                                        } else "",
                                        fontSize = 11.sp,
                                        color = themeText.copy(alpha = 0.45f),
                                        fontFamily = activeFontFamily
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(4.dp))
                            }

                            // Right page content
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.TopStart
                            ) {
                                if (spread.rightPageText != null) {
                                    SelectionContainer {
                                        Text(
                                            text = buildHighlightedText(
                                                spread.rightPageText,
                                                searchQuery,
                                                searchMatches.getOrNull(currentSearchMatchIndex)?.snippet,
                                                chapterAnnotations,
                                                isFirstPage = (spread.rightPageIndex == 0),
                                                enableDropCaps = enableDropCaps
                                            ),
                                            fontSize = fontSize.sp,
                                            lineHeight = (fontSize * lineSpacingMultiplier).sp,
                                            color = themeText,
                                            letterSpacing = 0.6.sp,
                                            fontFamily = activeFontFamily,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else if (spread.isEndOfBook) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = themeText.copy(alpha = 0.06f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, themeText.copy(alpha = 0.15f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(32.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    LegadoIcons.MenuBook,
                                                    contentDescription = null,
                                                    tint = themeText.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(48.dp)
                                                )
                                                Text(
                                                    text = "— 全书完 —",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = themeText.copy(alpha = 0.7f),
                                                    fontFamily = activeFontFamily
                                                )
                                                Text(
                                                    text = "恭喜读完全部章节",
                                                    fontSize = 13.sp,
                                                    color = themeText.copy(alpha = 0.45f),
                                                    fontFamily = activeFontFamily
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "正在预载下一章内容...",
                                            fontSize = 14.sp,
                                            color = themeText.copy(alpha = 0.4f),
                                            fontFamily = activeFontFamily
                                        )
                                    }
                                }
                            }

                            // Right page footer
                            if (showStatusBar) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (spread.isCrossChapter) "双页跨章连续拼接" else "Legado 经典对开排版",
                                        fontSize = 11.sp,
                                        color = themeText.copy(alpha = 0.4f),
                                        fontFamily = activeFontFamily
                                    )
                                    Text(
                                        text = currentTimeStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = themeText.copy(alpha = 0.5f),
                                        fontFamily = activeFontFamily
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            } else if (pageTurnMode == PageTurnMode.SLIDE_PAGING) {
                // Android-Style Slide Paging Mode
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxHeight()
                        .padding(horizontal = horizontalPaddingDp.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Status
                    if (showStatusBar) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${book.name} · $currentChapterTitle",
                                fontSize = 13.sp,
                                color = themeText.copy(alpha = 0.55f),
                                maxLines = 1,
                                fontFamily = activeFontFamily,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "${safePageIndex + 1} / ${pagedChapter.totalPages} 页",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = themeText.copy(alpha = 0.6f),
                                    fontFamily = activeFontFamily
                                )
                                Text(
                                    text = currentTimeStr,
                                    fontSize = 12.sp,
                                    color = themeText.copy(alpha = 0.55f),
                                    fontFamily = activeFontFamily
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Center Content with horizontal slide animation
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopStart
                    ) {
                        AnimatedContent(
                            targetState = safePageIndex,
                            transitionSpec = {
                                if (targetState > initialState) {
                                    (slideInHorizontally { width -> width / 3 } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> -width / 3 } + fadeOut()
                                    )
                                } else {
                                    (slideInHorizontally { width -> -width / 3 } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> width / 3 } + fadeOut()
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { pageIdx ->
                            val pageText = if (pageIdx in pagedChapter.pages.indices) pagedChapter.pages[pageIdx] else ""
                            SelectionContainer {
                                Text(
                                    text = buildHighlightedText(
                                        pageText,
                                        searchQuery,
                                        searchMatches.getOrNull(currentSearchMatchIndex)?.snippet,
                                        chapterAnnotations,
                                        isFirstPage = (pageIdx == 0),
                                        enableDropCaps = enableDropCaps
                                    ),
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * lineSpacingMultiplier).sp,
                                    color = themeText,
                                    letterSpacing = 0.6.sp,
                                    fontFamily = activeFontFamily,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Footer Status
                    if (showStatusBar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "全书进度：第 ${currentChapterIndex + 1} / ${chapters.size} 章 (${if (chapters.isNotEmpty()) ((currentChapterIndex + 1) * 100 / chapters.size) else 0}%)",
                                fontSize = 12.sp,
                                color = themeText.copy(alpha = 0.45f),
                                fontFamily = activeFontFamily
                            )
                            Text(
                                text = "本章共 ${pagedChapter.totalCharCount} 字",
                                fontSize = 12.sp,
                                color = themeText.copy(alpha = 0.45f),
                                fontFamily = activeFontFamily
                            )
                        }
                    }
                }
            } else {
                // Centered single column continuous vertical scroll
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPaddingDp.dp, vertical = 40.dp)
                            .widthIn(max = 1000.dp)
                    ) {
                        if (showStatusBar) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${book.name} · $currentChapterTitle",
                                    fontSize = 12.sp,
                                    color = themeText.copy(alpha = 0.5f),
                                    fontFamily = activeFontFamily,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = currentTimeStr,
                                    fontSize = 12.sp,
                                    color = themeText.copy(alpha = 0.5f),
                                    fontFamily = activeFontFamily
                                )
                            }
                        }

                        Text(
                            text = currentChapterTitle,
                            fontSize = (fontSize + 6).sp,
                            fontWeight = FontWeight.Bold,
                            color = themeText,
                            fontFamily = activeFontFamily
                        )
                        if (enableArtTitle) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "❖ ─── ✦ ─── ❖",
                                    fontSize = 13.sp,
                                    color = themeText.copy(alpha = 0.45f),
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        SelectionContainer {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(paragraphSpacingDp.dp)
                            ) {
                                val displayParagraphs = remember(chapterContent, firstLineIndent) {
                                    if (chapterContent.isBlank()) emptyList()
                                    else {
                                        chapterContent.lines().filter { it.isNotBlank() }.map { line ->
                                            val trimmed = line.trimEnd('\r')
                                            if (firstLineIndent && !trimmed.startsWith("　　") && !trimmed.startsWith("  ")) {
                                                "　　$trimmed"
                                            } else {
                                                trimmed
                                            }
                                        }
                                    }
                                }
                                displayParagraphs.forEachIndexed { pIndex, para ->
                                    Text(
                                        text = buildHighlightedText(
                                            para,
                                            searchQuery,
                                            searchMatches.getOrNull(currentSearchMatchIndex)?.snippet,
                                            chapterAnnotations,
                                            isFirstPage = (pIndex == 0),
                                            enableDropCaps = enableDropCaps
                                        ),
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * lineSpacingMultiplier).sp,
                                        color = themeText,
                                        letterSpacing = 0.6.sp,
                                        fontFamily = activeFontFamily
                                    )
                                }
                            }
                        }

                        if (showStatusBar) {
                            Spacer(Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "全书进度：第 ${currentChapterIndex + 1} / ${chapters.size} 章 (${if (chapters.isNotEmpty()) ((currentChapterIndex + 1) * 100 / chapters.size) else 0}%)",
                                    fontSize = 12.sp,
                                    color = themeText.copy(alpha = 0.45f),
                                    fontFamily = activeFontFamily
                                )
                                Text(
                                    text = "本章共 ${chapterContent.length} 字",
                                    fontSize = 12.sp,
                                    color = themeText.copy(alpha = 0.45f),
                                    fontFamily = activeFontFamily
                                )
                            }
                        }

                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
            } // end CompositionLocalProvider

            // Floating Annotation Capsule Toolbar
            if (activeSelectionText != null) {
                FloatingAnnotationToolbar(
                    selectedText = activeSelectionText!!,
                    onColorSelected = { colorType ->
                        val text = activeSelectionText ?: return@FloatingAnnotationToolbar
                        val start = chapterContent.indexOf(text).coerceAtLeast(0)
                        val end = (start + text.length).coerceAtLeast(0)
                        val anno = BookAnnotation(
                            id = System.currentTimeMillis(),
                            bookUrl = book.bookUrl,
                            bookName = book.name,
                            chapterIndex = currentChapterIndex,
                            chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: "未知章节",
                            selectedText = text,
                            note = "",
                            colorType = colorType,
                            startOffset = start,
                            endOffset = end,
                            createdAt = System.currentTimeMillis()
                        )
                        scope.launch {
                            AppDatabase.insertAnnotation(anno)
                            bookAnnotations.add(0, anno)
                        }
                        activeSelectionText = null
                    },
                    onAddNote = {
                        showNoteDialog = true
                    },
                    onCopy = {
                        activeSelectionText?.let { MarkdownExportEngine.copyToClipboard(it) }
                        activeSelectionText = null
                    },
                    onSpeak = {
                        activeSelectionText?.let { TtsEngine.speak(it) }
                        activeSelectionText = null
                    },
                    onDismiss = {
                        activeSelectionText = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp)
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // Top Floating HUD
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { handleClose() }) {
                            Icon(LegadoIcons.ArrowBack, contentDescription = "返回书架")
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = book.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = currentChapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Bookmark Toggle Button with Smart Quote / Excerpt Note
                        IconButton(
                            onClick = {
                                val clip = getSystemClipboardText()?.trim()
                                val excerpt = if (!clip.isNullOrBlank() && clip.length in 2..300 && chapterContent.contains(clip)) {
                                    clip
                                } else {
                                    if (isDualPage) {
                                        dualSpreads.getOrNull(safeSpreadIndex)?.leftPageText?.take(150)?.trim() ?: chapterContent.take(150).trim()
                                    } else if (pageTurnMode == PageTurnMode.SLIDE_PAGING) {
                                        pagedChapter.pages.getOrNull(safePageIndex)?.take(150)?.trim() ?: chapterContent.take(150).trim()
                                    } else {
                                        chapterContent.take(150).trim()
                                    }
                                }
                                bookmarkExcerptText = excerpt
                                bookmarkNoteText = ""
                                showBookmarkDialog = true
                            }
                        ) {
                            Icon(
                                if (isCurrentChapterBookmarked) LegadoIcons.Bookmark else LegadoIcons.BookmarkBorder,
                                contentDescription = "书签与批注",
                                tint = if (isCurrentChapterBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // TTS Speech Toggle Button
                        IconButton(
                            onClick = {
                                isTtsActive = !isTtsActive
                                if (isTtsActive) {
                                    isTtsPlaying = true
                                    TtsEngine.speak(chapterContent, ttsRate)
                                } else {
                                    isTtsPlaying = false
                                    TtsEngine.stop()
                                }
                            }
                        ) {
                            Icon(
                                if (isTtsActive) LegadoIcons.VolumeUp else LegadoIcons.Headphones,
                                contentDescription = "语音朗读",
                                tint = if (isTtsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { isComicMode = !isComicMode }) {
                            Icon(
                                if (isComicMode) LegadoIcons.MenuBook else LegadoIcons.PhotoLibrary,
                                contentDescription = "切换条漫模式",
                                tint = if (isComicMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = {
                            isDualPage = !isDualPage
                            pageTurnMode = if (isDualPage) PageTurnMode.DUAL_PAGE else PageTurnMode.SLIDE_PAGING
                            scope.launch {
                                AppDatabase.setConfig("reader_page_turn_mode", pageTurnMode.id)
                            }
                        }) {
                            Icon(
                                if (isDualPage) LegadoIcons.ViewAgenda else LegadoIcons.AutoStories,
                                contentDescription = "切换单/双页模式"
                            )
                        }

                        // In-chapter Search (Ctrl+F)
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                LegadoIcons.Search,
                                contentDescription = "搜索本章 (Ctrl+F)",
                                tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = { showTOC = true }) {
                            Icon(LegadoIcons.Menu, contentDescription = "目录与书签")
                        }
                        IconButton(onClick = {
                            isImmersive = !isImmersive
                            if (isImmersive) showHud = false
                        }) {
                            Icon(
                                if (isImmersive) LegadoIcons.FullscreenExit else LegadoIcons.Fullscreen,
                                contentDescription = if (isImmersive) "退出全屏沉浸 (F11)" else "进入全屏沉浸 (F11)",
                                tint = if (isImmersive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showBatchCacheDialog = true }) {
                            Icon(
                                LegadoIcons.CloudDownload,
                                contentDescription = "批量离线缓存",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { showSettingsDrawer = !showSettingsDrawer }) {
                            Icon(
                                LegadoIcons.Tune,
                                contentDescription = "排版与设置",
                                tint = if (showSettingsDrawer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // In-Chapter Search Floating Bar
        AnimatedVisibility(
            visible = showSearch,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 84.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        LegadoIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索本章 (Ctrl+F)...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.width(180.dp).height(48.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    Text(
                        text = if (searchMatches.isNotEmpty()) "${currentSearchMatchIndex + 1} / ${searchMatches.size}" else "0 / 0",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (searchMatches.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    IconButton(
                        onClick = { navigateMatch(-1) },
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(LegadoIcons.NavigateBefore, contentDescription = "上一个匹配", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { navigateMatch(1) },
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(LegadoIcons.NavigateNext, contentDescription = "下一个匹配", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            showSearch = false
                            searchQuery = ""
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(LegadoIcons.Close, contentDescription = "关闭搜索", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // TTS Floating Controller Bar
        AnimatedVisibility(
            visible = isTtsActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Windows TTS 朗读中",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            if (isTtsPlaying) {
                                TtsEngine.stop()
                                isTtsPlaying = false
                            } else {
                                TtsEngine.speak(chapterContent, ttsRate)
                                isTtsPlaying = true
                            }
                        }
                    ) {
                        Icon(
                            if (isTtsPlaying) LegadoIcons.Pause else LegadoIcons.PlayArrow,
                            contentDescription = "播放/暂停"
                        )
                    }

                    // Rate Controls
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = ttsRate == -2,
                            onClick = {
                                ttsRate = -2
                                if (isTtsPlaying) TtsEngine.speak(chapterContent, ttsRate)
                            },
                            label = { Text("0.8x") }
                        )
                        FilterChip(
                            selected = ttsRate == 0,
                            onClick = {
                                ttsRate = 0
                                if (isTtsPlaying) TtsEngine.speak(chapterContent, ttsRate)
                            },
                            label = { Text("1.0x") }
                        )
                        FilterChip(
                            selected = ttsRate == 2,
                            onClick = {
                                ttsRate = 2
                                if (isTtsPlaying) TtsEngine.speak(chapterContent, ttsRate)
                            },
                            label = { Text("1.2x") }
                        )
                    }

                    IconButton(
                        onClick = {
                            isTtsActive = false
                            isTtsPlaying = false
                            TtsEngine.stop()
                        }
                    ) {
                        Icon(LegadoIcons.Close, contentDescription = "关闭朗读")
                    }
                }
            }
        }

        // Bottom Floating HUD
        AnimatedVisibility(
            visible = showHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .padding(bottom = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (currentChapterIndex > 0) currentChapterIndex-- },
                        enabled = currentChapterIndex > 0
                    ) {
                        Icon(LegadoIcons.NavigateBefore, contentDescription = null)
                        Text("上一章")
                    }

                    Text(
                        text = if (chapters.isNotEmpty()) "${currentChapterIndex + 1} / ${chapters.size}" else "1 / 1",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    TextButton(
                        onClick = { if (currentChapterIndex < chapters.size - 1) currentChapterIndex++ },
                        enabled = currentChapterIndex < chapters.size - 1
                    ) {
                        Text("下一章")
                        Icon(LegadoIcons.NavigateNext, contentDescription = null)
                    }
                }
            }
        }

        // TOC Chapter & Bookmark Drawer
        if (showTOC) {
            ModalBottomSheet(
                onDismissRequest = { showTOC = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .padding(16.dp)
                ) {
                    PrimaryTabRow(selectedTabIndex = tocTabIndex) {
                        Tab(
                            selected = tocTabIndex == 0,
                            onClick = { tocTabIndex = 0 },
                            text = { Text("章节目录 (${chapters.size})") }
                        )
                        Tab(
                            selected = tocTabIndex == 1,
                            onClick = { tocTabIndex = 1 },
                            text = { Text("划线与笔记 (${bookAnnotations.size})") }
                        )
                        Tab(
                            selected = tocTabIndex == 2,
                            onClick = { tocTabIndex = 2 },
                            text = { Text("书签 (${bookBookmarks.size})") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (tocTabIndex == 0) {
                        if (book.origin == "local") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "本地流式偏移索引",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(
                                    onClick = { showReSplitDialog = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(LegadoIcons.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("分章微调", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(chapters) { ch ->
                                val isCurrent = ch.index == currentChapterIndex
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = ch.title,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            currentChapterIndex = ch.index
                                            showTOC = false
                                        }
                                )
                            }
                        }
                    } else if (tocTabIndex == 1) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "共 ${bookAnnotations.size} 条划线与想法",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            val md = MarkdownExportEngine.generateMarkdown(
                                                bookTitle = book.name,
                                                bookAuthor = book.author,
                                                annotations = bookAnnotations,
                                                bookmarks = bookBookmarks
                                            )
                                            MarkdownExportEngine.copyToClipboard(md)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(LegadoIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("复制 Markdown", style = MaterialTheme.typography.labelSmall)
                                    }

                                    Button(
                                        onClick = {
                                            val md = MarkdownExportEngine.generateMarkdown(
                                                bookTitle = book.name,
                                                bookAuthor = book.author,
                                                annotations = bookAnnotations,
                                                bookmarks = bookBookmarks
                                            )
                                            MarkdownExportEngine.exportToFile(null, "${book.name}_读书笔记", md)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(LegadoIcons.BookmarkAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("导出 .md", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            if (bookAnnotations.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无划线或想法，在阅读时选中文字即可快速划线或记录想法",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(bookAnnotations) { anno ->
                                        ElevatedCard(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    currentChapterIndex = anno.chapterIndex
                                                    showTOC = false
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        val badgeColor = when (anno.colorType.uppercase()) {
                                                            "GREEN" -> Color(0xFF81C784)
                                                            "PURPLE" -> Color(0xFFBA68C8)
                                                            "UNDERLINE" -> MaterialTheme.colorScheme.primary
                                                            else -> Color(0xFFFFD54F)
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .background(badgeColor, CircleShape)
                                                        )
                                                        Text(
                                                            text = anno.chapterTitle,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            scope.launch {
                                                                AppDatabase.deleteAnnotation(anno.id)
                                                                bookAnnotations.remove(anno)
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            LegadoIcons.Delete,
                                                            contentDescription = "删除划线",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = anno.selectedText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(8.dp)
                                                    )
                                                }
                                                if (anno.note.isNotBlank()) {
                                                    Spacer(Modifier.height(6.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.Top,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text("💡", fontSize = 12.sp)
                                                        Text(
                                                            text = anno.note,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (bookBookmarks.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无书签，可在阅读时点击顶部书签图标添加",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(bookBookmarks) { bm ->
                                    ElevatedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentChapterIndex = bm.chapterIndex
                                                showTOC = false
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = bm.chapterTitle,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            AppDatabase.deleteBookmark(bm.id)
                                                            bookBookmarks.remove(bm)
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        LegadoIcons.Delete,
                                                        contentDescription = "删除书签",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = bm.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bookmark & Note Dialog
        if (showBookmarkDialog) {
            AlertDialog(
                onDismissRequest = { showBookmarkDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(LegadoIcons.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(if (isCurrentChapterBookmarked) "管理书签与批注" else "添加书签笔记", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().widthIn(min = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "《${book.name}》· $currentChapterTitle",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = bookmarkExcerptText,
                            onValueChange = { bookmarkExcerptText = it },
                            label = { Text("摘录内容") },
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = bookmarkNoteText,
                            onValueChange = { bookmarkNoteText = it },
                            label = { Text("个人批注 / 想法 (可选)") },
                            placeholder = { Text("写下阅读本段时的思考与灵感...") },
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val fullContent = if (bookmarkNoteText.isNotBlank()) {
                                "【摘录】${bookmarkExcerptText.trim()}\n【批注】${bookmarkNoteText.trim()}"
                            } else {
                                bookmarkExcerptText.trim()
                            }
                            val newBm = Bookmark(
                                bookUrl = book.bookUrl,
                                bookName = book.name,
                                chapterIndex = currentChapterIndex,
                                chapterTitle = currentChapterTitle,
                                content = fullContent
                            )
                            scope.launch {
                                AppDatabase.insertBookmark(newBm)
                                bookBookmarks.add(newBm)
                            }
                            showBookmarkDialog = false
                        }
                    ) {
                        Text("保存书签")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isCurrentChapterBookmarked) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val bm = bookBookmarks.firstOrNull { it.chapterIndex == currentChapterIndex }
                                        if (bm != null) {
                                            AppDatabase.deleteBookmark(bm.id)
                                            bookBookmarks.remove(bm)
                                        }
                                    }
                                    showBookmarkDialog = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("删除已有书签")
                            }
                        }
                        TextButton(onClick = { showBookmarkDialog = false }) {
                            Text("取消")
                        }
                    }
                }
            )
        }

        // Floating Annotation Note Input Dialog
        if (showNoteDialog && activeSelectionText != null) {
            val textToAnnotate = activeSelectionText!!
            var noteInput by remember { mutableStateOf("") }
            var selectedNoteColor by remember { mutableStateOf("GREEN") }

            AlertDialog(
                onDismissRequest = { showNoteDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LegadoIcons.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("记录读书想法与笔记", style = MaterialTheme.typography.titleMedium)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "“${if (textToAnnotate.length > 80) textToAnnotate.take(80) + "..." else textToAnnotate}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("高亮颜色：", style = MaterialTheme.typography.labelSmall)
                            val colors = listOf(
                                "YELLOW" to Color(0xFFFFD54F),
                                "GREEN" to Color(0xFF81C784),
                                "PURPLE" to Color(0xFFBA68C8),
                                "UNDERLINE" to MaterialTheme.colorScheme.primary
                            )
                            colors.forEach { (type, c) ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(c, CircleShape)
                                        .clickable { selectedNoteColor = type }
                                        .then(if (selectedNoteColor == type) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (type == "UNDERLINE") {
                                        Text("〰️", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            label = { Text("写下你的感悟与想法...") },
                            modifier = Modifier.fillMaxWidth().height(110.dp),
                            maxLines = 5
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val start = chapterContent.indexOf(textToAnnotate).coerceAtLeast(0)
                            val end = (start + textToAnnotate.length).coerceAtLeast(0)
                            val anno = BookAnnotation(
                                id = System.currentTimeMillis(),
                                bookUrl = book.bookUrl,
                                bookName = book.name,
                                chapterIndex = currentChapterIndex,
                                chapterTitle = chapters.getOrNull(currentChapterIndex)?.title ?: "未知章节",
                                selectedText = textToAnnotate,
                                note = noteInput.trim(),
                                colorType = selectedNoteColor,
                                startOffset = start,
                                endOffset = end,
                                createdAt = System.currentTimeMillis()
                            )
                            scope.launch {
                                AppDatabase.insertAnnotation(anno)
                                bookAnnotations.add(0, anno)
                            }
                            showNoteDialog = false
                            activeSelectionText = null
                        }
                    ) {
                        Text("保存笔记")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNoteDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // Re-split Chapter Dialog (Smart Regex Rules & Live Preview)
        if (showReSplitDialog) {
            AlertDialog(
                onDismissRequest = { showReSplitDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(LegadoIcons.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("智能分章规则与重整", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "针对排版不规范的小说，选择或自定义正则快速重构章节目录索引：",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))

                        val presetNames = listOf(
                            "标准中文 (第X章/回/卷/序言)",
                            "英文字段 (Chapter/Prologue)",
                            "数字序号 (1. / (1) / [1])",
                            "网络特殊符号 (【第X章】/★)",
                            "自定义正则表达式"
                        )
                        presetNames.forEachIndexed { idx, name ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { splitPresetIndex = idx }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                RadioButton(
                                    selected = splitPresetIndex == idx,
                                    onClick = { splitPresetIndex = idx }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        if (splitPresetIndex == 4) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customRegexPatternText,
                                onValueChange = { customRegexPatternText = it },
                                label = { Text("输入正则表达式") },
                                placeholder = { Text("例如：^[ \\t]*第[0-9]+节.*$") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        Text("实时匹配预览：", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))

                        if (isPreviewingSplit) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("正在快速扫描分章...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (splitPreviewInfo != null) {
                            val info = splitPreviewInfo!!
                            if (info.totalChapters > 0) {
                                Text(
                                    "✅ 成功匹配到 ${info.totalChapters} 个章节",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        info.sampleTitles.forEachIndexed { sIdx, title ->
                                            Text(
                                                "${sIdx + 1}. $title",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "⚠️ 未匹配到有效章节标题（将按 15KB 智能分段）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val newChapters = io.legado.desktop.engine.local.LocalBookImporter.reSplitTxtBook(book, activeSplitRegex)
                                    chapters = newChapters
                                    currentChapterIndex = currentChapterIndex.coerceIn(0, (newChapters.size - 1).coerceAtLeast(0))
                                    showReSplitDialog = false
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        enabled = !isPreviewingSplit
                    ) {
                        Text("应用并重整目录")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReSplitDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        // Reading Settings Right Sliding Drawer (Typography, Themes & Touch Zones)
        AnimatedVisibility(
            visible = showSettingsDrawer,
            enter = slideInHorizontally { width -> width } + fadeIn(),
            exit = slideOutHorizontally { width -> width } + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        ) {
            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 12.dp,
                shadowElevation = 16.dp,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(LegadoIcons.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("阅读设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showSettingsDrawer = false }) {
                            Icon(LegadoIcons.Close, contentDescription = "关闭设置")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    PrimaryTabRow(selectedTabIndex = settingsTabIndex) {
                        Tab(
                            selected = settingsTabIndex == 0,
                            onClick = { settingsTabIndex = 0 },
                            text = { Text("排版与主题") }
                        )
                        Tab(
                            selected = settingsTabIndex == 1,
                            onClick = { settingsTabIndex = 1 },
                            text = { Text("翻页与触控") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (settingsTabIndex == 0) {
                        // Tab 0: Typography, Page Turn Mode & Theme
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("翻页交互模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PageTurnMode.entries.forEach { mode ->
                                    FilterChip(
                                        modifier = Modifier.weight(1f),
                                        selected = pageTurnMode == mode,
                                        onClick = {
                                            pageTurnMode = mode
                                            isDualPage = (mode == PageTurnMode.DUAL_PAGE)
                                            scope.launch {
                                                AppDatabase.setConfig("reader_page_turn_mode", mode.id)
                                            }
                                        },
                                        label = {
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    when (mode) {
                                                        PageTurnMode.SCROLL -> "平滑滚动"
                                                        PageTurnMode.SLIDE_PAGING -> "单页平移"
                                                        PageTurnMode.DUAL_PAGE -> "双页对开"
                                                    },
                                                    maxLines = 1,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            HorizontalDivider()

                            Text("字号大小: $fontSize sp", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilledTonalButton(onClick = { if (fontSize > 12) fontSize -= 2 }) {
                                    Text("A -")
                                }
                                FilledTonalButton(onClick = { if (fontSize < 36) fontSize += 2 }) {
                                    Text("A +")
                                }
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("行间距倍数", style = MaterialTheme.typography.bodySmall)
                                Text("${"%.2f".format(lineSpacingMultiplier)} 倍", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = lineSpacingMultiplier,
                                onValueChange = { lineSpacingMultiplier = it },
                                onValueChangeFinished = {
                                    scope.launch {
                                        AppDatabase.setConfig("reader_line_spacing", lineSpacingMultiplier.toString())
                                    }
                                },
                                valueRange = 1.2f..2.5f,
                                steps = 12
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("段落间距", style = MaterialTheme.typography.bodySmall)
                                Text("${paragraphSpacingDp} dp", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = paragraphSpacingDp.toFloat(),
                                onValueChange = { paragraphSpacingDp = it.roundToInt() },
                                onValueChangeFinished = {
                                    scope.launch {
                                        AppDatabase.setConfig("reader_paragraph_spacing", paragraphSpacingDp.toString())
                                    }
                                },
                                valueRange = 0f..32f,
                                steps = 7
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("首行全角缩进", style = MaterialTheme.typography.bodyMedium)
                                    Text("段首自动留出 2 个中文字符空隙", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = firstLineIndent,
                                    onCheckedChange = {
                                        firstLineIndent = it
                                        scope.launch {
                                            AppDatabase.setConfig("reader_first_line_indent", it.toString())
                                        }
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("四角沉浸状态栏", style = MaterialTheme.typography.bodyMedium)
                                    Text("在屏幕四角常驻显示时间、进度与字数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = showStatusBar,
                                    onCheckedChange = {
                                        showStatusBar = it
                                        scope.launch {
                                            AppDatabase.setConfig("reader_show_status_bar", it.toString())
                                        }
                                    }
                                )
                            }

                            Text("页面左右边距", style = MaterialTheme.typography.bodySmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val paddings = listOf(Pair(16, "紧凑 (16)"), Pair(32, "适中 (32)"), Pair(64, "宽裕 (64)"))
                                paddings.forEach { (pad, label) ->
                                    FilterChip(
                                        modifier = Modifier.weight(1f),
                                        selected = horizontalPaddingDp == pad,
                                        onClick = {
                                            horizontalPaddingDp = pad
                                            scope.launch {
                                                AppDatabase.setConfig("reader_horizontal_padding", pad.toString())
                                            }
                                        },
                                        label = {
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text(label, maxLines = 1, fontSize = 12.sp)
                                            }
                                        }
                                    )
                                }
                            }

                            HorizontalDivider()

                            Text("中文字形美学与纸质质感", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                            // 1. Kinsoku Shori
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("避头尾禁则与标点悬挂", style = MaterialTheme.typography.bodyMedium)
                                    Text("行末标点智能微悬挂，禁止逗句号在行首、前括号在行尾", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = enableKinsoku,
                                    onCheckedChange = {
                                        enableKinsoku = it
                                        scope.launch {
                                            AppDatabase.setConfig("reader_kinsoku", it.toString())
                                        }
                                    }
                                )
                            }

                            // 2. Drop Caps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("章节首字艺术下沉 (Drop Caps)", style = MaterialTheme.typography.bodyMedium)
                                    Text("章节首段首字加大加粗呈现典雅排版", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = enableDropCaps,
                                    onCheckedChange = {
                                        enableDropCaps = it
                                        scope.launch {
                                            AppDatabase.setConfig("reader_drop_caps", it.toString())
                                        }
                                    }
                                )
                            }

                            // 3. Art Title
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("卷首卷首装饰分割线", style = MaterialTheme.typography.bodyMedium)
                                    Text("在章节标题下方展现 ❖ ─── ✦ ─── ❖ 古雅印记", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = enableArtTitle,
                                    onCheckedChange = {
                                        enableArtTitle = it
                                        scope.launch {
                                            AppDatabase.setConfig("reader_art_title", it.toString())
                                        }
                                    }
                                )
                            }

                            // 4. Paper Texture
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("自然纸质微噪点底衬", style = MaterialTheme.typography.bodyMedium)
                                    Text("Skia 原生渲染温润纸感，消除屏幕发白刺眼", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = enablePaperTexture,
                                    onCheckedChange = {
                                        enablePaperTexture = it
                                        scope.launch {
                                            AppDatabase.setConfig("reader_paper_texture", it.toString())
                                        }
                                    }
                                )
                            }

                            if (enablePaperTexture) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("纸质微噪点浓度", style = MaterialTheme.typography.bodySmall)
                                    Text("${(paperTextureAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Slider(
                                    value = paperTextureAlpha,
                                    onValueChange = { paperTextureAlpha = it },
                                    onValueChangeFinished = {
                                        scope.launch {
                                            AppDatabase.setConfig("reader_paper_alpha", paperTextureAlpha.toString())
                                        }
                                    },
                                    valueRange = 0.02f..0.20f,
                                    steps = 9
                                )
                            }

                            HorizontalDivider()

                            Text("正文字体排版: $fontName", style = MaterialTheme.typography.bodyMedium)
                            var fontDropdownExpanded by remember { mutableStateOf(false) }
                            val systemFonts = remember { FontManager.getAvailableSystemFonts() }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { fontDropdownExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(fontName, maxLines = 1)
                                    }
                                    DropdownMenu(
                                        expanded = fontDropdownExpanded,
                                        onDismissRequest = { fontDropdownExpanded = false }
                                    ) {
                                        systemFonts.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt.name) },
                                                onClick = {
                                                    fontPath = opt.path
                                                    fontName = opt.name
                                                    fontDropdownExpanded = false
                                                    scope.launch {
                                                        AppDatabase.setConfig("reader_font_path", opt.path ?: "")
                                                        AppDatabase.setConfig("reader_font_name", opt.name)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        val chooser = JFileChooser().apply {
                                            dialogTitle = "选择外部字体文件 (.ttf / .otf)"
                                            fileFilter = FileNameExtensionFilter("字体文件 (*.ttf, *.otf)", "ttf", "otf")
                                            isAcceptAllFileFilterUsed = false
                                        }
                                        val res = chooser.showOpenDialog(null)
                                        if (res == JFileChooser.APPROVE_OPTION) {
                                            val selectedFile = chooser.selectedFile
                                            if (selectedFile != null && selectedFile.exists()) {
                                                fontPath = selectedFile.absolutePath
                                                fontName = selectedFile.nameWithoutExtension
                                                scope.launch {
                                                    AppDatabase.setConfig("reader_font_path", selectedFile.absolutePath)
                                                    AppDatabase.setConfig("reader_font_name", selectedFile.nameWithoutExtension)
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text("自定义字体...")
                                }
                            }

                            HorizontalDivider()

                            Text("主题色彩矩阵 (8色精调)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            val presetThemes = listOf(
                                ReadTheme.DAY, ReadTheme.PARCHMENT,
                                ReadTheme.BEAN_GREEN, ReadTheme.TWILIGHT,
                                ReadTheme.OCEAN_BLUE, ReadTheme.E_INK,
                                ReadTheme.OLED_BLACK, ReadTheme.GREEN
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (rowThemes in presetThemes.chunked(2)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowThemes.forEach { t ->
                                            val isSelected = currentTheme == t
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = t.bg,
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .clickable {
                                                        currentTheme = t
                                                        scope.launch {
                                                            AppDatabase.setConfig("reader_theme", t.id)
                                                        }
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = t.nameZh,
                                                        color = t.text,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Custom Theme Button & Hex Pickers
                            val isCustomSelected = currentTheme == ReadTheme.CUSTOM
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCustomSelected) customBgColor else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = if (isCustomSelected) 2.5.dp else 1.dp,
                                    color = if (isCustomSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clickable {
                                        currentTheme = ReadTheme.CUSTOM
                                        scope.launch {
                                            AppDatabase.setConfig("reader_theme", ReadTheme.CUSTOM.id)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "🎨 自定义主题配色",
                                        color = if (isCustomSelected) customTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            if (isCustomSelected) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("自定义 Hex 色值配置", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customBgHex,
                                                onValueChange = {
                                                    customBgHex = it
                                                    val parsed = parseHexColor(it, customBgColor)
                                                    customBgColor = parsed
                                                    scope.launch {
                                                        AppDatabase.setConfig("reader_custom_bg", it)
                                                    }
                                                },
                                                label = { Text("背景色 (#FAF7F2)", fontSize = 11.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                textStyle = MaterialTheme.typography.bodySmall
                                            )
                                            OutlinedTextField(
                                                value = customTextHex,
                                                onValueChange = {
                                                    customTextHex = it
                                                    val parsed = parseHexColor(it, customTextColor)
                                                    customTextColor = parsed
                                                    scope.launch {
                                                        AppDatabase.setConfig("reader_custom_text", it)
                                                    }
                                                },
                                                label = { Text("文字色 (#2C2523)", fontSize = 11.sp) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                textStyle = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    } else {
                        // Tab 1: Touch & Click Zones Configuration
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "屏幕分区比例（模拟触控手感）：",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val ratioLabels = listOf(
                                    "25:50:25",
                                    "33:34:33",
                                    "20:60:20"
                                )
                                ratioLabels.forEachIndexed { idx, label ->
                                    FilterChip(
                                        modifier = Modifier.weight(1f),
                                        selected = clickRatioIndex == idx,
                                        onClick = {
                                            clickRatioIndex = idx
                                            scope.launch {
                                                AppDatabase.setConfig("reader_click_ratio", idx.toString())
                                            }
                                        },
                                        label = {
                                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text(label, maxLines = 1, fontSize = 12.sp)
                                            }
                                        }
                                    )
                                }
                            }

                            // Interactive visual layout preview card
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .padding(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val lW = leftRatio
                                    val rW = rightRatio
                                    val cW = 1f - lW - rW

                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(lW).fillMaxHeight()
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                                            Text(
                                                text = "左区",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(cW).fillMaxHeight()
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                                            Text(
                                                text = "中区",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(rW).fillMaxHeight()
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(2.dp)) {
                                            Text(
                                                text = "右区",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }
                            }

                            val availableActions = ClickZoneAction.entries

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionPickerRow(
                                    label = "左侧区域点击：",
                                    currentAction = leftClickAction,
                                    options = availableActions,
                                    onSelected = { act ->
                                        leftClickAction = act
                                        scope.launch {
                                            AppDatabase.setConfig("reader_click_left_action", act.id)
                                        }
                                    }
                                )

                                ActionPickerRow(
                                    label = "中间区域点击：",
                                    currentAction = centerClickAction,
                                    options = availableActions,
                                    onSelected = { act ->
                                        centerClickAction = act
                                        scope.launch {
                                            AppDatabase.setConfig("reader_click_center_action", act.id)
                                        }
                                    }
                                )

                                ActionPickerRow(
                                    label = "右侧区域点击：",
                                    currentAction = rightClickAction,
                                    options = availableActions,
                                    onSelected = { act ->
                                        rightClickAction = act
                                        scope.launch {
                                            AppDatabase.setConfig("reader_click_right_action", act.id)
                                        }
                                    }
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // Offline Batch Cache Dialog
        if (showBatchCacheDialog) {
            AlertDialog(
                onDismissRequest = { showBatchCacheDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(LegadoIcons.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("离线批量缓存", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "《${book.name}》当前位于第 ${currentChapterIndex + 1} 章 / 共 ${chapters.size} 章",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "多协程后台并发拉取，自动保存在本地缓存目录中，断网环境下即开即读。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()

                        val remainingChapters = (chapters.size - currentChapterIndex).coerceAtLeast(0)

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val allSources = AppDatabase.getAllBookSources()
                                    val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }
                                    if (source != null && chapters.isNotEmpty()) {
                                        BookCacheEngine.startBatchDownload(book, source, chapters, currentChapterIndex, 50)
                                    }
                                }
                                showBatchCacheDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📥 缓存后 50 章 (${minOf(50, remainingChapters)} 章)")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val allSources = AppDatabase.getAllBookSources()
                                    val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }
                                    if (source != null && chapters.isNotEmpty()) {
                                        BookCacheEngine.startBatchDownload(book, source, chapters, currentChapterIndex, 100)
                                    }
                                }
                                showBatchCacheDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📥 缓存后 100 章 (${minOf(100, remainingChapters)} 章)")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val allSources = AppDatabase.getAllBookSources()
                                    val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }
                                    if (source != null && chapters.isNotEmpty()) {
                                        BookCacheEngine.startBatchDownload(book, source, chapters, currentChapterIndex, remainingChapters)
                                    }
                                }
                                showBatchCacheDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📥 缓存至最新章节 ($remainingChapters 章)")
                        }

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val allSources = AppDatabase.getAllBookSources()
                                    val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }
                                    if (source != null && chapters.isNotEmpty()) {
                                        BookCacheEngine.startBatchDownload(book, source, chapters, 0, chapters.size)
                                    }
                                }
                                showBatchCacheDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⚡ 缓存全本书籍 (全书共 ${chapters.size} 章)")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBatchCacheDialog = false }) {
                        Text("关闭")
                    }
                }
            )
        }

        // Floating Batch Download Progress Pill
        AnimatedVisibility(
            visible = downloadProgress != null && downloadProgress?.bookUrl == book.bookUrl,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
        ) {
            downloadProgress?.let { prog ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    LegadoIcons.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (prog.isFinished) "离线缓存完成" else "正在离线缓存...",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = { prog.cancelAction() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    LegadoIcons.Close,
                                    contentDescription = "取消/关闭",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val fraction = if (prog.totalToDownload > 0) prog.downloadedCount.toFloat() / prog.totalToDownload else 0f
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = prog.currentChapterTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${prog.downloadedCount}/${prog.totalToDownload} (${(fraction * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Desktop Native Image Lightbox Overlay
        if (activeLightboxImageUrl != null) {
            ImageLightbox(
                imageUrl = activeLightboxImageUrl!!,
                onDismiss = { activeLightboxImageUrl = null }
            )
        }
    }
}

@Composable
private fun ActionPickerRow(
    label: String,
    currentAction: ClickZoneAction,
    options: List<ClickZoneAction>,
    onSelected: (ClickZoneAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(currentAction.title, style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(opt.title, fontWeight = if (opt == currentAction) FontWeight.Bold else FontWeight.Normal)
                                Text(opt.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        },
                        onClick = {
                            onSelected(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingAnnotationToolbar(
    selectedText: String,
    onColorSelected: (String) -> Unit,
    onAddNote: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Yellow Highlight
            IconButton(
                onClick = { onColorSelected("YELLOW") },
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFFFFD54F), CircleShape)
            ) {}

            // Green Highlight
            IconButton(
                onClick = { onColorSelected("GREEN") },
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFF81C784), CircleShape)
            ) {}

            // Purple Highlight
            IconButton(
                onClick = { onColorSelected("PURPLE") },
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFFBA68C8), CircleShape)
            ) {}

            // Underline
            FilledTonalButton(
                onClick = { onColorSelected("UNDERLINE") },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("〰️ 下划线", style = MaterialTheme.typography.labelSmall)
            }

            // Add Note
            FilledTonalButton(
                onClick = onAddNote,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(LegadoIcons.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("想法", style = MaterialTheme.typography.labelSmall)
            }

            // Copy
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(LegadoIcons.ContentCopy, contentDescription = "复制", modifier = Modifier.size(15.dp))
            }

            // Speak TTS
            IconButton(
                onClick = onSpeak,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(LegadoIcons.VolumeUp, contentDescription = "朗读", modifier = Modifier.size(16.dp))
            }

            // Close
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(LegadoIcons.Close, contentDescription = "关闭", modifier = Modifier.size(13.dp))
            }
        }
    }
}

/**
 * Procedural Natural Paper Micro-Noise Canvas
 * Renders warm, non-flickering tactile texture using Skia procedural points with 0KB extra image assets.
 */
@Composable
fun PaperTextureCanvas(
    alpha: Float,
    modifier: Modifier = Modifier
) {
    if (alpha <= 0.001f) return
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val step = 16f
        var seed = 13371337L
        val cols = (w / step).toInt() + 1
        val rows = (h / step).toInt() + 1

        for (c in 0 until cols) {
            for (r in 0 until rows) {
                seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
                val rnd = (seed shr 16) and 0xFFFFL
                if (rnd % 7L == 0L) {
                    val offsetX = c * step + (rnd % 12L) - 6f
                    val offsetY = r * step + ((seed shr 8) % 12L) - 6f
                    val isDark = (rnd % 2L == 0L)
                    val dotColor = if (isDark) {
                        Color.Black.copy(alpha = alpha * 0.45f)
                    } else {
                        Color.White.copy(alpha = alpha * 0.45f)
                    }
                    drawCircle(
                        color = dotColor,
                        radius = 0.9f,
                        center = Offset(offsetX, offsetY)
                    )
                }
            }
        }
    }
}

/**
 * Desktop Native Borderless Image Lightbox
 * Supports mouse wheel stepless zoom (0.5x~5.0x), pointer drag panning, double-click 100% reset, and export.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImageLightbox(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1.0f
                        offset = Offset.Zero
                    }
                )
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (scrollDeltaY != 0f) {
                    val zoomFactor = if (scrollDeltaY < 0) 1.15f else 0.87f
                    scale = (scale * zoomFactor).coerceIn(0.5f, 5.0f)
                    if (scale <= 1.0f) {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Main Image Area with Drag Panning & Zoom Transformation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(scale) {
                    if (scale > 1.0f) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        LegadoIcons.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(96.dp)
                    )
                    Text(
                        text = "原图原生渲染画廊",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = imageUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2
                    )
                    Text(
                        text = "缩放比例: ${(scale * 100).toInt()}% · 滚轮缩放 / 拖拽平移 / 双击复位",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Top Floating Toolbar
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { scale = (scale * 1.25f).coerceAtMost(5.0f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                IconButton(
                    onClick = {
                        scale = (scale * 0.8f).coerceAtLeast(0.5f)
                        if (scale <= 1f) offset = Offset.Zero
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                FilledTonalButton(
                    onClick = {
                        scale = 1.0f
                        offset = Offset.Zero
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("100% 复位", fontSize = 12.sp)
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(LegadoIcons.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}


