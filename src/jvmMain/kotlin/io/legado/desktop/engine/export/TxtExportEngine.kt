package io.legado.desktop.engine.export

import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TxtExportEngine {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Format a chapter's content with standard Chinese typography rules:
     * - Normalize whitespace
     * - Optional 2 full-width spaces indent (　　)
     * - Compact multiple blank lines into at most one blank line
     */
    fun formatChapterContent(
        rawContent: String,
        indentParagraphs: Boolean = true,
        compactBlankLines: Boolean = true
    ): String {
        val lines = rawContent.lines()
        val resultLines = mutableListOf<String>()
        var lastWasBlank = false

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                if (compactBlankLines) {
                    if (!lastWasBlank) {
                        resultLines.add("")
                        lastWasBlank = true
                    }
                } else {
                    resultLines.add("")
                }
            } else {
                lastWasBlank = false
                val lineToAdd = if (indentParagraphs) {
                    "　　$trimmed"
                } else {
                    trimmed
                }
                resultLines.add(lineToAdd)
            }
        }

        return resultLines.joinToString("\r\n")
    }

    /**
     * Generate metadata header for TXT file
     */
    fun generateMetadataHeader(book: Book, totalChapters: Int): String {
        val sb = StringBuilder()
        sb.appendLine("=".repeat(50))
        sb.appendLine("书名：《${book.name}》")
        sb.appendLine("作者：${book.author.ifBlank { "未知" }}")
        if (!book.kind.isNullOrBlank()) {
            sb.appendLine("分类：${book.kind}")
        }
        if (book.originName.isNotBlank() || book.origin.isNotBlank()) {
            sb.appendLine("来源：${book.originName.ifBlank { book.origin }}")
        }
        sb.appendLine("总章节数：$totalChapters 章")
        sb.appendLine("导出时间：${dateFormat.format(Date())}")
        sb.appendLine("导出工具：Legado Windows 本地阅读器")
        sb.appendLine("=".repeat(50))
        sb.appendLine()

        val intro = book.customIntro?.ifBlank { null } ?: book.intro
        if (!intro.isNullOrBlank()) {
            sb.appendLine("【内容简介】")
            sb.appendLine(formatChapterContent(intro, indentParagraphs = true, compactBlankLines = true))
            sb.appendLine()
            sb.appendLine("=".repeat(50))
            sb.appendLine()
        }

        return sb.toString().replace("\n", "\r\n")
    }

    /**
     * Streamed export to TXT file
     */
    suspend fun export(
        book: Book,
        chapters: List<BookChapter>,
        options: ExportOptions,
        contentProvider: suspend (chapter: BookChapter, index: Int) -> String?,
        isCancelled: () -> Boolean = { false },
        onProgress: (current: Int, total: Int, chapterTitle: String) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val targetFile = options.targetFile
        val parentDir = targetFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        FileOutputStream(targetFile).buffered().use { fos ->
            // Write UTF-8 BOM if requested (prevents encoding issues on Windows Notepad/e-readers)
            if (options.includeBOM) {
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            }

            BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8)).use { writer ->
                // Write Metadata Header
                if (options.addMetadataHeader) {
                    writer.write(generateMetadataHeader(book, chapters.size))
                }

                val total = chapters.size
                for ((idx, chapter) in chapters.withIndex()) {
                    if (isCancelled()) {
                        throw kotlinx.coroutines.CancellationException("用户取消了 TXT 导出操作")
                    }

                    onProgress(idx + 1, total, chapter.title)

                    val content = contentProvider(chapter, idx) ?: ""
                    val formattedContent = formatChapterContent(
                        rawContent = content,
                        indentParagraphs = options.indentParagraphs,
                        compactBlankLines = options.compactBlankLines
                    )

                    // Write chapter heading
                    writer.write("\r\n\r\n")
                    writer.write(chapter.title.trim())
                    writer.write("\r\n\r\n")

                    // Write chapter body
                    if (formattedContent.isNotBlank()) {
                        writer.write(formattedContent)
                        writer.write("\r\n")
                    }
                    writer.flush()
                }
            }
        }

        targetFile
    }
}
