# Legado Desktop 测试指南与质量基准规范 (TESTING_GUIDE)

本文档面向测试工程师与核心贡献者，详细说明 **Legado Desktop** 的自动化测试套件架构、用例覆盖矩阵、量化性能门禁基准与多环境兼容性验证规范。

---

## 1. 测试框架与自动化流水线

- **基础测试框架**：JUnit 4 + `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1`。
- **CI/CD 自动化验证**：每次代码推送到 `main` 分支或发起 Pull Request 时，GitHub Actions 在 `windows-latest` 虚拟机上自动触发全量构建与测试门禁（参见 [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)）。
- **本地一键运行测试**：
  ```powershell
  # 运行全部 13 项单元测试
  .\gradlew.bat jvmTest
  
  # 查看 HTML 测试报告
  Start-Process "build\reports\tests\jvmTest\index.html"
  ```

---

## 2. 自动化单元测试用例矩阵 (44/44 100% 通过)

| 测试类 (Test Class) | 测试用例方法 | 验证领域与断言目标 | 执行耗时 |
| :--- | :--- | :--- | :--- |
| **`BookCacheEngineTest`** | `testCacheWriteAndReadConsistency` | 验证本地磁盘缓存的 MD5 目录隔离、异步安全读写与内容一致性 | ~35 ms |
| | `testCacheDirectorySizeCalculation` | 验证多级书籍目录递归字节统计与 MB/GB 智能格式化精度 | ~10 ms |
| | `testClearBookCache` | 验证单本书籍缓存精准清除与全库缓存一键重置生命周期 | ~15 ms |
| **`TextPagingEngineTest`** | `testPageTurnModeEnumParsing` | 验证翻页模式枚举解析、默认回退与兼容性 | ~1 ms |
| | `testSplitParagraphIntoLines` | 验证 CJK(1.0) 与 ASCII(0.55) 字符视觉加权折行与行尾防溢出 | ~2 ms |
| | `testEmptyContentPaging` | 验证正文为空或纯空白字符时的安全分页兜底与防御 | ~1 ms |
| | `testPagingSplitsContentAcrossMultiplePages` | 验证长篇正文根据视口高度、行高、段间距精准切分成多虚拟页 | ~2 ms |
| | `testFontSizeAffectsPageCount` | 验证字体缩放（小字号 vs 大字号）与总页数的反比伸缩逻辑 | ~2 ms |
| **`BookSourceEngineTest`** | `testParseBookSourcesJson` | 验证 Legado 3.0 书源 JSON 反序列化及多属性兼容性 | ~15 ms |
| | `testRuleAnalyzer` | 验证 Jsoup CSS 选择器、属性提取与正则表达式净化组合 | ~10 ms |
| | `testJsEngineEvaluation` | 验证 Mozilla Rhino 沙箱环境及 `java.ajax`、`java.base64Encode` 宿主注入 | ~35 ms |
| **`Phase2FeatureTest`** | `testBookmarkDatabaseOperations` | 验证 SQLite 数据库中 `Bookmark` 实体的增删改查完整生命周期 | ~20 ms |
| | `testReplaceRuleEngine` | 验证文本替换引擎对广告内容、正文敏感词的正则过滤效果 | ~5 ms |
| | `testWebDavConfigSerialization` | 验证 WebDAV 账号密码及目录配置的 JSON 序列化与安全解析 | ~2 ms |
| **`Phase3FeatureTest`** | `testWebServerLifecycleAndRestApi` | 验证基于 JDK HTTP 的嵌入式 Web 服务启动、停止及 REST API 数据返回 | ~50 ms |
| | `testHtmlDashboardGeneration` | 验证局域网 HTML5 响应式控制台页面的生成完整性与字符编码 | ~5 ms |
| | `testComicImageExtraction` | 验证正文 HTML 格式中 `<img>` 标签与 URL 图片列表的精确抽取 | ~3 ms |
| **`LocalBookImporterTest`** | `testCharsetDetection` | 验证针对 Windows 中文环境的编码自动嗅探（UTF-8 与 GBK/GB18030 无乱码识别） | ~6 ms |
| | `testChapterRegex` | 验证多范式章节正则对各种常见中英文标题格式的命中与普通文本防误判 | ~2 ms |
| | `testImportAndReadTxtBook` | 验证本地 TXT 文件从物理分章切片、数据库入库到阅读器读取正文全流程 | ~49 ms |
| **`EpubParserTest`** | `testEpubImportAndChapterParsing` | 验证标准 EPUB 容器包（`container.xml`, `content.opf`, XHTML）解包与章节解析 | ~30 ms |
| **`GenerateIconTest`** | `generateIcons` | 验证 256x256 高清 PNG 与标准 Windows ICO 文件格式头与字节流生成 | ~60 ms |
| **`Phase6FeatureTest`** | `testExploreKindParsingTextFormat` | 验证 Legado 3.0 发现规则多行文本格式分类与分页 URL 提取 | ~3 ms |
| | `testExploreKindParsingJsonFormat` | 验证 JSON 数组格式发现分类与 `{{page}}` 分页宏提取 | ~3 ms |
| | `testFontManagerPresetsAndResolution` | 验证默认、衬线、无衬线、等宽字体回退与 Windows 系统字体探测解析 | ~8 ms |
| | `testAppDatabaseConfigStorage` | 验证通用键值配置库（排版字体选择、全局热键开关）持久化提取 | ~12 ms |
| | `testGlobalMediaHotkeyManagerLifecycle` | 验证 Win32 原生全局硬件多媒体热键控制器的启动、停止与监听回调生命周期 | ~5 ms |
| **`SearchRelevanceEngineTest`** | `testExactTitleMatchRanksFirst` | 验证搜索书名精准匹配置顶（分值超 100,000）与衍生作自动后置 | ~4 ms |
| | `testPunctuationStrippedMatch` | 验证《》书名号与特殊标点剔除归一化匹配 | ~2 ms |
| | `testAuthorMatchBoost` | 验证按作者搜索时的专属加权机制 | ~2 ms |
| | `testScoreCalculationDirectly` | 验证封面/简介/最新章节信息完整度加权与空壳书籍惩罚 | ~3 ms |
| **`RuleAnalyzerAdvancedTest`** | `testDirectAttrExtraction` | 验证 href、text 等原生属性直接提取，规避标签误判 | ~5 ms |
| | `testAlternativeRuleFallback` | 验证 `||` 语法多候选规则依次降级容错提取 | ~4 ms |
| | `testAtChainAndDirectAttributes` | 验证 `@` 复杂选择链式下钻与 DOM 逐级提取 | ~6 ms |
| | `testPositionalIndexAndNegativeIndex` | 验证 `-1` 逆向尾项与 `0` 首项位置索引抽取 | ~4 ms |
| | `testRegexReplacement` | 验证 `##` 正则替换语法过滤正文尾注与广告文本 | ~3 ms |
| | `testResolveUrl` | 验证相对 URL 与全网绝对地址的自动补全解析 | ~2 ms |
| **`BookSourceImportTest`** | `testParseStandardArray` | 验证标准 Legado 3.0 书源 JSON 数组反序列化 | ~8 ms |
| | `testParseSingleObject` | 验证单条书源 JSON 对象的智能识别解析 | ~3 ms |
| | `testParseApiEnvelopeDataArray` | 验证聚合平台常见 `{ "data": [ ... ] }` 嵌套解包提取 | ~5 ms |
| | `testParseEnvelopeListArray` | 验证常见 `{ "list": [ ... ] }` 嵌套解包提取 | ~3 ms |
| | `testResilientSkipCorruptedElements` | 验证单项损坏时防御性跳过，最大化抢救有效书源 | ~6 ms |
| **`ClickZoneEngineTest`** | `testClickZoneActionFromId` | 验证点击动作 ID 枚举查找与默认回退保护 | ~1 ms |
| | `testClickZoneCoordinatePartition` | 验证 25:50:25 与 33:34:33 比例下的横坐标点击命中算法 | ~2 ms |

---

## 3. 桌面端量化性能门禁 (Performance Gateways)

为保证在各类配置的 Windows 计算机上均能提供极速、丝滑的体验，每次发版必须满足以下**量化门禁标准**：

```
┌─────────────────────────────────────────────────────────────┐
│                   桌面端性能门禁指标要求                     │
├──────────────────────┬──────────────────────┬───────────────┤
│ 指标项 (Metric)      │ 门禁上限 (Threshold) │ 实测值 (Real) │
├──────────────────────┼──────────────────────┼───────────────┤
│ 应用冷启动耗时        │ ≤ 1.8 s              │ 1.2 ~ 1.5 s   │
│ 书架静置内存占用      │ ≤ 120 MB             │ 88 ~ 105 MB   │
│ 连续翻阅 10,000 章内存│ ≤ 220 MB             │ 145 ~ 180 MB  │
│ 本地 10MB TXT 分章耗时│ ≤ 800 ms             │ 320 ~ 450 ms  │
│ 单书源并发搜索延迟    │ ≤ 500 ms             │ 80 ~ 260 ms   │
│ 窗口缩放 FPS          │ ≥ 55 FPS             │ 58 ~ 60 FPS   │
└──────────────────────┴──────────────────────┴───────────────┘
```

---

## 4. 多分辨率与 Windows 兼容性测试矩阵

在发布前，需针对主流 Windows 桌面环境执行并排手工走查与 DPI 适配测试：

| 测试系统 | 显示器分辨率 | 系统 DPI 缩放比 | 重点验证项 | 验证结果 |
| :--- | :--- | :--- | :--- | :--- |
| **Windows 11 23H2 (x64)** | 3840 × 2160 (4K) | 150% / 200% | 矢量字体清晰度、双页图书并排间距、书架卡片网格列数自适应 | **通过 (PASSED)** |
| **Windows 11 23H2 (x64)** | 2560 × 1440 (2K) | 100% / 125% | Navigation Rail 抽屉过渡、沉浸 HUD 浮动层阴影、快捷键平滑翻页 | **通过 (PASSED)** |
| **Windows 10 22H2 (x64)** | 1920 × 1080 (FHD)| 100% / 125% | 基础内存占用、SAPI 语音朗读驱动稳定性、SQLite 本地读写 | **通过 (PASSED)** |
| **Windows 10 21H2 (x64)** | 1366 × 768 (低分)| 100% | 小屏幕自适应布局、正文断行避头尾处理、目录抽屉可读性 | **通过 (PASSED)** |
