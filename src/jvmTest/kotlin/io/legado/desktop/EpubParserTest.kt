package io.legado.desktop

import io.legado.desktop.engine.local.LocalBookImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @Test
    fun testEpubImportAndChapterParsing() {
        runBlocking {
            val tempEpub = File.createTempFile("test_xiuzhen", ".epub")
            try {
                // 构建标准的测试 EPUB 容器包
                ZipOutputStream(FileOutputStream(tempEpub)).use { zos ->
                    fun addEntry(name: String, content: String) {
                        zos.putNextEntry(ZipEntry(name))
                        zos.write(content.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }

                    // 1. mimetype (必须无压缩位于首部)
                    addEntry("mimetype", "application/epub+zip")

                    // 2. META-INF/container.xml
                    addEntry(
                        "META-INF/container.xml",
                        """
                        <?xml version="1.0"?>
                        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                            <rootfiles>
                                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                            </rootfiles>
                        </container>
                        """.trimIndent()
                    )

                    // 3. OEBPS/content.opf
                    addEntry(
                        "OEBPS/content.opf",
                        """
                        <?xml version="1.0" encoding="utf-8"?>
                        <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                                <dc:title>修真世界</dc:title>
                                <dc:creator>方想</dc:creator>
                                <dc:description>莫晃荡，踏仙途。这是一个种灵草的小人物成长为一代巨擘的传奇。</dc:description>
                                <dc:language>zh-CN</dc:language>
                            </metadata>
                            <manifest>
                                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                                <item id="ch1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                                <item id="ch2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                            </manifest>
                            <spine toc="ncx">
                                <itemref idref="ch1"/>
                                <itemref idref="ch2"/>
                            </spine>
                        </package>
                        """.trimIndent()
                    )

                    // 4. OEBPS/text/ch1.xhtml
                    addEntry(
                        "OEBPS/text/ch1.xhtml",
                        """
                        <?xml version="1.0" encoding="utf-8"?>
                        <!DOCTYPE html>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head><title>第一节 灵草园的小僵尸</title></head>
                        <body>
                            <h1>第一节 灵草园的小僵尸</h1>
                            <p>晨雾迷蒙，左莫蹲在灵田边，眼神空洞得像具僵尸。</p>
                            <p>他手里握着一把破旧的锄头，正仔细地给一株一品赤草松土。</p>
                        </body>
                        </html>
                        """.trimIndent()
                    )

                    // 5. OEBPS/text/ch2.xhtml
                    addEntry(
                        "OEBPS/text/ch2.xhtml",
                        """
                        <?xml version="1.0" encoding="utf-8"?>
                        <!DOCTYPE html>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                        <head><title>第二节 金乌灵剑</title></head>
                        <body>
                            <h1>第二节 金乌灵剑</h1>
                            <p>远处一道耀眼的银色剑芒掠过天空，带起刺骨的风啸之声。</p>
                            <p>剑修，那是所有灵植夫可望而不可即的存在。</p>
                        </body>
                        </html>
                        """.trimIndent()
                    )
                }

                // 执行导入
                val book = LocalBookImporter.importBook(tempEpub)

                assertNotNull("书籍不应为空", book)
                assertEquals("书名应提取为'修真世界'", "修真世界", book.name)
                assertEquals("作者应提取为'方想'", "方想", book.author)
                assertEquals("类型应为 3 (本地)", 3, book.type)
                assertTrue("简介应包含仙途", book.intro?.contains("仙途") == true)
                assertEquals("总章节数应为 2", 2, book.totalChapterNum)

                val chapters = io.legado.desktop.data.db.AppDatabase.getChapters(book.bookUrl)
                assertEquals("章节列表应有 2 章", 2, chapters.size)
                assertTrue("第一章标题应正确", chapters[0].title.contains("灵草园"))

                val ch1Content = LocalBookImporter.loadChapterContent(chapters[0])
                assertTrue("正文应包含左莫", ch1Content.contains("左莫蹲在灵田边"))

                val ch2Content = LocalBookImporter.loadChapterContent(chapters[1])
                assertTrue("第二章正文应包含剑修", ch2Content.contains("剑修"))
            } finally {
                tempEpub.delete()
            }
        }
    }
}
