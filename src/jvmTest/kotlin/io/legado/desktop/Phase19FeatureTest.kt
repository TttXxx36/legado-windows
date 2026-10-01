package io.legado.desktop

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.ui.BookSortOrder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class Phase19FeatureTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Test
    fun testBookModel_customGroupSerialization() {
        val book = Book(
            bookUrl = "local://test-book-group",
            name = "测试分组书籍",
            author = "作者A",
            customGroup = "我的专属精选"
        )
        val encoded = json.encodeToString(book)
        assertTrue(encoded.contains("\"customGroup\":\"我的专属精选\""))

        val decoded = json.decodeFromString<Book>(encoded)
        assertEquals("我的专属精选", decoded.customGroup)
    }

    @Test
    fun testBookshelfFilterLogic() {
        val b1 = Book("url1", name = "书1", durChapterTime = 1000L, customGroup = null) // In progress
        val b2 = Book("url2", name = "书2", durChapterTime = 0L, customGroup = null)    // Unread
        val b3 = Book("url3", name = "书3", customGroup = "养肥")
        val b4 = Book("url4", name = "书4", customGroup = "完结")
        val b5 = Book("url5", name = "书5", customGroup = "科幻")

        val books = listOf(b1, b2, b3, b4, b5)

        // 全部
        val allFiltered = books.filter { true }
        assertEquals(5, allFiltered.size)

        // 在读: customGroup == "在读" || (customGroup.isNullOrBlank() && durChapterTime > 0)
        val readingFiltered = books.filter { it.customGroup == "在读" || (it.customGroup.isNullOrBlank() && it.durChapterTime > 0) }
        assertEquals(1, readingFiltered.size)
        assertEquals("书1", readingFiltered[0].name)

        // 养肥
        val fattenFiltered = books.filter { it.customGroup == "养肥" }
        assertEquals(1, fattenFiltered.size)
        assertEquals("书3", fattenFiltered[0].name)

        // 完结
        val finishedFiltered = books.filter { it.customGroup == "完结" }
        assertEquals(1, finishedFiltered.size)
        assertEquals("书4", finishedFiltered[0].name)

        // 自定义分组 "科幻"
        val customFiltered = books.filter { it.customGroup == "科幻" }
        assertEquals(1, customFiltered.size)
        assertEquals("书5", customFiltered[0].name)
    }

    @Test
    fun testBookshelfFourDimensionalSorting() {
        val b1 = Book("u1", name = "Apple", durChapterTime = 100L, order = 3)
        val b2 = Book("u2", name = "Banana", durChapterTime = 500L, order = 1)
        val b3 = Book("u3", name = "Cherry", durChapterTime = 300L, order = 2)

        val books = listOf(b1, b2, b3)

        // 1. Sort by LAST_READ (Descending)
        val sortedByLastReadDesc = books.sortedByDescending { it.durChapterTime }
        assertEquals("Banana", sortedByLastReadDesc[0].name)
        assertEquals("Cherry", sortedByLastReadDesc[1].name)
        assertEquals("Apple", sortedByLastReadDesc[2].name)

        // 2. Sort by LAST_READ (Ascending)
        val sortedByLastReadAsc = books.sortedBy { it.durChapterTime }
        assertEquals("Apple", sortedByLastReadAsc[0].name)
        assertEquals("Banana", sortedByLastReadAsc[2].name)

        // 3. Sort by ADD_TIME (order index Ascending)
        val sortedByOrderAsc = books.sortedBy { it.order }
        assertEquals("Banana", sortedByOrderAsc[0].name) // order 1
        assertEquals("Cherry", sortedByOrderAsc[1].name) // order 2
        assertEquals("Apple", sortedByOrderAsc[2].name)  // order 3

        // 4. Sort by NAME (A-Z)
        val sortedByNameAsc = books.sortedBy { it.name.lowercase() }
        assertEquals("Apple", sortedByNameAsc[0].name)
        assertEquals("Banana", sortedByNameAsc[1].name)
        assertEquals("Cherry", sortedByNameAsc[2].name)
    }

    @Test
    fun testDatabaseBookGroupLifecycle() {
        runBlocking {
            // Ensure default groups exist
            val initialGroups = AppDatabase.getAllBookGroups()
            assertTrue(initialGroups.contains("在读"))
            assertTrue(initialGroups.contains("养肥"))
            assertTrue(initialGroups.contains("完结"))

            // Create new group
            val testGroupName = "单元测试专属组_${System.currentTimeMillis()}"
            val created = AppDatabase.createBookGroup(testGroupName)
            assertTrue(created)
            val afterCreate = AppDatabase.getAllBookGroups()
            assertTrue(afterCreate.contains(testGroupName))

            // Create book in this group
            val testBook = Book(
                bookUrl = "local://group-test-book-${System.currentTimeMillis()}",
                name = "分组关联测试书",
                customGroup = testGroupName
            )
            AppDatabase.insertOrUpdateBook(testBook)

            val loaded = AppDatabase.getAllBooks().firstOrNull { it.bookUrl == testBook.bookUrl }
            assertNotNull(loaded)
            assertEquals(testGroupName, loaded?.customGroup)

            // Rename group
            val renamedGroupName = "${testGroupName}_已重命名"
            val renamed = AppDatabase.renameBookGroup(testGroupName, renamedGroupName)
            assertTrue(renamed)

            val afterRename = AppDatabase.getAllBookGroups()
            assertFalse(afterRename.contains(testGroupName))
            assertTrue(afterRename.contains(renamedGroupName))

            val loadedAfterRename = AppDatabase.getAllBooks().firstOrNull { it.bookUrl == testBook.bookUrl }
            assertEquals(renamedGroupName, loadedAfterRename?.customGroup)

            // Delete group (Must unbind book, NOT delete book)
            val deleted = AppDatabase.deleteBookGroup(renamedGroupName)
            assertTrue(deleted)

            val afterDelete = AppDatabase.getAllBookGroups()
            assertFalse(afterDelete.contains(renamedGroupName))

            val loadedAfterDelete = AppDatabase.getAllBooks().firstOrNull { it.bookUrl == testBook.bookUrl }
            assertNotNull(loadedAfterDelete) // Book is preserved!
            assertTrue(loadedAfterDelete?.customGroup.isNullOrBlank())

            // Clean up book
            AppDatabase.deleteBook(testBook.bookUrl)
        }
    }

    @Test
    fun testDatabaseBatchUpdateAndBatchDelete() {
        runBlocking {
            val b1 = Book("local://batch-1-${System.currentTimeMillis()}", name = "批量1")
            val b2 = Book("local://batch-2-${System.currentTimeMillis()}", name = "批量2")
            AppDatabase.insertOrUpdateBook(b1)
            AppDatabase.insertOrUpdateBook(b2)

            val urls = listOf(b1.bookUrl, b2.bookUrl)

            // Batch update customGroup
            AppDatabase.updateBooksGroup(urls, "批量精选组")
            val booksAfterGroup = AppDatabase.getAllBooks().filter { it.bookUrl in urls }
            assertEquals(2, booksAfterGroup.size)
            assertTrue(booksAfterGroup.all { it.customGroup == "批量精选组" })

            // Batch delete
            AppDatabase.deleteBooks(urls)
            val booksAfterDelete = AppDatabase.getAllBooks().filter { it.bookUrl in urls }
            assertTrue(booksAfterDelete.isEmpty())
        }
    }
}
