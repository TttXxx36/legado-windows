package io.legado.desktop.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.data.model.ReplaceRule
import io.legado.desktop.data.model.WebDavConfig
import io.legado.desktop.engine.BookSourceEngine
import io.legado.desktop.engine.sync.WebDavSync
import io.legado.desktop.server.LegadoWebServer
import java.awt.Desktop
import java.net.URI
import kotlinx.coroutines.launch

enum class NavDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    BOOKSHELF("书架", Icons.Filled.Book, Icons.Outlined.Book),
    DISCOVER("发现", Icons.Filled.Explore, Icons.Outlined.Explore),
    SOURCES("书源", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks),
    SETTINGS("设置", Icons.Filled.Settings, Icons.Outlined.Settings)
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
                            imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
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
                            }
                        )
                        NavDestination.DISCOVER -> SearchView(
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
            }
        }
    }
}

@Composable
fun BookshelfView(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onDeleteBook: (Book) -> Unit
) {
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { /* TODO: 打开文件选择器 */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导入本地书籍")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                        onDelete = { onDeleteBook(book) }
                    )
                }
            }
        }
    }
}

@Composable
fun BookCard(
    book: Book,
    onOpen: () -> Unit,
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
                        Icons.AutoMirrored.Filled.MenuBook,
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
            Text(
                text = book.author.ifBlank { "未知作者" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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

@Composable
fun SourcesView(
    sources: List<BookSource>,
    onAddSources: (List<BookSource>) -> Unit,
    onDeleteSource: (BookSource) -> Unit
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

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

            Button(onClick = { showImportDialog = true }) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        Icons.AutoMirrored.Filled.LibraryBooks,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "暂无书源，点击右上角“导入书源”粘贴 Legado 3.0 JSON",
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
                                        Icons.Default.Delete,
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
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入 Legado 3.0 书源") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "粘贴 Legado 书源 JSON 内容（支持单个或数组）：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            importError = null
                        },
                        placeholder = { Text("[ { \"bookSourceName\": \"示例书源\", ... } ]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        isError = importError != null
                    )
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
                    onClick = {
                        val parsed = BookSourceEngine.parseBookSources(importJsonText)
                        if (parsed.isEmpty()) {
                            importError = "未能识别有效的书源 JSON，请检查格式"
                        } else {
                            onAddSources(parsed)
                            showImportDialog = false
                            importJsonText = ""
                        }
                    }
                ) {
                    Text("确认导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
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

    LaunchedEffect(Unit) {
        val config = AppDatabase.getWebDavConfig()
        webDavUrl = config.url
        webDavUser = config.username
        webDavPass = config.password
        webDavDir = config.rootDir

        val loadedRules = AppDatabase.getReplaceRules()
        replaceRules.clear()
        replaceRules.addAll(loadedRules)
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
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
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

        // 3. Text Clean & Replace Rules Card
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
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
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
                    replaceRules.forEach { rule ->
                        ElevatedCard(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "表达式: ${rule.pattern} → 替换: '${rule.replacement}'",
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
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                AppDatabase.deleteReplaceRule(rule.id)
                                                replaceRules.remove(rule)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
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
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("在浏览器中打开")
                        }
                    }
                }
            }
        }

        // 5. Local Database Storage Card
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
                    text = "数据库路径: %APPDATA%\\LegadoDesktop\\legado.db\n存储引擎: SQLite 3 (JDBC Direct Connection)\n包含模块: 书籍、章节、书源、书签笔记、替换净化、应用配置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 5. About Card
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
                    text = "版本: 1.1.0 (Phase 2 全量集成)\n技术架构: Compose Multiplatform + Material Design 3 + Windows SAPI\n移植自: HapeLee/legado-with-MD3 & gedoor/legado\n开源协议: GPL-3.0",
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
