package io.legado.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.desktop.data.model.Book
import io.legado.desktop.ui.theme.LegadoIcons

data class PaletteAction(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val iconEmoji: String,
    val onExecute: () -> Unit
)

@Composable
fun CommandPaletteDialog(
    books: List<Book>,
    isInReader: Boolean,
    onOpenBook: (Book) -> Unit,
    onTriggerAi: () -> Unit,
    onTriggerTts: () -> Unit,
    onTriggerExport: () -> Unit,
    onTriggerChangeSource: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSources: () -> Unit,
    onImportLocalBook: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleDualPage: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 1. Generate candidate command actions
    val baseActions = remember(isInReader) {
        val list = mutableListOf<PaletteAction>()

        if (isInReader) {
            list.add(
                PaletteAction(
                    id = "ai_assistant",
                    title = "AI 智能阅读助手 (速读/释义/人物)",
                    subtitle = "唤出当前章节大模型速读摘要、划选释义与人物谱系",
                    category = "阅读增强",
                    iconEmoji = "✨",
                    onExecute = { onTriggerAi(); onDismissRequest() }
                )
            )
            list.add(
                PaletteAction(
                    id = "edge_tts",
                    title = "微软 Edge-TTS 拟人神经语音朗读",
                    subtitle = "播放/暂停高质量云端晓晓/云希自然朗读",
                    category = "沉浸听书",
                    iconEmoji = "🎧",
                    onExecute = { onTriggerTts(); onDismissRequest() }
                )
            )
            list.add(
                PaletteAction(
                    id = "export_book",
                    title = "全书打包导出 (TXT / EPUB)",
                    subtitle = "一键打包当前书籍为精排纯文本或标准电子书",
                    category = "离线导出",
                    iconEmoji = "📦",
                    onExecute = { onTriggerExport(); onDismissRequest() }
                )
            )
            list.add(
                PaletteAction(
                    id = "change_source",
                    title = "智能一键换源",
                    subtitle = "全网并发探测同名书源，无感切换且进度自动对齐",
                    category = "书源生态",
                    iconEmoji = "🔄",
                    onExecute = { onTriggerChangeSource(); onDismissRequest() }
                )
            )
            list.add(
                PaletteAction(
                    id = "toggle_dual_page",
                    title = "切换单页 / 双页对开排版",
                    subtitle = "大屏对称双页对开仿真书籍阅读",
                    category = "排版美化",
                    iconEmoji = "📖",
                    onExecute = { onToggleDualPage(); onDismissRequest() }
                )
            )
            list.add(
                PaletteAction(
                    id = "toggle_fullscreen",
                    title = "切换全屏沉浸阅读 (F11)",
                    subtitle = "隐藏窗口边框与任务栏，极致沉浸",
                    category = "视图模式",
                    iconEmoji = "🖥️",
                    onExecute = { onToggleFullscreen(); onDismissRequest() }
                )
            )
        }

        list.add(
            PaletteAction(
                id = "search_books",
                title = "全网并发搜索新书",
                subtitle = "跳转到发现搜索页，并发聚合检索多书源",
                category = "发现与搜索",
                iconEmoji = "🔍",
                onExecute = { onNavigateToSearch(); onDismissRequest() }
            )
        )
        list.add(
            PaletteAction(
                id = "sources_diagnostic",
                title = "书源管理与并发健康测速",
                subtitle = "批量检测书源可用性、一键清理失效源",
                category = "书源生态",
                iconEmoji = "🩺",
                onExecute = { onNavigateToSources(); onDismissRequest() }
            )
        )
        list.add(
            PaletteAction(
                id = "import_book",
                title = "导入本地电子书 (.txt / .epub)",
                subtitle = "支持本地 TXT 智能分章与 EPUB 极速入库",
                category = "书架管理",
                iconEmoji = "📥",
                onExecute = { onImportLocalBook(); onDismissRequest() }
            )
        )
        list.add(
            PaletteAction(
                id = "ai_settings",
                title = "AI 大模型服务配置 (DeepSeek/通义/Kimi/Ollama)",
                subtitle = "配置 API Key 与连接测试",
                category = "系统设置",
                iconEmoji = "⚙️",
                onExecute = { onOpenAiSettings(); onDismissRequest() }
            )
        )

        list
    }

    // 2. Filter actions and books based on search query
    val query = searchQuery.trim().lowercase()
    val filteredBooks = remember(query, books) {
        if (query.isEmpty()) {
            books.take(5)
        } else {
            books.filter {
                it.name.lowercase().contains(query) || it.author.lowercase().contains(query)
            }
        }
    }

    val filteredActions = remember(query, baseActions) {
        if (query.isEmpty()) {
            baseActions
        } else {
            baseActions.filter {
                it.title.lowercase().contains(query) ||
                it.subtitle.lowercase().contains(query) ||
                it.category.lowercase().contains(query)
            }
        }
    }

    // Total selectable items count: books + actions
    val totalItems = filteredBooks.size + filteredActions.size
    var selectedIndex by remember { mutableStateOf(0) }

    LaunchedEffect(query) {
        selectedIndex = 0
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .width(580.dp)
                .heightIn(max = 520.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (totalItems > 0) {
                                    selectedIndex = (selectedIndex + 1) % totalItems
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (totalItems > 0) {
                                    selectedIndex = (selectedIndex - 1 + totalItems) % totalItems
                                }
                                true
                            }
                            Key.Enter -> {
                                if (selectedIndex < filteredBooks.size) {
                                    onOpenBook(filteredBooks[selectedIndex])
                                    onDismissRequest()
                                } else {
                                    val actionIdx = selectedIndex - filteredBooks.size
                                    filteredActions.getOrNull(actionIdx)?.onExecute?.invoke()
                                }
                                true
                            }
                            Key.Escape -> {
                                onDismissRequest()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // Search Input Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        LegadoIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("输入指令、功能或书名... (↑↓ 选择, Enter 执行, Esc 退出)", fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Scrollable List of Results
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Category: Bookshelf Books
                    if (filteredBooks.isNotEmpty()) {
                        item {
                            Text(
                                text = "📚 书架直达 (${filteredBooks.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        itemsIndexed(filteredBooks) { idx, book ->
                            val isSelected = selectedIndex == idx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .clickable {
                                        onOpenBook(book)
                                        onDismissRequest()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("📖", fontSize = 16.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = book.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${book.author.ifBlank { "未知" }} · ${book.durChapterTitle ?: "未读"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text("按 Enter 秒开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // Category: Quick Actions
                    if (filteredActions.isNotEmpty()) {
                        item {
                            Text(
                                text = "⚡ 功能与快捷指令 (${filteredActions.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        itemsIndexed(filteredActions) { actIdx, action ->
                            val globalIdx = filteredBooks.size + actIdx
                            val isSelected = selectedIndex == globalIdx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .clickable {
                                        action.onExecute()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(action.iconEmoji, fontSize = 16.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = action.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = action.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(action.category, fontSize = 10.sp) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }

                    if (totalItems == 0) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("未找到匹配的指令或书籍", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Bottom Hotkey Hints
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "💡 提示：在任意界面按 Ctrl+K 或 Ctrl+P 均可随时呼出本指挥面板",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Esc 退出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
