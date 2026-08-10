# GTP Runtime

GTP Runtime 是一个面向 Android 游戏中文翻译的免 Root 运行时研究项目。仓库当前只固化领域词汇、证据边界、上游来源审计和前置架构闸门；**尚未建立产品代码或模块树**。

## 当前状态

已在隔离原型中观察到：

- 目标游戏 base APK 与 arm64 split 的摘要保持不变；
- 宿主拥有的 native 探针进入目标 guest 进程；
- 探针在该进程观察到 `libunity.so` 与 `libil2cpp.so`。

这些证据仅证明 native 载荷入口存在。它们不证明游戏已稳定运行、菜单或剧情已翻译，也不证明产品可交付。

当前最高优先级是 `docs/initial-plan.md` 定义的 Stage 0 Binder/PM 身份闸门。闸门通过前，不导入上游源码、不展开 BlackBox 重写，也不决定最终代码结构。

## 文档入口

- [`CONTEXT.md`](CONTEXT.md)：领域词汇。
- [`docs/initial-plan.md`](docs/initial-plan.md)：前置实验、决策树与停止条件。
- [`docs/audit/upstream-source-audit.md`](docs/audit/upstream-source-audit.md)：BlackBox 与 BlackReflection 的固定提交来源审计。
- [`docs/agents/`](docs/agents/)：Issue tracker、triage 标签和 domain docs 消费规则。
- [`AGENTS.md`](AGENTS.md)：仓库级 Agent 约束。

## 许可证状态

本仓库目前没有选定项目许可证，也没有导入 BlackBox、BlackReflection、microG、LSPlant 或 ShadowHook 的代码或二进制。公开可见不等于获得开源许可；项目许可证将在上游采用方式和代码边界确定后单独决策。
