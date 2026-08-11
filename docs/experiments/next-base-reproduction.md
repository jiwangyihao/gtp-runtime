# 下一轮身份基线复现与观测

## 结论边界

本文记录两条彼此独立的 AVD 观测：

1. 纯 AOSP x86_64 上的 **synthetic client → 未修改 microG 独立 Binder 基线**；
2. Google APIs x86_64 + `libndk_translation` 上的 **目标 arm64 split 安装/启动能力**。

二者不能拼接成一次有效 Stage 0：前者不能运行目标 APK，后者因预装 Google 特权包不能无冲突安装冻结 microG。Stage 0A/0B 均未因此通过。

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

## 纯 AOSP x86_64：microG 独立 Binder 基线

### 创建与启动

```powershell
$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
$Adb = "$Sdk\platform-tools\adb.exe"

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
  C:\tmp\stage0-apks\com.google.android.gms-250932030.apk
& $Adb -s emulator-5556 install -r `
  C:\tmp\stage0-apks\com.android.vending-84022630.apk
```

两次均返回 `Success`。系统分配：

- GMS UID `10150`；
- Vending UID `10151`；
- synthetic client UID `10152`。

### synthetic client

调用方位于临时目录 `C:\tmp\stage0-identity-client`，为纯 Java/AIDL debug APK，不含 native 库。AIDL 原样取自冻结 microG tag，并保留 Apache-2.0 文件头。

构建：

```powershell
& C:\Users\34404\.jdks\azul-17.0.18\bin\java.exe `
  -classpath C:\Users\34404\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\lib\gradle-launcher-9.5.0.jar `
  org.gradle.launcher.GradleMain :app:assembleDebug --no-daemon
```

安装并启动：

```powershell
& $Adb -s emulator-5556 install -r `
  C:\tmp\stage0-identity-client\app\build\outputs\apk\debug\app-debug.apk
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
  C:\tmp\mushoku-jp-1.0.8\jp.gree_ent.mushoku-828343.apk `
  C:\tmp\mushoku-jp-1.0.8\jp.gree_ent.mushoku-828343-config.arm64_v8a.apk
```

观测为 `x86_64`、native bridge `0`，安装返回 `INSTALL_FAILED_NO_MATCHING_ABIS`。这使纯 AOSP AVD 无法执行目标游戏 0B。

## Google APIs x86_64：目标运行能力但镜像污染

API 36.1 Google APIs 镜像报告：

- `abilist=x86_64,arm64-v8a`；
- `ro.dalvik.vm.native.bridge=libndk_translation.so`。

目标原版 base + arm64 split 安装成功，Unity 进程以系统 UID `10226` 启动。这只证明官方 native bridge 能运行该 arm64 输入。

该镜像预装：

- `/product/priv-app/PrebuiltGmsCore/PrebuiltGmsCore.apk`；
- `/product/app/LicenseChecker/LicenseChecker.apk`。

安装冻结 GMS 返回 `INSTALL_FAILED_VERSION_DOWNGRADE`；即使允许降级，系统签名和特权包身份也与 microG 冲突。因此没有在该镜像继续安装或混用 microG，目标启动日志不参与 GMS 身份判定。

## ARM64 AVD

已安装 `system-images;android-36;default;arm64-v8a` 并创建 AVD。x86_64 主机启动时模拟器明确退出：

```text
FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.
```

## 当前停止结论

目前没有一个 AVD 同时满足：

1. 无预装 Google 包冲突；
2. 未修改 microG 可按冻结摘要安装；
3. 目标原版 arm64 split 可安装并启动。

所以 Stage 0 身份闸门仍未完成。继续需要“纯 AOSP + 可重现 arm64 native bridge”镜像或 ARM64 主机/设备上的干净完整 Android 系统，然后由目标游戏本身调用未修改 Vending，取得服务端 calling UID/PID、PM UID/签名、Provider、AIDL 和回调证据。
