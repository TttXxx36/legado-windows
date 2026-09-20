package io.legado.desktop

import io.legado.desktop.data.model.Book
import io.legado.desktop.engine.BookCacheEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BookCacheEngineTest {

    private lateinit var tempTestDir: File

    @Before
    fun setup() {
        tempTestDir = Files.createTempDirectory("legado_cache_test").toFile()
    }

    @After
    fun teardown() {
        tempTestDir.deleteRecursively()
    }

    @Test
    fun testCacheWriteAndReadConsistency() = runBlocking {
        BookCacheEngine.setCustomCacheDir(tempTestDir.absolutePath)
        val book = Book(
            bookUrl = "https://test.novel/b1",
            name = "凡人修仙传",
            author = "忘语"
        )

        val chapterIndex = 1
        val sampleContent = "山边小村，一名皮肤黝黑的少年正坐在青石上发呆。"

        assertFalse("Initially should have no cache", BookCacheEngine.hasCache(book, chapterIndex))
        assertNull("Read non-existent cache should return null", BookCacheEngine.readCache(book, chapterIndex))

        val writeOk = BookCacheEngine.writeCache(book, chapterIndex, sampleContent)
        assertTrue("Write cache should succeed", writeOk)

        assertTrue("Should have cache after writing", BookCacheEngine.hasCache(book, chapterIndex))
        val readBack = BookCacheEngine.readCache(book, chapterIndex)
        assertEquals("Read back content should match", sampleContent, readBack)
    }

    @Test
    fun testCacheSizeCalculationAndFormatting() = runBlocking {
        BookCacheEngine.setCustomCacheDir(tempTestDir.absolutePath)
        val book = Book(
            bookUrl = "https://test.novel/b2",
            name = "仙逆",
            author = "耳根"
        )

        assertEquals("0 B", BookCacheEngine.formatFileSize(0L))
        assertEquals("500 B", BookCacheEngine.formatFileSize(500L))
        assertEquals("1.5 KB", BookCacheEngine.formatFileSize(1536L))
        assertEquals("2.0 MB", BookCacheEngine.formatFileSize(2 * 1024 * 1024L))

        BookCacheEngine.writeCache(book, 0, "这是第一章内容。".repeat(20))
        BookCacheEngine.writeCache(book, 1, "这是第二章内容。".repeat(30))

        val size = BookCacheEngine.getCacheTotalSizeBytes()
        assertTrue("Total cache size should be greater than 0", size > 0)
    }

    @Test
    fun testClearBookCacheAndClearAll() = runBlocking {
        BookCacheEngine.setCustomCacheDir(tempTestDir.absolutePath)
        val book1 = Book(bookUrl = "https://test.novel/b1", name = "遮天")
        val book2 = Book(bookUrl = "https://test.novel/b2", name = "完美世界")

        BookCacheEngine.writeCache(book1, 0, "登天路，踏歌行，弹指遮天。")
        BookCacheEngine.writeCache(book2, 0, "一粒尘可填海，一根草斩尽日月星辰。")

        assertTrue(BookCacheEngine.hasCache(book1, 0))
        assertTrue(BookCacheEngine.hasCache(book2, 0))

        // Clear only book1
        BookCacheEngine.clearBookCache(book1)
        assertFalse("Book1 cache should be gone", BookCacheEngine.hasCache(book1, 0))
        assertTrue("Book2 cache should remain", BookCacheEngine.hasCache(book2, 0))

        // Clear all
        BookCacheEngine.clearAllCache()
        assertFalse("Book2 cache should now be gone", BookCacheEngine.hasCache(book2, 0))
    }
}
