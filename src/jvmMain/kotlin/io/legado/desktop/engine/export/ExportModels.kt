package io.legado.desktop.engine.export

import io.legado.desktop.data.model.Book
import java.io.File

enum class ExportFormat(val extension: String, val displayName: String, val desc: String) {
    TXT("txt", "标准精排 TXT", "通用文本，支持 UTF-8 BOM，段落规范排版，兼容任意设备与阅读器"),
    EPUB("epub", "标准电子书 EPUB", "双兼容 OCF 电子书容器 (EPUB 2/3)，内置精致中文排版样式与多级目录")
}

enum class ExportScope(val displayName: String, val desc: String) {
    ALL_FETCH_MISSING("全书导出 (缺失章节联网缓存)", "导出整本所有章节；若本地未缓存，将自动从书源获取并保存"),
    CACHED_ONLY("仅导出已缓存章节", "仅导出当前本地已有缓存的章节，完全离线无等待")
}

data class ExportOptions(
    val format: ExportFormat,
    val scope: ExportScope,
    val targetFile: File,
    val includeBOM: Boolean = true,
    val indentParagraphs: Boolean = true,
    val addMetadataHeader: Boolean = true,
    val compactBlankLines: Boolean = true
)

data class ExportProgress(
    val book: Book,
    val currentChapterIndex: Int,
    val totalChapters: Int,
    val currentChapterTitle: String,
    val percentage: Float,
    val isFinished: Boolean = false,
    val isCancelled: Boolean = false,
    val errorMessage: String? = null,
    val exportedFile: File? = null
)
