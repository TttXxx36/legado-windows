package io.legado.desktop.engine.search

import io.legado.desktop.data.model.Book

data class AggregatedBook(
    val primaryBook: Book,
    val candidateSources: List<Book>
) {
    val title: String get() = primaryBook.name
    val author: String get() = primaryBook.author
    val sourceCount: Int get() = candidateSources.size
}

object SearchRelevanceEngine {

    /**
     * Sort search results using intelligent relevance scoring.
     * Highest priority: exact title match, followed by punctuation-stripped match,
     * prefix match, contains match, author match, and completeness bonuses.
     */
    fun sortSearchResults(books: List<Book>, keyword: String): List<Book> {
        val cleanKeyword = normalize(keyword)
        if (cleanKeyword.isEmpty()) return books

        return books.sortedWith(
            compareByDescending<Book> { calculateScore(it, cleanKeyword) }
                .thenByDescending { it.originName ?: "" }
        )
    }

    /**
     * Intelligently aggregates books with the same title & author into a single AggregatedBook card,
     * showing the best candidate source while preserving all alternate sources for seamless switching.
     */
    fun aggregateSearchResults(books: List<Book>, keyword: String): List<AggregatedBook> {
        val sorted = sortSearchResults(books, keyword)
        val cleanKeyword = normalize(keyword)

        // Group books by normalized title and author
        val grouped = linkedMapOf<String, MutableList<Book>>()
        for (book in sorted) {
            val key = "${normalize(book.name)}|${normalize(book.author ?: "")}"
            grouped.getOrPut(key) { mutableListOf() }.add(book)
        }

        return grouped.values.map { candidates ->
            val primary = candidates.maxByOrNull { calculateScore(it, cleanKeyword) } ?: candidates.first()
            AggregatedBook(
                primaryBook = primary,
                candidateSources = candidates
            )
        }.sortedByDescending { calculateScore(it.primaryBook, cleanKeyword) }
    }

    fun calculateScore(book: Book, cleanKeyword: String): Double {
        val title = normalize(book.name)
        val author = normalize(book.author ?: "")

        var score = 0.0

        // 1. Exact Title Match (Top Tier: 100,000+)
        if (title == cleanKeyword) {
            score += 100000.0
        } else {
            val titleNoPunct = stripPunctuation(title)
            val keyNoPunct = stripPunctuation(cleanKeyword)

            if (titleNoPunct == keyNoPunct && titleNoPunct.isNotEmpty()) {
                score += 90000.0
            } else if (title.startsWith(cleanKeyword)) {
                // "斗破苍穹之..." starts with keyword
                val diff = (title.length - cleanKeyword.length).coerceAtLeast(0)
                score += (40000.0 - diff * 150.0).coerceAtLeast(20000.0)
            } else if (title.contains(cleanKeyword)) {
                // "...斗破苍穹..." contains keyword
                val diff = (title.length - cleanKeyword.length).coerceAtLeast(0)
                score += (20000.0 - diff * 200.0).coerceAtLeast(5000.0)
            }
        }

        // 2. Author Match (Tier 2: 50,000+ if searching author, or secondary if author matches)
        if (author.isNotEmpty()) {
            if (author == cleanKeyword) {
                score += 50000.0
            } else if (author.contains(cleanKeyword)) {
                score += 15000.0
            }
        }

        // 3. Information completeness bonuses
        if (!book.coverUrl.isNullOrBlank()) score += 500.0
        if (!book.latestChapterTitle.isNullOrBlank()) score += 500.0
        if (!book.intro.isNullOrBlank()) score += 200.0

        // 4. Source Origin presence
        if (!book.originName.isNullOrBlank()) {
            score += 100.0
        }

        return score
    }

    private fun normalize(str: String): String {
        return str.trim()
            .removeSurrounding("《", "》")
            .removeSurrounding("【", "】")
            .removeSurrounding("[", "]")
            .lowercase()
    }

    private fun stripPunctuation(str: String): String {
        return str.replace(Regex("""[\s\p{Punct}\p{P}·]"""), "")
    }
}
