# Stage 0 复现实验

## 前置

- 设备需通过 `adb devices -l` 可见；本次设备信息见 `stage0-input-evidence.json`。
- 使用同一 microG tag `v0.3.15.250932` 的两个 APK，不得替换为 `master` 或其他构建。
- APK 进入设备宿主可读目录后，先由宿主探针校验 SHA-256；不要用系统 `adb install` 的成败替代容器实验。
- 实验只观察安装、虚拟 PM、组件解析和真实服务连接；不得修改 APK、Hook `Binder.getCallingUid()`、改写许可结果或使用 `FLAG_INSTALL_OVERRIDE_NO_CHECK`。
- 本页保留已执行步骤与复验要求。临时探针不属于产品基座，取证后已从 spike 工作树移除；当前干净 checkout 不能直接启动 `ProbeLaunchActivity`。复验前必须重建等价的只读探针、记录探针源码哈希，并保持本页禁止事项。

## 输入校验

```powershell
$javaHome = $env:JAVA_HOME
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (-not $javaHome -or -not $sdk) { throw '请先设置 JAVA_HOME 和 ANDROID_HOME（或 ANDROID_SDK_ROOT）' }
$stage0ApkDir = 'C:\tmp\stage0-apks'
$apksigner = Join-Path $sdk 'build-tools\36.1.0\apksigner.bat'
$aapt = Join-Path $sdk 'build-tools\36.1.0\aapt.exe'
$vendingApk = Join-Path $stage0ApkDir 'com.android.vending-84022630.apk'
$gmsApk = Join-Path $stage0ApkDir 'com.google.android.gms-250932030.apk'
& $apksigner verify --verbose --print-certs $vendingApk
& $apksigner verify --verbose --print-certs $gmsApk
& $aapt dump badging $vendingApk
& $aapt dump badging $gmsApk
```

预期输入：

- Vending：4,639,851 字节；SHA-256 `a973e0235a2829773a4faf36d235d5f703d1c04a2adff674ebaa535a2e78f937`。
- GMS：105,948,577 字节；SHA-256 `52597e77fd25fdd347574d0457ed1936a4b9561cf4c8d34e7ac8dd8191dfd4b9`。

## 容器准备

```powershell
$adb = Join-Path $sdk 'platform-tools\adb.exe'
& $adb push $vendingApk /sdcard/Android/data/com.carlos.multiapp/files/stage0/com.android.vending-84022630.apk
& $adb push $gmsApk /sdcard/Android/data/com.carlos.multiapp/files/stage0/com.google.android.gms-250932030.apk
& $adb shell am force-stop com.carlos.multiapp
& $adb logcat -c
& $adb shell am start -n com.carlos.multiapp/com.carlos.gtp.ProbeLaunchActivity --es gtp_mode stage0_prepare
& $adb logcat -d -v threadtime | Select-String 'GTP_STAGE0_(APK_INPUT|INSTALL_RESULT|VPM_PACKAGE|RESOLVE)'
```

探针使用默认 `VAppInstallerParams()`，记录安装状态、虚拟 UID、`getPackagesForUid`、签名摘要和 `ILicensingService` resolve。`bind=true` 不是成功条件。

## 目标 guest 发起服务连接

```powershell
& $adb shell am force-stop jp.gree_ent.mushoku
& $adb shell am force-stop com.carlos.multiapp
& $adb logcat -c
& $adb shell am start -n com.carlos.multiapp/com.carlos.gtp.ProbeLaunchActivity
Start-Sleep -Seconds 15
& $adb logcat -d -v threadtime | Select-String 'GTP_STAGE0_GUEST_|LicensingService|Calling uid|source uid|ERROR_NON_MATCHING_UID'
& $adb shell ps -A -o UID,PID,PPID,NAME | Select-String 'com.carlos.multiapp|jp.gree_ent.mushoku'
```

只有目标 guest 的 `afterApplicationCreate` 发起的 `Application.bindService` 计入实验；宿主 Activity 的 bind 不构成身份证据。探针记录 guest 可见 UID/包映射、bind、连接、AIDL 调用和回调。系统 `ps` 是实际 Linux UID/PID 的证据。

## 本次结果判据

独立进程拓扑连续 3 次冷启动均出现：

- guest 可见 target virtual UID = 10002；Vending virtual UID = 10004；
- `bindService()` 返回 `true`；
- 服务连接建立前，Vending 初始化流程触发 microG settings Provider 查询；查询期间出现 `SecurityException: Calling uid: 10004 doesn't match source uid: 10379`；
- 没有 `onServiceConnected`、`checkLicenseV2` 发送或许可证回调；
- 因服务未返回 Binder，**没有取得服务端 `Binder.getCallingUid/Pid()`**。

所以 0B 的严格结论是：**服务连接失败，身份不可判定；Stage 0 身份闸门未完成。**

0A 没有被运行：`stage0-topology-evidence.json` 固化的代码显示，当前 AAR 按 `(processName, virtualUid)` 分配进程，且 `VClient` 仅接受一次 `initProcess(ClientConfig)`；目标 10002 与 Vending 10004 无法在冻结边界内被调度到同一实际 guest 进程。因此 0A 是：**当前基座拓扑不可执行，没有服务端身份证据。**
