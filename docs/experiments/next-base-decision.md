# 下一轮身份实验基座决策

## 状态

- 决策时间：2026-08-11
- 结论：**完整 Android AVD 适合作为 Binder/PM 语义基线，但当前没有同时满足“无 Google 包冲突”和“可运行目标 arm64 split”的 AVD，因此目标游戏 Stage 0 仍为 blocked。**
- 这不是产品基座选择。纯 AOSP x86_64 AVD 已用于验证架构无关的 synthetic client → 未修改 microG 独立 Binder 链路；该结果不能外推到目标游戏。
- 原 Stage 0 身份闸门仍未完成：旧 VirtualApp/AAR 上 0A 未执行、0B 未到达服务端 caller UID 校验；新 AVD 上也尚未以目标游戏作为调用方到达该校验。

## 必须满足的系统契约

候选基座必须同时提供：

1. 每个安装包由系统 PackageManager 分配真实 Linux UID；
2. 服务端 `Binder.getCallingUid/Pid()` 返回内核观察值，不是应用 Hook 后的虚拟值；
3. `getPackagesForUid(callingUid)`、目标 `ApplicationInfo.uid`、签名与实际调用者一致；
4. ContentProvider 的 calling/source UID 使用同一身份模型；
5. `bindService()`、`onServiceConnected`、AIDL 调用与回调能跨独立进程完成；
6. 未修改 microG 保留原始身份校验和许可证失败语义。

任何仅在 Java 层返回 virtual UID、让多个 guest 共享宿主 Linux UID、或依赖 lease/wrapper/Hook 替代身份判断的方案都不满足。

## 候选筛选

### 1. 旧 VirtualApp/AAR 或同类应用内容器

**否决。** 已有三次稳定证据表明 Provider 路由把 virtual UID `10004` 与实际宿主 UID `10379` 混用，Vending 在 `onBind()` 前崩溃。继续需要重写 Binder/Provider/PM 身份模型，不能作为前置实验。

### 2. 当前 Xiaomi 实机上的 AVF/KVM 微虚拟机

**不可执行。** 实机观测：

- Android API 36、arm64；
- `virtualizationservice` 不存在；
- `/dev/kvm` 不存在；
- `ro.boot.hypervisor.protected_vm.supported=false`；
- `android.software.dynamic_system=false`；
- `gsi_tool status=normal`，但没有受支持 DSU shell 实现。

设备虽然可通过 KSU 获得 root，但 root 不会凭空增加缺失的 Hypervisor/AVF 服务，因此不能把 AVF 作为当前基线。

### 3. x86_64 主机上的纯 AOSP x86_64 AVD

**microG 独立 Binder 基线可执行，但无法运行目标输入。** 主机 WHPX 可用，API 36 AOSP AVD 可启动；冻结 microG GMS/Vending 可无冲突安装。架构无关 synthetic client 与 Vending/GMS 获得三个独立真实 UID，并完成 `bindService → onServiceConnected → checkLicenseV2`；Provider 初始化未出现旧容器的 UID mismatch。开启 `vending_licensing` 后，未修改 Vending 因没有账号保留原始失败语义。该镜像只有 `x86_64`、`ro.dalvik.vm.native.bridge=0`；目标游戏的 native split 只有 `arm64-v8a`，实装返回 `INSTALL_FAILED_NO_MATCHING_ABIS`，所以它不是目标游戏 0B 的可用基座。

### 4. x86_64 主机上的 ARM64 AVD

**不可执行。** 已安装 `system-images;android-36;default;arm64-v8a` 并创建 AVD；模拟器明确退出：`Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host`。

### 5. Google APIs x86_64 AVD（含 `libndk_translation`）

**镜像污染，不能用于 microG 身份结论。** 全新 API 36.1 Google APIs AVD 已观察到：

- `abilist=x86_64,arm64-v8a`；
- `ro.dalvik.vm.native.bridge=libndk_translation.so`；
- 目标原版 base + arm64 split 安装成功，Unity 进程以独立系统 UID 启动；
- 镜像预置特权 `com.google.android.gms` 与系统 `com.android.vending` LicenseChecker；
- 安装冻结 microG GMS 首先返回 `INSTALL_FAILED_VERSION_DOWNGRADE`，且即使允许降级也存在签名/特权身份冲突。

因此，目标启动证据只证明官方 native bridge 可运行 arm64 split；microG 安装冲突使该镜像不具备身份闸门资格。不得混用预装 Google 服务与 microG 后宣称 Stage 0 通过。

## 下一轮实验边界

### 已完成的 synthetic client 基线

在全新纯 AOSP x86_64 AVD 上构建了架构无关 `identity-client`，直接绑定锁定、未修改的 microG Vending：

- client UID `10152`、Vending UID `10151`、GMS UID `10150`；
- 服务解析、`bindService=true`、`onServiceConnected`、`checkLicenseV2` 均到达；
- 开启 microG 许可设置后，Vending 进入许可路径，并以“用户未登录”保留原始失败语义；
- 没有旧 VirtualApp 的 Provider source/calling UID mismatch。

该调用方不是目标游戏，且未修改 Vending 日志没有直接输出服务端 `Binder.getCallingUid/Pid()`。因此这只是 **microG 独立 Binder 基线**，不是 Stage 0A/0B 通过证据。

### microG 0B 重跑

使用同一冻结 release：

- tag `v0.3.15.250932`；
- commit `352f2d72fa52c6c3c4fdd79d575a071a0da72ad1`；
- Vending SHA-256 `a973e0235a2829773a4faf36d235d5f703d1c04a2adff674ebaa535a2e78f937`；
- GMS SHA-256 `52597e77fd25fdd347574d0457ed1936a4b9561cf4c8d34e7ac8dd8191dfd4b9`。

客户端最终必须替换为原版目标包；需要观测未修改 Vending 实际进入 `checkLicenseCommon()`，并取得以下服务端证据：

- calling UID/PID 是目标真实系统 UID/PID；
- Vending 的 PM 视图中目标 `ApplicationInfo.uid` 相同；
- `getPackagesForUid()` 解析到原签名目标包；
- Provider 初始化无 UID mismatch；
- bind、AIDL 和回调链路完整。

当前没有可同时安装冻结 microG 与运行目标 arm64 split 的干净 AVD，所以上述 0B 重跑尚不可执行。真实账号与许可证后端仍在身份闸门之后；不得伪造 `LICENSED`。

## 0A 处理

完整 Android 系统不会把两个不同安装包调度进同一 Linux 进程；因此旧计划中的“目标与 Vending 同一实际 guest 进程”不是完整系统的候选拓扑。下一轮只执行独立系统进程基线和 0B。0A 保留为旧应用内容器假设的不可执行记录，不通过改 Manifest、共享 UID 或重签名构造。

## 停止条件

- 无法得到一个没有 Google 包冲突、同时支持 arm64 native bridge 的可丢弃系统镜像；
- 未修改 Vending 不能在该系统中安装或其签名/摘要变化；
- 目标原版 split 不能在同一系统中安装并启动；
- Provider、PM 或 Binder 的实际身份视图不一致；
- 只有通过修改 microG、Hook Binder UID 或改变许可证结果才能继续。

当前已触发第一项：纯 AOSP 无 native bridge，Google APIs 有预装包冲突。因此 AVD 目标游戏路线保持 blocked，不把 synthetic client 基线升级为产品或 Stage 0 结论。

## 与交付的边界

- 当前只选定下一轮实验基线，没有完成 Stage 0 身份闸门；
- native 载荷入口仍仅在旧隔离 carrier 中有实机证据；
- A（许可证/GMS）未通过；
- B（容器稳定性）未通过；
- C（Unity/IL2CPP 翻译效果）未执行；
- 游戏完整运行、菜单翻译和可交付状态均无证据。
