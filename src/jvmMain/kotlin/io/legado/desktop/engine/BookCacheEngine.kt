package io.legado.desktop.engine

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.data.model.BookSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.security.MessageDigest

/**
 * Progress tracking state for background batch chapter downloads
 */
data class CacheDownloadProgress(
    val bookUrl: String,
    val bookName: String,
    val downloadedCount: Int,
    val totalToDownload: Int,
    val currentChapterTitle: String,
    val isFinished: Boolean = false,
    val errorMessage: String? = null,
    val cancelAction: () -> Unit = {}
)

object BookCacheEngine {
    const val KEY_CUSTOM_CACHE_DIR = "cache_custom_dir"

    private val _downloadProgress = MutableStateFlow<CacheDownloadProgress?>(null)
    val downloadProgress: StateFlow<CacheDownloadProgress?> = _downloadProgress.asStateFlow()

    private var activeDownloadJob: Job? = null
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getDefaultCacheDir(): File {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "LegadoDesktop" + File.separator + "book_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun getCacheDir(): File = withContext(Dispatchers.IO) {
        val custom = AppDatabase.getConfig(KEY_CUSTOM_CACHE_DIR, "")
        if (custom.isNotBlank()) {
            val dir = File(custom)
            if (!dir.exists()) dir.mkdirs()
            dir
        } else {
            getDefaultCacheDir()
        }
    }

    suspend fun setCustomCacheDir(path: String) = withContext(Dispatchers.IO) {
        AppDatabase.setConfig(KEY_CUSTOM_CACHE_DIR, path.trim())
    }

    fun getBookCacheFolder(rootDir: File, book: Book): File {
        val rawKey = "${book.bookUrl}_${book.name}"
        val md5 = try {
            val digest = MessageDigest.getInstance("MD5").digest(rawKey.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }.take(12)
        } catch (_: Exception) {
            rawKey.hashCode().toString().take(12)
        }
        val safeName = book.name.replace(Regex("""[\\/:*?"<>|]"""), "_").take(24).ifBlank { "book" }
        val folder = File(rootDir, "${safeName}_$md5")
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    fun getChapterFile(rootDir: File, book: Book, chapterIndex: Int): File {
        val bookFolder = getBookCacheFolder(rootDir, book)
        return File(bookFolder, "$chapterIndex.txt")
    }

    suspend fun hasCache(book: Book, chapterIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val rootDir = getCacheDir()
        val file = getChapterFile(rootDir, book, chapterIndex)
        file.exists() && file.length() > 0
    }

    suspend fun readCache(book: Book, chapterIndex: Int): String? = withContext(Dispatchers.IO) {
        val rootDir = getCacheDir()
        val file = getChapterFile(rootDir, book, chapterIndex)
        if (file.exists() && file.length() > 0) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun writeCache(book: Book, chapterIndex: Int, content: String): Boolean = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext false
        val rootDir = getCacheDir()
        val file = getChapterFile(rootDir, book, chapterIndex)
        try {
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearBookCache(book: Book): Boolean = withContext(Dispatchers.IO) {
        val rootDir = getCacheDir()
        val folder = getBookCacheFolder(rootDir, book)
        if (folder.exists()) {
            folder.deleteRecursively()
        } else {
            true
        }
    }

    suspend fun clearAllCache(): Boolean = withContext(Dispatchers.IO) {
        val rootDir = getCacheDir()
        if (rootDir.exists()) {
            rootDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        }
        true
    }

    suspend fun getCacheTotalSizeBytes(): Long = withContext(Dispatchers.IO) {
        val rootDir = getCacheDir()
        if (!rootDir.exists()) return@withContext 0L
        calculateFolderSize(rootDir)
    }

    private fun calculateFolderSize(folder: File): Long {
        var size = 0L
        val files = folder.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) calculateFolderSize(f) else f.length()
        }
        return size
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Start background batch download with rate-limiting and concurrency safety
     */
    fun startBatchDownload(
        book: Book,
        source: BookSource,
        chapters: List<BookChapter>,
        startIndex: Int,
        count: Int
    ) {
        // Cancel any active previous download job
        cancelDownload()

        val endIndex = (startIndex + count - 1).coerceAtMost(chapters.size - 1)
        val targetIndices = (startIndex..endIndex).toList()
        if (targetIndices.isEmpty()) return

        val totalToDownload = targetIndices.size
        var completed = 0

        val cancelAction: () -> Unit = {
            cancelDownload()
        }

        activeDownloadJob = downloadScope.launch {
            _downloadProgress.value = CacheDownloadProgress(
                bookUrl = book.bookUrl,
                bookName = book.name,
                downloadedCount = 0,
                totalToDownload = totalToDownload,
                currentChapterTitle = "正在准备离线任务...",
                cancelAction = cancelAction
            )

            val semaphore = Semaphore(3) // 3 concurrent download workers
            val deferredList = targetIndices.map { idx ->
                async {
                    semaphore.withPermit {
                        if (!isActive) return@withPermit

                        val chapter = chapters[idx]
                        // Check if already in cache
                        val cached = hasCache(book, idx)
                        if (!cached) {
                            try {
                                val content = BookSourceEngine.getContent(source, book, chapter)
                                if (content.isNotBlank() && !content.startsWith("加载正文失败")) {
                                    writeCache(book, idx, content)
                                }
                            } catch (_: Exception) {}
                            // Respectful request spacing against book source DDoS
                            delay(100)
                        }

                        synchronized(this@BookCacheEngine) {
                            completed++
                            _downloadProgress.value = CacheDownloadProgress(
                                bookUrl = book.bookUrl,
                                bookName = book.name,
                                downloadedCount = completed,
                                totalToDownload = totalToDownload,
                                currentChapterTitle = chapter.title,
                                cancelAction = cancelAction
                            )
                        }
                    }
                }
            }

            try {
                deferredList.awaitAll()
                _downloadProgress.value = CacheDownloadProgress(
                    bookUrl = book.bookUrl,
                    bookName = book.name,
                    downloadedCount = totalToDownload,
                    totalToDownload = totalToDownload,
                    currentChapterTitle = "离线缓存完成！共 $totalToDownload 章",
                    isFinished = true,
                    cancelAction = cancelAction
                )
                // Auto dismiss completion pill after 4 seconds
                delay(4000)
                _downloadProgress.value = null
            } catch (e: CancellationException) {
                _downloadProgress.value = null
            } catch (e: Exception) {
                _downloadProgress.value = CacheDownloadProgress(
                    bookUrl = book.bookUrl,
                    bookName = book.name,
                    downloadedCount = completed,
                    totalToDownload = totalToDownload,
                    currentChapterTitle = "缓存中断: ${e.message}",
                    isFinished = true,
                    errorMessage = e.message,
                    cancelAction = cancelAction
                )
            }
        }
    }

    fun cancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        _downloadProgress.value = null
    }
}
