package io.legado.desktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.data.model.ReplaceRule
import io.legado.desktop.data.model.WebDavConfig
import io.legado.desktop.engine.BookCacheEngine
import io.legado.desktop.engine.BookSourceDiagnosticEngine
import io.legado.desktop.engine.BookSourceEngine
import io.legado.desktop.engine.CacheDownloadProgress
import io.legado.desktop.engine.DiagnosticStatus
import io.legado.desktop.engine.hotkey.GlobalMediaHotkeyManager
import io.legado.desktop.engine.local.LocalBookImporter
import io.legado.desktop.engine.sync.WebDavSync
import io.legado.desktop.server.LegadoWebServer
import io.legado.desktop.ui.theme.LegadoIcons
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

enum class NavDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    BOOKSHELF("书架", LegadoIcons.Book, LegadoIcons.Book),
    DISCOVER("发现", LegadoIcons.Explore, LegadoIcons.Explore),
    SOURCES("书源", LegadoIcons.LibraryBooks, LegadoIcons.LibraryBooks),
    STATS("统计", LegadoIcons.BarChart, LegadoIcons.BarChart),
    SETTINGS("设置", LegadoIcons.Settings, LegadoIcons.Settings)
}

@Composable
fun AppShell(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    window: java.awt.Window? = null
) {
    var currentDestination by remember { mutableStateOf(NavDestination.BOOKSHELF) }
    var activeReadingBook by remember { mutableStateOf<Book?>(null) }
    val scope = rememberCoroutineScope()

    // Phase 17: Global Command Palette State & Global Hotkey Dispatcher (Ctrl+K / Ctrl+P)
    var showCommandPalette by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val dispatcher = java.awt.KeyEventDispatcher { event ->
            if (event.id == java.awt.event.KeyEvent.KEY_PRESSED) {
                val isCtrl = event.isControlDown || event.isMetaDown
                if (isCtrl && (event.keyCode == java.awt.event.KeyEvent.VK_K || event.keyCode == java.awt.event.KeyEvent.VK_P)) {
                    showCommandPalette = true
                    true
                } else false
            } else false
        }
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
        onDispose {
            java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
        }
    }

    val books = remember { mutableStateListOf<Book>() }
    val sources = remember { mutableStateListOf<BookSource>() }
    val globalDownloadProgress by BookCacheEngine.downloadProgress.collectAsState()

    // Windows Native Drag & Drop State
    var isDraggingOver by remember { mutableStateOf(false) }
    var dragToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dragToastMessage) {
        if (dragToastMessage != null) {
            kotlinx.coroutines.delay(3500L)
            dragToastMessage = null
        }
    }

    DisposableEffect(window) {
        if (window == null) return@DisposableEffect onDispose {}

        val dropTarget = object : java.awt.dnd.DropTarget() {
            override fun dragEnter(dtde: java.awt.dnd.DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY)
                    isDraggingOver = true
                } else {
                    dtde.rejectDrag()
                }
            }

            override fun dragOver(dtde: java.awt.dnd.DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY)
                    isDraggingOver = true
                }
            }

            override fun dragExit(dte: java.awt.dnd.DropTargetEvent) {
                isDraggingOver = false
            }

            override fun drop(dtde: java.awt.dnd.DropTargetDropEvent) {
                isDraggingOver = false
                try {
                    dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY)
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        val droppedFiles = transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as? List<*>
                        val bookFiles = droppedFiles?.filterIsInstance<java.io.File>()?.filter {
                            it.extension.equals("txt", ignoreCase = true) || it.extension.equals("epub", ignoreCase = true)
                        } ?: emptyList()

                        if (bookFiles.isNotEmpty()) {
                            scope.launch {
                                var successCount = 0
                                val importedNames = mutableListOf<String>()
                                for (file in bookFiles) {
                                    try {
                                        val imported = LocalBookImporter.importBook(file)
                                        if (!books.any { it.bookUrl == imported.bookUrl }) {
                                            books.add(0, imported)
                                        }
                                        importedNames.add("《${imported.name}》")
                                        successCount++
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                if (successCount > 0) {
                                    dragToastMessage = "成功导入 $successCount 本书籍：${importedNames.take(2).joinToString("、")}${if (successCount > 2) " 等" else ""}"
                                }
                            }
                            dtde.dropComplete(true)
                            return
                        }
                    }
                    dtde.dropComplete(false)
                } catch (_: Exception) {
                    dtde.dropComplete(false)
                }
            }
        }

        window.dropTarget = dropTarget
        onDispose {
            window.dropTarget = null
        }
    }

    // Load initial data from SQLite
    LaunchedEffect(Unit) {
        val loadedBooks = AppDatabase.getAllBooks()
        if (loadedBooks.isEmpty()) {
            val sampleBooks = listOf(
                Book(
                    bookUrl = "local://santi",
                    name = "三体",
                    author = "刘慈欣",
                    latestChapterTitle = "终章",
                    type = 3
                ),
                Book(
                    bookUrl = "local://guimi",
                    name = "诡秘之主",
                    author = "爱潜水的乌贼",
                    latestChapterTitle = "大结局",
                    type = 3
                ),
                Book(
                    bookUrl = "local://daogui",
                    name = "道诡异仙",
                    author = "狐尾的笔",
                    latestChapterTitle = "第990章 回归",
                    type = 3
                )
            )
            for (b in sampleBooks) {
                AppDatabase.insertOrUpdateBook(b)
            }
            books.addAll(sampleBooks)
        } else {
            books.addAll(loadedBooks)
        }

        val loadedSources = AppDatabase.getAllBookSources()
        sources.addAll(loadedSources)

        val hotkeyEnabled = AppDatabase.getConfig("global_media_hotkey_enabled", "true") == "true"
        if (hotkeyEnabled) {
            GlobalMediaHotkeyManager.start()
        }
    }

    if (activeReadingBook != null) {
        ReaderView(
            book = activeReadingBook!!,
            onClose = {
                activeReadingBook = null
                scope.launch {
                    books.clear()
                    books.addAll(AppDatabase.getAllBooks())
                }
            }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
            // Material Design 3 Navigation Rail
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                header = {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (darkTheme) LegadoIcons.LightMode else LegadoIcons.DarkMode,
                            contentDescription = "切换深浅主题"
                        )
                    }
                }
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                NavDestination.values().forEach { destination ->
                    val selected = currentDestination == destination
                    NavigationRailItem(
                        selected = selected,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = destination.title
                            )
                        },
                        label = { Text(destination.title) },
                        alwaysShowLabel = true
                    )
                }
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Crossfade(targetState = currentDestination) { dest ->
                    when (dest) {
                        NavDestination.BOOKSHELF -> BookshelfView(
                            books = books,
                            onOpenBook = { book -> activeReadingBook = book },
                            onDeleteBook = { book ->
                                scope.launch {
                                    AppDatabase.deleteBook(book.bookUrl)
                                    books.remove(book)
                                }
                            },
                            onBookImported = { importedBook ->
                                if (!books.any { it.bookUrl == importedBook.bookUrl }) {
                                    books.add(0, importedBook)
                                }
                            },
                            onDeleteBooks = { targetBooks, moveToTrash ->
                                scope.launch {
                                    val urls = targetBooks.map { it.bookUrl }
                                    AppDatabase.deleteBooks(urls)
                                    if (moveToTrash) {
                                        targetBooks.forEach { b ->
                                            try {
                                                val file = if (b.bookUrl.startsWith("file://")) {
                                                    File(URI(b.bookUrl))
                                                } else if (b.origin == "LOCAL" || File(b.bookUrl).exists()) {
                                                    File(b.bookUrl)
                                                } else null
                                                if (file != null && file.exists()) {
                                                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
                                                        Desktop.getDesktop().moveToTrash(file)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                    books.removeAll { it.bookUrl in urls }
                                }
                            },
                            onUpdateBooksGroup = { targetBooks, newGroup ->
                                scope.launch {
                                    val urls = targetBooks.map { it.bookUrl }
                                    AppDatabase.updateBooksGroup(urls, newGroup)
                                    for (i in books.indices) {
                                        val b = books[i]
                                        if (b.bookUrl in urls) {
                                            books[i] = b.copy(customGroup = newGroup)
                                        }
                                    }
                                }
                            }
                        )
                        NavDestination.DISCOVER -> DiscoverView(
                            sources = sources,
                            onOpenBook = { book -> activeReadingBook = book },
                            onBookAddedToShelf = { book ->
                                if (!books.any { it.bookUrl == book.bookUrl }) {
                                    books.add(book)
                                }
                            }
                        )
                        NavDestination.SOURCES -> SourcesView(
                            sources = sources,
                            onAddSources = { newSources ->
                                scope.launch {
                                    AppDatabase.insertBookSources(newSources)
                                    sources.clear()
                                    sources.addAll(AppDatabase.getAllBookSources())
                                }
                            },
                            onDeleteSource = { source ->
                                scope.launch {
                                    AppDatabase.deleteBookSource(source.bookSourceUrl)
                                    sources.remove(source)
                                }
                            },
                            onUpdateSource = { updatedSource ->
                                scope.launch {
                                    AppDatabase.insertOrUpdateBookSource(updatedSource)
                                    val idx = sources.indexOfFirst { it.bookSourceUrl == updatedSource.bookSourceUrl }
                                    if (idx >= 0) {
                                        sources[idx] = updatedSource
                                    }
                                }
                            },
                            onRefreshSources = {
                                scope.launch {
                                    sources.clear()
                                    sources.addAll(AppDatabase.getAllBookSources())
                                }
                            }
                        )
                        NavDestination.STATS -> AnalyticsView(
                            onOpenBook = { targetUrl ->
                                val book = books.find { it.bookUrl == targetUrl }
                                if (book != null) {
                                    activeReadingBook = book
                                }
                            }
                        )
                        NavDestination.SETTINGS -> SettingsView(darkTheme, onToggleTheme)
                    }
                }

                // Global Floating Batch Download Progress Pill
                GlobalDownloadPill(
                    prog = globalDownloadProgress,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp)
                )

                // Drag and Drop Toast Notification
                DragToastNotification(
                    message = dragToastMessage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }

        // Full Window Drag & Drop Overlay
        DragDropOverlay(
            visible = isDraggingOver,
            modifier = Modifier.fillMaxSize()
        )

        // Phase 17: Global Command Palette Dialog (Ctrl+K / Ctrl+P)
        if (showCommandPalette) {
            CommandPaletteDialog(
                books = books,
                isInReader = false,
                onOpenBook = { book ->
                    activeReadingBook = book
                },
                onTriggerAi = {
                    currentDestination = NavDestination.SETTINGS
                },
                onTriggerTts = {
                    val targetBook = books.filter { it.durChapterTime > 0 }.maxByOrNull { it.durChapterTime } ?: books.firstOrNull()
                    if (targetBook != null) {
                        activeReadingBook = targetBook
                    }
                },
                onTriggerExport = {
                    val targetBook = books.filter { it.durChapterTime > 0 }.maxByOrNull { it.durChapterTime } ?: books.firstOrNull()
                    if (targetBook != null) {
                        activeReadingBook = targetBook
                    }
                },
                onTriggerChangeSource = {
                    currentDestination = NavDestination.SOURCES
                },
                onNavigateToSearch = {
                    currentDestination = NavDestination.DISCOVER
                },
                onNavigateToSources = {
                    currentDestination = NavDestination.SOURCES
                },
                onImportLocalBook = {
                    val dialog = FileDialog(null as Frame?, "选择本地电子书 (.txt, .epub)", FileDialog.LOAD)
                    dialog.setFilenameFilter { _, name ->
                        name.endsWith(".txt", ignoreCase = true) || name.endsWith(".epub", ignoreCase = true)
                    }
                    dialog.isVisible = true
                    val file = dialog.file
                    val dir = dialog.directory
                    if (file != null && dir != null) {
                        val selectedFile = File(dir, file)
                        scope.launch {
                            try {
                                val imported = LocalBookImporter.importBook(selectedFile)
                                if (!books.any { it.bookUrl == imported.bookUrl }) {
                                    books.add(0, imported)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                },
                onToggleFullscreen = {
                    if (window is Frame) {
                        window.extendedState = if (window.extendedState == Frame.MAXIMIZED_BOTH) Frame.NORMAL else Frame.MAXIMIZED_BOTH
                    }
                },
                onToggleDualPage = {},
                onOpenAiSettings = {
                    currentDestination = NavDestination.SETTINGS
                },
                onNavigateToStats = {
                    currentDestination = NavDestination.STATS
                },
                onDismissRequest = { showCommandPalette = false }
            )
        }
    }
}
}

@Composable
fun GlobalDownloadPill(
    prog: CacheDownloadProgress?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = prog != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (prog != null) {
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
                            text = "${prog.bookName} · ${prog.currentChapterTitle}",
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

@Composable
fun DragToastNotification(
    message: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    LegadoIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun DragDropOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = LegadoIcons.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "📥 松开鼠标即可将书籍导入书架",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "支持 .TXT（智能流式分章秒开）与 .EPUB 格式，支持多文件批量拖入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

enum class BookSortOrder(val title: String) {
    LAST_READ("最近阅读"),
    ADD_TIME("添加时间"),
    NAME("书名 A-Z"),
    DURATION("阅读时长")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfView(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onBookImported: (Book) -> Unit = {},
    onDeleteBooks: (List<Book>, Boolean) -> Unit = { _, _ -> },
    onUpdateBooksGroup: (List<Book>, String?) -> Unit = { _, _ -> }
) {
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    // Phase 19: Groups, Search, Sort & Batch states
    var groups by remember { mutableStateOf(listOf("在读", "养肥", "完结")) }
    LaunchedEffect(Unit) {
        groups = AppDatabase.getAllBookGroups()
    }

    val allTabs = remember(groups) { listOf("全部") + groups }
    var selectedTabIdx by rememberSaveable { mutableStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(BookSortOrder.LAST_READ) }
    var sortAscending by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Batch mode state
    var isBatchMode by rememberSaveable { mutableStateOf(false) }
    val selectedBookUrls = remember { mutableStateListOf<String>() }

    // Dialogs state
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupNameInput by remember { mutableStateOf("") }

    var groupToRename by remember { mutableStateOf<String?>(null) }
    var renameGroupInput by remember { mutableStateOf("") }

    var groupToDelete by remember { mutableStateOf<String?>(null) }

    var showMoveToGroupDialog by remember { mutableStateOf(false) }
    var safeDeleteTargetBooks by remember { mutableStateOf<List<Book>?>(null) }

    var batchCacheBook by remember { mutableStateOf<Book?>(null) }
    var batchCacheChapters by remember { mutableStateOf<List<BookChapter>>(emptyList()) }
    var isPreparingChapters by remember { mutableStateOf(false) }
    var exportBook by remember { mutableStateOf<Book?>(null) }

    val currentTab = allTabs.getOrElse(selectedTabIdx) { "全部" }

    val filteredBooks = remember(books.toList(), currentTab, searchQuery) {
        books.filter { book ->
            val matchesQuery = searchQuery.isBlank() ||
                book.name.contains(searchQuery, ignoreCase = true) ||
                book.author.contains(searchQuery, ignoreCase = true)
            if (!matchesQuery) return@filter false

            when (currentTab) {
                "全部" -> true
                "在读" -> book.customGroup == "在读" || (book.customGroup.isNullOrBlank() && book.durChapterTime > 0)
                "养肥" -> book.customGroup == "养肥"
                "完结" -> book.customGroup == "完结"
                else -> book.customGroup == currentTab
            }
        }
    }

    val displayedBooks = remember(filteredBooks, sortOrder, sortAscending) {
        val sorted = when (sortOrder) {
            BookSortOrder.LAST_READ -> filteredBooks.sortedBy { it.durChapterTime }
            BookSortOrder.ADD_TIME -> filteredBooks.sortedBy { it.order }
            BookSortOrder.NAME -> filteredBooks.sortedBy { it.name.lowercase() }
            BookSortOrder.DURATION -> filteredBooks.sortedBy { it.durChapterTime }
        }
        if (sortAscending) sorted else sorted.reversed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "书架",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "共 ${books.size} 本书籍" + (if (currentTab != "全部") " · 当前分组: $currentTab (${filteredBooks.size}本)" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在分章导入...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }

                    // Batch Mode Toggle Button
                    if (books.isNotEmpty()) {
                        if (!isBatchMode) {
                            OutlinedButton(
                                onClick = {
                                    isBatchMode = true
                                    selectedBookUrls.clear()
                                }
                            ) {
                                Icon(LegadoIcons.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("批量管理")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    isBatchMode = false
                                    selectedBookUrls.clear()
                                }
                            ) {
                                Icon(LegadoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("退出管理")
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val dialog = FileDialog(null as Frame?, "选择本地电子书 (.txt, .epub)", FileDialog.LOAD)
                            dialog.setFilenameFilter { _, name ->
                                name.endsWith(".txt", ignoreCase = true) || name.endsWith(".epub", ignoreCase = true)
                            }
                            dialog.isVisible = true
                            val file = dialog.file
                            val dir = dialog.directory
                            if (file != null && dir != null) {
                                val selectedFile = File(dir, file)
                                scope.launch {
                                    isImporting = true
                                    importMessage = null
                                    try {
                                        val imported = LocalBookImporter.importBook(selectedFile)
                                        onBookImported(imported)
                                        importMessage = "《${imported.name}》(${if (selectedFile.extension.equals("epub", true)) "EPUB" else "TXT"}) 导入成功，共生成 ${imported.totalChapterNum} 个章节！"
                                    } catch (e: Exception) {
                                        importMessage = "导入失败: ${e.message}"
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            }
                        },
                        enabled = !isImporting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(LegadoIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("导入本地书籍")
                    }
                }
            }

            if (importMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = importMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { importMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(LegadoIcons.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Phase 19: Horizontal Scrollable Group Tabs Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allTabs.forEachIndexed { idx, tabName ->
                    val isSelected = selectedTabIdx == idx
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTabIdx = idx },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tabName)
                                if (tabName !in listOf("全部", "在读", "养肥", "完结")) {
                                    Spacer(Modifier.width(4.dp))
                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(LegadoIcons.Tune, contentDescription = "分组设置", modifier = Modifier.size(12.dp))
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("重命名") },
                                                onClick = {
                                                    showMenu = false
                                                    groupToRename = tabName
                                                    renameGroupInput = tabName
                                                },
                                                leadingIcon = { Icon(LegadoIcons.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("删除分组", color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showMenu = false
                                                    groupToDelete = tabName
                                                },
                                                leadingIcon = { Icon(LegadoIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                // Add Group Button
                OutlinedButton(
                    onClick = {
                        newGroupNameInput = ""
                        showCreateGroupDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(LegadoIcons.Add, contentDescription = "新建分组", modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建分组", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Phase 19: Search & Sort Filter Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("在书架中搜索书名、作者...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(LegadoIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(LegadoIcons.Close, contentDescription = "清空", modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                // Sort Dropdown
                Box {
                    OutlinedButton(
                        onClick = { showSortMenu = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(LegadoIcons.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("排序: ${sortOrder.title}", style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        BookSortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.title) },
                                onClick = {
                                    sortOrder = order
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOrder == order) {
                                        Icon(LegadoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }
                }

                // Ascending / Descending Toggle
                IconButton(
                    onClick = { sortAscending = !sortAscending },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        LegadoIcons.SwapVert,
                        contentDescription = if (sortAscending) "升序" else "降序",
                        tint = if (sortAscending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isBatchMode) {
                    OutlinedButton(
                        onClick = {
                            val allUrls = displayedBooks.map { it.bookUrl }
                            if (selectedBookUrls.containsAll(allUrls)) {
                                selectedBookUrls.clear()
                            } else {
                                selectedBookUrls.clear()
                                selectedBookUrls.addAll(allUrls)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(if (selectedBookUrls.containsAll(displayedBooks.map { it.bookUrl }) && displayedBooks.isNotEmpty()) "取消全选" else "全选")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Phase 17: 极速续读看板 (Quick Resume Hero Banner) - Show when not in batch mode and no active search
            if (!isBatchMode && searchQuery.isBlank() && selectedTabIdx == 0) {
                val lastReadBook = remember(books) {
                    books.filter { it.durChapterTime > 0 }.maxByOrNull { it.durChapterTime } ?: books.firstOrNull()
                }
                if (lastReadBook != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenBook(lastReadBook) },
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.size(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            LegadoIcons.Book,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "⚡ 极速续读",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = "《${lastReadBook.name}》",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "· ${lastReadBook.author.ifBlank { "未知作者" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = "当前读至：${lastReadBook.durChapterTitle?.ifBlank { "第 1 章" } ?: "第 1 章"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        "Ctrl+K 指挥中心",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Button(
                                    onClick = { onOpenBook(lastReadBook) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(LegadoIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("立即续读", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (displayedBooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "没有找到匹配“$searchQuery”的书籍" else "该分组暂无书籍",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().padding(bottom = if (isBatchMode && selectedBookUrls.isNotEmpty()) 80.dp else 0.dp)
                ) {
                    items(displayedBooks, key = { it.bookUrl }) { book ->
                        val isSelected = selectedBookUrls.contains(book.bookUrl)
                        BookCard(
                            book = book,
                            isBatchMode = isBatchMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedBookUrls.remove(book.bookUrl)
                                } else {
                                    selectedBookUrls.add(book.bookUrl)
                                }
                            },
                            onOpen = {
                                if (isBatchMode) {
                                    if (isSelected) selectedBookUrls.remove(book.bookUrl) else selectedBookUrls.add(book.bookUrl)
                                } else {
                                    onOpenBook(book)
                                }
                            },
                            onCache = {
                                batchCacheBook = book
                                scope.launch {
                                    isPreparingChapters = true
                                    var chs = AppDatabase.getChapters(book.bookUrl)
                                    if (chs.isEmpty()) {
                                        val allSources = AppDatabase.getAllBookSources()
                                        val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }
                                        if (source != null) {
                                            try {
                                                chs = BookSourceEngine.getChapters(source, book)
                                                if (chs.isNotEmpty()) {
                                                    AppDatabase.saveChapters(book.bookUrl, chs)
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    batchCacheChapters = chs
                                    isPreparingChapters = false
                                }
                            },
                            onExport = {
                                exportBook = book
                            },
                            onDelete = {
                                safeDeleteTargetBooks = listOf(book)
                            }
                        )
                    }
                }
            }
        }

        // Phase 19: Floating Batch Action Bar
        if (isBatchMode && selectedBookUrls.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "已选中 ${selectedBookUrls.size} 本书籍",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { showMoveToGroupDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(LegadoIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("移入分组")
                        }

                        OutlinedButton(
                            onClick = {
                                val targets = books.filter { it.bookUrl in selectedBookUrls }
                                scope.launch {
                                    val allSources = AppDatabase.getAllBookSources()
                                    for (b in targets) {
                                        var chs = AppDatabase.getChapters(b.bookUrl)
                                        val s = allSources.firstOrNull { it.bookSourceUrl == b.origin }
                                        if (s != null) {
                                            if (chs.isEmpty()) {
                                                try {
                                                    chs = BookSourceEngine.getChapters(s, b)
                                                    if (chs.isNotEmpty()) AppDatabase.saveChapters(b.bookUrl, chs)
                                                } catch (_: Exception) {}
                                            }
                                            if (chs.isNotEmpty()) {
                                                BookCacheEngine.startBatchDownload(b, s, chs, b.durChapterIndex, 50)
                                            }
                                        }
                                    }
                                }
                                selectedBookUrls.clear()
                                isBatchMode = false
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(LegadoIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("批量缓存(50章)")
                        }

                        Button(
                            onClick = {
                                safeDeleteTargetBooks = books.filter { it.bookUrl in selectedBookUrls }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(LegadoIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("批量删除")
                        }

                        IconButton(
                            onClick = {
                                selectedBookUrls.clear()
                                isBatchMode = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(LegadoIcons.Close, contentDescription = "取消批量")
                        }
                    }
                }
            }
        }
    }

    // Dialogs: Batch Cache Dialog
    if (batchCacheBook != null) {
        AlertDialog(
            onDismissRequest = { batchCacheBook = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(LegadoIcons.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("离线批量缓存: 《${batchCacheBook!!.name}》", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isPreparingChapters) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("正在拉取最新章节目录...", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Text("全书共 ${batchCacheChapters.size} 个章节，已就绪可执行离线下载。", style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider()

                        val totalCh = batchCacheChapters.size
                        val currentDur = batchCacheBook!!.durChapterIndex.coerceIn(0, (totalCh - 1).coerceAtLeast(0))
                        val remain = (totalCh - currentDur).coerceAtLeast(0)

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val allSources = AppDatabase.getAllBookSources()
                                    val source = allSources.firstOrNull { it.bookSourceUrl == batchCacheBook!!.origin }
                                    if (source != null && batchCacheChapters.isNotEmpty()) {
                                        BookCacheEngine.startBatchDownload(batchCacheBook!!, source, batchCacheChapters, currentDur, 50)
                                    }
                                }
                                batchCacheBook = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📥 缓存后 50 章 (${minOf(50, remain)} 章)")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val allSources = AppDatabase.getAllBookSources()
                                    val source = allSources.firstOrNull { it.bookSourceUrl == batchCacheBook!!.origin }
                                    if (source != null && batchCacheChapters.isNotEmpty()) {
                                        BookCacheEngine.startBatchDownload(batchCacheBook!!, source, batchCacheChapters, currentDur, 100)
                                    }
                                }
                                batchCacheBook = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📥 缓存后 100 章 (${minOf(100, remain)} 章)")
                        }

                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val allSources = AppDatabase.getAllBookSources()
                                    val source = allSources.firstOrNull { it.bookSourceUrl == batchCacheBook!!.origin }
                                    if (source != null && batchCacheChapters.isNotEmpty()) {
                                        BookCacheEngine.startBatchDownload(batchCacheBook!!, source, batchCacheChapters, 0, totalCh)
                                    }
                                }
                                batchCacheBook = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("⚡ 缓存全本书籍 (全书 $totalCh 章)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { batchCacheBook = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // Dialogs: Export Book Dialog
    if (exportBook != null) {
        BookExportDialog(
            book = exportBook!!,
            onDismissRequest = { exportBook = null }
        )
    }

    // Phase 19 Dialogs: Create Group Dialog
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("新建书架分组", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newGroupNameInput,
                    onValueChange = { newGroupNameInput = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newGroupNameInput.trim()
                        if (name.isNotBlank()) {
                            scope.launch {
                                AppDatabase.createBookGroup(name, groups.size)
                                groups = AppDatabase.getAllBookGroups()
                            }
                        }
                        showCreateGroupDialog = false
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Phase 19 Dialogs: Rename Group Dialog
    if (groupToRename != null) {
        AlertDialog(
            onDismissRequest = { groupToRename = null },
            title = { Text("重命名分组", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameGroupInput,
                    onValueChange = { renameGroupInput = it },
                    label = { Text("新分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val oldName = groupToRename!!
                        val newName = renameGroupInput.trim()
                        if (newName.isNotBlank() && newName != oldName) {
                            scope.launch {
                                AppDatabase.renameBookGroup(oldName, newName)
                                groups = AppDatabase.getAllBookGroups()
                                books.forEach { b ->
                                    if (b.customGroup == oldName) b.customGroup = newName
                                }
                            }
                        }
                        groupToRename = null
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToRename = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Phase 19 Dialogs: Delete Group Dialog
    if (groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("删除分组", fontWeight = FontWeight.Bold) },
            text = {
                Text("确认删除分组《${groupToDelete}》？分组内的书籍不会被删除，仅重置为未分组状态。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = groupToDelete!!
                        scope.launch {
                            AppDatabase.deleteBookGroup(name)
                            groups = AppDatabase.getAllBookGroups()
                            books.forEach { b ->
                                if (b.customGroup == name) b.customGroup = null
                            }
                        }
                        if (selectedTabIdx > 0 && allTabs.getOrNull(selectedTabIdx) == name) {
                            selectedTabIdx = 0
                        }
                        groupToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Phase 19 Dialogs: Move to Group Dialog
    if (showMoveToGroupDialog) {
        val moveOptions = listOf("未分组", "在读", "养肥", "完结") + groups.filter { it !in listOf("在读", "养肥", "完结") }
        AlertDialog(
            onDismissRequest = { showMoveToGroupDialog = false },
            title = { Text("移入书架分组", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    moveOptions.forEach { opt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val targetGroup = if (opt == "未分组") null else opt
                                    val targets = books.filter { it.bookUrl in selectedBookUrls }
                                    onUpdateBooksGroup(targets, targetGroup)
                                    selectedBookUrls.clear()
                                    isBatchMode = false
                                    showMoveToGroupDialog = false
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(LegadoIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(opt, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveToGroupDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Phase 19 Dialogs: Safe Delete Dialog (Adheres to Rule 5 Safe File Operations)
    if (safeDeleteTargetBooks != null) {
        val targets = safeDeleteTargetBooks!!
        var moveToTrash by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { safeDeleteTargetBooks = null },
            icon = {
                Icon(LegadoIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
            },
            title = {
                Text("确认移除书籍", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "即将从书架移除以下 ${targets.size} 本书籍：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            targets.forEach { b ->
                                Text("• 《${b.name}》 ${if (b.author.isNotBlank()) "(${b.author})" else ""}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { moveToTrash = !moveToTrash }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = moveToTrash,
                            onCheckedChange = { moveToTrash = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "同时将本地源文件移入系统回收站 (仅对本地导入文件生效，非永久粉碎)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBooks(targets, moveToTrash)
                        safeDeleteTargetBooks = null
                        selectedBookUrls.removeAll(targets.map { it.bookUrl })
                        if (isBatchMode && selectedBookUrls.isEmpty()) {
                            isBatchMode = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("确认移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { safeDeleteTargetBooks = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun BookCard(
    book: Book,
    onOpen: () -> Unit,
    onCache: () -> Unit,
    onExport: () -> Unit = {},
    onDelete: () -> Unit,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable {
                if (isBatchMode) onToggleSelect() else onOpen()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        LegadoIcons.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = book.name.take(4),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Batch mode checkbox in top-right
                if (isBatchMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }

                // Custom group tag in bottom-start if present
                if (!book.customGroup.isNullOrBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = book.customGroup!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = book.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = book.author.ifBlank { "未知作者" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                if (!isBatchMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCache, modifier = Modifier.size(28.dp)) {
                            Icon(
                                LegadoIcons.CloudDownload,
                                contentDescription = "批量缓存",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onExport, modifier = Modifier.size(28.dp)) {
                            Icon(
                                LegadoIcons.Download,
                                contentDescription = "导出书籍 (TXT/EPUB)",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(
                                LegadoIcons.Delete,
                                contentDescription = "删除书籍",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            if (!book.latestChapterTitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "最新: ${book.latestChapterTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun DiscoverView() {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "发现",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "从已配置的书源中探索热门推荐、排行榜与分类",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesView(
    sources: List<BookSource>,
    onAddSources: (List<BookSource>) -> Unit,
    onDeleteSource: (BookSource) -> Unit,
    onUpdateSource: (BookSource) -> Unit = {},
    onRefreshSources: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var importTabIndex by remember { mutableStateOf(0) } // 0: 网络在线, 1: 本地文件, 2: 剪贴板/粘贴

    // Tab 0: Online URL
    var importUrlText by remember { mutableStateOf("") }
    var isFetchingUrl by remember { mutableStateOf(false) }

    // Tab 1: Local File
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileContent by remember { mutableStateOf<String?>(null) }

    // Tab 2: Manual / Clipboard JSON
    var importJsonText by remember { mutableStateOf("") }

    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Phase 14: Filter, Diagnostic & Batch Operation State
    var keyword by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("全部") }
    var isTestingAll by remember { mutableStateOf(false) }
    var testProgress by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showCleanConfirmDialog by remember { mutableStateOf(false) }
    var sortByLatency by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val allGroups = remember(sources) {
        val groups = mutableSetOf<String>()
        sources.forEach { s ->
            val g = s.bookSourceGroup?.trim()
            if (!g.isNullOrEmpty()) {
                g.split("[,;，；]".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }.forEach { groups.add(it) }
            }
        }
        listOf("全部") + groups.sorted() + listOf("未分组")
    }

    val filteredSources = remember(sources, keyword, selectedGroup) {
        sources.filter { s ->
            val matchesKeyword = keyword.isBlank() ||
                s.bookSourceName.contains(keyword, ignoreCase = true) ||
                s.bookSourceUrl.contains(keyword, ignoreCase = true)
            val matchesGroup = when (selectedGroup) {
                "全部" -> true
                "未分组" -> s.bookSourceGroup.isNullOrBlank()
                else -> s.bookSourceGroup?.contains(selectedGroup) == true
            }
            matchesKeyword && matchesGroup
        }
    }

    val displayedSources = remember(filteredSources, sortByLatency) {
        if (sortByLatency) {
            filteredSources.sortedWith(
                compareBy<BookSource> { s ->
                    val status = BookSourceDiagnosticEngine.getStatus(s.bookSourceUrl)
                    if (status is DiagnosticStatus.Healthy) status.latencyMs else Long.MAX_VALUE
                }.thenBy { it.bookSourceName }
            )
        } else {
            filteredSources
        }
    }

    fun getClipboardString(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String
            } else null
        } catch (_: Exception) {
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "书源管理中台",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "当前已导入 ${sources.size} 个 Legado 3.0 书源 (${sources.count { it.enabled }} 个已启用)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { showExportDialog = true },
                    enabled = sources.isNotEmpty()
                ) {
                    Icon(LegadoIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导出书源")
                }

                Button(onClick = {
                    showImportDialog = true
                    importError = null
                    importSuccessMessage = null
                }) {
                    Icon(LegadoIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导入书源")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search & Batch Action Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                placeholder = { Text("搜索书源名称或网址...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                trailingIcon = {
                    if (keyword.isNotEmpty()) {
                        IconButton(onClick = { keyword = "" }) {
                            Icon(LegadoIcons.Clear, contentDescription = "清空")
                        }
                    }
                }
            )

            // ⚡ 一键测速
            OutlinedButton(
                onClick = {
                    if (isTestingAll) return@OutlinedButton
                    isTestingAll = true
                    testProgress = "0/${filteredSources.size}"
                    scope.launch {
                        BookSourceDiagnosticEngine.testAllSources(filteredSources) { done, total, _, _ ->
                            testProgress = "$done/$total"
                        }
                        isTestingAll = false
                        testProgress = ""
                        statusMessage = "测速诊断完成！"
                    }
                },
                enabled = !isTestingAll && filteredSources.isNotEmpty(),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isTestingAll) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("测速中 ($testProgress)")
                } else {
                    Icon(LegadoIcons.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("一键测速")
                }
            }

            // ⚡ 按延迟排序
            OutlinedButton(
                onClick = { sortByLatency = !sortByLatency },
                enabled = !isTestingAll && filteredSources.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = if (sortByLatency) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) else ButtonDefaults.outlinedButtonColors()
            ) {
                Icon(
                    imageVector = LegadoIcons.SwapVert,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (sortByLatency) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(if (sortByLatency) "按延迟升序 ✓" else "按延迟排序")
            }

            // ⏸️ 禁用失效源
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val count = BookSourceDiagnosticEngine.disableFailedSources(filteredSources)
                        statusMessage = "已自动禁用 $count 个超时/失效书源"
                        onRefreshSources()
                    }
                },
                enabled = !isTestingAll && filteredSources.isNotEmpty(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(LegadoIcons.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("禁用失效源")
            }

            // 🗑️ 清理失效源
            OutlinedButton(
                onClick = { showCleanConfirmDialog = true },
                enabled = !isTestingAll && filteredSources.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(LegadoIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("清理失效源")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Group Filter Chips Row
        if (allGroups.size > 2) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                allGroups.forEach { grp ->
                    FilterChip(
                        selected = selectedGroup == grp,
                        onClick = { selectedGroup = grp },
                        label = { Text(grp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Notification Banner
        if (statusMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(24.dp)) {
                        Icon(LegadoIcons.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (displayedSources.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        LegadoIcons.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (sources.isEmpty()) "暂无书源，点击右上角“导入书源”导入" else "没有符合当前筛选条件的书源",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedSources, key = { it.bookSourceUrl }) { source ->
                    var itemStatus by remember(source.bookSourceUrl, isTestingAll) {
                        mutableStateOf(BookSourceDiagnosticEngine.getStatus(source.bookSourceUrl))
                    }
                    var isItemTesting by remember { mutableStateOf(false) }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = source.bookSourceName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!source.bookSourceGroup.isNullOrBlank()) {
                                        AssistChip(
                                            onClick = { selectedGroup = source.bookSourceGroup?.split("[,;，；]".toRegex())?.firstOrNull()?.trim() ?: "全部" },
                                            label = { Text(source.bookSourceGroup ?: "", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                                Text(
                                    text = source.bookSourceUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Diagnostic status badge
                                when (val st = itemStatus) {
                                    is DiagnosticStatus.Healthy -> {
                                        Surface(
                                            color = Color(0xFFE8F5E9),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "🟢 ${st.latencyMs}ms",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF2E7D32),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    is DiagnosticStatus.Checking -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                                Text("测速中", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    is DiagnosticStatus.Timeout -> {
                                        Surface(
                                            color = Color(0xFFFFF9C4),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "🟡 超时",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFF57F17),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    is DiagnosticStatus.Error -> {
                                        Surface(
                                            color = Color(0xFFFFEBEE),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "🔴 失败",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFC62828),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    is DiagnosticStatus.Empty -> {
                                        Surface(
                                            color = Color(0xFFFFF3E0),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "⚠️ 无数据",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFFE65100),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    DiagnosticStatus.Untested -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "⚪ 未测速",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                // Enabled Switch
                                Switch(
                                    checked = source.enabled,
                                    onCheckedChange = { chk ->
                                        val updated = source.copy(enabled = chk)
                                        onUpdateSource(updated)
                                    }
                                )

                                // Single Test Button
                                IconButton(
                                    onClick = {
                                        if (isItemTesting) return@IconButton
                                        isItemTesting = true
                                        itemStatus = DiagnosticStatus.Checking
                                        scope.launch {
                                            val res = BookSourceDiagnosticEngine.testSource(source)
                                            itemStatus = res
                                            isItemTesting = false
                                        }
                                    },
                                    enabled = !isItemTesting
                                ) {
                                    Icon(LegadoIcons.Speed, contentDescription = "单源测速", modifier = Modifier.size(20.dp))
                                }

                                // Delete Button
                                IconButton(onClick = { onDeleteSource(source) }) {
                                    Icon(
                                        LegadoIcons.Delete,
                                        contentDescription = "删除书源",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("📤 导出书源备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "当前共 ${sources.size} 个书源，可直接导出为标准 Legado 3.0 格式 JSON，方便在手机端或其他电脑间迁移备份。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                showExportDialog = false
                                try {
                                    val jsonStr = kotlinx.serialization.json.Json { prettyPrint = true; ignoreUnknownKeys = true }.encodeToString(sources)
                                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                    clipboard.setContents(StringSelection(jsonStr), null)
                                    statusMessage = "已成功复制 ${sources.size} 个书源 JSON 到系统剪贴板！"
                                } catch (e: Exception) {
                                    statusMessage = "复制失败: ${e.message}"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(LegadoIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("复制到剪贴板")
                        }

                        Button(
                            onClick = {
                                showExportDialog = false
                                try {
                                    val dialog = FileDialog(null as Frame?, "导出书源为 JSON 文件", FileDialog.SAVE)
                                    dialog.file = "legado_sources_${System.currentTimeMillis() / 1000}.json"
                                    dialog.isVisible = true
                                    val file = dialog.file
                                    val dir = dialog.directory
                                    if (file != null && dir != null) {
                                        val targetFile = File(dir, file)
                                        val jsonStr = kotlinx.serialization.json.Json { prettyPrint = true; ignoreUnknownKeys = true }.encodeToString(sources)
                                        targetFile.writeText(jsonStr, Charsets.UTF_8)
                                        statusMessage = "书源已成功导出至: ${targetFile.name}"
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "导出失败: ${e.message}"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(LegadoIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("保存为 .json 文件")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCleanConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCleanConfirmDialog = false },
            title = { Text("清理失效书源") },
            text = { Text("确定要彻底清理当前测速为超时或错误的书源吗？这将从本地数据库中移除这些失效书源。") },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanConfirmDialog = false
                        scope.launch {
                            val deleted = BookSourceDiagnosticEngine.deleteFailedSources(filteredSources)
                            statusMessage = "已彻底清理 $deleted 个失效书源"
                            onRefreshSources()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认清理")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isFetchingUrl) showImportDialog = false
            },
            title = {
                Text(
                    text = "导入 Legado 3.0 书源",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrimaryTabRow(selectedTabIndex = importTabIndex) {
                        Tab(
                            selected = importTabIndex == 0,
                            onClick = { importTabIndex = 0; importError = null },
                            text = { Text("网络在线导入") }
                        )
                        Tab(
                            selected = importTabIndex == 1,
                            onClick = { importTabIndex = 1; importError = null },
                            text = { Text("本地文件导入") }
                        )
                        Tab(
                            selected = importTabIndex == 2,
                            onClick = { importTabIndex = 2; importError = null },
                            text = { Text("剪贴板/粘贴") }
                        )
                    }

                    when (importTabIndex) {
                        0 -> {
                            // Online URL Import Tab
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "输入书源链接（支持 Legado 一键导入接口、网络 JSON 合集链接或 CDN 地址）：",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = importUrlText,
                                    onValueChange = {
                                        importUrlText = it
                                        importError = null
                                    },
                                    placeholder = { Text("https://example.com/sources.json 或书源合集链接") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = false,
                                    maxLines = 3,
                                    isError = importError != null
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clip = getClipboardString()?.trim()
                                            if (!clip.isNullOrBlank() && (clip.startsWith("http://") || clip.startsWith("https://"))) {
                                                importUrlText = clip
                                                importError = null
                                            } else {
                                                importError = "剪贴板中未找到以 http(s):// 开头的网址"
                                            }
                                        }
                                    ) {
                                        Icon(LegadoIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("粘贴剪贴板网址")
                                    }

                                    if (isFetchingUrl) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Text("正在拉取远程书源...", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Local File Import Tab
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "选择存储在本地电脑中的 Legado 3.0 书源文件（.json 或 .txt）：",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedButton(
                                    onClick = {
                                        val chooser = JFileChooser().apply {
                                            dialogTitle = "选择本地 Legado 书源文件"
                                            fileFilter = FileNameExtensionFilter("书源文件 (*.json, *.txt)", "json", "txt")
                                            isMultiSelectionEnabled = false
                                        }
                                        val res = chooser.showOpenDialog(null)
                                        if (res == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                                            val f = chooser.selectedFile
                                            try {
                                                val content = f.readText(Charsets.UTF_8)
                                                selectedFileName = f.name
                                                selectedFileContent = content
                                                importError = null
                                            } catch (e: Exception) {
                                                importError = "读取文件失败: ${e.message}"
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(LegadoIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (selectedFileName == null) "浏览并选择本地书源文件..." else "重新选择文件")
                                }

                                if (selectedFileName != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "已选文件：$selectedFileName",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            val previewList = selectedFileContent?.let { BookSourceEngine.parseBookSources(it) } ?: emptyList()
                                            Text(
                                                text = "文件大小: ${(selectedFileContent?.toByteArray(Charsets.UTF_8)?.size ?: 0) / 1024} KB  |  预估可解析: ${previewList.size} 个有效书源",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Clipboard / Manual JSON Tab
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "粘贴书源 JSON 源码（支持单个对象或数组）：",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    TextButton(
                                        onClick = {
                                            val clip = getClipboardString()?.trim()
                                            if (!clip.isNullOrBlank()) {
                                                importJsonText = clip
                                                importError = null
                                            } else {
                                                importError = "剪贴板为空或未包含文本"
                                            }
                                        }
                                    ) {
                                        Icon(LegadoIcons.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("一键贴入")
                                    }
                                }
                                OutlinedTextField(
                                    value = importJsonText,
                                    onValueChange = {
                                        importJsonText = it
                                        importError = null
                                    },
                                    placeholder = { Text("[ { \"bookSourceName\": \"示例书源\", \"bookSourceUrl\": \"...\" } ]") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    isError = importError != null
                                )
                            }
                        }
                    }

                    if (importError != null) {
                        Text(
                            text = importError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isFetchingUrl,
                    onClick = {
                        when (importTabIndex) {
                            0 -> {
                                if (importUrlText.isBlank()) {
                                    importError = "请输入有效的书源网络链接"
                                    return@Button
                                }
                                scope.launch {
                                    isFetchingUrl = true
                                    importError = null
                                    try {
                                        val fetched = BookSourceEngine.importFromUrl(importUrlText)
                                        if (fetched.isEmpty()) {
                                            importError = "未能从该网址获取到有效书源，请检查链接或网络访问"
                                        } else {
                                            onAddSources(fetched)
                                            showImportDialog = false
                                            importUrlText = ""
                                        }
                                    } catch (e: Exception) {
                                        importError = "拉取书源异常: ${e.localizedMessage ?: e.message}"
                                    } finally {
                                        isFetchingUrl = false
                                    }
                                }
                            }
                            1 -> {
                                val content = selectedFileContent
                                if (content.isNullOrBlank()) {
                                    importError = "请先选择有效的书源文件"
                                    return@Button
                                }
                                val parsed = BookSourceEngine.parseBookSources(content)
                                if (parsed.isEmpty()) {
                                    importError = "所选文件中未能识别到有效的 Legado 3.0 书源"
                                } else {
                                    onAddSources(parsed)
                                    showImportDialog = false
                                    selectedFileName = null
                                    selectedFileContent = null
                                }
                            }
                            2 -> {
                                if (importJsonText.isBlank()) {
                                    importError = "请输入或粘贴书源 JSON 内容"
                                    return@Button
                                }
                                val parsed = BookSourceEngine.parseBookSources(importJsonText)
                                if (parsed.isEmpty()) {
                                    importError = "未能识别有效的书源 JSON，请检查格式"
                                } else {
                                    onAddSources(parsed)
                                    showImportDialog = false
                                    importJsonText = ""
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isFetchingUrl) "正在解析..." else "确认导入")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isFetchingUrl,
                    onClick = { showImportDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SettingsView(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // WebDAV state
    var webDavUrl by remember { mutableStateOf("") }
    var webDavUser by remember { mutableStateOf("") }
    var webDavPass by remember { mutableStateOf("") }
    var webDavDir by remember { mutableStateOf("legado") }
    var webDavStatus by remember { mutableStateOf<String?>(null) }
    var isWebDavBusy by remember { mutableStateOf(false) }

    // Replace Rules state
    val replaceRules = remember { mutableStateListOf<ReplaceRule>() }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var newRuleName by remember { mutableStateOf("") }
    var newRulePattern by remember { mutableStateOf("") }
    var newRuleReplacement by remember { mutableStateOf("") }

    var isHotkeyEnabled by remember { mutableStateOf(GlobalMediaHotkeyManager.isEnabled) }

    LaunchedEffect(Unit) {
        val config = AppDatabase.getWebDavConfig()
        webDavUrl = config.url
        webDavUser = config.username
        webDavPass = config.password
        webDavDir = config.rootDir

        val loadedRules = AppDatabase.getReplaceRules()
        replaceRules.clear()
        replaceRules.addAll(loadedRules)

        isHotkeyEnabled = AppDatabase.getConfig("global_media_hotkey_enabled", "true") == "true"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // 1. Appearance Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "界面与外观",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("深色模式", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "切换亮色/深色 Material Design 3 主题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = darkTheme, onCheckedChange = { onToggleTheme() })
                }
            }
        }

        // 2. WebDAV Cloud Sync Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "WebDAV 云同步与多端备份",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "兼容 Legado 安卓端 WebDAV 备份格式，可在坚果云、Nextcloud 等服务间无缝双向同步书架、进度与书源。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = webDavUrl,
                    onValueChange = { webDavUrl = it },
                    label = { Text("WebDAV 服务器地址") },
                    placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = webDavUser,
                        onValueChange = { webDavUser = it },
                        label = { Text("账号") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = webDavPass,
                        onValueChange = { webDavPass = it },
                        label = { Text("密码 / 应用授权码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = webDavDir,
                    onValueChange = { webDavDir = it },
                    label = { Text("云端存储根目录") },
                    placeholder = { Text("legado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val cfg = WebDavConfig(webDavUrl, webDavUser, webDavPass, webDavDir)
                            scope.launch {
                                isWebDavBusy = true
                                AppDatabase.saveWebDavConfig(cfg)
                                val res = WebDavSync.testConnection(cfg)
                                webDavStatus = if (res.isSuccess) res.getOrNull() else "连接失败: ${res.exceptionOrNull()?.message}"
                                isWebDavBusy = false
                            }
                        },
                        enabled = !isWebDavBusy && webDavUrl.isNotBlank()
                    ) {
                        Text("测试连接并保存")
                    }

                    Button(
                        onClick = {
                            val cfg = WebDavConfig(webDavUrl, webDavUser, webDavPass, webDavDir)
                            scope.launch {
                                isWebDavBusy = true
                                AppDatabase.saveWebDavConfig(cfg)
                                val res = WebDavSync.backupAll(cfg)
                                webDavStatus = if (res.isSuccess) res.getOrNull() else "备份失败: ${res.exceptionOrNull()?.message}"
                                isWebDavBusy = false
                            }
                        },
                        enabled = !isWebDavBusy && webDavUrl.isNotBlank()
                    ) {
                        Icon(LegadoIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("备份到云端")
                    }

                    OutlinedButton(
                        onClick = {
                            val cfg = WebDavConfig(webDavUrl, webDavUser, webDavPass, webDavDir)
                            scope.launch {
                                isWebDavBusy = true
                                AppDatabase.saveWebDavConfig(cfg)
                                val res = WebDavSync.restoreAll(cfg)
                                webDavStatus = if (res.isSuccess) res.getOrNull() else "恢复失败: ${res.exceptionOrNull()?.message}"
                                isWebDavBusy = false
                            }
                        },
                        enabled = !isWebDavBusy && webDavUrl.isNotBlank()
                    ) {
                        Icon(LegadoIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("从云端恢复")
                    }
                }

                if (webDavStatus != null) {
                    Text(
                        text = webDavStatus ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (webDavStatus?.contains("成功") == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 3. Storage & Cache Management Card
        var currentCachePath by remember { mutableStateOf("") }
        var cacheSizeBytes by remember { mutableStateOf(0L) }
        var showClearCacheConfirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val dir = BookCacheEngine.getCacheDir()
            currentCachePath = dir.absolutePath
            cacheSizeBytes = BookCacheEngine.getCacheTotalSizeBytes()
        }

        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "离线缓存与存储管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "已占用: ${BookCacheEngine.formatFileSize(cacheSizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "所有离线章节以纯文本分层存储在本地磁盘，不增加 SQLite 数据库文件的写锁压力与膨胀风险。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = currentCachePath,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("当前缓存根目录") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val chooser = JFileChooser().apply {
                                dialogTitle = "选择离线缓存存储目录"
                                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                isAcceptAllFileFilterUsed = false
                            }
                            val res = chooser.showOpenDialog(null)
                            if (res == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                                val selectedDir = chooser.selectedFile
                                scope.launch {
                                    BookCacheEngine.setCustomCacheDir(selectedDir.absolutePath)
                                    val dir = BookCacheEngine.getCacheDir()
                                    currentCachePath = dir.absolutePath
                                    cacheSizeBytes = BookCacheEngine.getCacheTotalSizeBytes()
                                }
                            }
                        }
                    ) {
                        Icon(LegadoIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("更改存储目录...")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                BookCacheEngine.setCustomCacheDir("")
                                val dir = BookCacheEngine.getCacheDir()
                                currentCachePath = dir.absolutePath
                                cacheSizeBytes = BookCacheEngine.getCacheTotalSizeBytes()
                            }
                        }
                    ) {
                        Text("恢复默认路径")
                    }

                    OutlinedButton(
                        onClick = { showClearCacheConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(LegadoIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("清空离线缓存")
                    }
                }

                if (showClearCacheConfirm) {
                    AlertDialog(
                        onDismissRequest = { showClearCacheConfirm = false },
                        title = { Text("确认清空所有离线缓存？") },
                        text = { Text("清空后所有已下载章节的离线文件将被清除，不影响已保存在书架上的书籍及阅读进度。") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    scope.launch {
                                        BookCacheEngine.clearAllCache()
                                        cacheSizeBytes = BookCacheEngine.getCacheTotalSizeBytes()
                                        showClearCacheConfirm = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("确认清空")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearCacheConfirm = false }) {
                                Text("取消")
                            }
                        }
                    )
                }
            }
        }

        // 4. Text Clean & Replace Rules Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "净化与替换规则 (${replaceRules.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "过滤小说中的牛皮癣广告、微信推广及错误排版段落",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(onClick = { showAddRuleDialog = true }) {
                        Icon(LegadoIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加规则")
                    }
                }

                if (replaceRules.isEmpty()) {
                    Text(
                        text = "暂无净化规则，可点击“添加规则”配置正则或普通文本替换",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(replaceRules) { rule ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rule.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "“${rule.pattern}” -> “${rule.replacement}”",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = rule.isEnabled,
                                            onCheckedChange = { isChecked ->
                                                rule.isEnabled = isChecked
                                                scope.launch {
                                                    AppDatabase.insertOrUpdateReplaceRule(rule)
                                                    replaceRules.clear()
                                                    replaceRules.addAll(AppDatabase.getReplaceRules())
                                                }
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    AppDatabase.deleteReplaceRule(rule.id)
                                                    replaceRules.remove(rule)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                LegadoIcons.Delete,
                                                contentDescription = "删除规则",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
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

        // 4. Local Network Web Server Card
        var isServerRunning by remember { mutableStateOf(LegadoWebServer.isRunning) }
        val localIp = remember { LegadoWebServer.getLocalIpAddress() }

        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "局域网 Web 传书与控制台服务",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isServerRunning) "服务运行中: http://$localIp:${LegadoWebServer.port}" else "未开启（开启后可用手机直接访问电脑书架或导入书源）",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isServerRunning,
                        onCheckedChange = { start ->
                            if (start) {
                                LegadoWebServer.start()
                                isServerRunning = LegadoWebServer.isRunning
                            } else {
                                LegadoWebServer.stop()
                                isServerRunning = false
                            }
                        }
                    )
                }

                if (isServerRunning) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                try {
                                    Desktop.getDesktop().browse(URI("http://127.0.0.1:${LegadoWebServer.port}"))
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        ) {
                            Icon(LegadoIcons.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("在浏览器中打开")
                        }
                    }
                }
            }
        }

        // 5. Global Media Hotkeys Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "全局多媒体硬件按键 & 快捷键",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "允许在窗口最小化或后台听书时，直接响应键盘媒体键或组合键",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isHotkeyEnabled,
                        onCheckedChange = { enable ->
                            isHotkeyEnabled = enable
                            scope.launch {
                                AppDatabase.setConfig("global_media_hotkey_enabled", enable.toString())
                                if (enable) {
                                    GlobalMediaHotkeyManager.start()
                                } else {
                                    GlobalMediaHotkeyManager.stop()
                                }
                            }
                        }
                    )
                }

                Text(
                    text = "• 播放 / 暂停: 键盘 [Play/Pause] 键 或 [Ctrl + Alt + 空格]\n• 下一章 / 快进: 键盘 [Next Track] 键 或 [Ctrl + Alt + 右方向键]\n• 上一章 / 后退: 键盘 [Prev Track] 键 或 [Ctrl + Alt + 左方向键]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 6. Local Database Storage Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "数据存储",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "数据库路径: %APPDATA%\\LegadoDesktop\\legado.db\n存储引擎: SQLite 3 (JDBC Direct Connection)\n包含模块: 书籍、章节、书源、发现规则、排版偏好、书签笔记、替换净化、应用配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 7. About Card
        OutlinedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "关于 Legado Windows",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "版本: 1.2.0 (Phase 6 发现引擎/全系统字体排版/全局多媒体热键)\n技术架构: Compose Multiplatform + Material Design 3 + Windows SAPI\n移植自: HapeLee/legado-with-MD3 & gedoor/legado\n开源协议: GPL-3.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = { showAddRuleDialog = false },
            title = { Text("添加净化替换规则") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newRuleName,
                        onValueChange = { newRuleName = it },
                        label = { Text("规则名称") },
                        placeholder = { Text("例如：去除微信公众号推广") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRulePattern,
                        onValueChange = { newRulePattern = it },
                        label = { Text("匹配模式 (正则或文本)") },
                        placeholder = { Text("例如：关注微信.*看全集") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRuleReplacement,
                        onValueChange = { newRuleReplacement = it },
                        label = { Text("替换内容 (留空则直接删除)") },
                        placeholder = { Text("默认留空直接剔除") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRuleName.isNotBlank() && newRulePattern.isNotBlank()) {
                            val r = ReplaceRule(
                                name = newRuleName.trim(),
                                pattern = newRulePattern.trim(),
                                replacement = newRuleReplacement,
                                isRegex = true,
                                isEnabled = true
                            )
                            scope.launch {
                                AppDatabase.insertOrUpdateReplaceRule(r)
                                replaceRules.add(r)
                                showAddRuleDialog = false
                                newRuleName = ""
                                newRulePattern = ""
                                newRuleReplacement = ""
                            }
                        }
                    },
                    enabled = newRuleName.isNotBlank() && newRulePattern.isNotBlank()
                ) {
                    Text("保存规则")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRuleDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
