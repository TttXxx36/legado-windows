package io.legado.desktop

import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import io.legado.desktop.engine.export.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class Phase15FeatureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val sampleBook = Book(
        bookUrl = "https://example.com/books/100",
        name = "万道剑仙",
        author = "梦入凡尘",
        kind = "玄幻修真",
        origin = "https://example.com",
        originName = "云溪文学",
        intro = "少年持三尺青峰，破九天十地，剑斩红尘万道！",
        totalChapterNum = 3
    )

    private val sampleChapters = listOf(
        BookChapter(
            url = "https://example.com/books/100/1",
            title = "第一章 青锋初试",
            bookUrl = sampleBook.bookUrl,
            index = 0
        ),
        BookChapter(
            url = "https://example.com/books/100/2",
            title = "第二章 灵泉仙酿",
            bookUrl = sampleBook.bookUrl,
            index = 1
        ),
        BookChapter(
            url = "https://example.com/books/100/3",
            title = "第三章 绝壁参悟",
            bookUrl = sampleBook.bookUrl,
            index = 2
        )
    )

    private val sampleContents = mapOf(
        0 to "晨光熹微，白露未晞。\n\n少年立于崖上，拔出长剑，剑气如霜。\n\n\n\n他低语道：“此剑名为‘斩雪’。”",
        1 to "酒家老者递过一葫芦仙酿。\n入口醇厚甘冽，真气如长河奔涌。\n“好酒！当浮一大白！”",
        2 to "绝壁之上刻着三道剑痕。\n剑势浑然天成，宛若游龙腾空。<破空之声隐隐作响 & 惊雷乍现>"
    )

    @Test
    fun testTxtFormattingAndCompaction() {
        val raw = "第一段文字。\n\n\n\n第二段文字。\n   第三段有空格文字。   "
        val formatted = TxtExportEngine.formatChapterContent(raw, indentParagraphs = true, compactBlankLines = true)
        
        // Assert full-width indent
        assertTrue("Lines should start with 2 full-width spaces", formatted.contains("　　第一段文字。"))
        assertTrue("Lines should be trimmed and indented", formatted.contains("　　第三段有空格文字。"))
        
        // Assert multiple blank lines compacted to single blank line
        assertFalse("Should not contain 3 consecutive CRLF", formatted.contains("\r\n\r\n\r\n"))
    }

    @Test
    fun testTxtMetadataHeader() {
        val header = TxtExportEngine.generateMetadataHeader(sampleBook, 3)
        assertTrue(header.contains("《万道剑仙》"))
        assertTrue(header.contains("梦入凡尘"))
        assertTrue(header.contains("玄幻修真"))
        assertTrue(header.contains("云溪文学"))
        assertTrue(header.contains("总章节数：3 章"))
        assertTrue(header.contains("少年持三尺青峰"))
    }

    @Test
    fun testTxtExportFileWithBom() = runBlocking {
        val targetFile = File(tempFolder.root, "test_book.txt")
        val options = ExportOptions(
            format = ExportFormat.TXT,
            scope = ExportScope.ALL_FETCH_MISSING,
            targetFile = targetFile,
            includeBOM = true,
            indentParagraphs = true,
            addMetadataHeader = true
        )

        var progressCount = 0
        val file = TxtExportEngine.export(
            book = sampleBook,
            chapters = sampleChapters,
            options = options,
            contentProvider = { _, idx -> sampleContents[idx] },
            onProgress = { cur, tot, _ -> progressCount = cur }
        )

        assertTrue("Target file must exist", file.exists())
        assertEquals("Progress count should reach 3", 3, progressCount)

        val bytes = file.readBytes()
        // Check UTF-8 BOM: 0xEF, 0xBB, 0xBF
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])

        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("第一章 青锋初试"))
        assertTrue(text.contains("第二章 灵泉仙酿"))
        assertTrue(text.contains("第三章 绝壁参悟"))
        assertTrue(text.contains("少年立于崖上，拔出长剑"))
    }

    @Test
    fun testEpubXmlEscaping() {
        val text = "A & B < C > D \"quote\" 'apostrophe'"
        val escaped = EpubExportEngine.escapeXml(text)
        assertEquals("A &amp; B &lt; C &gt; D &quot;quote&quot; &apos;apostrophe&apos;", escaped)
    }

    @Test
    fun testEpubExportValidOcfContainer() = runBlocking {
        val targetFile = File(tempFolder.root, "test_book.epub")
        val options = ExportOptions(
            format = ExportFormat.EPUB,
            scope = ExportScope.ALL_FETCH_MISSING,
            targetFile = targetFile
        )

        val file = EpubExportEngine.export(
            book = sampleBook,
            chapters = sampleChapters,
            options = options,
            contentProvider = { _, idx -> sampleContents[idx] }
        )

        assertTrue("EPUB file must exist", file.exists())
        assertTrue("EPUB file size must be > 0", file.length() > 0)

        // Verify with ZipInputStream for first entry MUST be STORED mimetype
        file.inputStream().use { fis ->
            ZipInputStream(fis).use { zis ->
                val firstEntry = zis.nextEntry
                assertNotNull("First entry must not be null", firstEntry)
                assertEquals("First entry name must be 'mimetype'", "mimetype", firstEntry!!.name)
                assertEquals("mimetype entry must be STORED (uncompressed)", ZipEntry.STORED, firstEntry.method)

                val buffer = ByteArray(20)
                val read = zis.read(buffer)
                assertEquals("mimetype length must be 20", 20, read)
                assertEquals("application/epub+zip", String(buffer, Charsets.US_ASCII))
            }
        }

        // Verify entire EPUB structure with ZipFile
        ZipFile(file).use { zip ->
            // Check container.xml
            val containerEntry = zip.getEntry("META-INF/container.xml")
            assertNotNull("container.xml must exist", containerEntry)
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
            assertTrue(containerXml.contains("OEBPS/content.opf"))

            // Check style.css
            val cssEntry = zip.getEntry("OEBPS/style.css")
            assertNotNull("style.css must exist", cssEntry)

            // Check titlepage.xhtml
            val titleEntry = zip.getEntry("OEBPS/titlepage.xhtml")
            assertNotNull("titlepage.xhtml must exist", titleEntry)
            val titleContent = zip.getInputStream(titleEntry).bufferedReader().readText()
            assertTrue(titleContent.contains("万道剑仙"))
            assertTrue(titleContent.contains("梦入凡尘"))

            // Check content.opf
            val opfEntry = zip.getEntry("OEBPS/content.opf")
            assertNotNull("content.opf must exist", opfEntry)
            val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
            assertTrue(opfContent.contains("<dc:title>万道剑仙</dc:title>"))
            assertTrue(opfContent.contains("<dc:creator>梦入凡尘</dc:creator>"))
            assertTrue(opfContent.contains("toc.ncx"))
            assertTrue(opfContent.contains("nav.xhtml"))
            assertTrue(opfContent.contains("chapter_0.xhtml"))
            assertTrue(opfContent.contains("chapter_1.xhtml"))
            assertTrue(opfContent.contains("chapter_2.xhtml"))

            // Check EPUB 2 toc.ncx
            val ncxEntry = zip.getEntry("OEBPS/toc.ncx")
            assertNotNull("toc.ncx must exist", ncxEntry)
            val ncxContent = zip.getInputStream(ncxEntry).bufferedReader().readText()
            assertTrue(ncxContent.contains("第一章 青锋初试"))
            assertTrue(ncxContent.contains("第二章 灵泉仙酿"))

            // Check EPUB 3 nav.xhtml
            val navEntry = zip.getEntry("OEBPS/nav.xhtml")
            assertNotNull("nav.xhtml must exist", navEntry)
            val navContent = zip.getInputStream(navEntry).bufferedReader().readText()
            assertTrue(navContent.contains("<nav epub:type=\"toc\""))
            assertTrue(navContent.contains("第三章 绝壁参悟"))

            // Check chapter xhtml files and XML entity escaping
            val chap2Entry = zip.getEntry("OEBPS/chapter_2.xhtml")
            assertNotNull("chapter_2.xhtml must exist", chap2Entry)
            val chap2Content = zip.getInputStream(chap2Entry).bufferedReader().readText()
            assertTrue("Entity '<' should be escaped", chap2Content.contains("&lt;破空之声"))
            assertTrue("Entity '&' should be escaped", chap2Content.contains("&amp; 惊雷乍现"))
            assertTrue("Tag '<p class=\"paragraph\">' should wrap text", chap2Content.contains("<p class=\"paragraph\">"))
        }
    }

    @Test
    fun testBookExportManagerHelpers() {
        val sanitized = BookExportManager.sanitizeFileName("修真:从凡人开始?*\"<>/|")
        assertEquals("修真_从凡人开始_______", sanitized)

        val txtName = BookExportManager.getSuggestedFileName(sampleBook, ExportFormat.TXT)
        assertEquals("万道剑仙 - 梦入凡尘.txt", txtName)

        val epubName = BookExportManager.getSuggestedFileName(sampleBook, ExportFormat.EPUB)
        assertEquals("万道剑仙 - 梦入凡尘.epub", epubName)
    }
}
