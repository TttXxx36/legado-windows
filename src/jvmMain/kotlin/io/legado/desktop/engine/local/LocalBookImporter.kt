package io.legado.desktop.engine.local

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

object LocalBookImporter {

    private val localBooksDir: File by lazy {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        File(File(appData, "LegadoDesktop"), "local_books").apply { if (!exists()) mkdirs() }
    }

    // Legado 经典分章正则：支持第X章/回/节/卷、Chapter、序言、后记、尾声、结语等
    val CHAPTER_PATTERN = Regex(
        "^[ \\t]*(?:第[0-9一二三四五六七八九十百千万]+[章回节卷集幕篇话][^\\n]{0,35}|Chapter\\s+\\d+[^\\n]{0,35}|[【\\[]?(?:序[言章]?|前言|楔子|后记|尾声|结语)[】\\]]?[^\\n]{0,35})$",
        RegexOption.MULTILINE
    )

    /**
     * 智能探测文本编码 (支持 UTF-8 BOM, UTF-16, UTF-8 严格校验，异常时回退到 GB18030/GBK)
     */
    fun detectCharset(file: File): Charset {
        val bytes = file.inputStream().use { stream ->
            val buf = ByteArray(minOf(file.length(), 65536).toInt())
            val read = stream.read(buf)
            buf.copyOf(read.coerceAtLeast(0))
        }

        // 检查 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return Charsets.UTF_16BE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return Charsets.UTF_16LE
        }

        // 尝试严格 UTF-8 解码
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            Charsets.UTF_8
        } catch (_: Exception) {
            // 中文 Windows 常用回退编码
            try {
                Charset.forName("GB18030")
            } catch (_: Exception) {
                Charset.forName("GBK")
            }
        }
    }

    /**
     * 导入本地 TXT 小说，智能分章并持久化到本地数据库
     */
    suspend fun importTxtBook(file: File): Book = withContext(Dispatchers.IO) {
        require(file.exists()) { "文件不存在: ${file.absolutePath}" }
        val charset = detectCharset(file)
        val rawText = file.readText(charset)

        val bookName = file.nameWithoutExtension
        val bookId = md5("${file.absolutePath}_${file.lastModified()}")
        val bookDir = File(localBooksDir, bookId).apply { if (!exists()) mkdirs() }

        // 提取作者（尝试在文本头部 1000 字符内识别 "作者：xxx"）
        val headerSnippet = rawText.take(1000)
        val authorMatch = Regex("""(?:作者|著|文)\s*[:：]\s*([^\s\r\n]{1,20})""").find(headerSnippet)
        val author = authorMatch?.groupValues?.get(1)?.trim() ?: "本地导入"

        // 智能分章
        val matches = CHAPTER_PATTERN.findAll(rawText).toList()
        val chapters = mutableListOf<BookChapter>()

        val bookUrl = "local://$bookId"

        if (matches.isEmpty()) {
            // 没有识别出标准章节标题，按每 5000 字符分段
            val chunkSize = 5000
            var chunkIndex = 0
            var start = 0
            while (start < rawText.length) {
                val end = minOf(start + chunkSize, rawText.length)
                val chunkContent = rawText.substring(start, end).trim()
                val chapterTitle = "第 ${chunkIndex + 1} 部分"
                val chapterFile = File(bookDir, "chapter_$chunkIndex.txt")
                chapterFile.writeText(chunkContent, Charsets.UTF_8)

                chapters.add(
                    BookChapter(
                        url = chapterFile.absolutePath,
                        title = chapterTitle,
                        bookUrl = bookUrl,
                        index = chunkIndex
                    )
                )
                start = end
                chunkIndex++
            }
        } else {
            // 如果第一章之前有内容，作为前言/序章
            val firstMatch = matches.first()
            if (firstMatch.range.first > 0) {
                val introContent = rawText.substring(0, firstMatch.range.first).trim()
                if (introContent.isNotEmpty()) {
                    val chapterFile = File(bookDir, "chapter_0.txt")
                    chapterFile.writeText(introContent, Charsets.UTF_8)
                    chapters.add(
                        BookChapter(
                            url = chapterFile.absolutePath,
                            title = "序言 / 前言",
                            bookUrl = bookUrl,
                            index = 0
                        )
                    )
                }
            }

            for (i in matches.indices) {
                val match = matches[i]
                val title = match.value.trim()
                val contentStart = match.range.last + 1
                val contentEnd = if (i + 1 < matches.size) matches[i + 1].range.first else rawText.length
                val content = if (contentStart < contentEnd) rawText.substring(contentStart, contentEnd).trim() else ""

                val chapterIndex = chapters.size
                val chapterFile = File(bookDir, "chapter_$chapterIndex.txt")
                chapterFile.writeText("$title\n\n$content", Charsets.UTF_8)

                chapters.add(
                    BookChapter(
                        url = chapterFile.absolutePath,
                        title = title,
                        bookUrl = bookUrl,
                        index = chapterIndex
                    )
                )
            }
        }

        val book = Book(
            bookUrl = bookUrl,
            name = bookName,
            author = author,
            kind = "本地书籍",
            type = 3, // 3: 本地
            origin = "local",
            originName = "本地导入",
            tocUrl = bookUrl,
            totalChapterNum = chapters.size,
            latestChapterTitle = chapters.lastOrNull()?.title,
            latestChapterTime = System.currentTimeMillis()
        )

        AppDatabase.insertOrUpdateBook(book)
        AppDatabase.saveChapters(bookUrl, chapters)

        book
    }

    /**
     * 读取本地章节内容
     */
    suspend fun loadChapterContent(chapter: BookChapter): String = withContext(Dispatchers.IO) {
        val file = File(chapter.url)
        if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            "无法加载本地章节内容，文件可能已被移动或删除。\n路径: ${chapter.url}"
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
