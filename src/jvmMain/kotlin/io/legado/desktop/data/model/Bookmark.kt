package io.legado.desktop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Bookmark(
    val id: Long = System.currentTimeMillis(),
    val bookUrl: String,
    val bookName: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val content: String,
    val note: String? = null,
    val time: Long = System.currentTimeMillis()
)
