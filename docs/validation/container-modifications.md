# 容器改造验证轨

## 状态

**未完成。** 已知旧 VirtualApp/AAR 不是可直接交付的基座；本轨的目标是验证具体改造能否消除具体失败，并据此决定吸收、重写或放弃哪些实现材料。

## 冻结基线

- carrier commit：`0f9165454ef78c7be65ae06a69ec5ac0536a770e`；
- 目标 base/split 摘要见 `../initial-plan.md`；
- microG 固定发布物见 `gms-detection-contracts.md`；
- Android 16 arm64 真机；
- 原始失败证据：`../experiments/stage0-result.md` 和对应 JSON。

每项改造必须基于隔离分支/工程；先保存未改基线结果，再只改变一个契约。禁止在日志字符串层“消除报错”来冒充行为修复。

## 已知缺口

| ID | 观测 | 当前假设 | 需要修改的系统契约 |
| --- | --- | --- | --- |
| C-K01 | 所有 stub 的真实 Linux UID 是宿主 UID，guest 看到 virtual UID | Java Hook 视图与跨进程内核身份混用 | PM/Provider/Binder 统一身份模型 |
| C-K02 | Vending settings Provider 报 calling `10004` / source `10379` 不一致 | Provider acquisition/call 未携带一致 principal | Provider authority 解析、caller/source 验证与返回通道 |
| C-K03 | `bindService=true` 但无 `onServiceConnected` | 服务初始化在返回 Binder 前失败 | Service 生命周期、Provider 依赖与回调路由 |
| C-K04 | VAMS 按 `(processName, virtualUid)` 分配进程，VClient 只允许单 ClientConfig | 同 stub 不能自然承载不同 principal | 进程分配模型；不要靠共享进程伪造系统身份 |
| C-K05 | 旧 SandHook `initNative` 缺 JNI | 打包路径损坏且上游过时 | 删除旧 Hook 栈，保留独立 native loader |
| C-K06 | 只实现旧 `getInstallerPackageName` 路径 | Android 现代安装来源视图不完整 | 安装会话、安装记录、`InstallSourceInfo` 与 PM 查询 |
| C-K07 | 目标 native probe 可加载 | guest callback/System.load seam 有效 | 固化窄载荷接口，不与 GMS 耦合 |

## 实验顺序

### K0：可重复基线

输入：未改 carrier、两个 synthetic APK（caller 与 endpoint）。

证据：三次冷启动的 process record、实际 `ps` UID/PID、virtual PM 映射、Provider 调用、Service 连接、AIDL、回调和进程死亡。

通过：三次失败位置一致，能够由结构化事件而非人工猜测定位。

### K1：Package/Install 记录

实现最小安装记录模型，明确 `installerPackageName`、`initiatingPackageName`、`packageSource`、签名、split 和版本；补齐 Android 12–16 查询路径。

通过：synthetic caller 在冷启动、宿主重启和重新安装后看到同一自洽记录；真实系统基线与容器视图逐字段比较。不得把 installer 字段写死到全局返回值。

### K2：Provider 身份路由

只用 synthetic Provider，端点记录实际 Binder UID/PID、容器 principal、authority、source package 和 PM 查询结果。

通过：合法 caller 三次成功；另一个同宿主 UID guest 不能借用其 principal；进程死亡重连不串号。失败时保留 SecurityException，不放宽为全局信任宿主 UID。

### K3：Service/AIDL 双向链

在 K2 通过后，验证独立 guest 的 `bindService → onServiceConnected → request → callback → binder death → rebind`。

通过：每个 connection 与发起 principal 单调关联，跨连接不可重放；实际 Kernel Identity 和逻辑身份均被记录但不混称。

### K4：microG settings + Vending

安装冻结、未修改 microG。先验证 settings Provider，再用 synthetic client 调用 `ILicensingService`。保留许可关闭、未登录等真实负结果。

通过：容器结果达到完整 AOSP 基线同一调用阶段，无 UID mismatch，未修改 microG 返回自己的真实结果。

### K5：Hook Runtime 替换

删除 carrier 中损坏的 SandHook/Pine/Xposed 初始化路径。保留 `guest callback → System.load()`，以最小探针验证目标库加载事件；LSPlant/ShadowHook 是否纳入由 Root Hook 轨证据决定。

通过：不启用任何游戏相关 Hook 时，容器启动行为不因旧 Hook 栈崩溃；探针可重复加载一次且不会重复初始化。

### K6：目标兼容回归

仅在 K1–K5 各自通过后接目标。逐项验证 split、Activity、Provider、Service、native loader、前后台、进程重启和 Unity 渲染。

通过：只说明所列兼容机制工作；完整 B 证据链还要求长时间、输入、音频、网络和恢复矩阵。

## 变更归属决策

- 若修复局限在少数 Android 12–16 适配器，可在自有基座内 clean-room 重写；
- 若需要保留 BlackBox 文件，必须先完成对应逐文件人工审计并保留原始历史/NOTICE；
- BlackReflection 优先重写为构建期生成映射，而非暴露其旧运行时接口；
- 如果 K2/K3 只能通过伪造 Binder UID、全局信任宿主 UID或修改端点安全校验，停止该应用级拓扑；
- 单个 K 实验失败不阻止 GMS 检测轨和 Root Hook 轨继续。

## 产物要求

每个实验新增一份 JSON：固定输入、基线提交、唯一处理变量、三次运行、实际观测、判定和回滚状态；总结文档只引用 JSON，不粘贴完整 logcat。
