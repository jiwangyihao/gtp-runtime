# Stage 0 实验交接归档

> 来源：`C:/tmp/gtp-stage0-full-experiment-handoff.md`
>
> 本文是脱敏研究记录归档，不是当前计划、实现规格或产品承诺。原交接文档中的路径、artifact 引用和本地截图位置仅作为历史来源线索保留；设备标识、账号、代理订阅和代理节点不写入本仓库。

## 交接目标与原始边界

原始交接将产品目标描述为：容器运行原版 split APK，并在 guest 进程内进行原生取文/翻译；当时明确不要求开发或跑通容器 MVP。它同时把 Stage 0 定义为有 Root 真机上的运行前置语义实验，而非最终免 Root 产品验收。

本归档保留这一目标，但纠正后续文档曾出现的路线混淆：MediaProjection/OCR 旁路不是产品目标；其工程已移入 `experiments/screen-overlay-archive/`，只作为独立历史机制实验。

## 实验问题

在 APK 字节、签名、设备、账号、GMS/Play 环境和网络条件不变时，安装会话中的 `installer attribution` 是否决定当前观察到的 PAIRIP 初始启动闸门结果？

该问题不等于“能否伪造 Play 许可”，也不等于“获得 Play ownership”。

## 固定输入与控制变量

- 目标包：`jp.gree_ent.mushoku`；versionCode `828343`；versionName `1.0.8`；
- base SHA-256：`50f7dbb7e4c53eb056b1732596298cf4bd70f432da6f4a7d666b4e9edcd724ed`；
- arm64 split SHA-256：`51ccee61fccd3d6f1d309a74503dcde36e0f2e6e936bd2b8fec7627f1fe46b76`；
- 同一 Root 真机、同一账号和网络条件；每次切换均卸载、重新安装、清空数据、冷启动；
- A：普通 `adb install-multiple`，观察到 `installerPackageName=null`、`initiatingPackageName=com.android.shell`；
- B：安装会话声明 `-i com.android.vending`，观察到 `installerPackageName=com.android.vending`、`initiatingPackageName=com.android.shell`；
- 两种条件的 `packageSource=1` 均未变化；APK 未修改、未重签名、许可服务响应未修改。

事后 `cmd package set-installer` 尝试没有形成有效处理；有效处理是重新安装时的安装会话 attribution。

## A/B/A/B/A 观测

| 运行 | installer attribution | 30 秒状态 | PAIRIP/Play 初始拒绝 | 结论 |
| --- | --- | --- | --- | --- |
| A2 | `null` | 游戏退出，Play 在前台 | 出现 | A 条件复现拒绝 |
| B2 | `com.android.vending` | 游戏进程存活，通知权限界面遮挡 | 未出现 | 许可/Games 路径日志成功；不单独证明 Unity 前台 |
| A3 | `null` | 游戏退出，Play 在前台 | 出现 | A 条件再次复现 |
| B3 | `com.android.vending` | `UnityPlayerActivity` 在前台，游戏存活 | 未出现 | 进入 Unity 标题路径 |
| A4 | `null` | 游戏退出，Play 在前台 | 出现 | 回滚后的第三次复现 |

## 可保留结论

1. 在这台设备、这个游戏版本和这个控制变量设计下，`installer attribution` 是当前 PAIRIP 初始启动分支的充分且可重复控制变量。
2. 非日区账号在 B 条件下也跨过了该初始闸门；不能据此断言日区下载资格是必要条件。
3. 该证据只覆盖初始启动路径；不覆盖长时间游玩、资源下载、支付、后续授权或 Play Integrity。
4. 容器必须维护自洽的安装会话、安装记录、PackageManager 视图、UID/Binder 身份和 GMS 回调语义；不能把结论简化为全局硬编码 `com.android.vending`。
5. 最终设备状态已回滚：目标恢复普通侧载，应用/Play 停止，临时网络夹具清理。归档不写设备序列号、账号或代理节点。

## 不应保留为结论的表述

- “官方 Play 安装已经完成”；
- “获得了 Play ownership”；
- “许可证已被绕过或伪造”；
- “游戏已经完成翻译”；
- “容器 MVP 已跑通”；
- “Stage 0 身份闸门已经通过”。

## 与当前三轨计划的关系

- **容器轨**：把 installer attribution、InstallSourceInfo、PM、Provider、Binder 和进程模型变成逐项改造实验；
- **GMS 轨**：把本实验作为 G-01 局部证据，继续验证 Vending、账号、Provider、网络和 Integrity 检测面；
- **Hook 轨**：不等待容器，在 Root + LSPosed arm64 真机验证 Unity/IL2CPP 库加载、文本观测和只读译文呈现。

原始完整交接文本已按用户要求从临时 handoff 脱敏归档；冗长日志、截图和设备私密信息不进入仓库。