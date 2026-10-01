package io.legado.desktop.engine.align

import io.legado.desktop.data.model.BookChapter
import kotlin.math.max
import kotlin.math.min

data class AlignmentResult(
    val index: Int,
    val chapter: BookChapter,
    val confidence: Float, // 0.0f - 1.0f
    val strategy: String
)

object ChapterAlignmentEngine {

    /**
     * Converts a Chinese numeral string (up to 99999) to an Integer.
     * Supports characters: 零一二两三四五六七八九十百千万
     */
    fun parseChineseNumber(chinese: String): Int? {
        val clean = chinese.trim().replace("两", "二")
        if (clean.isEmpty()) return null

        val digitMap = mapOf(
            '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
        )
        val unitMap = mapOf(
            '十' to 10, '百' to 100, '千' to 1000, '万' to 10000
        )

        var total = 0
        var currentSection = 0
        var currentDigit = 0
        var hasDigit = false

        for (ch in clean) {
            when {
                ch in digitMap -> {
                    currentDigit = digitMap[ch]!!
                    hasDigit = true
                }
                ch in unitMap -> {
                    val unit = unitMap[ch]!!
                    if (unit == 10000) {
                        currentSection = (currentSection + (if (hasDigit) currentDigit else 1)) * unit
                        total += currentSection
                        currentSection = 0
                        currentDigit = 0
                        hasDigit = false
                    } else {
                        currentSection += (if (hasDigit) currentDigit else 1) * unit
                        currentDigit = 0
                        hasDigit = false
                    }
                }
                else -> return null // Unexpected character
            }
        }
        total += currentSection + currentDigit
        return if (total > 0 || clean == "零") total else null
    }

    /**
     * Extracts chapter index integer from title.
     * Examples:
     * "第105章 决战天门山" -> 105
     * "第一百零五章 决战" -> 105
     * "105. 决战天门山" -> 105
     * "第十二回 宝钗扑蝶" -> 12
     */
    fun extractChapterNumber(title: String): Int? {
        val t = title.trim()

        // 1. "第 105 章/回/集/卷/部/篇"
        val regexArabic = """第\s*(\d+)\s*[章节回集卷部篇]""".toRegex()
        regexArabic.find(t)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }

        // 2. "第 一百零五 章/回/集/卷/部/篇"
        val regexChinese = """第\s*([零一二两三四五六七八九十百千万]+)\s*[章节回集卷部篇]""".toRegex()
        regexChinese.find(t)?.let { match ->
            parseChineseNumber(match.groupValues[1])?.let { return it }
        }

        // 3. Leading number "105. 决战" or "105 决战" or "105、决战" or "105 - 决战"
        val regexLeadingDigits = """^\s*(\d+)\s*[\.、\s\-:：]""".toRegex()
        regexLeadingDigits.find(t)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }

        return null
    }

    /**
     * Cleans chapter title by removing chapter prefixes, volume labels, and noise.
     * "第105章 决战天门山 (求月票)" -> "决战天门山"
     */
    fun cleanSubtitle(title: String): String {
        var s = title.trim()
        // Remove trailing or inner parentheses content like (求月票), [修], （加更）
        s = s.replace("""\([^\)]*\)""".toRegex(), "")
             .replace("""（[^）]*）""".toRegex(), "")
             .replace("""\[[^\]]*\]""".toRegex(), "")
             .replace("""【[^】]*】""".toRegex(), "")
             .replace("""\{[^\}]*\}""".toRegex(), "")

        // Remove volume prefix e.g. "第一卷", "卷一", "正文卷", "VIP卷"
        s = s.replace("""^第?[0-9零一二两三四五六七八九十百千万]+卷\s*""".toRegex(), "")
             .replace("""^[正外番]文卷\s*""".toRegex(), "")
             .replace("""^VIP卷\s*""".toRegex(), "")

        // Remove chapter index prefix e.g. "第105章", "第十二回", "105."
        s = s.replace("""^第\s*[0-9零一二两三四五六七八九十百千万]+\s*[章节回集卷部篇]\s*""".toRegex(), "")
             .replace("""^\s*\d+\s*[\.、\s\-:：]\s*""".toRegex(), "")

        // Remove non-word noise and punctuation
        s = s.replace("""[·•_—\-\s\r\n\t]+""".toRegex(), "")
        return s.trim()
    }

    /**
     * Computes character similarity (Levenshtein distance ratio).
     */
    fun calculateStringSimilarity(s1: String, s2: String): Float {
        if (s1.isEmpty() && s2.isEmpty()) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        if (s1 == s2) return 1.0f

        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        val distance = dp[len1][len2]
        val maxLen = max(len1, len2)
        return (1.0f - distance.toFloat() / maxLen).coerceIn(0.0f, 1.0f)
    }

    /**
     * Smartly aligns a chapter from candidate source chapters to the target book's current chapter.
     */
    fun alignChapter(
        currentTitle: String,
        currentIndex: Int,
        candidates: List<BookChapter>
    ): AlignmentResult {
        if (candidates.isEmpty()) {
            return AlignmentResult(
                index = 0,
                chapter = BookChapter(url = "", title = currentTitle, bookUrl = "", index = 0),
                confidence = 0.0f,
                strategy = "候选章节列表为空"
            )
        }

        val cleanCurrentTitle = cleanSubtitle(currentTitle)
        val currentNum = extractChapterNumber(currentTitle)

        // 1. Exact raw title match
        val exactRawIdx = candidates.indexOfFirst { it.title.trim() == currentTitle.trim() }
        if (exactRawIdx >= 0) {
            return AlignmentResult(
                index = exactRawIdx,
                chapter = candidates[exactRawIdx],
                confidence = 1.0f,
                strategy = "精确标题完全匹配"
            )
        }

        // 2. Clean subtitle match + Chapter Number match
        var bestIdx = -1
        var bestScore = -1.0f
        var bestStrategy = ""

        for ((idx, ch) in candidates.withIndex()) {
            val candidateTitle = ch.title
            val candidateClean = cleanSubtitle(candidateTitle)
            val candidateNum = extractChapterNumber(candidateTitle)

            var score = 0.0f
            var strategy = ""

            val subtitleSim = calculateStringSimilarity(cleanCurrentTitle, candidateClean)

            if (currentNum != null && candidateNum != null && currentNum == candidateNum) {
                // Same chapter number!
                if (cleanCurrentTitle.isEmpty() || candidateClean.isEmpty() || subtitleSim >= 0.7f) {
                    score = 0.98f
                    strategy = "章节序号一致 (${currentNum}) 且标题高度契合"
                } else {
                    score = 0.92f + (subtitleSim * 0.05f)
                    strategy = "章节序号一致 (${currentNum})"
                }
            } else if (cleanCurrentTitle.isNotEmpty() && candidateClean.isNotEmpty()) {
                if (cleanCurrentTitle == candidateClean) {
                    score = 0.95f
                    strategy = "核心章节标题完全一致 ($cleanCurrentTitle)"
                } else if (subtitleSim >= 0.8f) {
                    score = 0.80f + (subtitleSim * 0.15f)
                    strategy = "标题相似度极高 (${(subtitleSim * 100).toInt()}%)"
                } else if (cleanCurrentTitle.contains(candidateClean) || candidateClean.contains(cleanCurrentTitle)) {
                    score = 0.82f
                    strategy = "章节标题包含匹配"
                }
            }

            // Proximity bonus: if index is close to currentIndex, give small confidence boost
            val indexDiff = kotlin.math.abs(idx - currentIndex)
            if (indexDiff <= 3 && score > 0.7f) {
                score = min(1.0f, score + 0.03f)
            }

            if (score > bestScore) {
                bestScore = score
                bestIdx = idx
                bestStrategy = strategy
            }
        }

        if (bestIdx >= 0 && bestScore >= 0.7f) {
            return AlignmentResult(
                index = bestIdx,
                chapter = candidates[bestIdx],
                confidence = bestScore,
                strategy = bestStrategy
            )
        }

        // 3. Fallback: Proximity by current index
        val fallbackIdx = currentIndex.coerceIn(0, candidates.size - 1)
        val fallbackCh = candidates[fallbackIdx]
        val fallbackClean = cleanSubtitle(fallbackCh.title)
        val fallbackSim = calculateStringSimilarity(cleanCurrentTitle, fallbackClean)

        return AlignmentResult(
            index = fallbackIdx,
            chapter = fallbackCh,
            confidence = (0.4f + fallbackSim * 0.2f).coerceIn(0.3f, 0.65f),
            strategy = "序号位置保底推荐 (第 ${fallbackIdx + 1} 章)"
        )
    }
}
