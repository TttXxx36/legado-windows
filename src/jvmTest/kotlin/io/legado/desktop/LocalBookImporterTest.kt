package io.legado.desktop

import io.legado.desktop.engine.local.LocalBookImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

class LocalBookImporterTest {

    @Test
    fun testCharsetDetection() {
        val tempUtf8 = File.createTempFile("test_utf8", ".txt")
        try {
            tempUtf8.writeText("这是 UTF-8 编码的测试文本内容", Charsets.UTF_8)
            val detected = LocalBookImporter.detectCharset(tempUtf8)
            assertEquals("应成功检测为 UTF-8", Charsets.UTF_8, detected)
        } finally {
            tempUtf8.delete()
        }

        val tempGbk = File.createTempFile("test_gbk", ".txt")
        try {
            val gbkCharset = Charset.forName("GBK")
            tempGbk.writeBytes("这是 GBK 编码的测试小说正文".toByteArray(gbkCharset))
            val detectedGbk = LocalBookImporter.detectCharset(tempGbk)
            assertTrue("应成功检测为 GB18030 或 GBK", detectedGbk.name().contains("GB"))
        } finally {
            tempGbk.delete()
        }
    }

    @Test
    fun testChapterRegex() {
        val testHeaders = listOf(
            "第一章 梦醒时分",
            "第123章 决战云巅",
            "第一百二十四章 逆转乾坤",
            "Chapter 10 The Odyssey",
            "序言",
            "【前言】",
            "尾声",
            "后记"
        )

        for (header in testHeaders) {
            assertTrue("正则应匹配章节标题: $header", LocalBookImporter.CHAPTER_PATTERN.containsMatchIn(header))
        }

        val nonHeaders = listOf(
            "今天天气很好，我们去第一中学读书。",
            "这里没有任何章节标记。"
        )

        for (nonHeader in nonHeaders) {
            assertFalse("普通文本不应误判为章节标题: $nonHeader", LocalBookImporter.CHAPTER_PATTERN.containsMatchIn(nonHeader))
        }
    }

    @Test
    fun testImportAndReadTxtBook() {
        runBlocking {
            val tempFile = File.createTempFile("天道图录", ".txt")
            try {
                val novelContent = """
                    作者：梦入神机
                    这是一本宏大的仙侠巨作。
                    
                    第一章 惊变
                    大雨滂沱，黑夜如墨。
                    少年前行在泥泞的山道上，眼神如火。
                    
                    第二章 筑基
                    春去秋来，寒暑更替。
                    洞府内的灵气渐渐化为漩涡，少年吐出一口浊气。
                    
                    第3章 登仙
                    九霄雷劫降临，万道金光破开云层。
                """.trimIndent()

                tempFile.writeText(novelContent, Charsets.UTF_8)

                val importedBook = LocalBookImporter.importTxtBook(tempFile)

                assertNotNull("导入的书籍不应为空", importedBook)
                assertTrue("书名应提取为文件名", importedBook.name.contains("天道图录"))
                assertEquals("作者识别应为'梦入神机'", "梦入神机", importedBook.author)
                assertEquals("类型应为 3 (本地书籍)", 3, importedBook.type)
                assertEquals("来源应为 local", "local", importedBook.origin)
                assertTrue("总章节数应 >= 3", importedBook.totalChapterNum >= 3)

                val chapters = io.legado.desktop.data.db.AppDatabase.getChapters(importedBook.bookUrl)
                assertTrue("数据库中的章节数应与书籍一致", chapters.isNotEmpty())

                val firstChapter = chapters.first { it.title.contains("第一章") }
                val content = LocalBookImporter.loadChapterContent(firstChapter)
                assertTrue("正文应包含章节内容", content.contains("大雨滂沱"))
            } finally {
                tempFile.delete()
            }
        }
    }
}
