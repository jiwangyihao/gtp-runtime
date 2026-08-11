# 完整系统与 AVD 机制基线归档

## 结论边界

本文记录三条彼此独立的 AVD 观测：

1. 纯 AOSP 36.0 x86_64 上的 **synthetic client → 未修改 microG 独立 Binder 基线**；
2. 原始 Google APIs 36.1 x86_64 + `libndk_translation` 上的 **目标 arm64 split 安装/启动能力**；
3. 官方 SDK 包清单与两份镜像构建身份的 **不可拼接性检查**。

三者不能拼接成一次有效 Stage 0：第一条不能运行目标 APK，第二条因预装 Google 包不能安装冻结 microG，第三条证明两份 system 镜像不是同一构建且 SDK 仓库没有纯 AOSP 36.1 x86_64。Stage 0A/0B 均未因此通过。

## 冻结输入

- microG tag：`v0.3.15.250932`
- tag commit：`352f2d72fa52c6c3c4fdd79d575a071a0da72ad1`
- Vending SHA-256：`a973e0235a2829773a4faf36d235d5f703d1c04a2adff674ebaa535a2e78f937`
- GMS SHA-256：`52597e77fd25fdd347574d0457ed1936a4b9561cf4c8d34e7ac8dd8191dfd4b9`
- 目标 base SHA-256：`50f7dbb7e4c53eb056b1732596298cf4bd70f432da6f4a7d666b4e9edcd724ed`
- 目标 arm64 split SHA-256：`51ccee61fccd3d6f1d309a74503dcde36e0f2e6e936bd2b8fec7627f1fe46b76`

## 主机与实机能力筛选

主机 `emulator-check.exe accel` 返回 0，WHPX 可用。

当前 Xiaomi 实机不具备受支持的完整 VM 路线：

- `virtualizationservice` 不存在；
- `/dev/kvm` 不存在；
- `ro.boot.hypervisor.protected_vm.supported=false`；
- `android.software.dynamic_system=false`。

设备有 KSU root，但 root 不改变以上硬件/系统服务缺失。

## 镜像版本一致性检查

使用 Azul JDK 17 执行 `sdkmanager.bat --list`。API 36.1 的系统镜像清单仅包含 `google_apis`、`google_apis_playstore`、`android-wear-signed` 及 16 KB page-size 变体，没有 `system-images;android-36.1;default;x86_64`。

本机两份可比较镜像的构建身份如下：

| 字段 | AOSP default x86_64 | Google APIs x86_64 |
| --- | --- | --- |
| SDK 路径 | `android-36/default/x86_64` | `android-36.1/google_apis/x86_64` |
| fingerprint | `Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys` | `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys` |
| `sdk_full` | `36.0` | `36.1` |
| incremental | `13818094` | `14574095` |
| 安全补丁 | `2025-07-05` | `2026-01-05` |
| native bridge | `0` | `libndk_translation.so` |

fingerprint、build ID、incremental、产品身份与系统版本均不同。因此没有复制 system/framework/native bridge 文件，也没有把可写系统删包变体作为干净镜像。一次删包变体在重启或 `-wipe-data` 后恢复预装包；另一次变体发生 `libandroid_servers.so`/`broadcastradio_TunerCallback` JNI 注册崩溃。只有未修改系统分区的原始 AVD 参与下面的复验。

## 纯 AOSP x86_64：microG 独立 Binder 基线

### 创建与启动

```powershell
$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
$Adb = "$Sdk\platform-tools\adb.exe"
$WorkDir = '<WORKDIR>'

& "$Sdk\cmdline-tools\latest\bin\sdkmanager.bat" `
  'system-images;android-36;default;x86_64'
& "$Sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd `
  -n gtp-stage0-aosp36 `
  -k 'system-images;android-36;default;x86_64' `
  --device pixel_7 --force
& "$Sdk\emulator\emulator.exe" `
  -avd gtp-stage0-aosp36 -wipe-data -no-snapshot -no-boot-anim `
  -gpu host -no-window -no-audio -port 5556
```

等待 `service check package` 返回 `found` 后，确认系统没有 `com.google.android.gms` 或 `com.android.vending`。

### 安装冻结 microG

```powershell
& $Adb -s emulator-5556 install -r `
  "$WorkDir\stage0-apks\com.google.android.gms-250932030.apk"
& $Adb -s emulator-5556 install -r `
  "$WorkDir\stage0-apks\com.android.vending-84022630.apk"
```

两次均返回 `Success`。系统分配：

- GMS UID `10150`；
- Vending UID `10151`；
- synthetic client UID `10152`。

### synthetic client

调用方位于 `<WORKDIR>\stage0-identity-client`，为纯 Java/AIDL debug APK，不含 native 库。AIDL 原样取自冻结 microG tag，并保留 Apache-2.0 文件头。

构建：

```powershell
& "$env:JAVA_HOME\bin\java.exe" `
  -classpath '<GRADLE_9_5_HOME>\lib\gradle-launcher-9.5.0.jar' `
  org.gradle.launcher.GradleMain :app:assembleDebug --no-daemon
```

安装并启动：

```powershell
& $Adb -s emulator-5556 install -r `
  "$WorkDir\stage0-identity-client\app\build\outputs\apk\debug\app-debug.apk"
& $Adb -s emulator-5556 logcat -c
& $Adb -s emulator-5556 shell am start -n `
  io.github.community.gtp.stage0.identity/.MainActivity
```

### 首次结果：许可关闭

观测：

- `CLIENT_IDENTITY pid=3915 uid=10152 appInfoUid=10152`；
- `packagesForUid=[io.github.community.gtp.stage0.identity]`；
- 唯一解析端点为未修改 Vending `LicensingService`；
- `bindService=true`；
- `onServiceConnected` 到达；
- `checkLicenseV2` 到达未修改 Vending；
- Vending 记录许可被用户设置关闭，并保留原始负结果抑制语义。

### 开启许可后的结果

在调试 AOSP 中以 root shell 更新 microG 自有 Provider：

```powershell
& $Adb -s emulator-5556 root
& $Adb -s emulator-5556 shell content update `
  --uri content://com.google.android.gms.microg.settings/vending `
  --bind vending_licensing:b:true
& $Adb -s emulator-5556 shell content query `
  --uri content://com.google.android.gms.microg.settings/vending `
  --projection vending_licensing
```

查询返回 `vending_licensing=1`。重启 client 与 Vending 后观测：

- client PID `4079` / UID `10152`；
- Vending PID `4097` / UID `10151`；
- GMS PID `3952` / UID `10150`；
- `bindService=true`；
- `onServiceConnected` 到达；
- `checkLicenseV2(io.github.community.gtp.stage0.identity, ...)` 到达；
- 未修改 Vending 记录“用户未登录”，并保留原始负结果抑制语义；
- 没有旧 VirtualApp 的 `Calling uid ... doesn't match source uid ...` Provider 异常。

限制：调用方是 synthetic client，不是目标游戏；未修改 Vending 日志没有直接输出服务端 `Binder.getCallingUid/Pid()`。因此这只证明完整 Android 系统能建立 microG 独立 Binder 链路，不证明目标游戏 Stage 0B 通过。

### 目标安装失败

```powershell
& $Adb -s emulator-5556 shell getprop ro.product.cpu.abilist
& $Adb -s emulator-5556 shell getprop ro.dalvik.vm.native.bridge
& $Adb -s emulator-5556 install-multiple `
  "$WorkDir\mushoku-jp-1.0.8\jp.gree_ent.mushoku-828343.apk" `
  "$WorkDir\mushoku-jp-1.0.8\jp.gree_ent.mushoku-828343-config.arm64_v8a.apk"
```

观测为 `x86_64`、native bridge `0`，安装返回 `INSTALL_FAILED_NO_MATCHING_ABIS`。这使纯 AOSP AVD 无法执行目标游戏 0B。

## Google APIs 36.1 x86_64：目标运行能力但 microG 冲突

创建全新 `gtp-stage0-googleapis36-pristine` AVD，以 `-wipe-data -no-snapshot` 启动且不修改系统分区。启动完成后观测：

- fingerprint `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys`；
- `sys.boot_completed=1`、`service check package=found`、`sys.system_server.start_count=1`；
- `abilist=x86_64,arm64-v8a`；
- `ro.dalvik.vm.native.bridge=libndk_translation.so`；
- GMS 路径 `/product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk`，versionCode `253434038`；
- GSF 路径 `/system_ext/priv-app/GoogleServicesFramework/GoogleServicesFramework.apk`；
- Vending 路径 `/product/app/LicenseChecker/LicenseChecker.apk`，versionCode `1801`。

冻结输入摘要再次计算并匹配本文开头。目标 base + arm64 split 执行 `adb install-multiple` 返回 `Success`；冻结 GMS 安装返回 `INSTALL_FAILED_VERSION_DOWNGRADE`，冻结 Vending 安装返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.android.vending signatures do not match newer version`。未使用 `-d`、卸载系统包、重签名或任何覆盖检查参数。

启动 `jp.gree_ent.mushoku/com.unity3d.player.UnityPlayerActivity` 返回 `Status: ok`，目标进程 PID `5973`、系统 UID `10226`。`/proc/5973/maps` 同时包含目标 arm64 `libunity.so`、`libil2cpp.so` 与 `/system/lib64/libndk_translation.so` 及其代理库；日志出现 `IL2CPP: JNI_OnLoad`。顶层界面随后变为目标自身的 `com.pairip.licensecheck.LicenseActivity`。

这只证明官方 native bridge 能加载目标原版 arm64 输入并到达 PAIRIP 许可页。冻结 microG 未能安装，故目标没有调用未修改 Vending；该日志不参与 microG Binder/PM 身份判定，也不证明许可证或游戏完整运行。

## ARM64 AVD

已安装 `system-images;android-36;default;arm64-v8a` 并创建 AVD。x86_64 主机启动时模拟器明确退出：

```text
FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.
```

## 当前结论与用途

目前没有一个已复现 AVD 同时满足：

1. 无预装 Google 包冲突；
2. 未修改 microG 可按冻结摘要安装；
3. 目标原版 arm64 split 可安装并启动；
4. system/framework/native bridge 来自同一构建。

官方 SDK 仓库没有纯 AOSP 36.1 x86_64；AOSP 36.0 与 Google APIs 36.1 的 fingerprint 和系统版本不同，禁止拼接。原始 Google APIs AVD 已复验目标启动与 microG 安装冲突；可写系统删包变体不可复现且不参与结论。

因此，这组 AVD 记录不能让目标游戏的 Stage 0 身份闸门通过：目标没有在同一环境进入未修改 Vending，服务端 calling UID/PID、PM UID/签名、Provider、AIDL 与回调证据没有组合产生。但它仍提供两个独立对照：纯 AOSP 的完整系统 Binder/Provider/PM 基线，以及官方 native bridge 加载原版 arm64 Unity/IL2CPP 的执行基线。

该限制只终止“把这两份 AVD 拼成一次目标 Stage 0”的尝试，不停止其他机制实验。后续分别进入容器契约改造、GMS 检测面验证和 Root + LSPosed Hook 验证；不得再把 OCR 旁路当作主路线或身份闸门的替代。
