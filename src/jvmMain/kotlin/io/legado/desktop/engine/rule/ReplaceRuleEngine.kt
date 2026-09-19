package io.legado.desktop.engine.rule

import io.legado.desktop.data.db.AppDatabase
import io.legado.desktop.data.model.Book
import io.legado.desktop.data.model.BookSource
import io.legado.desktop.data.model.ReplaceRule

object ReplaceRuleEngine {

    suspend fun applyRules(
        rawContent: String,
        book: Book? = null,
        source: BookSource? = null
    ): String {
        var text = rawContent
        if (text.isBlank()) return text

        val rules = AppDatabase.getReplaceRules().filter { it.isEnabled }
        if (rules.isEmpty()) return text

        for (rule in rules) {
            if (!isRuleInScope(rule, book, source)) continue

            try {
                text = if (rule.isRegex) {
                    text.replace(Regex(rule.pattern), rule.replacement)
                } else {
                    text.replace(rule.pattern, rule.replacement)
                }
            } catch (e: Exception) {
                // Ignore regex syntax errors during text replacement
            }
        }

        return text
    }

    private fun isRuleInScope(rule: ReplaceRule, book: Book?, source: BookSource?): Boolean {
        val scope = rule.scope?.trim()
        if (scope.isNullOrEmpty()) return true // Global rule

        val scopes = scope.split("[,;，；]".toRegex()).map { it.trim() }.filter { it.isNotEmpty() }
        for (s in scopes) {
            if (book != null && (book.name.contains(s) || book.bookUrl.contains(s))) return true
            if (source != null && (source.bookSourceName.contains(s) || source.bookSourceUrl.contains(s))) return true
        }
        return false
    }
}
