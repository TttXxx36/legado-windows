# Legado Desktop (Windows 原生开源阅读器)

<p align="center">
  <img src="https://raw.githubusercontent.com/HapeLee/legado-with-MD3/master/app/src/main/ic_launcher-web.png" width="128" height="128" alt="Legado Desktop Logo" />
</p>

<p align="center">
  <b>基于 Compose Multiplatform 与 Material Design 3 的 Windows 原生开源阅读客户端</b><br/>
  深度兼容移动端经典 <a href="https://github.com/HapeLee/legado-with-MD3">legado-with-MD3</a> 书源生态与核心功能
</p>

<p align="center">
  <a href="https://github.com/TttXxx36/legado-windows/releases/latest"><img src="https://img.shields.io/github/v/release/TttXxx36/legado-windows?style=flat-square&color=blue" alt="Latest Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-green.svg?style=flat-square" alt="License" /></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.0.20-purple.svg?style=flat-square" alt="Kotlin" /></a>
  <a href="https://www.jetbrains.com/lp/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose_Multiplatform-1.6.11-blue.svg?style=flat-square" alt="Compose Desktop" /></a>
  <a href="https://github.com/TttXxx36/legado-windows/actions"><img src="https://img.shields.io/badge/platform-Windows%20x64-lightgrey.svg?style=flat-square" alt="Platform" /></a>
</p>

---

## 📖 项目简介 (Introduction)

**Legado Desktop** 是专为 Windows 平台打造的现代化、原生级桌面阅读软件。灵感与架构传承自 Android 端著名开源阅读器 **[legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)**。

在充分尊重 PC 桌面交互习惯的同时，保留并扩展了 Legado 最为核心的书源解析规则、净化替换系统、WebDAV 云端同步、以及局域网 Web 阅读服务。让您在电脑前同样能够享受无缝、沉浸、纯粹的阅读体验。

---

## ✨ 核心特性 (Features)

### 🎨 Material Design 3 桌面级美学
- **自适应宽屏布局**：左侧导航导轨 (Navigation Rail) + 右侧多功能区，专为 1080P/2K/4K 大屏适配。
- **现代化网格书架**：多列自适应卡片式书架，支持阅读进度直观展示与快速置顶管理。
- **沉浸式阅读器**：
  - **单页 / 双页模式切换**：大屏双栏排版阅读，体验纸质书翻阅感。
  - **漫画/瀑布流模式**：连续纵向卷轴拼图，专为图文章节与漫画源深度优化。
  - **丰富的主题与排版**：支持自定义字号、行高、背景色（护眼浅绿、羊皮纸、深邃夜间暗黑等）。

### ⚡ 强大的书源规则引擎 (Legado 3.0 兼容)
- **多维度规则解析器**：完整支持 Jsoup CSS Selector、XPath 1.0/2.0、正则表达式提取与替换。
- **内置 Rhino JavaScript 沙箱**：完美执行包含 `java.ajax`、`java.base64Encode` 等高级 JS 动态解析脚本。
- **并发跨书源检索**：毫秒级多源并发异步查询，精准展示来源、最新章节与作者信息。
- **正文净化与内容替换**：支持正则批量广告过滤与段落清理，带来极致纯净的正文阅读。

### 🔄 多端生态联动与同步 (Ecosystem Sync)
- **WebDAV 双向云同步**：
  - 与 Android 端 Legado 无缝共享阅读进度、书架数据、书签笔记与自定义书源。
  - 支持自动备份与手动冲突合并策略。
- **局域网嵌入式 Web 服务器**：
  - 内置基于 Ktor 的轻量级 Web 仪表盘（默认端口 `1122`）。
  - 支持局域网内通过手机浏览器、平板直接访问书架与阅读，提供丰富的 RESTful API 接口。

### 🔊 桌面原生级功能增强
- **Windows 原生 TTS 朗读**：利用 Windows 底层 `System.Speech` SAPI 语音合成引擎，提供自然流畅的离线听书能力。
- **轻量本地化存储**：基于 SQLite 本地数据库，数据全掌控于本地 `%APPDATA%\LegadoDesktop\legado.db`，快速可靠。

---

## 🖥 界面预览 (Screenshots)

| 书架管理 (Bookshelf Grid) | 沉浸阅读 (Reader View) |
| :---: | :---: |
| 现代化卡片式书架，进度可视与书源状态监测 | 单页/双页平铺排版，自定义字体与主题样式 |

| 全网搜索 (Multi-Source Search) | 局域网 Web 服务 (Embedded Web) |
| :---: | :---: |
| 多书源并行搜索，快速筛选优选书源 | 手机扫码直连，跨平台共享阅读与进度 |

---

## 🚀 快速开始 (Quick Start)

### 方式一：下载预编译版本 (推荐)
前往 **[Releases 页面](https://github.com/TttXxx36/legado-windows/releases/latest)** 选择合适版本：

| 格式 | 文件名 | 说明 |
| :--- | :--- | :--- |
| **EXE 安装包 (推荐)** | `LegadoDesktop-1.0.0.exe` | 现代化向导式安装，自动创建桌面及开始菜单快捷方式，**内置 JRE，免装 Java** |
| **MSI 安装包** | `LegadoDesktop-1.0.0.msi` | Windows Installer 原生安装包，支持静默部署 |
| **免安装便携版 (推荐)** | `LegadoDesktop-windows-x64-1.0.0-portable.zip` | 解压即用绿色版，双击 `LegadoDesktop.exe` 即可启动，**零依赖免配置** |
| **可执行 JAR** | `LegadoDesktop-windows-x64-1.0.0.jar` | 跨平台 Uber-JAR，需 Java 17/21 环境 (`java -jar`) |

### 方式二：从源码编译构建
```powershell
# 1. 克隆本仓库
git clone https://github.com/TttXxx36/legado-windows.git
cd legado-windows

# 2. 运行自动化单元测试
.\gradlew.bat jvmTest

# 3. 直接在开发环境下运行
.\gradlew.bat run

# 4. 打包生成独立的可执行 Jar 包
.\gradlew.bat packageUberJarForCurrentOS
# 打包产物输出路径: build\compose\jars\LegadoDesktop-windows-x64-1.0.0.jar
```

---

## 🛠 技术架构 (Tech Stack)

```
legado-windows
├── UI Layer (Compose Multiplatform Desktop)
│   ├── Material Design 3 (Dark / Light Theme)
│   ├── Navigation Rail & Responsive Layout
│   └── Views (Bookshelf, Reader, Search, WebDAV, WebServer, Settings)
├── Core Engine
│   ├── RuleAnalyzer (Jsoup CSS Selector + XPath + Regex)
│   ├── JsEngine (Mozilla Rhino Sandbox + Java Host Extensions)
│   ├── BookSourceEngine (Legado 3.0 JSON Specification Parser)
│   ├── ReplaceRuleEngine (Regex-based advertisement & text cleaner)
│   └── TtsEngine (Windows PowerShell System.Speech SAPI Bridge)
├── Data & Network Layer
│   ├── SQLite (JDBC Driver + Local %APPDATA% Storage)
│   ├── Ktor CIO HTTP Client (TLS/SSL + Concurrent Async Networking)
│   ├── WebDavSync (Two-way cloud synchronization)
│   └── LegadoWebServer (Embedded LAN Web Dashboard & REST API on port 1122)
└── Testing
    └── JUnit 4 + Kotlin Coroutines Test Suite
```

---

## 🤝 鸣谢与致敬 (Acknowledgments)

- **[gedoor/legado](https://github.com/gedoor/legado)**：传奇开源阅读项目的奠基之作。
- **[HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)**：极具美感与工匠精神的 Material Design 3 移动端实现。
- **[JetBrains Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform)**：优雅高效的跨平台声明式 UI 框架。

---

## 📄 开源许可证 (License)

本项目遵循 [GNU General Public License v3.0 (GPL-3.0)](LICENSE) 开源协议。
欢迎提交 Issue 和 Pull Request 一同完善 Windows 生态下的阅读体验！
