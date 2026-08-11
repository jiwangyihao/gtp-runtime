# Stage 0 身份拓扑实验结果

## 状态

- 执行时间：2026-08-11
- 设备：Xiaomi `uke`，Android API 36，arm64-v8a
- 当前基座：隔离的 VirtualApp14 carrier spike；它不是本仓库产品代码
- **Stage 0A（目标与 Vending 同一实际 guest 进程）：当前基座拓扑不可执行，未产生服务端身份证据**
- **Stage 0B（Vending 独立实际 guest 进程）：服务连接失败，身份闸门未完成/不可判定**
- 结论否决当前 VirtualApp/AAR 继续作为 Stage 0 实验基座，不证明 0A/0B 的 Binder caller 身份本身失败，也不证明其他容器或完整系统环境必然失败。

## 冻结输入

两次拓扑判断使用同一个 microG release/tag：

- `v0.3.15.250932`
- tag commit：`352f2d72fa52c6c3c4fdd79d575a071a0da72ad1`
- `com.android.vending-84022630.apk`
  - 字节数：4,639,851
  - SHA-256：`a973e0235a2829773a4faf36d235d5f703d1c04a2adff674ebaa535a2e78f937`
- `com.google.android.gms-250932030.apk`
  - 字节数：105,948,577
  - SHA-256：`52597e77fd25fdd347574d0457ed1936a4b9561cf4c8d34e7ac8dd8191dfd4b9`

`apksigner verify --verbose --print-certs` 对两份 APK 均返回 0，v1/v2 签名验证为 true；签名证书 SHA-256 均为 `9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165`。`aapt dump badging` 分别确认包名、版本和 ABI。容器私有 `base.apk` 的 SHA-256 与以上冻结发布物一致，未修改 microG 字节。

系统侧直接安装只用于排除输入问题，不参与 Binder 身份判定：

- Vending：`INSTALL_FAILED_UPDATE_INCOMPATIBLE`，因设备已有系统 `com.android.vending` 签名不同；
- GMS：`INSTALL_FAILED_VERSION_DOWNGRADE`，因设备系统 GMS versionCode 较新。

容器普通安装 API 使用默认 `VAppInstallerParams()`，未使用 `FLAG_INSTALL_OVERRIDE_NO_CHECK`。首次安装返回 `status=0`；重复安装返回 `status=5`（`STATUS_FAILURE_CONFLICT`），而虚拟 PM 中原冻结 APK 保持已安装且摘要不变。

## 容器准备证据

虚拟 PM 结果：

- `com.google.android.gms`：virtual UID / `ApplicationInfo.uid` = `10003`；`getPackagesForUid(10003)` = `[com.google.android.gms]`；
- `com.android.vending`：virtual UID / `ApplicationInfo.uid` = `10004`；`getPackagesForUid(10004)` = `[com.android.vending]`；
- `ILicensingService` 唯一解析结果：`com.android.vending/.licensing.LicensingService`，virtual UID `10004`，process `com.android.vending`，permission `com.android.vending.CHECK_LICENSE`。

目标 guest 中观察到：

- target virtual UID / `ApplicationInfo.uid` = `10002`；
- `getPackagesForUid(10002)` = `[jp.gree_ent.mushoku]`；
- 目标签名 SHA-256 = `1cba3db0e2ec409155182f6ad5017eca136286e34ce04b0706eb22431229713d`；
- 原始 base 与 arm64 split 摘要仍分别匹配 `50f7d…724ed` 与 `51cce…46b76`。

日志字段名 `kernelUid` 来自 guest 内被 VirtualApp Hook 后的 `Process.myUid()` 视图，值为 virtual UID；系统 `ps` 才是 Kernel Identity 证据。系统 `ps` 显示目标及全部 stub 进程实际 Linux UID 均为宿主 UID `10379`。

## Stage 0B：独立进程拓扑

### 执行

目标 guest 上下文在 `afterApplicationCreate` 中：

1. 解析未修改 Vending 的 `ILicensingService`；
2. 调用 `Application.bindService()`；
3. 若连接成立，才会通过从 microG 固定 tag 原样生成的 AIDL 调用 `checkLicenseV2()`；
4. 记录连接、发送与回调。

### 三次冷启动结果

三次均出现：

- 目标 guest 被分配到 `com.carlos.multiapp:p0`；
- Vending 服务被分配到另一个 stub（观测为 `p2`）；
- client `bindService()` 返回 `true`；
- 服务连接建立前，Vending 初始化流程触发了 microG settings Provider 查询；
- 查询期间稳定出现异常：`SecurityException: Calling uid: 10004 doesn't match source uid: 10379`；
- 该异常只证明当前 Provider 路由存在 UID 不一致，不是 `LicensingService` 对 `Binder.getCallingUid/Pid()` 的观测。
- 三次均没有 `onServiceConnected`、`checkLicenseV2` 发送或许可证回调。

因此，`bindService() == true` 只证明请求被容器接受，不证明 Binder 往返成功。服务未返回 Binder，故本实验**没有在未修改 microG 服务端取得 `Binder.getCallingUid/Pid()`**，也未到达 `LicensingService.checkLicenseCommon()` 的 caller UID 校验。

### 判定

**服务连接失败；身份不可判定。** 0B 没有满足 Stage 0 的身份验收条件。当前独立进程路由至少在 Android 16 的 Provider 身份校验上不可用；修复它需要改变容器身份路由或系统契约，已超出 Stage 0。禁止用 Hook、wrapper、lease、自报包名或修改 microG 来补造服务端身份证据。

## Stage 0A：同一实际 guest 进程拓扑

代码证据见 `stage0-topology-evidence.json`：`VActivityManagerService.startProcessIfNeedLocked()` 按 `(processName, virtualUid)` 查找和保存 `ProcessRecord`；`VClient.initProcess()` 在已有 `clientConfig` 时拒绝第二次初始化。目标与 Vending 分别是 `(jp.gree_ent.mushoku, 10002)` 和 `(com.android.vending, 10004)`，因此当前基座会将其分配到不同 stub。

在不修改 Vending Manifest、virtual UID、VAMS/VClient 进程模型或服务路由的前提下，当前 AAR 没有配置入口令未修改 Vending 与目标共用同一实际 guest 进程。继续构造该条件属于容器重写，而非 Stage 0 前置实验。

### 判定

**当前基座拓扑不可执行。** 未修改 Vending 无法在冻结边界内被调度到目标已有实际 guest 进程，因此没有产生 0A 服务端身份证据。这不是 0A 身份验证失败，更不是一般性 Android 结论；继续实验需先重写容器进程模型，超出 Stage 0。

## 架构决策

按 `docs/initial-plan.md` 的停止条件：

1. 否决当前 VirtualApp/AAR 继续作为 Stage 0 应用级 GMS 实验基座；
2. 不把 0B 误报为已完成的 Binder caller 身份实验；
3. 不进入 Google 账号、真实许可证后端或 Play Integrity 验收；
4. 不把 guest-visible virtual UID 当 Kernel Identity；
5. 不通过 Hook `Binder.getCallingUid()`、Provider wrapper、lease 或修改 microG 身份/许可结果继续；
6. 不在该基座上展开 BlackBox 大规模重写；
7. 后续只能重新评估具备一致 Binder/Provider/PM 身份视图的完整容器内核、完整系统虚拟机，或官方安装配合外部 OCR/覆盖层。

## 与产品交付的边界

- native 载荷入口仍然已有实机证据；
- A 证据链在当前基座未完成：0A 不可执行，0B 服务连接失败且服务端 caller 身份不可判定；
- B（容器稳定性）已出现阻断性 Provider/PM 兼容错误，未通过；
- C（Unity/IL2CPP 翻译效果）未执行；
- 游戏完整运行、菜单翻译和可交付状态均没有证据。

## 证据文件

- [`stage0-input-evidence.json`](stage0-input-evidence.json)：设备、APK 签名/包信息及系统安装冲突；
- [`stage0-preparation-evidence.json`](stage0-preparation-evidence.json)：容器普通安装、虚拟 PM 与组件解析；
- [`stage0-independent-process-repeats.json`](stage0-independent-process-repeats.json)：三次冷启动独立进程实验的结构化日志；
- [`stage0-topology-evidence.json`](stage0-topology-evidence.json)：0A 不可执行的基座版本、源码哈希、源码摘录与限定结论；
- [`stage0-reproduction.md`](stage0-reproduction.md)：可复现实验命令和判据。
