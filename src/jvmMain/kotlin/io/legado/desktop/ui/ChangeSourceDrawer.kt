package io.legado.desktop.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.engine.BookSourceEngine
import io.legado.desktop.engine.align.AlignmentResult
import io.legado.desktop.engine.align.ChapterAlignmentEngine
import io.legado.desktop.ui.theme.LegadoIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class SourceHistoryRecord(
    val origin: String,
    val originName: String,
    val tocUrl: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CandidateSourceUiModel(
    val book: Book,
    val latencyMs: Long,
    val source: BookSource,
    val totalChapters: Int = 0,
    var chapters: List<BookChapter>? = null,
    var alignmentResult: AlignmentResult? = null,
    var isAligning: Boolean = false,
    var previewContent: String? = null,
    var isPreviewing: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeSourceDrawer(
    visible: Boolean,
    book: Book,
    currentChapterTitle: String,
    currentChapterIndex: Int,
    previousSource: SourceHistoryRecord?,
    onClose: () -> Unit,
    onRollback: () -> Unit,
    onSelectSource: (targetBook: Book, targetSource: BookSource, alignedChapterIndex: Int, newChapters: List<BookChapter>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSearching by remember { mutableStateOf(false) }
    var totalSourcesCount by remember { mutableStateOf(0) }
    var completedSourcesCount by remember { mutableStateOf(0) }
    val candidates = remember { mutableStateListOf<CandidateSourceUiModel>() }

    // Manual Chapter Picker Dialog
    var manualPickerModel by remember { mutableStateOf<CandidateSourceUiModel?>(null) }
    var manualPickerSearchQuery by remember { mutableStateOf("") }

    // Start streaming search whenever drawer becomes visible
    LaunchedEffect(visible, book.bookUrl) {
        if (!visible) return@LaunchedEffect

        isSearching = true
        completedSourcesCount = 0
        candidates.clear()

        val allSources = AppDatabase.getAllBookSources().filter { it.enabled && !it.searchUrl.isNullOrBlank() }
        totalSourcesCount = allSources.size

        val cleanBookName = book.name.trim().replace("""[《》【】\[\]\s]""".toRegex(), "")

        allSources.forEach { source ->
            scope.launch(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                try {
                    val searchResult = withTimeoutOrNull(6000L) {
                        BookSourceEngine.search(source, book.name)
                    } ?: emptyList()
                    val latency = System.currentTimeMillis() - startTime

                    val matched = searchResult.filter {
                        it.name.trim().replace("""[《》【】\[\]\s]""".toRegex(), "").equals(cleanBookName, ignoreCase = true)
                    }

                    if (matched.isNotEmpty()) {
                        matched.forEach { matchedBook ->
                            val uiModel = CandidateSourceUiModel(
                                book = matchedBook,
                                latencyMs = latency,
                                source = source
                            )
                            withContext(Dispatchers.Main) {
                                // Insert maintaining latency sorting (with current source at top)
                                val isCurrent = matchedBook.origin == book.origin
                                if (isCurrent) {
                                    candidates.add(0, uiModel)
                                } else {
                                    val currentCount = candidates.count { it.book.origin == book.origin }
                                    var insertIdx = candidates.size
                                    for (i in currentCount until candidates.size) {
                                        if (latency < candidates[i].latencyMs) {
                                            insertIdx = i
                                            break
                                        }
                                    }
                                    candidates.add(insertIdx, uiModel)
                                }
                            }

                            // Asynchronously fetch chapters and compute alignment
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val chs = BookSourceEngine.getChapters(source, matchedBook)
                                    if (chs.isNotEmpty()) {
                                        val align = ChapterAlignmentEngine.alignChapter(
                                            currentTitle = currentChapterTitle,
                                            currentIndex = currentChapterIndex,
                                            candidates = chs
                                        )
                                        withContext(Dispatchers.Main) {
                                            uiModel.chapters = chs
                                            uiModel.alignmentResult = align
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    withContext(Dispatchers.Main) {
                        completedSourcesCount++
                        if (completedSourcesCount >= totalSourcesCount) {
                            isSearching = false
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(440.dp)
                .shadow(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                LegadoIcons.SwapHoriz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "智能换源与对齐",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "当前阅读: $currentChapterTitle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(LegadoIcons.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Rollback banner if previous source is available
                if (previousSource != null && previousSource.origin != book.origin) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onRollback() },
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "⏪ 一键切回上一书源",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "${previousSource.originName} · 第 ${previousSource.chapterIndex + 1} 章 (${previousSource.chapterTitle})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            FilledTonalButton(
                                onClick = onRollback,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("立即撤销", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Streaming search status banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isSearching) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text(
                                    "全网流式检索中: $completedSourcesCount / $totalSourcesCount 书源...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Text(
                                "检索完成：共发现 ${candidates.size} 个可用源站",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            "按延迟升序",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Candidates List
                if (candidates.isEmpty() && !isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "全网暂未检索到其他同名可用书源",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(candidates, key = { it.book.origin + it.book.bookUrl }) { item ->
                            val isCurrent = item.book.origin == book.origin

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = if (isCurrent)
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                else
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Row 1: Source Name, Badges & Latency
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = item.book.originName.ifBlank { "未知书源" },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isCurrent) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                ) {
                                                    Text(
                                                        "当前在用",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Latency Badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                item.latencyMs < 600 -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                                item.latencyMs < 1500 -> Color(0xFFED6C02).copy(alpha = 0.15f)
                                                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                            }
                                        ) {
                                            Text(
                                                text = "${item.latencyMs}ms",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = when {
                                                    item.latencyMs < 600 -> Color(0xFF2E7D32)
                                                    item.latencyMs < 1500 -> Color(0xFFED6C02)
                                                    else -> MaterialTheme.colorScheme.error
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    // Latest Chapter Info
                                    if (!item.book.latestChapterTitle.isNullOrBlank()) {
                                        Text(
                                            text = "最新: ${item.book.latestChapterTitle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(Modifier.height(6.dp))

                                    // Alignment info banner
                                    val align = item.alignmentResult
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (align != null && align.confidence >= 0.8f)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            if (align == null) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                                    Text("正在对齐目录...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                            } else {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (align.confidence >= 0.8f)
                                                            "✅ 对齐: 第 ${align.index + 1} 章 · ${align.chapter.title}"
                                                        else
                                                            "⚠️ 相似度较低: 第 ${align.index + 1} 章 (${align.chapter.title})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (align.confidence >= 0.8f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${align.strategy} (置信度 ${(align.confidence * 100).toInt()}%)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (item.chapters != null) {
                                                TextButton(
                                                    onClick = {
                                                        manualPickerModel = item
                                                        manualPickerSearchQuery = ""
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(24.dp)
                                                ) {
                                                    Text("微调选章", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }

                                    // Preview content expandable section
                                    if (item.previewContent != null) {
                                        Spacer(Modifier.height(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "正文片段即时质检 (前 300 字):",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    IconButton(
                                                        onClick = { item.previewContent = null },
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        Icon(LegadoIcons.Close, contentDescription = "收起", modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = item.previewContent!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 6,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Actions row: Quick Preview & Switch
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Quick Preview Button
                                        OutlinedButton(
                                            onClick = {
                                                if (item.previewContent != null) {
                                                    item.previewContent = null
                                                    return@OutlinedButton
                                                }
                                                val targetChapter = item.alignmentResult?.chapter
                                                if (targetChapter != null) {
                                                    scope.launch {
                                                        item.isPreviewing = true
                                                        try {
                                                            val raw = withContext(Dispatchers.IO) {
                                                                withTimeoutOrNull(5000L) {
                                                                    BookSourceEngine.getContent(item.source, item.book, targetChapter)
                                                                }
                                                            } ?: "（预览超时，书源响应缓慢）"
                                                            val snippet = raw.trim().replace("""\s+""".toRegex(), " ").take(300)
                                                            item.previewContent = if (snippet.isBlank()) "（该章节正文为空或需要会员校验）" else "$snippet..."
                                                        } catch (e: Exception) {
                                                            item.previewContent = "（预览加载失败: ${e.message}）"
                                                        } finally {
                                                            item.isPreviewing = false
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = item.alignmentResult != null && !item.isPreviewing,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            if (item.isPreviewing) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(if (item.previewContent != null) "收起预览" else "👁️ 预览正文", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Spacer(Modifier.width(8.dp))

                                        // Switch button
                                        Button(
                                            onClick = {
                                                val targetChs = item.chapters ?: emptyList()
                                                val alignedIdx = item.alignmentResult?.index ?: currentChapterIndex
                                                onSelectSource(item.book, item.source, alignedIdx, targetChs)
                                            },
                                            enabled = !isCurrent && item.chapters != null,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(if (isCurrent) "当前书源" else "切换此源", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Chapter Selection Dialog
    if (manualPickerModel != null && manualPickerModel?.chapters != null) {
        val targetModel = manualPickerModel!!
        val chList = targetModel.chapters!!

        val filteredChapters = remember(chList, manualPickerSearchQuery) {
            if (manualPickerSearchQuery.isBlank()) chList
            else chList.filter { it.title.contains(manualPickerSearchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { manualPickerModel = null },
            title = {
                Text("手动对齐章节 - 《${targetModel.book.originName}》", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    OutlinedTextField(
                        value = manualPickerSearchQuery,
                        onValueChange = { manualPickerSearchQuery = it },
                        placeholder = { Text("搜索章节序号或标题...") },
                        leadingIcon = { Icon(LegadoIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("共 ${filteredChapters.size} 个章节，点击选定为阅读当前章：", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(filteredChapters) { ch ->
                            val rawIdx = chList.indexOf(ch)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        targetModel.alignmentResult = AlignmentResult(
                                            index = rawIdx,
                                            chapter = ch,
                                            confidence = 1.0f,
                                            strategy = "用户手动校准"
                                        )
                                        manualPickerModel = null
                                    }
                            ) {
                                Text(
                                    text = "第 ${rawIdx + 1} 章 · ${ch.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { manualPickerModel = null }) {
                    Text("取消")
                }
            }
        )
    }
}
