package io.legado.desktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import io.legado.desktop.engine.CacheDownloadProgress
import io.legado.desktop.engine.BookSourceEngine
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
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.launch

enum class NavDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    BOOKSHELF("书架", LegadoIcons.Book, LegadoIcons.Book),
    DISCOVER("发现", LegadoIcons.Explore, LegadoIcons.Explore),
    SOURCES("书源", LegadoIcons.LibraryBooks, LegadoIcons.LibraryBooks),
    SETTINGS("设置", LegadoIcons.Settings, LegadoIcons.Settings)
}

@Composable
fun AppShell(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    var currentDestination by remember { mutableStateOf(NavDestination.BOOKSHELF) }
    var activeReadingBook by remember { mutableStateOf<Book?>(null) }
    val scope = rememberCoroutineScope()

    val books = remember { mutableStateListOf<Book>() }
    val sources = remember { mutableStateListOf<BookSource>() }
    val globalDownloadProgress by BookCacheEngine.downloadProgress.collectAsState()

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
fun BookshelfView(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit,
    onBookImported: (Book) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }
    var importMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
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
                    text = "共 ${books.size} 本书籍",
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

        Spacer(modifier = Modifier.height(16.dp))

        var batchCacheBook by remember { mutableStateOf<Book?>(null) }
        var batchCacheChapters by remember { mutableStateOf<List<BookChapter>>(emptyList()) }
        var isPreparingChapters by remember { mutableStateOf(false) }

        if (books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "书架空空如也，快导入书籍或在“发现”中搜索吧",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(books) { book ->
                    BookCard(
                        book = book,
                        onOpen = { onOpenBook(book) },
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
                        onDelete = { onDeleteBook(book) }
                    )
                }
            }
        }

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
    }
}

@Composable
fun BookCard(
    book: Book,
    onOpen: () -> Unit,
    onCache: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCache, modifier = Modifier.size(28.dp)) {
                        Icon(
                            LegadoIcons.CloudDownload,
                            contentDescription = "批量缓存",
                            tint = MaterialTheme.colorScheme.primary,
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
    onDeleteSource: (BookSource) -> Unit
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
                    text = "书源管理",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "当前已导入 ${sources.size} 个 Legado 3.0 书源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

        Spacer(modifier = Modifier.height(24.dp))

        if (sources.isEmpty()) {
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
                        text = "暂无书源，点击右上角“导入书源”通过网络链接、本地文件或剪贴板导入",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sources) { source ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.bookSourceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = source.bookSourceUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!source.bookSourceGroup.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(source.bookSourceGroup ?: "") }
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onDeleteSource(source) }) {
                                    Icon(
                                        LegadoIcons.Delete,
                                        contentDescription = "删除书源",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
