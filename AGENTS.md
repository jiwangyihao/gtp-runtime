# 仓库指令

- 默认使用简体中文编写项目文档、Issue 和评审结论；代码标识符与外部接口保留原文。
- 添加产品代码、fork、subtree 或 vendor 上游前，先阅读 `CONTEXT.md`、`docs/initial-plan.md` 与 `docs/audit/upstream-source-audit.md`。
- Stage 0 Binder/PM 身份实验是架构闸门；未通过前，不得宣称 Binder-first、broker 或 lease 拓扑可行，也不得展开 BlackBox 大规模重写。
- `BindingLease` 只能选择容器侧路由，不能改变内核 Binder calling UID、替代服务端身份校验或改变许可证结果。
- microG 默认使用锁定提交和摘要的未修改上游发布物；不得伪造 `LICENSED`、绕过 PAIRIP/Play Integrity 或修改目标 APK。
- 任何上游文件进入仓库前，必须在逐文件来源清单中记录仓库、固定提交、原路径、摘要、版权/许可证证据、改动边界和保留的声明。根目录许可证不得被推导为所有文件的独立归属证明。
- 始终分开记录 A（许可证/GMS）、B（容器稳定性）、C（Unity/IL2CPP 翻译效果）三条证据链。native 载荷入口已验证，不等于游戏可运行、已翻译或可交付。
- 未经新的架构决策，不创建占位模块、空实现、兼容 shim 或通用插件系统。

## Agent skills

### Issue tracker

工作项使用 GitHub Issues；外部 PR 不作为统一 triage 请求入口。见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认五标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。见 `docs/agents/triage-labels.md`。

### Domain docs

采用 single-context：根目录 `CONTEXT.md` + `docs/adr/`。见 `docs/agents/domain.md`。
