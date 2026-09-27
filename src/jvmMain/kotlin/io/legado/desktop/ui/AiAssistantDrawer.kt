package io.legado.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.desktop.data.model.Book
import io.legado.desktop.engine.ai.*
import io.legado.desktop.ui.theme.LegadoIcons
import kotlinx.coroutines.launch

@Composable
fun AiAssistantDrawer(
    book: Book,
    chapterTitle: String,
    chapterContent: String,
    selectedText: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var aiConfig by remember { mutableStateOf<AiConfig?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val messages = remember { mutableStateListOf<AiMessage>() }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load AI config on inception
    LaunchedEffect(Unit) {
        aiConfig = AiAssistantEngine.loadConfig()
        if (messages.isEmpty()) {
            messages.add(
                AiMessage(
                    role = "assistant",
                    content = "你好！我是你的 AI 读书助手。我可以为你进行当前章节速读、深度解析文言生僻词句、梳理登场人物谱系，或者解答你对《${book.name}》的任何情节疑问。"
                )
            )
        }
    }

    // Auto trigger explanation if opened with selected text
    LaunchedEffect(selectedText) {
        if (!selectedText.isNullOrBlank() && aiConfig != null) {
            val queryText = selectedText.trim()
            inputText = ""
            messages.add(AiMessage(role = "user", content = "请解释词句：「$queryText」"))
            isLoading = true
            errorMessage = null
            scope.launch {
                try {
                    val result = AiAssistantEngine.explainSelection(queryText, chapterContent, aiConfig!!)
                    messages.add(AiMessage(role = "assistant", content = result))
                    listState.animateScrollToItem(messages.size - 1)
                } catch (e: Exception) {
                    errorMessage = e.message
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Surface(
        modifier = modifier
            .width(400.dp)
            .fillMaxHeight()
            .shadow(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            LegadoIcons.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI 智能阅读助手",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = aiConfig?.let { "${it.provider.displayName} (${it.model})" } ?: "正在加载...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(LegadoIcons.Settings, contentDescription = "大模型设置", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            messages.clear()
                            messages.add(
                                AiMessage(
                                    role = "assistant",
                                    content = "已清空对话记录。请随时点击上方快捷功能或直接向我提问！"
                                )
                            )
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(LegadoIcons.Delete, contentDescription = "清空对话", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(LegadoIcons.Close, contentDescription = "关闭抽屉", modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider()

            // 2. Quick Action Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Summary
                AssistChip(
                    onClick = {
                        val cfg = aiConfig ?: return@AssistChip
                        messages.add(AiMessage(role = "user", content = "⚡ 请对本章《$chapterTitle》进行速读摘要"))
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val reply = AiAssistantEngine.summarizeChapter(chapterTitle, chapterContent, cfg)
                                messages.add(AiMessage(role = "assistant", content = reply))
                                listState.animateScrollToItem(messages.size - 1)
                            } catch (e: Exception) {
                                errorMessage = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading && chapterContent.isNotBlank(),
                    label = { Text("⚡ 章节速读", fontSize = 12.sp) }
                )

                // Quick Characters
                AssistChip(
                    onClick = {
                        val cfg = aiConfig ?: return@AssistChip
                        messages.add(AiMessage(role = "user", content = "👥 请梳理本章《$chapterTitle》登场人物与势力谱系"))
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val reply = AiAssistantEngine.extractCharacters(chapterTitle, chapterContent, cfg)
                                messages.add(AiMessage(role = "assistant", content = reply))
                                listState.animateScrollToItem(messages.size - 1)
                            } catch (e: Exception) {
                                errorMessage = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    enabled = !isLoading && chapterContent.isNotBlank(),
                    label = { Text("👥 人物谱系", fontSize = 12.sp) }
                )

                // Quick Selection Gloss
                if (!selectedText.isNullOrBlank()) {
                    AssistChip(
                        onClick = {
                            val cfg = aiConfig ?: return@AssistChip
                            val query = selectedText.trim()
                            messages.add(AiMessage(role = "user", content = "🔍 释义选中文本: 「$query」"))
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val reply = AiAssistantEngine.explainSelection(query, chapterContent, cfg)
                                    messages.add(AiMessage(role = "assistant", content = reply))
                                    listState.animateScrollToItem(messages.size - 1)
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading,
                        label = { Text("🔍 划选释义", fontSize = 12.sp) }
                    )
                }
            }

            // 3. Message Stream List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role.equals("user", ignoreCase = true)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            if (!isUser) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        LegadoIcons.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            }

                            Card(
                                shape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (isUser) 12.dp else 2.dp,
                                    bottomEnd = if (isUser) 2.dp else 12.dp
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    }
                                ),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Text(
                                    text = msg.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    if (isLoading) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 36.dp, top = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "AI 正在分析剧情并组织回答...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Error alert banner if any
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { errorMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(LegadoIcons.Close, contentDescription = "关闭", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            HorizontalDivider()

            // 4. Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("向 AI 询问本章剧情、伏笔或人物...", fontSize = 13.sp) },
                    maxLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        val cfg = aiConfig
                        if (text.isNotBlank() && cfg != null && !isLoading) {
                            messages.add(AiMessage(role = "user", content = text))
                            inputText = ""
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val reply = AiAssistantEngine.chatWithContext(
                                        chapterTitle = chapterTitle,
                                        content = chapterContent,
                                        history = messages.dropLast(1),
                                        userQuestion = text,
                                        config = cfg
                                    )
                                    messages.add(AiMessage(role = "assistant", content = reply))
                                    listState.animateScrollToItem(messages.size - 1)
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = inputText.isNotBlank() && !isLoading && aiConfig != null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        LegadoIcons.PlayArrow,
                        contentDescription = "发送提问",
                        tint = if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Settings Dialog Mount
    if (showSettingsDialog) {
        AiSettingsDialog(
            initialConfig = aiConfig,
            onDismissRequest = { showSettingsDialog = false },
            onSaved = { saved ->
                aiConfig = saved
            }
        )
    }
}
