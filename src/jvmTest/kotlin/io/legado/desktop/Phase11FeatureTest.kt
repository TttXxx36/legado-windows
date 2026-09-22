package io.legado.desktop

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.engine.local.ChapterPresets
import io.legado.desktop.engine.local.LocalBookImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Phase11FeatureTest {

    @Test
    fun testPortableLocalBooksDirectoryResolution() {
        val dir = LocalBookImporter.getLocalBooksDirectory()
        assertNotNull("本地书籍目录不应为空", dir)
        assertTrue("本地书籍目录应当存在", dir.exists())
        assertTrue("本地书籍目录应当具备可写权限", dir.canWrite())
        assertTrue("目录名应当以 local_books 结尾", dir.name == "local_books")
    }

    @Test
    fun testLargeTxtByteOffsetScanningAndRandomAccessRead() = runBlocking {
        val tempFile = File.createTempFile("phase11_test_novel", ".txt")
        try {
            val sb = StringBuilder()
            sb.append("作者：天蚕土豆\n")
            sb.append("书名：万界流式传奇\n")
            sb.append("简介：这是一部专为测试百兆流式字节偏移扫描的测试范本。\n\n")

            val chapterCount = 10
            for (i in 1..chapterCount) {
                sb.append("第${i}章 天地玄黄大破界\n")
                for (j in 1..30) {
                    sb.append("这是第 $i 章的第 $j 段正文内容，包含丰富的中文多字节字符（UTF-8 三字节）：道可道非常道，名可名非常名。天地不仁，以万物为刍狗。\n")
                }
                sb.append("\n")
            }

            tempFile.writeText(sb.toString(), Charsets.UTF_8)

            val imported = LocalBookImporter.importTxtBook(tempFile)
            assertNotNull(imported)
            assertTrue("书名应包含文件名前缀", imported.name.startsWith("phase11_test_novel"))
            assertEquals("天蚕土豆", imported.author)
            assertTrue("识别章节数应包含序言及10个正文章节", imported.totalChapterNum >= 10)

            val chapters = AppDatabase.getChapters(imported.bookUrl)
            assertTrue("数据库中章节列表不应为空", chapters.isNotEmpty())

            // 验证各个章节的 URL 均带有标准字节偏移量参数
            for (ch in chapters) {
                assertTrue("章节URL应包含 start 参数", ch.url.contains("#start="))
                assertTrue("章节URL应包含 end 参数", ch.url.contains("&end="))
                assertTrue("章节URL应包含 charset 参数", ch.url.contains("&charset="))
            }

            // 验证 RandomAccess 精确 Seek 读取
            val targetChapter = chapters.first { it.title.contains("第5章") }
            val content = LocalBookImporter.loadChapterContent(targetChapter)
            assertTrue("读取出的正文应当包含第5章内容", content.contains("第 5 章"))
            assertTrue("读取出的正文应当包含中文完整字符", content.contains("道可道非常道"))
            assertFalse("不应跨章读入第6章内容", content.contains("第6章"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testPresetRegexesMatching() {
        // 1. 标准中文预设
        val chineseHeaders = listOf(
            "第一章 降临", "第100章 决战", "第一百二十章 破晓", "前言", "【序言】", "后记", "尾声"
        )
        for (h in chineseHeaders) {
            assertTrue("标准中文正则应匹配: $h", ChapterPresets.STANDARD_CHINESE.containsMatchIn(h))
        }

        // 2. 英文预设
        val englishHeaders = listOf(
            "Chapter 1 Introduction", "chapter 42 The Answer", "Prologue", "Epilogue", "Book 3"
        )
        for (h in englishHeaders) {
            assertTrue("英文字段正则应匹配: $h", ChapterPresets.ENGLISH.containsMatchIn(h))
        }

        // 3. 数字序号预设
        val numberedHeaders = listOf(
            "1. 缘起", "2. 探索", "10. 终局", "(1) 启航", "[2] 征程"
        )
        for (h in numberedHeaders) {
            assertTrue("数字序号正则应匹配: $h", ChapterPresets.NUMBERED.containsMatchIn(h))
        }

        // 4. 网络特殊符号预设
        val webHeaders = listOf(
            "【第一章】 觉醒", "★ 第2章 奇遇 ★", "☆ 第3章 蜕变 ☆", "◆ 第四章 登神 ◆", "【Chapter 5】 归来"
        )
        for (h in webHeaders) {
            assertTrue("网络特殊符号正则应匹配: $h", ChapterPresets.WEB_SPECIAL.containsMatchIn(h))
        }
    }

    @Test
    fun testPreviewSplitAndReSplitTxtBook() = runBlocking {
        val tempFile = File.createTempFile("resplit_test_novel", ".txt")
        try {
            val content = """
                书名：特异规则小说
                作者：神秘客
                
                ★ 第一回 荒山客栈 ★
                夜黑风高，风声呼啸。
                
                ★ 第二回 密室逃脱 ★
                机关重重，石门紧闭。
                
                ★ 第三回 真相大白 ★
                一切谜题皆已揭晓。
            """.trimIndent()
            tempFile.writeText(content, Charsets.UTF_8)

            // 先使用普通默认正则导入（可能无法识别 ★ 符号格式，或被兜底分段）
            val imported = LocalBookImporter.importTxtBook(tempFile)
            assertNotNull(imported)

            // 使用 WEB_SPECIAL 规则进行预览
            val preview = LocalBookImporter.previewSplit(imported, ChapterPresets.WEB_SPECIAL)
            assertTrue("预览应识别到章节", preview.matched)
            assertEquals("应匹配到 4 个章节(含序言前言)", 4, preview.totalChapters)
            assertTrue("样本标题应包含第一回", preview.sampleTitles.any { it.contains("第一回") })

            // 执行重新分章
            val newChapters = LocalBookImporter.reSplitTxtBook(imported, ChapterPresets.WEB_SPECIAL)
            assertEquals("重整后章节数应为 4(含序言前言)", 4, newChapters.size)
            assertEquals("序言标题应当正确", "序言 / 前言", newChapters[0].title)
            assertEquals("第一回标题应当正确", "★ 第一回 荒山客栈 ★", newChapters[1].title)

            // 重新验证读取
            val ch1Content = LocalBookImporter.loadChapterContent(newChapters[1])
            assertTrue("第一回正文应正确加载", ch1Content.contains("夜黑风高"))

            val ch3Content = LocalBookImporter.loadChapterContent(newChapters[3])
            assertTrue("第三回正文应正确加载", ch3Content.contains("一切谜题皆已揭晓"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testFileResolutionAndFallback() {
        val temp = File.createTempFile("resolve_test", ".txt")
        try {
            val resolved = LocalBookImporter.resolveFile(temp.absolutePath)
            assertTrue("存在的绝对路径应能正确解析", resolved.exists())
            assertEquals(temp.canonicalPath, resolved.canonicalPath)

            val nonExistent = LocalBookImporter.resolveFile("D:\\RandomNonExistentPath_123456.txt")
            assertNotNull("不存在的路径不应抛出异常", nonExistent)
        } finally {
            temp.delete()
        }
    }
}
