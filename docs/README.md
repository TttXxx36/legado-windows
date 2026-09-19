# Legado Desktop 官方文档中心 (Documentation Hub)

欢迎查阅 **Legado Desktop (Windows 原生开源阅读客户端)** 的官方技术与工程文档中心。
本项目基于 **Kotlin 2.0 + Compose Multiplatform 1.6 + Material Design 3** 构建，深度传承并兼容经典开源阅读软件 [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3) 与 [gedoor/legado](https://github.com/gedoor/legado)。

---

## 📚 文档目录导览

```
docs/
├── README.md                      # 文档中心总览（本文档）
├── planning/                      # 规划与演进设计
│   └── ROADMAP.md                 # 完整阶段开发路线图与里程碑规格
├── testing/                       # 测试与质量保障
│   └── TESTING_GUIDE.md           # 自动化测试矩阵与量化性能基准指南
├── audit/                         # 审计与安全合规
│   └── AUDIT_REPORT.md            # 安全审查、权限隔离与开源许可证合规报告
└── breakthroughs/                 # 重大突破技术复盘
    └── CORE_BREAKTHROUGHS.md      # 双重视角：核心架构技术突破剖析与用户演进里程碑
```

---

## 🧭 模块速查

| 文档模块 | 核心内容 | 目标受众 |
| :--- | :--- | :--- |
| **[🗺️ 开发路线图 (ROADMAP)](planning/ROADMAP.md)** | Phase 1 (MVP 基线) 至 Phase 5 (EPUB 与桌面图标) 的完整里程碑演进记录与未来规划 | 开发者、产品关注者 |
| **[🧪 测试指南 (TESTING_GUIDE)](testing/TESTING_GUIDE.md)** | 13 项单元测试用例全量矩阵、CI/CD 自动化集成、冷启动 $\le 1.8\text{s}$ / 内存 $\le 120\text{MB}$ 等量化性能门禁 | QA 测试人员、核心贡献者 |
| **[🛡️ 安全审计报告 (AUDIT_REPORT)](audit/AUDIT_REPORT.md)** | AppData 数据沙箱隔离、Rhino JS 执行安全、WebDAV 传输安全、GPL-3.0 开源许可合规分析 | 技术审核、安全审计人员 |
| **[💡 核心突破剖析 (CORE_BREAKTHROUGHS)](breakthroughs/CORE_BREAKTHROUGHS.md)** | Legado 3.0 解析引擎、双页图书排版、SAPI 原生语音、EPUB 容器解包等底层技术突破与产品演进复盘 | 架构师、技术发烧友 |

---

## 快速贡献与反馈
若您在阅读文档或使用过程中发现任何问题，欢迎通过以下渠道参与共建：
- GitHub Issue 提交：[https://github.com/TttXxx36/legado-windows/issues](https://github.com/TttXxx36/legado-windows/issues)
- 提交 Pull Request：[https://github.com/TttXxx36/legado-windows/pulls](https://github.com/TttXxx36/legado-windows/pulls)
