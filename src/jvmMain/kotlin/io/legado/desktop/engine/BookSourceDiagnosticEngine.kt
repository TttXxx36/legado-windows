package io.legado.desktop.engine

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.engine.network.HttpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

sealed class DiagnosticStatus {
    object Untested : DiagnosticStatus()
    object Checking : DiagnosticStatus()
    data class Healthy(val latencyMs: Long) : DiagnosticStatus()
    object Timeout : DiagnosticStatus()
    data class Error(val message: String) : DiagnosticStatus()
    object Empty : DiagnosticStatus()

    fun isFailed(): Boolean = this is Timeout || this is Error
}

object BookSourceDiagnosticEngine {

    val diagnosticResults = ConcurrentHashMap<String, DiagnosticStatus>()

    fun getStatus(bookSourceUrl: String): DiagnosticStatus {
        return diagnosticResults[bookSourceUrl] ?: DiagnosticStatus.Untested
    }

    fun setStatus(bookSourceUrl: String, status: DiagnosticStatus) {
        diagnosticResults[bookSourceUrl] = status
    }

    /**
     * Test connectivity and responsiveness of a single BookSource with timeout
     */
    suspend fun testSource(source: BookSource, timeoutMs: Long = 5000L): DiagnosticStatus = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val result = withTimeoutOrNull(timeoutMs) {
                // If searchUrl is available, try a fast search probe or header request
                if (!source.searchUrl.isNullOrBlank()) {
                    try {
                        val searchResults = BookSourceEngine.search(source, "雪")
                        val latency = System.currentTimeMillis() - start
                        if (searchResults.isNotEmpty()) {
                            DiagnosticStatus.Healthy(latency)
                        } else {
                            // Empty search result is still reachable
                            DiagnosticStatus.Healthy(latency)
                        }
                    } catch (e: Exception) {
                        // Fall back to probing base URL
                        probeUrl(source.bookSourceUrl, start, source.header)
                    }
                } else {
                    probeUrl(source.bookSourceUrl, start, source.header)
                }
            }
            val finalStatus = result ?: DiagnosticStatus.Timeout
            diagnosticResults[source.bookSourceUrl] = finalStatus
            finalStatus
        } catch (e: Exception) {
            val errorStatus = DiagnosticStatus.Error(e.localizedMessage ?: "网络连接失败")
            diagnosticResults[source.bookSourceUrl] = errorStatus
            errorStatus
        }
    }

    private suspend fun probeUrl(url: String, start: Long, headerJson: String?): DiagnosticStatus {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return DiagnosticStatus.Error("无效的书源地址协议")
        }
        val customHeaders = BookSourceEngine.parseHeaderMap(headerJson)
        val body = HttpHelper.get(url, customHeaders)
        val latency = System.currentTimeMillis() - start
        return if (body.isNotBlank()) {
            DiagnosticStatus.Healthy(latency)
        } else {
            DiagnosticStatus.Empty
        }
    }

    /**
     * Concurrently diagnostic test all sources with a controlled concurrency limit
     */
    suspend fun testAllSources(
        sources: List<BookSource>,
        concurrency: Int = 8,
        onProgress: (testedCount: Int, totalCount: Int, lastSource: BookSource, status: DiagnosticStatus) -> Unit = { _, _, _, _ -> }
    ): Map<String, DiagnosticStatus> = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(concurrency)
        var testedCount = 0
        val totalCount = sources.size

        // Mark all as checking
        sources.forEach { diagnosticResults[it.bookSourceUrl] = DiagnosticStatus.Checking }

        val deferred = sources.map { source ->
            async {
                val status = semaphore.withPermit {
                    testSource(source)
                }
                synchronized(this@BookSourceDiagnosticEngine) {
                    testedCount++
                    onProgress(testedCount, totalCount, source, status)
                }
                Pair(source.bookSourceUrl, status)
            }
        }

        deferred.awaitAll().toMap()
    }

    /**
     * One-click disable all sources that timed out or failed
     */
    suspend fun disableFailedSources(sources: List<BookSource>): Int = withContext(Dispatchers.IO) {
        var disabledCount = 0
        for (source in sources) {
            val status = getStatus(source.bookSourceUrl)
            if (status.isFailed() && source.enabled) {
                source.enabled = false
                AppDatabase.insertOrUpdateBookSource(source)
                disabledCount++
            }
        }
        disabledCount
    }

    /**
     * One-click clean up (delete) all sources that timed out or failed
     */
    suspend fun deleteFailedSources(sources: List<BookSource>): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        for (source in sources) {
            val status = getStatus(source.bookSourceUrl)
            if (status.isFailed()) {
                AppDatabase.deleteBookSource(source.bookSourceUrl)
                diagnosticResults.remove(source.bookSourceUrl)
                deletedCount++
            }
        }
        deletedCount
    }
}
