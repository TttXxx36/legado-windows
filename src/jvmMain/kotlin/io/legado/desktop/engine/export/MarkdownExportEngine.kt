package io.legado.desktop.engine.export

import io.legado.desktop.data.model.BookAnnotation
import io.legado.desktop.data.model.Bookmark
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MarkdownExportEngine {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun colorToBadge(colorType: String): String {
        return when (colorType.uppercase()) {
            "YELLOW" -> "🟡 [高亮]"
            "GREEN" -> "🟢 [思考]"
            "PURPLE" -> "🟣 [重点]"
            "UNDERLINE" -> "〰️ [下划线]"
            else -> "📝 [划线]"
        }
    }

    /**
     * Generate structured Markdown documentation from book annotations and bookmarks.
     */
    fun generateMarkdown(
        bookTitle: String,
        bookAuthor: String = "未知",
        annotations: List<BookAnnotation>,
        bookmarks: List<Bookmark> = emptyList()
    ): String {
        val sb = StringBuilder()
        val now = dateFormat.format(Date())

        sb.appendLine("# 《$bookTitle》读书笔记与划线导出")
        sb.appendLine()
        sb.appendLine("> **作者**：${bookAuthor.ifBlank { "未知" }}  ")
        sb.appendLine("> **导出时间**：$now  ")
        sb.appendLine("> **划线总数**：${annotations.size} 条  ")
        if (bookmarks.isNotEmpty()) {
            sb.appendLine("> **书签总数**：${bookmarks.size} 条  ")
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // Group annotations by chapter
        if (annotations.isNotEmpty()) {
            sb.appendLine("## 📖 划线与笔记")
            sb.appendLine()

            val grouped = annotations.groupBy { it.chapterIndex to it.chapterTitle }
            for ((chapterKey, chapterAnnotations) in grouped) {
                val (_, chapterTitle) = chapterKey
                sb.appendLine("### $chapterTitle")
                sb.appendLine()

                for (anno in chapterAnnotations) {
                    val badge = colorToBadge(anno.colorType)
                    val timeStr = dateFormat.format(Date(anno.createdAt))

                    // Blockquote for selected text
                    sb.appendLine("> ${anno.selectedText.replace("\n", "\n> ")}")
                    sb.appendLine()
                    sb.appendLine("- **类型**：$badge")
                    if (anno.note.isNotBlank()) {
                        sb.appendLine("- **想法**：💡 ${anno.note}")
                    }
                    sb.appendLine("- **时间**：$timeStr")
                    sb.appendLine()
                }
            }
        }

        // Bookmarks section if any
        if (bookmarks.isNotEmpty()) {
            sb.appendLine("## 🔖 书签记录")
            sb.appendLine()

            val groupedBookmarks = bookmarks.groupBy { it.chapterIndex to it.chapterTitle }
            for ((chapterKey, chapterBookmarks) in groupedBookmarks) {
                val (_, chapterTitle) = chapterKey
                sb.appendLine("### $chapterTitle")
                sb.appendLine()

                for (bm in chapterBookmarks) {
                    val timeStr = dateFormat.format(Date(bm.time))
                    if (bm.content.isNotBlank()) {
                        sb.appendLine("> ${bm.content.replace("\n", "\n> ")}")
                        sb.appendLine()
                    }
                    if (!bm.note.isNullOrBlank()) {
                        sb.appendLine("- **附注**：📌 ${bm.note}")
                    }
                    sb.appendLine("- **时间**：$timeStr")
                    sb.appendLine()
                }
            }
        }

        sb.appendLine("---")
        sb.appendLine("*本笔记由 Legado Windows 本地阅读器自动生成*")
        return sb.toString()
    }

    /**
     * Copy markdown text directly to the Windows OS clipboard.
     */
    fun copyToClipboard(content: String) {
        val selection = StringSelection(content)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    /**
     * Open a native Windows save file dialog to export Markdown file with UTF-8 encoding.
     */
    fun exportToFile(parent: Frame?, defaultFileName: String, content: String): File? {
        val dialog = FileDialog(parent, "导出读书笔记 Markdown", FileDialog.SAVE).apply {
            file = if (defaultFileName.endsWith(".md")) defaultFileName else "$defaultFileName.md"
            isVisible = true
        }

        val dir = dialog.directory ?: return null
        val filename = dialog.file ?: return null
        val targetFile = File(dir, filename)

        targetFile.writeText(content, Charsets.UTF_8)
        return targetFile
    }
}
