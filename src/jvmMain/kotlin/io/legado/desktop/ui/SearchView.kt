package io.legado.desktop.ui

import androidx.compose.animation.AnimatedVisibility
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

@Composable
fun SearchView(
    onOpenBook: (Book) -> Unit,
    onBookAddedToShelf: (Book) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchKeyword by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchStatus by remember { mutableStateOf<String?>(null) }
    val searchResults = remember { mutableStateListOf<Book>() }

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

            searchStatus = "正在同时请求 ${sources.size} 个书源..."
            val deferred = sources.map { source ->
                async(Dispatchers.IO) {
                    try {
                        BookSourceEngine.search(source, searchKeyword)
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }

            val results = deferred.awaitAll().flatten()
            searchResults.addAll(results)
            isSearching = false
            searchStatus = if (results.isEmpty()) "未找到相关书籍" else "共检索到 ${results.size} 本书籍"
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
                items(searchResults) { book ->
                    SearchResultCard(
                        book = book,
                        onAddToShelf = {
                            scope.launch {
                                AppDatabase.insertOrUpdateBook(book)
                                onBookAddedToShelf(book)
                            }
                        },
                        onReadNow = {
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
    book: Book,
    onAddToShelf: () -> Unit,
    onReadNow: () -> Unit
) {
    var added by remember { mutableStateOf(false) }

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

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = book.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (book.originName.isNotBlank()) {
                            AssistChip(
                                onClick = {},
                                label = { Text(book.originName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Text(
                        text = "作者: ${book.author.ifBlank { "未知" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!book.latestChapterTitle.isNullOrBlank()) {
                        Text(
                            text = "最新: ${book.latestChapterTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onAddToShelf()
                        added = true
                    },
                    enabled = !added
                ) {
                    Icon(if (added) LegadoIcons.Check else LegadoIcons.BookmarkAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (added) "已在书架" else "加书架")
                }

                Button(onClick = onReadNow) {
                    Icon(LegadoIcons.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("阅读")
                }
            }
        }
    }
}
