# 下一轮基座决策（归档）

## 状态

本文是上一轮 AVD/完整系统路线的归档记录。它不再规定“Stage 0 未通过即停止所有应用级研究”；当前生产集成冻结，但容器改造实验可以在隔离副本继续。

## 有价值的基线

- 纯 AOSP 36.0 x86_64 能安装冻结 microG；synthetic client 到未修改 Vending 的独立 Binder 链路可完成 `bindService → onServiceConnected → checkLicenseV2`，并保留真实负结果。
- 该 AVD 没有目标所需 arm64 native ABI，目标安装返回 `INSTALL_FAILED_NO_MATCHING_ABIS`，所以不能证明目标游戏 0B。
- Google APIs 36.1 x86_64 含官方 `libndk_translation`，可安装并启动目标 arm64 split，观察到 `libunity.so`、`libil2cpp.so` 和 `IL2CPP: JNI_OnLoad`，但预装 GMS/GSF/LicenseChecker 阻止冻结 microG 安装。
- AOSP 36.0 与 Google APIs 36.1 的系统构建身份不同，禁止拼接 system/framework/native bridge。

## 修订后的价值判断

这些结果不是“容器路线结束”，而是三项机制基线：

1. 完整 Android 的 Binder/Provider/PM 语义可作为容器改造的对照；
2. installer attribution、Vending 和 GMS 检测面必须拆开验证；
3. 官方 native bridge 证明原版 arm64 Unity/IL2CPP 可在隔离环境加载，但没有翻译 Hook 证据。

## 当前不再采用的强停止语句

不要再使用“0A/0B 未通过即停止所有应用级路线”。改为：

- 冻结旧基座的生产集成；
- 保留并修复局部改造实验；
- 若改造依赖伪造 Binder UID、改变 microG/许可证/Integrity 语义，则停止该改造方向；
- 机制实验失败不阻止其他轨道继续。

## 候选基座约束

任何新基座候选都必须逐项验证：真实 Linux UID、`Binder.getCallingUid/Pid()`、`getPackagesForUid()`、签名、Provider source/calling UID、Service/AIDL/回调和安装来源查询。synthetic client 只能证明系统契约，不可外推目标游戏。