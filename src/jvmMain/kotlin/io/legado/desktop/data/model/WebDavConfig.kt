package io.legado.desktop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WebDavConfig(
    var url: String = "",
    var username: String = "",
    var password: String = "",
    var rootDir: String = "legado"
)
