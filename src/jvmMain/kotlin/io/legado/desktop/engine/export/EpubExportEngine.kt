package io.legado.desktop.engine.export

import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubExportEngine {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    /**
     * XML / XHTML character escaping
     */
    fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Generate standard embedded Chinese typography CSS
     */
    fun generateCss(): String {
        return """
            @charset "UTF-8";
            html, body {
                margin: 0;
                padding: 0;
                font-family: "PingFang SC", "Microsoft YaHei", "Noto Serif CJK SC", "Source Han Serif SC", SimSun, serif;
                font-size: 1em;
                line-height: 1.85;
                color: #2c3e50;
                background-color: #fafbfc;
                text-align: justify;
                text-justify: inter-ideograph;
            }
            .content-container {
                padding: 5% 6%;
            }
            h1.book-title {
                text-align: center;
                font-size: 2.2em;
                font-weight: 700;
                margin-top: 1.5em;
                margin-bottom: 0.4em;
                color: #1a1a1a;
            }
            .book-author {
                text-align: center;
                font-size: 1.15em;
                color: #606266;
                margin-bottom: 2em;
            }
            .book-intro-card {
                background: #f4f6f8;
                border-left: 4px solid #4a90e2;
                border-radius: 4px;
                padding: 1.2em 1.5em;
                margin: 2em 0;
            }
            .book-intro-card h3 {
                margin-top: 0;
                margin-bottom: 0.8em;
                color: #303133;
                font-size: 1.1em;
            }
            .book-intro-card p {
                text-indent: 2em;
                margin: 0.4em 0;
                color: #505255;
            }
            .meta-info {
                text-align: center;
                font-size: 0.85em;
                color: #909399;
                margin-top: 3em;
            }
            h2.chapter-title {
                text-align: center;
                font-size: 1.5em;
                font-weight: bold;
                margin-top: 1.5em;
                margin-bottom: 1.5em;
                color: #1a1a1a;
                border-bottom: 1px solid #ebeef5;
                padding-bottom: 0.6em;
            }
            p.paragraph {
                text-indent: 2em;
                margin-top: 0.5em;
                margin-bottom: 0.5em;
                word-wrap: break-word;
                letter-spacing: 0.02em;
            }
        """.trimIndent()
    }

    /**
     * Generate META-INF/container.xml
     */
    fun generateContainerXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()
    }

    /**
     * Generate cover / title page (OEBPS/titlepage.xhtml)
     */
    fun generateTitlePageXhtml(book: Book, totalChapters: Int): String {
        val intro = book.customIntro?.ifBlank { null } ?: book.intro
        val introParagraphs = intro?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<!DOCTYPE html>""")
        sb.appendLine("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">""")
        sb.appendLine("""<head>""")
        sb.appendLine("""    <meta charset="utf-8"/>""")
        sb.appendLine("""    <title>${escapeXml(book.name)}</title>""")
        sb.appendLine("""    <link rel="stylesheet" type="text/css" href="style.css"/>""")
        sb.appendLine("""</head>""")
        sb.appendLine("""<body>""")
        sb.appendLine("""    <div class="content-container">""")
        sb.appendLine("""        <h1 class="book-title">${escapeXml(book.name)}</h1>""")
        sb.appendLine("""        <div class="book-author">作者：${escapeXml(book.author.ifBlank { "未知" })}</div>""")

        if (introParagraphs.isNotEmpty()) {
            sb.appendLine("""        <div class="book-intro-card">""")
            sb.appendLine("""            <h3>内容简介</h3>""")
            for (p in introParagraphs) {
                sb.appendLine("""            <p class="paragraph">${escapeXml(p)}</p>""")
            }
            sb.appendLine("""        </div>""")
        }

        sb.appendLine("""        <div class="meta-info">""")
        if (!book.kind.isNullOrBlank()) {
            sb.appendLine("""            <p>作品分类：${escapeXml(book.kind!!)}</p>""")
        }
        sb.appendLine("""            <p>全书章节：共 $totalChapters 章</p>""")
        sb.appendLine("""            <p>本书由 Legado Windows 本地阅读器打包导出</p>""")
        sb.appendLine("""        </div>""")
        sb.appendLine("""    </div>""")
        sb.appendLine("""</body>""")
        sb.appendLine("""</html>""")
        return sb.toString()
    }

    /**
     * Generate chapter XHTML page
     */
    fun generateChapterXhtml(chapter: BookChapter, content: String): String {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<!DOCTYPE html>""")
        sb.appendLine("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">""")
        sb.appendLine("""<head>""")
        sb.appendLine("""    <meta charset="utf-8"/>""")
        sb.appendLine("""    <title>${escapeXml(chapter.title)}</title>""")
        sb.appendLine("""    <link rel="stylesheet" type="text/css" href="style.css"/>""")
        sb.appendLine("""</head>""")
        sb.appendLine("""<body>""")
        sb.appendLine("""    <div class="content-container">""")
        sb.appendLine("""        <h2 class="chapter-title">${escapeXml(chapter.title)}</h2>""")

        for (line in lines) {
            sb.appendLine("""        <p class="paragraph">${escapeXml(line)}</p>""")
        }

        sb.appendLine("""    </div>""")
        sb.appendLine("""</body>""")
        sb.appendLine("""</html>""")
        return sb.toString()
    }

    /**
     * Generate OEBPS/content.opf
     */
    fun generateContentOpf(
        book: Book,
        chapters: List<BookChapter>,
        uuid: String
    ): String {
        val nowIso = isoDateFormat.format(Date())
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<package version="3.0" unique-identifier="BookId" xmlns="http://www.idpf.org/2007/opf">""")
        sb.appendLine("""    <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">""")
        sb.appendLine("""        <dc:identifier id="BookId">urn:uuid:$uuid</dc:identifier>""")
        sb.appendLine("""        <dc:title>${escapeXml(book.name)}</dc:title>""")
        sb.appendLine("""        <dc:creator>${escapeXml(book.author.ifBlank { "未知" })}</dc:creator>""")
        sb.appendLine("""        <dc:language>zh-CN</dc:language>""")
        if (!book.intro.isNullOrBlank()) {
            sb.appendLine("""        <dc:description>${escapeXml(book.intro!!)}</dc:description>""")
        }
        sb.appendLine("""        <meta property="dcterms:modified">$nowIso</meta>""")
        sb.appendLine("""    </metadata>""")

        sb.appendLine("""    <manifest>""")
        sb.appendLine("""        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        sb.appendLine("""        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        sb.appendLine("""        <item id="style" href="style.css" media-type="text/css"/>""")
        sb.appendLine("""        <item id="titlepage" href="titlepage.xhtml" media-type="application/xhtml+xml"/>""")
        for (i in chapters.indices) {
            sb.appendLine("""        <item id="chap_$i" href="chapter_$i.xhtml" media-type="application/xhtml+xml"/>""")
        }
        sb.appendLine("""    </manifest>""")

        sb.appendLine("""    <spine toc="ncx">""")
        sb.appendLine("""        <itemref idref="titlepage"/>""")
        for (i in chapters.indices) {
            sb.appendLine("""        <itemref idref="chap_$i"/>""")
        }
        sb.appendLine("""    </spine>""")
        sb.appendLine("""</package>""")
        return sb.toString()
    }

    /**
     * Generate EPUB 2 NCX Table of Contents (OEBPS/toc.ncx)
     */
    fun generateTocNcx(book: Book, chapters: List<BookChapter>, uuid: String): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">""")
        sb.appendLine("""    <head>""")
        sb.appendLine("""        <meta name="dtb:uid" content="urn:uuid:$uuid"/>""")
        sb.appendLine("""        <meta name="dtb:depth" content="1"/>""")
        sb.appendLine("""        <meta name="dtb:totalPageCount" content="0"/>""")
        sb.appendLine("""        <meta name="dtb:maxPageNumber" content="0"/>""")
        sb.appendLine("""    </head>""")
        sb.appendLine("""    <docTitle><text>${escapeXml(book.name)}</text></docTitle>""")
        sb.appendLine("""    <navMap>""")

        var playOrder = 1
        sb.appendLine("""        <navPoint id="nav_titlepage" playOrder="${playOrder++}">""")
        sb.appendLine("""            <navLabel><text>书籍信息</text></navLabel>""")
        sb.appendLine("""            <content src="titlepage.xhtml"/>""")
        sb.appendLine("""        </navPoint>""")

        for ((i, chapter) in chapters.withIndex()) {
            sb.appendLine("""        <navPoint id="nav_chap_$i" playOrder="${playOrder++}">""")
            sb.appendLine("""            <navLabel><text>${escapeXml(chapter.title)}</text></navLabel>""")
            sb.appendLine("""            <content src="chapter_$i.xhtml"/>""")
            sb.appendLine("""        </navPoint>""")
        }

        sb.appendLine("""    </navMap>""")
        sb.appendLine("""</ncx>""")
        return sb.toString()
    }

    /**
     * Generate EPUB 3 Navigation Document (OEBPS/nav.xhtml)
     */
    fun generateNavXhtml(book: Book, chapters: List<BookChapter>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<!DOCTYPE html>""")
        sb.appendLine("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="zh-CN">""")
        sb.appendLine("""<head>""")
        sb.appendLine("""    <meta charset="utf-8"/>""")
        sb.appendLine("""    <title>目录 - ${escapeXml(book.name)}</title>""")
        sb.appendLine("""    <link rel="stylesheet" type="text/css" href="style.css"/>""")
        sb.appendLine("""</head>""")
        sb.appendLine("""<body>""")
        sb.appendLine("""    <nav epub:type="toc" id="toc">""")
        sb.appendLine("""        <h2>目录</h2>""")
        sb.appendLine("""        <ol>""")
        sb.appendLine("""            <li><a href="titlepage.xhtml">书籍信息</a></li>""")
        for ((i, chapter) in chapters.withIndex()) {
            sb.appendLine("""            <li><a href="chapter_$i.xhtml">${escapeXml(chapter.title)}</a></li>""")
        }
        sb.appendLine("""        </ol>""")
        sb.appendLine("""    </nav>""")
        sb.appendLine("""</body>""")
        sb.appendLine("""</html>""")
        return sb.toString()
    }

    /**
     * Export book to standard EPUB 2/3 OCF Container
     */
    suspend fun export(
        book: Book,
        chapters: List<BookChapter>,
        options: ExportOptions,
        contentProvider: suspend (chapter: BookChapter, index: Int) -> String?,
        isCancelled: () -> Boolean = { false },
        onProgress: (current: Int, total: Int, chapterTitle: String) -> Unit = { _, _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val targetFile = options.targetFile
        val parentDir = targetFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val uuid = UUID.randomUUID().toString()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(targetFile))).use { zipOut ->
            // 1. MUST BE FIRST ENTRY: mimetype, STORED, NO EXTRA, application/epub+zip
            val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimetypeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
                crc = CRC32().apply { update(mimetypeBytes) }.value
            }
            zipOut.putNextEntry(mimetypeEntry)
            zipOut.write(mimetypeBytes)
            zipOut.closeEntry()

            // 2. META-INF/container.xml
            val containerBytes = generateContainerXml().toByteArray(Charsets.UTF_8)
            val containerEntry = ZipEntry("META-INF/container.xml").apply {
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(containerEntry)
            zipOut.write(containerBytes)
            zipOut.closeEntry()

            // 3. OEBPS/style.css
            val cssBytes = generateCss().toByteArray(Charsets.UTF_8)
            val cssEntry = ZipEntry("OEBPS/style.css").apply {
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(cssEntry)
            zipOut.write(cssBytes)
            zipOut.closeEntry()

            // 4. OEBPS/titlepage.xhtml
            val titlePageBytes = generateTitlePageXhtml(book, chapters.size).toByteArray(Charsets.UTF_8)
            val titlePageEntry = ZipEntry("OEBPS/titlepage.xhtml").apply {
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(titlePageEntry)
            zipOut.write(titlePageBytes)
            zipOut.closeEntry()

            // 5. OEBPS/content.opf
            val opfBytes = generateContentOpf(book, chapters, uuid).toByteArray(Charsets.UTF_8)
            val opfEntry = ZipEntry("OEBPS/content.opf").apply {
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(opfEntry)
            zipOut.write(opfBytes)
            zipOut.closeEntry()

            // 6. OEBPS/toc.ncx (EPUB 2)
            val ncxBytes = generateTocNcx(book, chapters, uuid).toByteArray(Charsets.UTF_8)
            val ncxEntry = ZipEntry("OEBPS/toc.ncx").apply {
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(ncxEntry)
            zipOut.write(ncxBytes)
            zipOut.closeEntry()

            // 7. OEBPS/nav.xhtml (EPUB 3)
            val navBytes = generateNavXhtml(book, chapters).toByteArray(Charsets.UTF_8)
            val navEntry = ZipEntry("OEBPS/nav.xhtml").apply {
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(navEntry)
            zipOut.write(navBytes)
            zipOut.closeEntry()

            // 8. OEBPS/chapter_X.xhtml
            val total = chapters.size
            for ((idx, chapter) in chapters.withIndex()) {
                if (isCancelled()) {
                    throw kotlinx.coroutines.CancellationException("用户取消了 EPUB 导出操作")
                }

                onProgress(idx + 1, total, chapter.title)

                val content = contentProvider(chapter, idx) ?: ""
                val chapterXhtml = generateChapterXhtml(chapter, content)
                val chapterBytes = chapterXhtml.toByteArray(Charsets.UTF_8)

                val chapEntry = ZipEntry("OEBPS/chapter_$idx.xhtml").apply {
                    method = ZipEntry.DEFLATED
                }
                zipOut.putNextEntry(chapEntry)
                zipOut.write(chapterBytes)
                zipOut.closeEntry()
            }
        }

        targetFile
    }
}
