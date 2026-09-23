package io.legado.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import io.legado.desktop.ui.theme.LegadoIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.engine.BookSourceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

import io.legado.desktop.engine.search.AggregatedBook
import io.legado.desktop.engine.search.SearchRelevanceEngine
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun SearchView(
    onOpenBook: (Book) -> Unit,
    onBookAddedToShelf: (Book) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchKeyword by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchStatus by remember { mutableStateOf<String?>(null) }
    val searchResults = remember { mutableStateListOf<AggregatedBook>() }

    fun performSearch() {
        if (searchKeyword.isBlank() || isSearching) return
        isSearching = true
        searchResults.clear()
        searchStatus = "正在检索已配置书源..."

        scope.launch {
            val sources = AppDatabase.getAllBookSources().filter { it.enabled && !it.searchUrl.isNullOrBlank() }
            if (sources.isEmpty()) {
                searchStatus = "未找到可用的书源，请先前往“书源”页面导入 Legado 3.0 书源！"
                isSearching = false
                return@launch
            }

            searchStatus = "正在并发检索 ${sources.size} 个书源..."
            val deferred = sources.map { source ->
                async(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(6000L) {
                            BookSourceEngine.search(source, searchKeyword)
                        } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }

            val rawResults = deferred.awaitAll().flatten()
            val aggregated = SearchRelevanceEngine.aggregateSearchResults(rawResults, searchKeyword)
            searchResults.clear()
            searchResults.addAll(aggregated)
            isSearching = false
            searchStatus = if (aggregated.isEmpty()) "未找到相关书籍" else "共检索到 ${aggregated.size} 本书籍（已智能聚合多书源，按相关度排序）"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "发现与搜索",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "多书源并发检索，聚合去重与在线章节发现",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                placeholder = { Text("输入书名或作者...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (searchKeyword.isNotEmpty()) {
                        IconButton(onClick = { searchKeyword = "" }) {
                            Icon(LegadoIcons.Clear, contentDescription = "清空")
                        }
                    }
                }
            )

            Button(
                onClick = { performSearch() },
                enabled = searchKeyword.isNotBlank() && !isSearching,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(56.dp)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(LegadoIcons.Search, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text("搜索")
            }
        }

        if (searchStatus != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = searchStatus ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (searchResults.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "输入书名搜索网络在线书籍，支持直接一键加入书架并阅读",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(searchResults) { aggregated ->
                    SearchResultCard(
                        aggregatedBook = aggregated,
                        onAddToShelf = { book ->
                            scope.launch {
                                AppDatabase.insertOrUpdateBook(book)
                                onBookAddedToShelf(book)
                            }
                        },
                        onReadNow = { book ->
                            scope.launch {
                                AppDatabase.insertOrUpdateBook(book)
                                onOpenBook(book)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    aggregatedBook: AggregatedBook,
    onAddToShelf: (Book) -> Unit,
    onReadNow: (Book) -> Unit
) {
    var selectedBook by remember(aggregatedBook) { mutableStateOf(aggregatedBook.primaryBook) }
    var added by remember(selectedBook) { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            LegadoIcons.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedBook.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedBook.originName.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    if (aggregatedBook.sourceCount > 1) {
                                        showSourcePicker = true
                                    }
                                },
                                label = { Text(selectedBook.originName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (aggregatedBook.sourceCount > 1) {
                            FilterChip(
                                selected = false,
                                onClick = { showSourcePicker = true },
                                label = { Text("${aggregatedBook.sourceCount} 个可用书源", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = {
                                    Icon(LegadoIcons.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                    Text(
                        text = "作者: ${selectedBook.author.ifBlank { "未知" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!selectedBook.latestChapterTitle.isNullOrBlank()) {
                        Text(
                            text = "最新: ${selectedBook.latestChapterTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onAddToShelf(selectedBook)
                        added = true
                    },
                    enabled = !added
                ) {
                    Icon(if (added) LegadoIcons.Check else LegadoIcons.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (added) "已在书架" else "加书架")
                }

                Button(onClick = { onReadNow(selectedBook) }) {
                    Icon(LegadoIcons.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("阅读")
                }
            }
        }
    }

    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            title = {
                Text(
                    text = "选择书源 (${selectedBook.name})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(aggregatedBook.candidateSources) { candidate ->
                        val isCurrent = candidate.bookUrl == selectedBook.bookUrl
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBook = candidate
                                    showSourcePicker = false
                                }
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
                                        text = candidate.originName.ifBlank { "未知书源" },
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (!candidate.latestChapterTitle.isNullOrBlank()) {
                                        Text(
                                            text = "最新: ${candidate.latestChapterTitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isCurrent) {
                                    Icon(
                                        LegadoIcons.Check,
                                        contentDescription = "当前选中",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcePicker = false }) {
                    Text("关闭")
                }
            }
        )
    }
}
