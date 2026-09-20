# Legado Desktop 安全审计与合规审查报告 (AUDIT_REPORT)

**报告版本**：v1.2.0  
**审查日期**：2026-09-20  
**审计范围**：`io.legado.desktop` 全部核心代码、本地存储、网络交互、JS 沙箱、Win32 全局热键伴侣与第三方开源依赖。

---

## 1. 安全架构与隐私隔离审查

### 1.1 数据隔离与文件系统沙箱 (AppData Isolation)
- **审查结论**：**通过 (SECURE)**。
- **技术实现**：
  - 应用所有本地持久化数据严格限定在操作系统分配的标准用户数据目录中：`%APPDATA%\LegadoDesktop\`。
  - 本地 SQLite 数据库文件定位为 `%APPDATA%\LegadoDesktop\legado.db`，本地书籍正文切片存储于 `%APPDATA%\LegadoDesktop\local_books\`。
  - 严禁向 Windows 系统目录（`C:\Windows`、`Program Files`）或注册表写入非授权用户配置，卸载或清理时用户数据边界清晰。

### 1.2 JavaScript 规则沙箱安全审查 (JS Sandbox Hardening)
- **审查结论**：**通过 (SECURE)**。
- **威胁建模**：书源由第三方或公开网络提供，恶意书源可能试图通过脚本执行本地命令、读取敏感文件或植入木马。
- **防护措施**：
  - 采用 **Mozilla Rhino** 引擎，使用密封上下文（`Context.enter()`）运行。
  - 仅显式注入受控的白名单宿主方法：`java.ajax()`、`java.base64Encode()`、`java.base64Decode()`、`java.put()`、`java.get()`。
  - 阻断 Java 原生反射与进程创建类（禁止调用 `java.lang.Runtime.getRuntime().exec()`、`java.lang.ProcessBuilder`），防止恶意代码突破执行环境。

### 1.3 网络通信与凭据加密安全 (Network & Credentials)
- **审查结论**：**通过 (SECURE)**。
- **技术规范**：
  - 全站网络请求基于 OkHttp 4 与 Ktor 引擎，强制启用 TLS 1.2 / TLS 1.3 证书握手校验，杜绝明文降级。
  - WebDAV 云同步账号与密码保存在本地 SQLite 数据库中，仅在与用户指定的 WebDAV 节点通信时通过带有 Basic/Bearer 鉴权的加密通道传输，绝不向任何第三方遥测服务器上报。

### 1.4 本地原生伴侣进程与字体文件解析安全 (Native Process & Font Security)
- **审查结论**：**通过 (SECURE)**。
- **技术规范**：
  - 全局硬件热键伴侣程序（`legado-hotkey.exe`）为本仓库源码编译产物（5KB），仅包含调用 Win32 `GetAsyncKeyState` 探测特定多媒体按键的只读循环，不包含任何网络请求、文件修改或注入模块。
  - 伴侣进程生命周期与主应用程序强绑定（主程序退出时通过 JVM Shutdown Hook 强制销毁），避免常驻驻留。
  - 外部字体加载严格限定于标准 `.ttf` / `.otf` 文件，由 Skia 底层经过格式有效性校验与异常保护（自动回退至系统默认字体），防止损坏字体文件引发 JVM 进程崩溃。

---

## 2. 代码规范与工程合规审计

- **编码与跨平台无损 I/O**：全库所有文件读写均显式指定字符集（`-Encoding utf8` / `Charsets.UTF_8`），规避了 Windows 默认 GBK 环境下 CJK 乱码或 BOM 损坏风险。
- **静态安全扫描防误报规范**：自动化测试中严格遵循规则，动态组装假密钥与凭据（不硬编码 `sk-...` 等连续敏感特征），有效防止触发安全扫描工具的误告警。
- **防破坏性版本控制规则**：严格执行无损 Git 规范，不使用破坏性指令改写代码历史。

---

## 3. 开源许可证兼容性审查 (License Compliance)

Legado Desktop 遵循 **GNU General Public License v3.0 (GPL-3.0)**。本工程对所有直接和传递依赖项的许可证进行了合规比对：

| 依赖库 (Dependency) | 当前使用版本 | 许可证类型 (License) | GPL-3.0 兼容性 | 审查意见 |
| :--- | :--- | :--- | :--- | :--- |
| **JetBrains Compose Multiplatform** | 1.6.11 | Apache License 2.0 | **兼容** | 允许商业使用与修改 |
| **Kotlin Standard Library & Coroutines** | 2.0.20 / 1.8.1 | Apache License 2.0 | **兼容** | 官方核心运行时库 |
| **Square OkHttp** | 4.12.0 | Apache License 2.0 | **兼容** | 成熟网络客户端 |
| **Jsoup HTML Parser** | 1.18.1 | MIT License | **完全兼容** | 宽松开源许可 |
| **Mozilla Rhino** | 1.7.15 | MPL 2.0 | **完全兼容** | 弱 Copyleft，作为独立类库调用符合规范 |
| **Xerial SQLite JDBC** | 3.46.1.0 | Apache License 2.0 | **兼容** | 驱动层封装规范 |

**综合审计结论**：本项目整体依赖结构清晰，许可证流转合规，无任何专有闭源或污染性协议冲突。
