package io.legado.desktop.engine.rule

import io.legado.desktop.engine.script.JsEngine
import org.jsoup.nodes.Element
import java.net.URI

object RuleAnalyzer {

    fun extractList(element: Element, rule: String?): List<Element> {
        if (rule.isNullOrBlank()) return listOf(element)
        val cleanRule = rule.trim()

        // Handle @css: or normal CSS
        val cssSelector = cleanRule.removePrefix("@css:").trim()
        return try {
            element.select(cssSelector)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun extractString(
        element: Element,
        rule: String?,
        baseUrl: String = ""
    ): String {
        if (rule.isNullOrBlank()) return ""

        var currentText = ""
        val trimmed = rule.trim()

        // 1. Handle JavaScript blocks
        if (trimmed.startsWith("<js>") && trimmed.endsWith("</js>")) {
            val script = trimmed.removeSurrounding("<js>", "</js>").trim()
            val jsRes = JsEngine.eval(script, result = element.outerHtml(), baseUrl = baseUrl)
            return jsRes?.toString() ?: ""
        }

        // 2. Handle Regex replacement ##pattern##replace
        val regexParts = trimmed.split("##")
        val mainRule = regexParts[0].trim()

        // 3. Extract via CSS / Attribute
        if (mainRule.isNotEmpty()) {
            val ruleTokens = mainRule.split("@")
            val selector = ruleTokens[0].trim()
            val attr = if (ruleTokens.size > 1) ruleTokens[1].trim() else "text"

            val targetEl = if (selector.isNotEmpty()) {
                val cleanSelector = selector
                    .replace("class.", ".")
                    .replace("id.", "#")
                    .replace("tag.", "")
                element.selectFirst(cleanSelector)
            } else {
                element
            }

            currentText = when (attr.lowercase()) {
                "text" -> targetEl?.text() ?: ""
                "textnodes" -> targetEl?.textNodes()?.joinToString("\n") { it.text().trim() } ?: ""
                "html" -> targetEl?.html() ?: ""
                "href" -> targetEl?.attr("href") ?: ""
                "src" -> targetEl?.attr("src") ?: ""
                else -> if (attr.isNotEmpty()) targetEl?.attr(attr) ?: "" else targetEl?.text() ?: ""
            }
        }

        // 4. Apply regex replacements if present
        if (regexParts.size >= 3) {
            val pattern = regexParts[1]
            val replacement = regexParts[2]
            try {
                currentText = currentText.replace(Regex(pattern), replacement)
            } catch (e: Exception) {
                // Ignore regex errors
            }
        }

        // 5. If it is a relative URL and attr is href/src, resolve to absolute URL
        if ((trimmed.contains("@href") || trimmed.contains("@src")) && baseUrl.isNotEmpty()) {
            currentText = resolveUrl(baseUrl, currentText)
        }

        return currentText.trim()
    }

    fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val trimmed = relativeUrl.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (e: Exception) {
            trimmed
        }
    }
}
