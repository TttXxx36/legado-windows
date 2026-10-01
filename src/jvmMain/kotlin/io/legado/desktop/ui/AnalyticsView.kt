package io.legado.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.desktop.data.db.BookReadingStat
import io.legado.desktop.engine.analytics.AnalyticsSummary
import io.legado.desktop.engine.analytics.ReadingAnalyticsEngine
import io.legado.desktop.ui.theme.LegadoIcons
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AnalyticsView(
    onOpenBook: (bookUrl: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf(AnalyticsSummary()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            isLoading = true
            try {
                summary = ReadingAnalyticsEngine.getSummary()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Header Title & Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = LegadoIcons.BarChart,
                        contentDescription = "统计看板",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "阅读数据与习惯看板",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "记录每一次阅读心流，见证点滴书海积累",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { refresh() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = LegadoIcons.Refresh,
                    contentDescription = "刷新",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("刷新统计")
            }
        }

        // 2. 4 Major KPI Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            KpiCard(
                title = "今日专注",
                value = ReadingAnalyticsEngine.formatDuration(summary.todaySeconds),
                subtext = "每日持续阅读好习惯",
                iconEmoji = "⏱️",
                cardColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "累计阅读",
                value = ReadingAnalyticsEngine.formatDuration(summary.totalSeconds),
                subtext = "在书海中漫游的总时长",
                iconEmoji = "📖",
                cardColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "阅读字数",
                value = ReadingAnalyticsEngine.formatWords(summary.totalWords),
                subtext = "字字珠玑，沉淀见识",
                iconEmoji = "✍️",
                cardColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "连续打卡",
                value = "${summary.streakDays} 天",
                subtext = if (summary.streakDays > 0) "保持连胜，继续加油！" else "今天从翻开一页开始",
                iconEmoji = "🔥",
                cardColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        // 3. 365-day Reading Heatmap Card
        ReadingHeatmapSection(
            heatmapData = summary.heatmapData
        )

        // 4. Reading Habit Time Distribution & Top Books Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time Distribution Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "🕒 24小时阅读时段分布",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "了解自己在一天中最放松专注的黄金阅读时刻",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    summary.timeSlotDistribution.forEach { (slot, ratio) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(slot, style = MaterialTheme.typography.bodySmall)
                                Text("${(ratio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }

            // Top Books Card
            Card(
                modifier = Modifier.weight(1.2f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🏆 最长相伴书籍榜单",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "记录你投入心力最多的挚爱读物",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (summary.topBooks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "暂无阅读记录，快去书架挑一本开始阅读吧！",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        summary.topBooks.forEachIndexed { index, stat ->
                            TopBookItem(
                                rank = index + 1,
                                stat = stat,
                                onOpenBook = { onOpenBook(stat.bookUrl) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtext: String,
    iconEmoji: String,
    cardColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = iconEmoji,
                    fontSize = 20.sp
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReadingHeatmapSection(
    heatmapData: Map<String, Int>
) {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today = LocalDate.now()
    var hoveredDateInfo by remember { mutableStateOf<Pair<String, Int>?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📅 365天阅读活跃全景热力图",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = hoveredDateInfo?.let { (d, sec) ->
                            "$d: 阅读 ${ReadingAnalyticsEngine.formatDuration(sec)}"
                        } ?: "点亮每一格绿意，见证每一次专注阅读（悬停可查看具体日期）",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hoveredDateInfo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (hoveredDateInfo != null) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HeatmapLegendCell(Color.LightGray.copy(alpha = 0.3f))
                    HeatmapLegendCell(Color(0xFF81C784))
                    HeatmapLegendCell(Color(0xFF4CAF50))
                    HeatmapLegendCell(Color(0xFF2E7D32))
                    Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 52 columns x 7 days grid
            // Find start date: today minus 52 weeks (364 days), adjusted to start on Monday
            val startDay = today.minusDays(363) // 52 weeks * 7 = 364 days total
            val weeks = 52
            val daysInWeek = 7

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Day of week labels on left
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Box(modifier = Modifier.size(13.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Heatmap Grid: 52 columns
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (w in 0 until weeks) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (d in 0 until daysInWeek) {
                                val cellDate = startDay.plusDays((w * 7 + d).toLong())
                                if (cellDate.isAfter(today)) {
                                    Box(modifier = Modifier.size(13.dp))
                                } else {
                                    val dateStr = cellDate.format(dateFormatter)
                                    val seconds = heatmapData[dateStr] ?: 0
                                    val cellColor = when {
                                        seconds <= 0 -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                        seconds < 900 -> Color(0xFF81C784) // < 15m
                                        seconds < 2700 -> Color(0xFF4CAF50) // 15m - 45m
                                        else -> Color(0xFF2E7D32) // > 45m
                                    }

                                    val interactionSource = remember { MutableInteractionSource() }
                                    val isHovered by interactionSource.collectIsHoveredAsState()

                                    LaunchedEffect(isHovered) {
                                        if (isHovered) {
                                            hoveredDateInfo = Pair(dateStr, seconds)
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(cellColor)
                                            .hoverable(interactionSource)
                                            .clickable {
                                                hoveredDateInfo = Pair(dateStr, seconds)
                                            }
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

@Composable
private fun HeatmapLegendCell(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
}

@Composable
private fun TopBookItem(
    rank: Int,
    stat: BookReadingStat,
    onOpenBook: () -> Unit
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFB300)
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpenBook() }
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(rankColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    color = rankColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {
                Text(
                    text = stat.bookName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "已读 ${ReadingAnalyticsEngine.formatWords(stat.totalWords.toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = ReadingAnalyticsEngine.formatDuration(stat.totalSeconds),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            TextButton(
                onClick = onOpenBook,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("阅读", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
