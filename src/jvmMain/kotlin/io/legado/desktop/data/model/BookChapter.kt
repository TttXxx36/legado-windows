package io.legado.desktop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookChapter(
    val url: String,               // 章节链接
    val title: String,             // 章节标题
    val bookUrl: String,           // 所属书籍链接
    val index: Int,                // 章节排序序号
    var isVip: Boolean = false,    // 是否 VIP 章节
    var isPay: Boolean = false,    // 是否付费章节
    var tag: String? = null,       // 章节标签
    var variable: String? = null   // 变量
)
