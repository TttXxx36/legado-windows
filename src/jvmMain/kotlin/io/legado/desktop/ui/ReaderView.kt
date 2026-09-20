package io.legado.desktop.ui

import androidx.compose.animation.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import io.legado.desktop.data.model.ClickZoneAction
import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.data.model.Bookmark
import io.legado.desktop.engine.BookCacheEngine
import io.legado.desktop.engine.BookSourceEngine
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
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

enum class ReadTheme(
    val nameZh: String,
    val bg: Color,
    val text: Color
) {
    DAY("象牙白", Color(0xFFFAF7F2), Color(0xFF2C2523)),
    PARCHMENT("羊皮纸", Color(0xFFF4ECD8), Color(0xFF382E20)),
    GREEN("护眼绿", Color(0xFFE8EFE6), Color(0xFF233226)),
    NIGHT("夜间黑", Color(0xFF161618), Color(0xFFD4D4D8))
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var fontPath by remember { mutableStateOf<String?>(null) }
    var fontName by remember { mutableStateOf("默认系统字体") }
    val activeFontFamily = remember(fontPath) { FontManager.getFontFamily(fontPath) }
    var showTOC by remember { mutableStateOf(false) }
    var tocTabIndex by remember { mutableStateOf(0) } // 0: 目录, 1: 书签
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsTabIndex by remember { mutableStateOf(0) } // 0: 排版与主题, 1: 翻页与屏幕触控
    var showHud by remember { mutableStateOf(true) }

    // Advanced 5-Dimension Typography & Page Turn Mode
    var pageTurnMode by remember { mutableStateOf(PageTurnMode.SCROLL) }
    var lineSpacingMultiplier by remember { mutableStateOf(1.75f) }
    var paragraphSpacingDp by remember { mutableStateOf(16) }
    var firstLineIndent by remember { mutableStateOf(true) }
    var horizontalPaddingDp by remember { mutableStateOf(32) }
    var showBatchCacheDialog by remember { mutableStateOf(false) }

    // Pagination Calculation States
    var viewportWidthPx by remember { mutableStateOf(800f) }
    var viewportHeightPx by remember { mutableStateOf(900f) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var targetPageWhenChapterLoaded by remember { mutableStateOf<Int?>(null) }

    val downloadProgress by BookCacheEngine.downloadProgress.collectAsState()

    val pagedChapter = remember(
        chapterContent,
        viewportWidthPx,
        viewportHeightPx,
        fontSize,
        lineSpacingMultiplier,
        paragraphSpacingDp,
        firstLineIndent,
        horizontalPaddingDp
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
            firstLineIndent = firstLineIndent
        )
    }

    // Safely clamp current page index
    val safePageIndex = remember(currentPageIndex, pagedChapter.totalPages) {
        if (pagedChapter.totalPages <= 0) 0
        else currentPageIndex.coerceIn(0, pagedChapter.totalPages - 1)
    }

    // Immersive / Fullscreen State
    var isImmersive by remember { mutableStateOf(false) }

    // Screen Click Zones Preferences
    var leftClickAction by remember { mutableStateOf(ClickZoneAction.PAGE_PREV) }
    var centerClickAction by remember { mutableStateOf(ClickZoneAction.TOGGLE_MENU) }
    var rightClickAction by remember { mutableStateOf(ClickZoneAction.PAGE_NEXT) }
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
                if (pageTurnMode == PageTurnMode.SLIDE_PAGING && !isDualPage) {
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
                if (pageTurnMode == PageTurnMode.SLIDE_PAGING && !isDualPage) {
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
                    ReadTheme.PARCHMENT -> ReadTheme.GREEN
                    ReadTheme.GREEN -> ReadTheme.NIGHT
                    ReadTheme.NIGHT -> ReadTheme.DAY
                }
            }
            ClickZoneAction.NONE -> {}
        }
    }

    // Bookmarks State
    val bookBookmarks = remember { mutableStateListOf<Bookmark>() }

    // TTS State
    var isTtsActive by remember { mutableStateOf(false) }
    var isTtsPlaying by remember { mutableStateOf(false) }
    var ttsRate by remember { mutableStateOf(0) }

    // Load chapters, bookmarks & preferences
    LaunchedEffect(book.bookUrl) {
        val savedPath = AppDatabase.getConfig("reader_font_path", "")
        val savedName = AppDatabase.getConfig("reader_font_name", "默认系统字体")
        if (savedPath.isNotBlank()) {
            fontPath = savedPath
            fontName = savedName
        }

        val savedLeft = AppDatabase.getConfig("reader_click_left_action", ClickZoneAction.PAGE_PREV.id)
        val savedCenter = AppDatabase.getConfig("reader_click_center_action", ClickZoneAction.TOGGLE_MENU.id)
        val savedRight = AppDatabase.getConfig("reader_click_right_action", ClickZoneAction.PAGE_NEXT.id)
        val savedRatio = AppDatabase.getConfig("reader_click_ratio", "0").toIntOrNull() ?: 0

        leftClickAction = ClickZoneAction.fromId(savedLeft, ClickZoneAction.PAGE_PREV)
        centerClickAction = ClickZoneAction.fromId(savedCenter, ClickZoneAction.TOGGLE_MENU)
        rightClickAction = ClickZoneAction.fromId(savedRight, ClickZoneAction.PAGE_NEXT)
        clickRatioIndex = savedRatio

        // Load 5-Dimension Typography & Page Turn Mode
        val savedMode = AppDatabase.getConfig("reader_page_turn_mode", PageTurnMode.SCROLL.id)
        pageTurnMode = PageTurnMode.fromId(savedMode)

        val savedLineSpacing = AppDatabase.getConfig("reader_line_spacing", "1.75").toFloatOrNull() ?: 1.75f
        lineSpacingMultiplier = savedLineSpacing

        val savedParaSpacing = AppDatabase.getConfig("reader_paragraph_spacing", "16").toIntOrNull() ?: 16
        paragraphSpacingDp = savedParaSpacing

        val savedIndent = AppDatabase.getConfig("reader_first_line_indent", "true").toBooleanStrictOrNull() ?: true
        firstLineIndent = savedIndent

        val savedPadding = AppDatabase.getConfig("reader_horizontal_padding", "32").toIntOrNull() ?: 32
        horizontalPaddingDp = savedPadding

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
            .background(currentTheme.bg)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.F11 -> {
                            isImmersive = !isImmersive
                            if (isImmersive) showHud = false
                            true
                        }
                        Key.DirectionRight, Key.PageDown, Key.Spacebar -> {
                            executeAction(ClickZoneAction.PAGE_NEXT)
                            true
                        }
                        Key.DirectionLeft, Key.PageUp -> {
                            executeAction(ClickZoneAction.PAGE_PREV)
                            true
                        }
                        Key.Escape -> {
                            if (isImmersive) {
                                isImmersive = false
                                true
                            } else {
                                handleClose()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Reader Content Canvas
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
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
                            modifier = Modifier.fillMaxWidth()
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
                // Dual-page spread layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 64.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = chapterContent,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineSpacingMultiplier).sp,
                            color = currentTheme.text,
                            letterSpacing = 0.5.sp,
                            fontFamily = activeFontFamily
                        )
                    }
                    VerticalDivider(color = currentTheme.text.copy(alpha = 0.15f))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "【双页模式 - 右栏页签】\n\n当前正文共 ${chapterContent.length} 字。\n已启用 Legado 净化过滤规则。\n翻页可使用 Space / 方向键 / PageDown。\n点击页面任意空白处可显示/隐藏顶部与底部阅读面板。",
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineSpacingMultiplier).sp,
                            color = currentTheme.text.copy(alpha = 0.7f),
                            fontFamily = activeFontFamily
                        )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentChapterTitle,
                            fontSize = 13.sp,
                            color = currentTheme.text.copy(alpha = 0.55f),
                            maxLines = 1,
                            fontFamily = activeFontFamily,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${safePageIndex + 1} / ${pagedChapter.totalPages} 页",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = currentTheme.text.copy(alpha = 0.6f),
                            fontFamily = activeFontFamily
                        )
                    }

                    Spacer(Modifier.height(10.dp))

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
                            Text(
                                text = pageText,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * lineSpacingMultiplier).sp,
                                color = currentTheme.text,
                                letterSpacing = 0.6.sp,
                                fontFamily = activeFontFamily,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Footer Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "本章共 ${pagedChapter.totalCharCount} 字",
                            fontSize = 12.sp,
                            color = currentTheme.text.copy(alpha = 0.45f),
                            fontFamily = activeFontFamily
                        )
                        Text(
                            text = "Legado 仿真分页模式",
                            fontSize = 12.sp,
                            color = currentTheme.text.copy(alpha = 0.35f)
                        )
                    }
                }
            } else {
                // Centered single column continuous vertical scroll
                Column(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxHeight()
                        .padding(horizontal = horizontalPaddingDp.dp, vertical = 64.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = currentChapterTitle,
                        fontSize = (fontSize + 6).sp,
                        fontWeight = FontWeight.Bold,
                        color = currentTheme.text,
                        fontFamily = activeFontFamily
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = chapterContent,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineSpacingMultiplier).sp,
                        color = currentTheme.text,
                        letterSpacing = 0.6.sp,
                        fontFamily = activeFontFamily
                    )
                    Spacer(Modifier.height(80.dp))
                }
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
                        // Bookmark Toggle Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (isCurrentChapterBookmarked) {
                                        val bm = bookBookmarks.firstOrNull { it.chapterIndex == currentChapterIndex }
                                        if (bm != null) {
                                            AppDatabase.deleteBookmark(bm.id)
                                            bookBookmarks.remove(bm)
                                        }
                                    } else {
                                        val newBm = Bookmark(
                                            bookUrl = book.bookUrl,
                                            bookName = book.name,
                                            chapterIndex = currentChapterIndex,
                                            chapterTitle = currentChapterTitle,
                                            content = chapterContent.take(100).trim() + "..."
                                        )
                                        AppDatabase.insertBookmark(newBm)
                                        bookBookmarks.add(newBm)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                if (isCurrentChapterBookmarked) LegadoIcons.Bookmark else LegadoIcons.BookmarkBorder,
                                contentDescription = "书签",
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

                        IconButton(onClick = { isDualPage = !isDualPage }) {
                            Icon(
                                if (isDualPage) LegadoIcons.ViewAgenda else LegadoIcons.AutoStories,
                                contentDescription = "切换单/双页模式"
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
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(LegadoIcons.Tune, contentDescription = "排版与设置")
                        }
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
                            text = { Text("书签笔记 (${bookBookmarks.size})") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (tocTabIndex == 0) {
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

        // Reading Settings Dialog (Typography, Themes & Touch Zones)
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("阅读设置", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(min = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PrimaryTabRow(selectedTabIndex = settingsTabIndex) {
                            Tab(
                                selected = settingsTabIndex == 0,
                                onClick = { settingsTabIndex = 0 },
                                text = { Text("排版与主题") }
                            )
                            Tab(
                                selected = settingsTabIndex == 1,
                                onClick = { settingsTabIndex = 1 },
                                text = { Text("翻页与触控区域") }
                            )
                        }

                        if (settingsTabIndex == 0) {
                            // Tab 0: Typography, Page Turn Mode & Theme
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("翻页交互模式", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PageTurnMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = pageTurnMode == mode,
                                            onClick = {
                                                pageTurnMode = mode
                                                scope.launch {
                                                    AppDatabase.setConfig("reader_page_turn_mode", mode.id)
                                                }
                                            },
                                            label = { Text(mode.title) }
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

                                Text("页面左右边距", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val paddings = listOf(Pair(16, "紧凑 (16dp)"), Pair(32, "适中 (32dp)"), Pair(64, "宽裕 (64dp)"))
                                    paddings.forEach { (pad, label) ->
                                        FilterChip(
                                            selected = horizontalPaddingDp == pad,
                                            onClick = {
                                                horizontalPaddingDp = pad
                                                scope.launch {
                                                    AppDatabase.setConfig("reader_horizontal_padding", pad.toString())
                                                }
                                            },
                                            label = { Text(label) }
                                        )
                                    }
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

                                Text("阅读底色", style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ReadTheme.values().forEach { t ->
                                        val selected = currentTheme == t
                                        Button(
                                            onClick = { currentTheme = t },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = t.bg,
                                                contentColor = t.text
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                t.nameZh,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Tab 1: Touch & Click Zones Configuration
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(
                                    text = "屏幕分区比例（模拟安卓端触控翻页手感）：",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val ratioLabels = listOf(
                                        "25% : 50% : 25% (经典)",
                                        "33% : 34% : 33% (均等)",
                                        "20% : 60% : 20% (宽中间)"
                                    )
                                    ratioLabels.forEachIndexed { idx, label ->
                                        FilterChip(
                                            selected = clickRatioIndex == idx,
                                            onClick = {
                                                clickRatioIndex = idx
                                                scope.launch {
                                                    AppDatabase.setConfig("reader_click_ratio", idx.toString())
                                                }
                                            },
                                            label = { Text(label) }
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
                                            .height(68.dp)
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
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                                Text(
                                                    text = "左区\n${leftClickAction.title.take(5)}",
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
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                                Text(
                                                    text = "中区\n${centerClickAction.title.take(5)}",
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
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                                Text(
                                                    text = "右区\n${rightClickAction.title.take(5)}",
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
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showSettingsDialog = false }) {
                        Text("完成")
                    }
                }
            )
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
