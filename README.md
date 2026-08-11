# GTP Runtime

GTP Runtime 是一个面向 Android 游戏进程内中文翻译的研究项目。目标路线是：在免 Root 容器中运行原版 split APK，并在 guest 的 Unity/IL2CPP 进程内加载受控 Hook Runtime，取得原文并呈现译文。

仓库当前处于**机制验证阶段**，不是可用 MVP：

- 容器轨：确认旧基座缺少哪些系统契约、每项改造能否消除对应失败；
- GMS 轨：分离安装来源、许可服务、账号/Games、Binder/Provider/PM 身份和 Integrity 等检测面；
- Hook 轨：先在已有 Root + LSPosed 的 arm64 真机上验证 Unity/IL2CPP Hook 的加载、取文和呈现机制，再把已证明的载荷接口迁入容器。

三条轨道可独立推进、各自给出证据。任何一轨的实验通过都不代表游戏已完成翻译或产品可交付。

## 当前有价值的证据

1. **容器载荷入口**：隔离 VirtualApp 原型已把宿主 native 探针加载到目标 guest 进程，并观察到 `libunity.so` 与 `libil2cpp.so`。这只证明载荷入口存在。
2. **容器缺口**：旧基座在 Vending 初始化时混用了 guest-visible UID 与宿主 Linux UID，Provider 路由在返回 Binder 前失败；旧 SandHook 路径也缺少 JNI 实现。这些是待改造契约，不是“所有容器路线失败”的结论。
3. **GMS 系统基线**：纯 AOSP AVD 中，synthetic client 可完成未修改 microG Vending 的 `bindService → onServiceConnected → checkLicenseV2`，并保留原始负结果语义；调用方不是目标游戏。
4. **安装来源归因**：在固定 Root 真机环境和同一原版 APK 下，A/B/A/B/A 实验随 `installerPackageName` 在 `null` 与 `com.android.vending` 之间切换而稳定翻转当前 PAIRIP 初始启动结果。该结论只覆盖该游戏版本、设备和初始闸门，不代表 Play 所有权、许可证成功或 Integrity 通过。
5. **原生执行环境**：Google APIs AVD 的官方 `libndk_translation` 能加载目标 arm64 `libunity.so`/`libil2cpp.so` 并到达 PAIRIP 页面；未完成翻译 Hook 验证。
6. **Root Hook 试验台**：当前 arm64 / Android 16 真机具备 KernelSU root，且运行 `zygisk_lsposed`；目标游戏已安装。尚未证明 LSPosed 模块作用域、Hook 命中、文本取得或译文呈现。

## 当前未完成

- 没有完成经改造容器的冷启动、组件、Binder/Provider/PM、重启或 Unity 稳定性验收；
- 没有在容器内完成目标游戏到 GMS/Vending 的一致身份链；
- 没有枚举完目标运行路径实际触发的全部 GMS/PAIRIP/Integrity 检测；
- 没有在 Root/LSPosed 环境取得一条目标游戏真实 Unity/IL2CPP 文本；
- 没有显示一条由进程内 Hook 驱动的中文译文；
- 游戏完整运行、剧情翻译和免 Root 产品均未交付。

## 研究路线

- [`docs/research-evaluation.md`](docs/research-evaluation.md)：对偏离工作的价值分级和归档决定。
- [`docs/validation/container-modifications.md`](docs/validation/container-modifications.md)：容器改造假设与逐项验证。
- [`docs/validation/gms-detection-contracts.md`](docs/validation/gms-detection-contracts.md)：GMS/许可证检测面的独立实验矩阵。
- [`docs/validation/root-lsposed-il2cpp.md`](docs/validation/root-lsposed-il2cpp.md)：Root + LSPosed 下的 Hook 机制验证。
- [`docs/archive/stage0-full-experiment-handoff.md`](docs/archive/stage0-full-experiment-handoff.md)：后续 handoff 的脱敏原始归档。
- [`docs/initial-plan.md`](docs/initial-plan.md)：当前总计划和轨道间依赖。

## 历史旁路实验

`experiments/screen-overlay-archive/` 保存曾实现的 MediaProjection + ML Kit OCR + 悬浮窗实验。它证明屏幕捕获、本地 OCR、确定性词典和点击穿透覆盖层可以组合运行；但 OCR 已被否决为产品取文路线，因此该工程不在主构建、不继续扩展，也不作为 Unity/IL2CPP Hook 证据。

## 安全与许可证边界

- 不修改、重签名目标 APK；
- 不伪造许可证结果，不绕过 PAIRIP 或 Play Integrity；
- installer attribution 实验只用于识别客户端决策输入，不等同于获得 Play 所有权；
- microG 默认保持锁定、未修改的上游发布物；
- 上游代码进入仓库前遵循 [`docs/audit/upstream-source-audit.md`](docs/audit/upstream-source-audit.md) 的逐文件审计；
- 本仓库仍未选择项目许可证。
