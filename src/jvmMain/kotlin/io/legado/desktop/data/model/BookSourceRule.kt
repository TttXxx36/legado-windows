package io.legado.desktop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchRule(
    val checkKeyWord: String? = null,
    val bookList: String? = null,
    val name: String? = null,
    val author: String? = null,
    val intro: String? = null,
    val kind: String? = null,
    val lastChapter: String? = null,
    val updateTime: String? = null,
    val bookUrl: String? = null,
    val coverUrl: String? = null,
    val wordCount: String? = null
)

@Serializable
data class ExploreRule(
    val bookList: String? = null,
    val name: String? = null,
    val author: String? = null,
    val intro: String? = null,
    val kind: String? = null,
    val lastChapter: String? = null,
    val updateTime: String? = null,
    val bookUrl: String? = null,
    val coverUrl: String? = null,
    val wordCount: String? = null
)

@Serializable
data class BookInfoRule(
    val init: String? = null,
    val name: String? = null,
    val author: String? = null,
    val intro: String? = null,
    val kind: String? = null,
    val lastChapter: String? = null,
    val updateTime: String? = null,
    val coverUrl: String? = null,
    val tocUrl: String? = null,
    val wordCount: String? = null,
    val canReName: String? = null
)

@Serializable
data class TocRule(
    val chapterList: String? = null,
    val chapterName: String? = null,
    val chapterUrl: String? = null,
    val isVip: String? = null,
    val updateTime: String? = null,
    val nextTocUrl: String? = null
)

@Serializable
data class ContentRule(
    val content: String? = null,
    val nextContentUrl: String? = null,
    val webJs: String? = null,
    val sourceRegex: String? = null,
    val replaceRegex: String? = null,
    val imageStyle: String? = null
)

@Serializable
data class ReviewRule(
    val reviewUrl: String? = null,
    val avatar: String? = null,
    val content: String? = null,
    val date: String? = null,
    val author: String? = null
)
