package io.legado.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.desktop.data.model.Book
import io.legado.desktop.engine.export.*
import io.legado.desktop.ui.theme.LegadoIcons
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun BookExportDialog(
    book: Book,
    onDismissRequest: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.EPUB) }
    var selectedScope by remember { mutableStateOf(ExportScope.ALL_FETCH_MISSING) }
    var includeBOM by remember { mutableStateOf(true) }
    var indentParagraphs by remember { mutableStateOf(true) }
    var addMetadataHeader by remember { mutableStateOf(true) }

    // Default target save path on Desktop or User Home
    val defaultDir = remember {
        val desktop = File(System.getProperty("user.home"), "Desktop")
        if (desktop.exists() && desktop.canWrite()) desktop else File(System.getProperty("user.home"))
    }
    var targetFile by remember(selectedFormat) {
        mutableStateOf(File(defaultDir, BookExportManager.getSuggestedFileName(book, selectedFormat)))
    }

    val exportProgress by BookExportManager.exportProgress.collectAsState()
    val isExporting = exportProgress != null && !exportProgress!!.isFinished && !exportProgress!!.isCancelled

    DisposableEffect(Unit) {
        onDispose {
            BookExportManager.clearProgress()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isExporting) {
                BookExportManager.clearProgress()
                onDismissRequest()
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    LegadoIcons.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "导出书籍: 《${book.name}》",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress or Complete view
                val progress = exportProgress
                if (progress != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                progress.errorMessage != null -> MaterialTheme.colorScheme.errorContainer
                                progress.isFinished -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
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
                                Text(
                                    text = when {
                                        progress.errorMessage != null -> "❌ 导出失败"
                                        progress.isCancelled -> "⚠️ 导出已取消"
                                        progress.isFinished -> "🎉 全书打包导出成功！"
                                        else -> "⏳ 正在导出书籍..."
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        progress.errorMessage != null -> MaterialTheme.colorScheme.onErrorContainer
                                        progress.isFinished -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )

                                if (!progress.isFinished && !progress.isCancelled && progress.errorMessage == null) {
                                    Text(
                                        text = "${(progress.percentage * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            if (!progress.isFinished && !progress.isCancelled && progress.errorMessage == null) {
                                LinearProgressIndicator(
                                    progress = { progress.percentage },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Text(
                                    text = progress.currentChapterTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (progress.totalChapters > 0) {
                                    Text(
                                        text = "进度: ${progress.currentChapterIndex} / ${progress.totalChapters} 章",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (progress.errorMessage != null) {
                                Text(
                                    text = progress.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            if (progress.isFinished && progress.exportedFile != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "文件已保存至:\n${progress.exportedFile.absolutePath}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilledTonalButton(
                                            onClick = { BookExportManager.openFile(progress.exportedFile) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("打开文件")
                                        }
                                        Button(
                                            onClick = { BookExportManager.revealInExplorer(progress.exportedFile) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("打开所在文件夹")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isExporting && (progress == null || progress.errorMessage != null || progress.isCancelled)) {
                    // 1. Format Selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("导出格式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ExportFormat.values().forEach { fmt ->
                                val isSelected = selectedFormat == fmt
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedFormat = fmt }
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(fmt.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { selectedFormat = fmt }
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = fmt.desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Scope Selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("导出范围", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                ExportScope.values().forEach { sc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedScope = sc }
                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedScope == sc,
                                            onClick = { selectedScope = sc }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(sc.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(sc.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Format Specific Options
                    if (selectedFormat == ExportFormat.TXT) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("排版选项", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { indentParagraphs = !indentParagraphs },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = indentParagraphs, onCheckedChange = { indentParagraphs = it })
                                Text("正文段落首行全角双空格缩进 (　　)", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { addMetadataHeader = !addMetadataHeader },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = addMetadataHeader, onCheckedChange = { addMetadataHeader = it })
                                Text("文件开头包含书名/作者/分类/简介题头", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { includeBOM = !includeBOM },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = includeBOM, onCheckedChange = { includeBOM = it })
                                Text("写入 UTF-8 BOM 标识 (推荐，防止各种外部设备乱码)", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // 4. Save Location Picker
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("保存位置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = targetFile.absolutePath,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    val dialog = FileDialog(null as Frame?, "保存导出文件", FileDialog.SAVE).apply {
                                        directory = targetFile.parent
                                        file = targetFile.name
                                        isVisible = true
                                    }
                                    val dir = dialog.directory
                                    val chosenFile = dialog.file
                                    if (dir != null && chosenFile != null) {
                                        val ext = selectedFormat.extension
                                        val finalName = if (chosenFile.endsWith(".$ext", ignoreCase = true)) chosenFile else "$chosenFile.$ext"
                                        targetFile = File(dir, finalName)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("更改...", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val progress = exportProgress
            if (isExporting) {
                OutlinedButton(
                    onClick = { BookExportManager.cancelExport() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("取消导出")
                }
            } else if (progress != null && progress.isFinished) {
                Button(
                    onClick = {
                        BookExportManager.clearProgress()
                        onDismissRequest()
                    }
                ) {
                    Text("完成")
                }
            } else {
                Button(
                    onClick = {
                        val options = ExportOptions(
                            format = selectedFormat,
                            scope = selectedScope,
                            targetFile = targetFile,
                            includeBOM = includeBOM,
                            indentParagraphs = indentParagraphs,
                            addMetadataHeader = addMetadataHeader,
                            compactBlankLines = true
                        )
                        BookExportManager.startExport(book, options)
                    }
                ) {
                    Icon(LegadoIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("开始导出")
                }
            }
        },
        dismissButton = {
            if (!isExporting && (exportProgress == null || !exportProgress!!.isFinished)) {
                TextButton(
                    onClick = {
                        BookExportManager.clearProgress()
                        onDismissRequest()
                    }
                ) {
                    Text("取消")
                }
            }
        }
    )
}
