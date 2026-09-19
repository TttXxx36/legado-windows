package io.legado.desktop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val bookUrl: String,                       // 书籍地址 (主键)
    var name: String = "",                     // 书名
    var author: String = "",                   // 作者
    var kind: String? = null,                  // 分类/标签
    var coverUrl: String? = null,              // 封面链接
    var intro: String? = null,                 // 简介
    var customIntro: String? = null,           // 自定义简介
    var charset: String? = null,               // 编码
    var type: Int = 0,                         // 0: 文本, 1: 音频, 2: 漫画, 3: 本地
    var group: Int = 0,                        // 分组
    var latestChapterTitle: String? = null,    // 最新章节名
    var latestChapterTime: Long = 0L,          // 最新更新时间
    var lastCheckTime: Long = 0L,              // 上次检查更新时间
    var totalChapterNum: Int = 0,              // 章节总数
    var durChapterTitle: String? = null,       // 当前阅读章节名
    var durChapterIndex: Int = 0,              // 当前阅读章节序号
    var durChapterPos: Int = 0,                // 当前章节阅读字符位置
    var durChapterTime: Long = 0L,             // 上次阅读时间
    var origin: String = "",                   // 书源 URL 或本地标识
    var originName: String = "",               // 书源名称
    var tocUrl: String = "",                   // 目录 URL
    var order: Int = 0,                        // 书架自定义排序
    var originOrder: Int = 0,                  // 书源排序
    var variable: String? = null               // 变量存储
)
