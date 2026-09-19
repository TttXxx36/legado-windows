package io.legado.desktop.engine.local

import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

object EpubParser {

    data class EpubResult(
        val book: Book,
        val chapters: List<BookChapter>
    )

    suspend fun parseEpub(epubFile: File, localBooksDir: File): EpubResult = withContext(Dispatchers.IO) {
        require(epubFile.exists()) { "EPUB 文件不存在: ${epubFile.absolutePath}" }
        val bookId = md5("${epubFile.absolutePath}_${epubFile.lastModified()}")
        val bookDir = File(localBooksDir, bookId).apply { if (!exists()) mkdirs() }
        val bookUrl = "local://$bookId"

        ZipFile(epubFile).use { zip ->
            // 1. 读取 META-INF/container.xml 找到 OPF 路径
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw IllegalArgumentException("非法的 EPUB 文件: 缺少 META-INF/container.xml")
            val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
            val containerDoc = Jsoup.parse(containerXml, "", org.jsoup.parser.Parser.xmlParser())
            val opfPath = containerDoc.select("rootfile").attr("full-path")
            if (opfPath.isBlank()) {
                throw IllegalArgumentException("无法从 container.xml 找到 OPF 描述文件路径")
            }

            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalArgumentException("缺少 OPF 描述文件: $opfPath")
            val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
            val opfContent = zip.getInputStream(opfEntry).bufferedReader().readText()
            val opfDoc = Jsoup.parse(opfContent, "", org.jsoup.parser.Parser.xmlParser())

            // 2. 提取书籍元数据
            val title = opfDoc.select("dc|title, title").firstOrNull()?.text()?.trim()
                ?.ifBlank { epubFile.nameWithoutExtension } ?: epubFile.nameWithoutExtension
            val author = opfDoc.select("dc|creator, creator").firstOrNull()?.text()?.trim()
                ?.ifBlank { "未知作者" } ?: "未知作者"
            val intro = opfDoc.select("dc|description, description").firstOrNull()?.text()?.trim()

            // 3. 构建清单 Manifest
            val manifest = mutableMapOf<String, String>() // id -> href
            var coverHref: String? = null
            opfDoc.select("manifest > item").forEach { item ->
                val id = item.attr("id")
                val href = item.attr("href")
                val mediaType = item.attr("media-type")
                val properties = item.attr("properties")

                manifest[id] = href
                if (properties.contains("cover-image") || id.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true)) {
                    if (mediaType.startsWith("image/")) {
                        coverHref = href
                    }
                }
            }

            // 保存封面文件（如果存在）
            var localCoverPath: String? = null
            if (coverHref != null) {
                val fullCoverPath = resolvePath(opfDir, coverHref!!)
                val coverEntry = zip.getEntry(fullCoverPath)
                if (coverEntry != null) {
                    val ext = fullCoverPath.substringAfterLast(".", "jpg")
                    val coverFile = File(bookDir, "cover.$ext")
                    zip.getInputStream(coverEntry).use { input ->
                        coverFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    localCoverPath = coverFile.absolutePath
                }
            }

            // 4. 解析阅读顺序 Spine
            val spineItemIds = opfDoc.select("spine > itemref").map { it.attr("idref") }

            val chapters = mutableListOf<BookChapter>()

            for (itemId in spineItemIds) {
                val href = manifest[itemId] ?: continue
                val fullPath = resolvePath(opfDir, href)
                val entry = zip.getEntry(fullPath) ?: continue

                val htmlContent = zip.getInputStream(entry).bufferedReader().readText()
                val htmlDoc = Jsoup.parse(htmlContent)

                // 提取章节标题：优先取 h1, h2, h3, title
                val chapterTitle = htmlDoc.select("h1, h2, h3, title").firstOrNull()?.text()?.trim()
                    ?.take(50)?.ifBlank { null }
                    ?: htmlDoc.body().text().take(25).trim().ifBlank { "第 ${chapters.size + 1} 节" }

                // 提取正文文本段落
                val bodyText = htmlDoc.body().select("p, div").map { it.text().trim() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .ifBlank { htmlDoc.body().text() }

                val chapterIndex = chapters.size
                val chapterFile = File(bookDir, "chapter_$chapterIndex.txt")
                chapterFile.writeText("$chapterTitle\n\n$bodyText", Charsets.UTF_8)

                chapters.add(
                    BookChapter(
                        url = chapterFile.absolutePath,
                        title = chapterTitle,
                        bookUrl = bookUrl,
                        index = chapterIndex
                    )
                )
            }

            val book = Book(
                bookUrl = bookUrl,
                name = title,
                author = author,
                kind = "EPUB 电子书",
                coverUrl = localCoverPath,
                intro = intro,
                type = 3, // 本地书籍
                origin = "local",
                originName = "EPUB 本地导入",
                tocUrl = bookUrl,
                totalChapterNum = chapters.size,
                latestChapterTitle = chapters.lastOrNull()?.title,
                latestChapterTime = System.currentTimeMillis()
            )

            EpubResult(book, chapters)
        }
    }

    private fun resolvePath(baseDir: String, relativePath: String): String {
        val combined = if (relativePath.startsWith("/")) relativePath.removePrefix("/") else baseDir + relativePath
        val parts = combined.split("/")
        val resolved = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> {}
                ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.size - 1)
                else -> resolved.add(part)
            }
        }
        return resolved.joinToString("/")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
