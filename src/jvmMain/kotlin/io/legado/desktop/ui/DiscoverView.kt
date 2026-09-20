package io.legado.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.engine.explore.ExploreEngine
import io.legado.desktop.engine.explore.ExploreKind
import io.legado.desktop.ui.theme.LegadoIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverView(
    sources: List<BookSource>,
    onOpenBook: (Book) -> Unit,
    onBookAddedToShelf: (Book) -> Unit
) {
    val scope = rememberCoroutineScope()
    var activeSubTab by remember { mutableStateOf(0) } // 0: 书源发现, 1: 全网搜索

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "发现",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (activeSubTab == 0) "浏览书源分类与热门榜单" else "毫秒级多书源跨库检索",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mode Selector
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = activeSubTab == 0,
                    onClick = { activeSubTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Icon(LegadoIcons.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分类榜单")
                }
                SegmentedButton(
                    selected = activeSubTab == 1,
                    onClick = { activeSubTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Icon(LegadoIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("全网搜索")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (activeSubTab == 1) {
            SearchView(
                onOpenBook = onOpenBook,
                onBookAddedToShelf = onBookAddedToShelf
            )
        } else {
            ExploreSourcePanel(
                sources = sources,
                onOpenBook = onOpenBook,
                onBookAddedToShelf = onBookAddedToShelf
            )
        }
    }
}

@Composable
private fun ExploreSourcePanel(
    sources: List<BookSource>,
    onOpenBook: (Book) -> Unit,
    onBookAddedToShelf: (Book) -> Unit
) {
    val scope = rememberCoroutineScope()
    val exploreSources = remember(sources) {
        sources.filter { it.enabled && !it.exploreUrl.isNullOrBlank() }
    }

    if (exploreSources.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    LegadoIcons.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = "暂无包含「发现」规则的可用书源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "请前往「书源」管理页面导入带有 exploreUrl 规则的书源",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var selectedSourceIndex by remember { mutableStateOf(0) }
    val currentSource = exploreSources.getOrNull(selectedSourceIndex) ?: exploreSources.first()

    val kinds = remember(currentSource) {
        ExploreEngine.parseExploreKinds(currentSource.exploreUrl)
    }

    var selectedKindIndex by remember(currentSource) { mutableStateOf(0) }
    val currentKind = kinds.getOrNull(selectedKindIndex)

    var currentPage by remember(currentSource, selectedKindIndex) { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val books = remember { mutableStateListOf<Book>() }

    // Load explore books on change
    fun loadExplore(page: Int) {
        val kind = currentKind ?: return
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val result = ExploreEngine.explore(currentSource, kind.url, page)
                books.clear()
                books.addAll(result)
                if (result.isEmpty()) {
                    errorMessage = "该分类暂未解析到书籍内容"
                }
            } catch (e: Exception) {
                errorMessage = "请求发现失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(currentSource, selectedKindIndex, currentPage) {
        loadExplore(currentPage)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Source and Category Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source Dropdown Selector
            var sourceMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { sourceMenuExpanded = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(LegadoIcons.LibraryBooks, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(currentSource.bookSourceName, fontWeight = FontWeight.Medium)
                }

                DropdownMenu(
                    expanded = sourceMenuExpanded,
                    onDismissRequest = { sourceMenuExpanded = false }
                ) {
                    exploreSources.forEachIndexed { index, src ->
                        DropdownMenuItem(
                            text = { Text(src.bookSourceName) },
                            onClick = {
                                selectedSourceIndex = index
                                selectedKindIndex = 0
                                currentPage = 1
                                sourceMenuExpanded = false
                            },
                            leadingIcon = {
                                if (index == selectedSourceIndex) {
                                    Icon(LegadoIcons.Check, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            }

            // Category Tags Row
            if (kinds.isNotEmpty()) {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier.weight(1f).horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    kinds.forEachIndexed { index, kind ->
                        FilterChip(
                            selected = index == selectedKindIndex,
                            onClick = {
                                selectedKindIndex = index
                                currentPage = 1
                            },
                            label = { Text(kind.title) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Content Area
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载分类推荐书籍...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (errorMessage != null && books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(books) { book ->
                        DiscoverBookCard(
                            book = book,
                            onOpenBook = { onOpenBook(book) },
                            onAddToShelf = {
                                scope.launch {
                                    AppDatabase.insertOrUpdateBook(book)
                                    onBookAddedToShelf(book)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Pagination Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { if (currentPage > 1) currentPage-- },
                enabled = currentPage > 1 && !isLoading
            ) {
                Icon(LegadoIcons.NavigateBefore, contentDescription = null)
                Text("上一页")
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = "第 $currentPage 页",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.width(16.dp))

            OutlinedButton(
                onClick = { currentPage++ },
                enabled = books.isNotEmpty() && !isLoading
            ) {
                Text("下一页")
                Icon(LegadoIcons.NavigateNext, contentDescription = null)
            }
        }
    }
}

@Composable
fun DiscoverBookCard(
    book: Book,
    onOpenBook: () -> Unit,
    onAddToShelf: () -> Unit
) {
    var added by remember { mutableStateOf(false) }

    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Book Cover
                Surface(
                    modifier = Modifier.size(width = 64.dp, height = 86.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            LegadoIcons.Book,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Book Details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "作者: ${book.author.ifBlank { "佚名" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!book.kind.isNullOrBlank()) {
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = book.kind!!.take(8),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }

                    if (!book.latestChapterTitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "最新: ${book.latestChapterTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!book.intro.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = book.intro!!.replace("\n", " ").trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onAddToShelf()
                        added = true
                    },
                    enabled = !added,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        if (added) LegadoIcons.Check else LegadoIcons.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (added) "已在书架" else "加书架")
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onOpenBook,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(LegadoIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("立即阅读")
                }
            }
        }
    }
}
