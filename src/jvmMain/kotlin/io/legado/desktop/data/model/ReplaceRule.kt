package io.legado.desktop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ReplaceRule(
    val id: Long = System.currentTimeMillis(),
    var name: String = "",
    var group: String? = null,
    var pattern: String = "",
    var replacement: String = "",
    var scope: String? = null,
    var isEnabled: Boolean = true,
    var isRegex: Boolean = true,
    var order: Int = 0
)
