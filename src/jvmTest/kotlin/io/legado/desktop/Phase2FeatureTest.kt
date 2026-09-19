package io.legado.desktop

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Bookmark
import io.legado.desktop.data.model.ReplaceRule
import io.legado.desktop.data.model.WebDavConfig
import io.legado.desktop.engine.rule.ReplaceRuleEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class Phase2FeatureTest {

    @Test
    fun testBookmarkDatabaseCrud() {
        runBlocking {
            val bm = Bookmark(
                id = 9999L,
                bookUrl = "test://book/bm",
                bookName = "三体",
                chapterIndex = 3,
                chapterTitle = "第3章 红岸基地",
                content = "不要回答！不要回答！不要回答！",
                note = "关键历史转折点"
            )

            AppDatabase.insertBookmark(bm)
            val loaded = AppDatabase.getBookmarks(bm.bookUrl)
            assertTrue(loaded.any { it.id == 9999L && it.chapterTitle == "第3章 红岸基地" })

            AppDatabase.deleteBookmark(9999L)
            val remaining = AppDatabase.getBookmarks(bm.bookUrl)
            assertFalse(remaining.any { it.id == 9999L })
        }
    }

    @Test
    fun testReplaceRuleEngine() {
        runBlocking {
            val rule = ReplaceRule(
                id = 8888L,
                name = "广告净化",
                pattern = "关注微信公众号【.*】免费看全集",
                replacement = "",
                isRegex = true,
                isEnabled = true
            )

            AppDatabase.insertOrUpdateReplaceRule(rule)

            val dirtyText = """
                天地玄黄，宇宙洪荒。
                关注微信公众号【好书天天读】免费看全集
                日月盈昃，辰宿列张。
            """.trimIndent()

            val cleanText = ReplaceRuleEngine.applyRules(dirtyText)
            assertFalse(cleanText.contains("好书天天读"))
            assertTrue(cleanText.contains("天地玄黄"))
            assertTrue(cleanText.contains("日月盈昃"))

            AppDatabase.deleteReplaceRule(8888L)
        }
    }

    @Test
    fun testWebDavConfigPersistence() {
        runBlocking {
            val config = WebDavConfig(
                url = "https://dav.example.com/dav/",
                username = "test_user",
                password = "test_password_123",
                rootDir = "legado_sync"
            )

            AppDatabase.saveWebDavConfig(config)
            val loaded = AppDatabase.getWebDavConfig()
            assertEquals("https://dav.example.com/dav/", loaded.url)
            assertEquals("test_user", loaded.username)
            assertEquals("test_password_123", loaded.password)
            assertEquals("legado_sync", loaded.rootDir)
        }
    }
}
