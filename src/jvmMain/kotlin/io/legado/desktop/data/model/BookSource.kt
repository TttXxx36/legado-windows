package io.legado.desktop.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BookSource(
    val bookSourceUrl: String,                     // 书源唯一标识 URL (主键)
    var bookSourceName: String = "",               // 书源名称
    var bookSourceGroup: String? = null,           // 书源分组
    var bookSourceType: Int = 0,                   // 0: 文本, 1: 音频, 2: 图片/漫画
    var bookSourceComment: String? = null,         // 注释说明
    var enabled: Boolean = true,                   // 是否启用
    var enabledExplore: Boolean = true,            // 是否启用发现
    var weight: Int = 0,                           // 权重
    var customOrder: Int = 0,                      // 排序序号
    var header: String? = null,                    // 自定义 HTTP 请求头 (JSON)
    var loginUrl: String? = null,                  // 登录地址
    var loginUi: String? = null,                   // 登录界面定义
    var loginCheckJs: String? = null,              // 登录检测脚本
    var searchUrl: String? = null,                 // 搜索 URL
    var exploreUrl: String? = null,                // 发现 URL
    var ruleSearch: SearchRule? = null,            // 搜索规则
    var ruleExplore: ExploreRule? = null,          // 发现规则
    var ruleBookInfo: BookInfoRule? = null,        // 书籍详情规则
    var ruleToc: TocRule? = null,                  // 目录规则
    var ruleContent: ContentRule? = null,          // 正文规则
    var ruleReview: ReviewRule? = null,            // 段评规则
    var lastUpdateTime: Long = 0L                  // 最后更新时间
)
