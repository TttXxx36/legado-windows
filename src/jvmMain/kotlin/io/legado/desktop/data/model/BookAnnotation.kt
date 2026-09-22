package io.legado.desktop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookAnnotation(
    val id: Long = System.currentTimeMillis(),
    val bookUrl: String,
    val bookName: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val selectedText: String,
    val note: String = "",
    val colorType: String = "YELLOW", // "YELLOW", "GREEN", "PURPLE", "UNDERLINE"
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
