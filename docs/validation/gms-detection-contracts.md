# GMS/许可证检测契约轨

## 状态

**未完成。** 目前只完成了两个有界结果：

1. 固定 Root 真机上的 installer attribution A/B/A/B/A 实验；
2. 纯 AOSP x86_64 AVD 上 synthetic client 到未修改 microG Vending 的架构无关 Binder 基线。

目标游戏在未修改 microG 上的完整身份链、真实账号、后端许可、后续资源授权和 Integrity 均未完成。

## 统一输入

- 目标包：`jp.gree_ent.mushoku`，versionCode `828343`；
- 原版 base/split SHA-256：见 `../initial-plan.md`；
- microG：`v0.3.15.250932`，commit `352f2d72fa52c6c3c4fdd79d575a071a0da72ad1`；
- Vending SHA-256：`a973e0235a2829773a4faf36d235d5f703d1c04a2adff674ebaa535a2e78f937`；
- GMS SHA-256：`52597e77fd25fdd347574d0457ed1936a4b9561cf4c8d34e7ac8dd8191dfd4b9`。

实验记录必须区分：

- **事实**：设备/系统观察到的包字段、Binder 事件、服务返回、进程状态；
- **派生判断**：对某检测面的假设；
- **未证实项**：没有到达的调用或无法观察的服务端行为。

## 已完成的局部证据

### G-01：installer attribution

固定设备、账号、网络与 APK 字节，A 条件使用普通安装会话，B 条件在安装会话声明 `-i com.android.vending`。A/B/A/B/A 的当前观测：

- A：`installerPackageName=null`，进入 `com.pairip.licensecheck.LicenseActivity`，Play 显示无法识别安装来源；
- B：`installerPackageName=com.android.vending`，未出现该初始拒绝；B3 进入 `UnityPlayerActivity` 并保持存活；
- 最终已回滚到普通侧载，未修改 APK。

证据原文已归档于 `docs/archive/stage0-full-experiment-handoff.md`；截图仍只保留在实验机临时目录，仓库不上传设备敏感信息。

结论：installer attribution 是**该游戏版本和该设备环境下 PAIRIP 初始启动分支的可重复控制变量**。不得写成“官方 Play 安装完成”“Play ownership 已取得”或“许可证已绕过”。

### G-02：架构无关 microG Binder 基线

纯 AOSP 36.0 x86_64、未修改 microG、synthetic client：

- 各 APK 获得独立真实系统 UID；
- `bindService → onServiceConnected → checkLicenseV2` 到达；
- microG 未登录时保留真实负结果；
- 没有旧 VirtualApp 的 Provider UID mismatch。

这只证明完整系统的基础服务契约可运行，不能外推目标游戏，也没有直接取得 Vending 内部 `Binder.getCallingUid/Pid()` 日志。

## 待验证检测面

| ID | 检测/契约 | 最小实验 | 事实通过条件 | 不允许的替代 |
| --- | --- | --- | --- | --- |
| G-03 | InstallSource / installer attribution | 比较普通安装与真实 Play/受支持安装会话 | 目标包各查询 API 字段自洽，且未修改目标行为一致 | 全局硬编码 `com.android.vending` |
| G-04 | Vending Service 解析与 UID/签名 | 目标真实调用未修改 `ILicensingService` | bind、AIDL、回调到达；服务端原始校验自行决定结果 | wrapper 改 caller、伪造 UID、改 microG |
| G-05 | GMS/GSF Provider | 逐 authority 记录 acquire/call/source UID | calling/source UID、包、签名视图一致 | Provider wrapper 把所有请求映射到宿主 |
| G-06 | Account/Check-in | 使用测试账号或用户明确授权的真实账号 | 账号状态、token 请求和失败语义来自真实服务 | 注入 token、伪造已登录 |
| G-07 | Play Games | 仅当目标真实调用时启用 | 真实 binder/网络流程和错误可复现 | 假造游戏发现/授权 |
| G-08 | 网络/资源/后端授权 | 只读记录请求域名、状态和错误类别 | 资源下载与会话流程在不篡改请求下完成 | 重写服务端 verdict |
| G-09 | Play Integrity / DroidGuard | 仅在前层到达且用户授权时观测 | 记录真实 verdict；可失败 | Hook/patch 伪造 verdict |
| G-10 | Payment / update | 仅由目标调用触发时研究 | 不修改金额、包签名或后端结果 | 绕过支付或更新校验 |

## 实验规则

- 每个 G ID 只改变一个处理变量；优先使用同一固定 APK 和同一设备快照；
- 先验证包解析、安装来源和 Binder/Provider，再接账号和网络；
- 许可关闭、未登录、服务不可用、Integrity 不满足都必须保留为负结果；
- microG 默认零改动；只有实机明确证明纯 API 兼容缺口，才讨论最小、可审计、优先上游化补丁；不得改 LicensingService 的身份或许可证语义；
- 任何结果只能标注到具体检测面、设备、版本和调用阶段。

## 停止条件

- 需要伪造 Binder calling UID、全局信任宿主 UID、替换签名或改变 microG/PAIRIP/Integrity 结果；
- 无法取得真实服务端调用或只能观察 `bindService=true`；
- 设备/镜像混用导致 APK、系统或服务版本无法归因。

## 交付边界

本轨通过某个 G ID 不代表 A 证据链整体通过，更不代表游戏已运行或已翻译。最终 A 仍需真实账号、真实服务、真实错误和目标实际调用路径的组合证据。
