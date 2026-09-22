package io.legado.desktop

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookAnnotation
import io.legado.desktop.data.model.Bookmark
import io.legado.desktop.engine.export.MarkdownExportEngine
import io.legado.desktop.ui.buildHighlightedText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Phase12FeatureTest {

    @Test
    fun testAnnotationDatabaseCrudAndCascadeDelete() = runBlocking {
        val testBookUrl = "local://phase12_test_book_${System.currentTimeMillis()}"
        val testBook = Book(
            bookUrl = testBookUrl,
            name = "深度批注测试书籍",
            author = "极客作者",
            kind = "现代文学",
            origin = "local"
        )
        AppDatabase.insertOrUpdateBook(testBook)

        val anno1 = BookAnnotation(
            id = 10001L,
            bookUrl = testBookUrl,
            bookName = testBook.name,
            chapterIndex = 0,
            chapterTitle = "第一章 混沌初开",
            selectedText = "天地不仁，以万物为刍狗。",
            note = "老子道德经的核心名言",
            colorType = "YELLOW",
            startOffset = 10,
            endOffset = 22,
            createdAt = 1700000000000L
        )

        val anno2 = BookAnnotation(
            id = 10002L,
            bookUrl = testBookUrl,
            bookName = testBook.name,
            chapterIndex = 0,
            chapterTitle = "第一章 混沌初开",
            selectedText = "圣人不仁，以百姓为刍狗。",
            note = "",
            colorType = "UNDERLINE",
            startOffset = 25,
            endOffset = 37,
            createdAt = 1700000005000L
        )

        val anno3 = BookAnnotation(
            id = 10003L,
            bookUrl = testBookUrl,
            bookName = testBook.name,
            chapterIndex = 1,
            chapterTitle = "第二章 太极生两仪",
            selectedText = "阴阳和合，万象更新。",
            note = "辩证哲学的开端",
            colorType = "GREEN",
            startOffset = 5,
            endOffset = 15,
            createdAt = 1700000010000L
        )

        // 1. Insert annotations
        AppDatabase.insertAnnotation(anno1)
        AppDatabase.insertAnnotation(anno2)
        AppDatabase.insertAnnotation(anno3)

        // 2. Query all annotations for book
        val bookAnnos = AppDatabase.getAnnotations(testBookUrl)
        assertEquals("书籍划线总数应为 3", 3, bookAnnos.size)

        // 3. Query chapter-specific annotations
        val ch0Annos = AppDatabase.getChapterAnnotations(testBookUrl, 0)
        assertEquals("第0章划线数应为 2", 2, ch0Annos.size)
        assertEquals("老子道德经的核心名言", ch0Annos[0].note)

        val ch1Annos = AppDatabase.getChapterAnnotations(testBookUrl, 1)
        assertEquals("第1章划线数应为 1", 1, ch1Annos.size)
        assertEquals("GREEN", ch1Annos[0].colorType)

        // 4. Delete single annotation
        AppDatabase.deleteAnnotation(anno2.id)
        val afterDelete = AppDatabase.getChapterAnnotations(testBookUrl, 0)
        assertEquals("删除后第0章划线数应为 1", 1, afterDelete.size)
        assertEquals(anno1.id, afterDelete[0].id)

        // 5. Cascade delete with deleteBook
        AppDatabase.deleteBook(testBookUrl)
        val remainingAnnos = AppDatabase.getAnnotations(testBookUrl)
        assertTrue("书籍删除后划线应全部级联清除", remainingAnnos.isEmpty())
    }

    @Test
    fun testMarkdownExportEngineFormatting() {
        val annotations = listOf(
            BookAnnotation(
                id = 1L,
                bookUrl = "local://book1",
                bookName = "百年孤独",
                chapterIndex = 0,
                chapterTitle = "第 1 章",
                selectedText = "多年以后，面对行刑队，奥雷里亚诺·布恩迪亚上校将会回想起父亲带他去见识冰块的那个遥远的下午。",
                note = "魔幻现实主义最著名的开篇句式。",
                colorType = "YELLOW",
                createdAt = 1710000000000L
            ),
            BookAnnotation(
                id = 2L,
                bookUrl = "local://book1",
                bookName = "百年孤独",
                chapterIndex = 0,
                chapterTitle = "第 1 章",
                selectedText = "世界太新，很多事物还没有名字，必须用手指去指。",
                note = "",
                colorType = "UNDERLINE",
                createdAt = 1710000005000L
            ),
            BookAnnotation(
                id = 3L,
                bookUrl = "local://book1",
                bookName = "百年孤独",
                chapterIndex = 1,
                chapterTitle = "第 2 章",
                selectedText = "时间不是在流逝，而是在原地兜圈子。",
                note = "宿命论与循环时间的体现。",
                colorType = "PURPLE",
                createdAt = 1710000010000L
            )
        )

        val bookmarks = listOf(
            Bookmark(
                id = 101L,
                bookUrl = "local://book1",
                bookName = "百年孤独",
                chapterIndex = 0,
                chapterTitle = "第 1 章",
                content = "马孔多在那个时候是个二十户人家的村落...",
                note = "记录村庄最初的规模",
                time = 1710000000000L
            )
        )

        val md = MarkdownExportEngine.generateMarkdown(
            bookTitle = "百年孤独",
            bookAuthor = "加西亚·马尔克斯",
            annotations = annotations,
            bookmarks = bookmarks
        )

        assertNotNull(md)
        assertTrue("应包含书籍主标题", md.contains("# 《百年孤独》读书笔记与划线导出"))
        assertTrue("应包含作者", md.contains("加西亚·马尔克斯"))
        assertTrue("应包含划线总数", md.contains("**划线总数**：3 条"))
        assertTrue("应包含书签总数", md.contains("**书签总数**：1 条"))
        assertTrue("应按章节分组包含 第 1 章", md.contains("### 第 1 章"))
        assertTrue("应按章节分组包含 第 2 章", md.contains("### 第 2 章"))
        assertTrue("应包含黄色高亮标识", md.contains("🟡 [高亮]"))
        assertTrue("应包含下划线标识", md.contains("〰️ [下划线]"))
        assertTrue("应包含重点紫色标识", md.contains("🟣 [重点]"))
        assertTrue("应包含批注想法", md.contains("💡 魔幻现实主义最著名的开篇句式。"))
        assertTrue("应包含书签章节与内容", md.contains("马孔多在那个时候是个二十户人家的村落"))
        assertTrue("应包含版权生成注记", md.contains("Legado Windows 本地阅读器自动生成"))
    }

    @Test
    fun testSkiaHighlightAndAnnotationLayering() {
        val sampleText = "道可道非常道，名可名非常名。无名天地之始，有名万物之母。"
        val annotations = listOf(
            BookAnnotation(
                id = 1L,
                bookUrl = "test",
                bookName = "test",
                chapterIndex = 0,
                chapterTitle = "ch1",
                selectedText = "名可名非常名",
                note = "注解测试",
                colorType = "GREEN"
            ),
            BookAnnotation(
                id = 2L,
                bookUrl = "test",
                bookName = "test",
                chapterIndex = 0,
                chapterTitle = "ch1",
                selectedText = "有名万物之母",
                note = "",
                colorType = "UNDERLINE"
            )
        )

        // 1. 无搜索且无划线
        val plain = buildHighlightedText(sampleText, "", null, emptyList())
        assertEquals("无高亮时文本一致", sampleText, plain.text)
        assertTrue("无高亮时 SpanStyles 为空", plain.spanStyles.isEmpty())

        // 2. 仅划线
        val annotatedOnly = buildHighlightedText(sampleText, "", null, annotations)
        assertEquals(sampleText, annotatedOnly.text)
        assertTrue("有划线时应生成 SpanStyle", annotatedOnly.spanStyles.isNotEmpty())

        // 3. 划线 + 关键词搜索叠加 (Dual-layer highlight)
        val combined = buildHighlightedText(sampleText, "天地", null, annotations)
        assertEquals(sampleText, combined.text)
        assertTrue("叠加后 SpanStyles 数量应当包含划线与搜索高亮", combined.spanStyles.size >= 3)
    }

    @Test
    fun testMarkdownSpecialCharactersAndMultilineNotes() {
        val annotations = listOf(
            BookAnnotation(
                id = 99L,
                bookUrl = "test",
                bookName = "多行文本测试",
                chapterIndex = 0,
                chapterTitle = "第一章",
                selectedText = "第一行文字\n第二行文字\n第三行文字",
                note = "这是第一行思考\n这是第二行思考",
                colorType = "YELLOW"
            )
        )

        val md = MarkdownExportEngine.generateMarkdown("多行测试", "测试员", annotations)
        // 验证换行引用块在 Markdown 中正确转换为 `> ` 嵌套
        assertTrue("多行引用应当被引言符号保持", md.contains("> 第一行文字\n> 第二行文字\n> 第三行文字"))
        assertTrue("想法应被正常包含", md.contains("这是第一行思考"))
    }

    @Test
    fun testColorToBadgeMapping() {
        assertEquals("🟡 [高亮]", MarkdownExportEngine.colorToBadge("YELLOW"))
        assertEquals("🟡 [高亮]", MarkdownExportEngine.colorToBadge("yellow"))
        assertEquals("🟢 [思考]", MarkdownExportEngine.colorToBadge("GREEN"))
        assertEquals("🟣 [重点]", MarkdownExportEngine.colorToBadge("PURPLE"))
        assertEquals("〰️ [下划线]", MarkdownExportEngine.colorToBadge("UNDERLINE"))
        assertEquals("📝 [划线]", MarkdownExportEngine.colorToBadge("UNKNOWN"))
    }
}
