package io.legado.desktop.data.model

enum class ClickZoneAction(
    val id: String,
    val title: String,
    val description: String
) {
    PAGE_PREV("page_prev", "上一页（章首切上一章）", "向上平滑翻页，若在顶部自动切至上一章"),
    PAGE_NEXT("page_next", "下一页（章尾切下一章）", "向下平滑翻页，若在底部自动切至下一章"),
    TOGGLE_MENU("toggle_menu", "呼出/隐藏菜单面板", "点击显示或隐藏顶部和底部控制栏"),
    PREV_CHAPTER("prev_chapter", "直接切换上一章", "跳过当前章节直接加载上一章"),
    NEXT_CHAPTER("next_chapter", "直接切换下一章", "跳过当前章节直接加载下一章"),
    OPEN_TOC("open_toc", "打开目录与书签", "快速弹出章节目录与书签抽屉"),
    TOGGLE_THEME("toggle_theme", "快速切换日夜间主题", "在白昼主题与夜间黑主题间轮换"),
    NONE("none", "无响应（防误触）", "点击该区域不执行任何操作");

    companion object {
        fun fromId(id: String, default: ClickZoneAction = NONE): ClickZoneAction {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: default
        }
    }
}
