package io.legado.desktop.engine.rule

import io.legado.desktop.engine.script.JsEngine
import org.jsoup.nodes.Element
import java.net.URI

object RuleAnalyzer {

    private val DIRECT_ATTRS = setOf("text", "textnodes", "html", "href", "src", "content", "title", "alt", "value")

    /**
     * Extract a list of Elements using Legado 3.0 rule syntax.
     * Supports:
     * - '||' fallback alternative selectors
     * - '@' hierarchy chaining (e.g. table@tr@td@a)
     * - Index filters (e.g. tr!0 to skip header, a.-1 for last, p.0 for first)
     * - Standard CSS selectors
     */
    fun extractList(element: Element, rule: String?): List<Element> {
        if (rule.isNullOrBlank()) return listOf(element)

        // 1. Handle || fallback alternative rules
        if (rule.contains("||")) {
            for (alt in rule.split("||")) {
                val subList = extractSingleList(element, alt.trim())
                if (subList.isNotEmpty()) return subList
            }
            return emptyList()
        }

        return extractSingleList(element, rule.trim())
    }

    private fun extractSingleList(element: Element, rawRule: String): List<Element> {
        if (rawRule.isBlank()) return listOf(element)
        val cleanRule = rawRule.removePrefix("@css:").trim()

        // If rule has hierarchy @ (e.g. table@tr@td@a or class.c wrap@li or tbody@tr!0)
        if (cleanRule.contains("@")) {
            val tokens = cleanRule.split("@")
            var currentElements = listOf(element)

            for (token in tokens) {
                val trimmedToken = token.trim()
                if (trimmedToken.isEmpty()) continue

                val (selector, indexOp) = parseTokenAndIndex(trimmedToken)
                val cleanSelector = cleanCssSelector(selector)

                currentElements = currentElements.flatMap { parent ->
                    val selected = if (cleanSelector.isNotEmpty()) parent.select(cleanSelector) else listOf(parent)
                    applyIndexOp(selected, indexOp)
                }

                if (currentElements.isEmpty()) break
            }
            return currentElements
        }

        // Standard CSS selector
        val (selector, indexOp) = parseTokenAndIndex(cleanRule)
        val cleanSelector = cleanCssSelector(selector)
        return try {
            val selected = element.select(cleanSelector)
            applyIndexOp(selected, indexOp)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extract a single string value from an Element.
     * Supports:
     * - '||' fallback alternative rules
     * - Direct attributes: 'href', 'text', 'html', '@href', '@text', etc.
     * - JavaScript blocks: <js>...</js> and @js:...
     * - Multi-token hierarchy: class.crumbs@tag.a.-1@text
     * - Regex replacements: ##pattern##replacement or ##pattern (replace with empty)
     * - Automatic relative URL resolution
     */
    fun extractString(
        element: Element,
        rule: String?,
        baseUrl: String = ""
    ): String {
        if (rule.isNullOrBlank()) return ""

        // 1. Handle || fallback alternative rules
        if (rule.contains("||")) {
            for (alt in rule.split("||")) {
                val res = extractSingleString(element, alt.trim(), baseUrl)
                if (res.isNotBlank()) return res
            }
            return ""
        }

        return extractSingleString(element, rule.trim(), baseUrl)
    }

    private fun extractSingleString(
        element: Element,
        rawRule: String,
        baseUrl: String
    ): String {
        val trimmed = rawRule.trim()
        if (trimmed.isEmpty()) return ""

        // 1. Handle JavaScript blocks
        if (trimmed.startsWith("<js>") && trimmed.endsWith("</js>")) {
            val script = trimmed.removeSurrounding("<js>", "</js>").trim()
            val jsRes = JsEngine.eval(script, result = element.outerHtml(), baseUrl = baseUrl)
            return jsRes?.toString()?.trim() ?: ""
        }
        if (trimmed.startsWith("@js:")) {
            val script = trimmed.removePrefix("@js:").trim()
            val jsRes = JsEngine.eval(script, result = element.outerHtml(), baseUrl = baseUrl)
            return jsRes?.toString()?.trim() ?: ""
        }

        // 2. Handle Regex replacement ##pattern##replace or ##pattern
        val regexParts = trimmed.split("##")
        val mainRule = regexParts[0].trim()

        var currentText = ""
        var isUrlAttr = false

        if (mainRule.isNotEmpty()) {
            val lowerMain = mainRule.removePrefix("@").lowercase()

            // 3. Direct attribute check on current element
            if (DIRECT_ATTRS.contains(lowerMain)) {
                currentText = extractAttrFromElement(element, lowerMain)
                if (lowerMain == "href" || lowerMain == "src") isUrlAttr = true
            } else if (mainRule.contains("@")) {
                val tokens = mainRule.split("@").filter { it.isNotBlank() }
                if (tokens.isNotEmpty()) {
                    val lastToken = tokens.last().trim()
                    val lowerLast = lastToken.lowercase()

                    val (attrName, selectorTokens) = if (DIRECT_ATTRS.contains(lowerLast) || lowerLast.startsWith("attr.")) {
                        lowerLast.removePrefix("attr.") to tokens.dropLast(1)
                    } else {
                        "text" to tokens
                    }

                    if (attrName == "href" || attrName == "src") isUrlAttr = true

                    // Navigate selectorTokens to find target Element
                    var currentEl: Element? = element
                    for (tok in selectorTokens) {
                        if (currentEl == null) break
                        val (sel, indexOp) = parseTokenAndIndex(tok)
                        val cleanSel = cleanCssSelector(sel)
                        val matched = if (cleanSel.isNotEmpty()) currentEl.select(cleanSel) else listOf(currentEl)
                        currentEl = applyIndexOp(matched, indexOp).firstOrNull()
                    }

                    if (currentEl != null) {
                        currentText = extractAttrFromElement(currentEl, attrName)
                    }
                }
            } else {
                // Simple selector or selector@text
                val (selector, indexOp) = parseTokenAndIndex(mainRule)
                val cleanSelector = cleanCssSelector(selector)
                val matched = element.select(cleanSelector)
                val target = applyIndexOp(matched, indexOp).firstOrNull()
                currentText = target?.text() ?: ""
            }
        }

        // 4. Apply regex replacements if present
        if (regexParts.size >= 2) {
            val pattern = regexParts[1]
            val replacement = if (regexParts.size >= 3) regexParts[2] else ""
            if (pattern.isNotEmpty()) {
                try {
                    currentText = currentText.replace(Regex(pattern), replacement)
                } catch (_: Exception) {}
            }
        }

        // 5. Automatic URL resolution
        if ((isUrlAttr || currentText.startsWith("/") || (!currentText.startsWith("http://") && !currentText.startsWith("https://") && (trimmed.contains("href") || trimmed.contains("src") || trimmed.contains("url")))) && baseUrl.isNotEmpty() && currentText.isNotEmpty()) {
            currentText = resolveUrl(baseUrl, currentText)
        }

        return currentText.trim()
    }

    private fun extractAttrFromElement(el: Element, attrName: String): String {
        return when (attrName.lowercase()) {
            "text" -> el.text()
            "textnodes" -> el.textNodes().joinToString("\n") { it.text().trim() }
            "html" -> el.html()
            "href" -> el.attr("href")
            "src" -> el.attr("src")
            "content" -> el.attr("content").ifEmpty { el.attr("value") }
            "title" -> el.attr("title")
            "alt" -> el.attr("alt")
            "value" -> el.attr("value")
            else -> el.attr(attrName).ifEmpty { el.text() }
        }
    }

    private fun parseTokenAndIndex(token: String): Pair<String, String?> {
        val trimmed = token.trim()
        if (trimmed.contains("!")) {
            val parts = trimmed.split("!")
            return parts[0].trim() to "!${parts[1].trim()}"
        }
        // Check for .0, .1, .-1, .-2
        val indexMatch = Regex("""^(.*)\.(-?\d+)$""").find(trimmed)
        if (indexMatch != null) {
            val sel = indexMatch.groupValues[1].trim()
            val idx = indexMatch.groupValues[2].trim()
            return sel to idx
        }
        return trimmed to null
    }

    private fun applyIndexOp(elements: List<Element>, indexOp: String?): List<Element> {
        if (elements.isEmpty() || indexOp == null) return elements

        if (indexOp.startsWith("!")) {
            val skipIndex = indexOp.removePrefix("!").toIntOrNull() ?: 0
            return elements.filterIndexed { idx, _ -> idx != skipIndex }
        }

        val idx = indexOp.toIntOrNull() ?: return elements
        return if (idx < 0) {
            val realIndex = elements.size + idx
            if (realIndex in elements.indices) listOf(elements[realIndex]) else emptyList()
        } else {
            if (idx in elements.indices) listOf(elements[idx]) else emptyList()
        }
    }

    private fun cleanCssSelector(selector: String): String {
        return selector
            .replace("class.", ".")
            .replace("id.", "#")
            .replace("tag.", "")
            .trim()
    }

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val trimmed = relativeUrl.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("javascript:")) {
            return trimmed
        }
        if (trimmed.startsWith("//")) {
            val protocol = if (baseUrl.startsWith("https://")) "https:" else "http:"
            return "$protocol$trimmed"
        }
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(trimmed).toString()
        } catch (e: Exception) {
            val cleanBase = baseUrl.trimEnd('/')
            if (trimmed.startsWith("/")) {
                try {
                    val uri = URI(baseUrl)
                    "${uri.scheme}://${uri.authority}$trimmed"
                } catch (_: Exception) {
                    "$cleanBase$trimmed"
                }
            } else {
                "$cleanBase/$trimmed"
            }
        }
    }
}
