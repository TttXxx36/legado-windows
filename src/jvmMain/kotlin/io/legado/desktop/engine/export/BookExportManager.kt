package io.legado.desktop.engine.export

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.engine.BookCacheEngine
import io.legado.desktop.engine.BookSourceEngine
import io.legado.desktop.engine.rule.ReplaceRuleEngine
import io.legado.desktop.engine.local.LocalBookImporter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Desktop
import java.io.File

object BookExportManager {

    private val _exportProgress = MutableStateFlow<ExportProgress?>(null)
    val exportProgress: StateFlow<ExportProgress?> = _exportProgress.asStateFlow()

    private var activeJob: Job? = null
    private val exportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Clean filename for Windows filesystem
     */
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    /**
     * Generate suggested filename for a book and format
     */
    fun getSuggestedFileName(book: Book, format: ExportFormat): String {
        val safeName = sanitizeFileName(book.name.ifBlank { "book" })
        val safeAuthor = sanitizeFileName(book.author.ifBlank { "未知" })
        return "$safeName - $safeAuthor.${format.extension}"
    }

    /**
     * Open target file directly with system default viewer
     */
    fun openFile(file: File) {
        if (!file.exists()) return
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
            }
        } catch (_: Exception) {}
    }

    /**
     * Open and reveal target file in Windows Explorer
     */
    fun revealInExplorer(file: File) {
        if (!file.exists()) return
        try {
            // Windows native select in Explorer
            Runtime.getRuntime().exec(arrayOf("explorer.exe", "/select,", file.absolutePath))
        } catch (_: Exception) {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    val parent = file.parentFile ?: file
                    Desktop.getDesktop().open(parent)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Cancel currently running export task
     */
    fun cancelExport() {
        activeJob?.cancel()
        activeJob = null
        val current = _exportProgress.value
        if (current != null && !current.isFinished) {
            _exportProgress.value = current.copy(
                isCancelled = true,
                errorMessage = "用户已取消导出"
            )
        }
    }

    /**
     * Reset progress state
     */
    fun clearProgress() {
        _exportProgress.value = null
    }

    /**
     * Start book export task
     */
    fun startExport(
        book: Book,
        options: ExportOptions,
        onFinished: ((File) -> Unit)? = null
    ) {
        cancelExport()

        activeJob = exportScope.launch {
            _exportProgress.value = ExportProgress(
                book = book,
                currentChapterIndex = 0,
                totalChapters = 0,
                currentChapterTitle = "正在读取章节目录...",
                percentage = 0f
            )

            try {
                // 1. Load chapters from DB
                var chapters = AppDatabase.getChapters(book.bookUrl)
                if (chapters.isEmpty()) {
                    if (book.type == 3 || book.origin == "local") {
                        throw IllegalStateException("本地书籍目录为空，无法导出")
                    }
                    val allSources = AppDatabase.getAllBookSources()
                    val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }
                    if (source != null) {
                        chapters = BookSourceEngine.getChapters(source, book)
                        if (chapters.isNotEmpty()) {
                            AppDatabase.saveChapters(book.bookUrl, chapters)
                        }
                    }
                }

                if (chapters.isEmpty()) {
                    throw IllegalStateException("未能获取到《${book.name}》的章节目录，无法导出")
                }

                // 2. Filter chapters if CACHED_ONLY
                val targetChapters = if (options.scope == ExportScope.CACHED_ONLY) {
                    val filtered = mutableListOf<BookChapter>()
                    for (ch in chapters) {
                        if (BookCacheEngine.hasCache(book, ch.index)) {
                            filtered.add(ch)
                        }
                    }
                    if (filtered.isEmpty()) {
                        throw IllegalStateException("《${book.name}》当前无任何本地离线缓存章节，请选择全书导出或先在阅读器/书架中下载缓存！")
                    }
                    filtered
                } else {
                    chapters
                }

                val total = targetChapters.size
                _exportProgress.value = ExportProgress(
                    book = book,
                    currentChapterIndex = 0,
                    totalChapters = total,
                    currentChapterTitle = "准备导出，共 $total 章...",
                    percentage = 0f
                )

                val allSources = if (book.type != 3 && book.origin != "local") {
                    AppDatabase.getAllBookSources()
                } else {
                    emptyList()
                }
                val source = allSources.firstOrNull { it.bookSourceUrl == book.origin }

                // Content provider with offline cache first + dynamic fetch
                val contentProvider: suspend (BookChapter, Int) -> String? = { ch, idx ->
                    // 1. Offline cache
                    val cached = BookCacheEngine.readCache(book, ch.index)
                    if (!cached.isNullOrBlank()) {
                        cached
                    } else if (options.scope == ExportScope.CACHED_ONLY) {
                        ""
                    } else {
                        // 2. Fetch missing chapter
                        val raw = if (book.type == 3 || book.origin == "local") {
                            LocalBookImporter.loadChapterContent(ch)
                        } else if (source != null) {
                            try {
                                BookSourceEngine.getContent(source, book, ch)
                            } catch (_: Exception) {
                                ""
                            }
                        } else {
                            ""
                        }

                        val cleaned = if (source != null) {
                            ReplaceRuleEngine.applyRules(raw, book, source)
                        } else {
                            raw
                        }

                        // Save newly fetched content to local cache
                        if (cleaned.isNotBlank() && !cleaned.startsWith("【正文内容为空】") && !cleaned.startsWith("【正文加载异常】")) {
                            BookCacheEngine.writeCache(book, ch.index, cleaned)
                        }
                        cleaned
                    }
                }

                val onProgressUpdate: (Int, Int, String) -> Unit = { cur, tot, title ->
                    val pct = if (tot > 0) cur.toFloat() / tot else 0f
                    _exportProgress.value = ExportProgress(
                        book = book,
                        currentChapterIndex = cur,
                        totalChapters = tot,
                        currentChapterTitle = title,
                        percentage = pct
                    )
                }

                val exportedFile = when (options.format) {
                    ExportFormat.TXT -> {
                        TxtExportEngine.export(
                            book = book,
                            chapters = targetChapters,
                            options = options,
                            contentProvider = contentProvider,
                            isCancelled = { !isActive },
                            onProgress = onProgressUpdate
                        )
                    }
                    ExportFormat.EPUB -> {
                        EpubExportEngine.export(
                            book = book,
                            chapters = targetChapters,
                            options = options,
                            contentProvider = contentProvider,
                            isCancelled = { !isActive },
                            onProgress = onProgressUpdate
                        )
                    }
                }

                _exportProgress.value = ExportProgress(
                    book = book,
                    currentChapterIndex = total,
                    totalChapters = total,
                    currentChapterTitle = "全书导出完成！共 $total 章",
                    percentage = 1.0f,
                    isFinished = true,
                    exportedFile = exportedFile
                )

                onFinished?.invoke(exportedFile)

            } catch (e: CancellationException) {
                _exportProgress.value = ExportProgress(
                    book = book,
                    currentChapterIndex = 0,
                    totalChapters = 0,
                    currentChapterTitle = "导出已被取消",
                    percentage = 0f,
                    isCancelled = true,
                    errorMessage = "导出任务已中止"
                )
            } catch (e: Exception) {
                _exportProgress.value = ExportProgress(
                    book = book,
                    currentChapterIndex = 0,
                    totalChapters = 0,
                    currentChapterTitle = "导出失败",
                    percentage = 0f,
                    isFinished = true,
                    errorMessage = e.message ?: "未知导出异常"
                )
            }
        }
    }
}
