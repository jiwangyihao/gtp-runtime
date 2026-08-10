# 初步方案：先证明身份拓扑，再决定代码结构

## 文档状态

本文固化已经确认的前置约束、实验和停止条件。它不是实现规格，也不决定最终模块目录、fork、subtree、vendor 或依赖引入方式。

## 目标

在不修改目标游戏 APK、不绕过许可证或完整性校验的前提下，判断免 Root 应用级容器能否为未修改的 microG Vending 提供自然一致的 Binder/PackageManager 身份视图。只有结果可证实后，才选择容器基座和代码结构。

## 已知事实

- 目标包：`jp.gree_ent.mushoku`。
- 已观测 base APK SHA-256：`50f7dbb7e4c53eb056b1732596298cf4bd70f432da6f4a7d666b4e9edcd724ed`。
- 已观测 arm64 split SHA-256：`51ccee61fccd3d6f1d309a74503dcde36e0f2e6e936bd2b8fec7627f1fe46b76`。
- 隔离原型已把宿主 native 探针加载进目标 guest 进程，并在同一进程观察到 `libunity.so` 与 `libil2cpp.so`。
- 旧 VirtualApp 原型无法绑定 `com.android.vending.licensing.ILicensingService`，同时存在 SandHook JNI 初始化失败和 Google 服务缺失。
- 上述事实只证明 native 载荷入口；游戏完整运行、菜单翻译和可交付状态均未验证。
- `Binder.getCallingUid()` 返回发送当前 Binder 事务进程的 Linux UID。路由对象、wrapper、lease 或 guest 自报包名不能改变这个事实。

## 锁定的 microG 实验对象

Stage 0A 与 Stage 0B 必须使用同一版本，不得把 `master` 清单、不同构建或不同 release 混用：

- release/tag：`v0.3.15.250932`
- tag commit：`352f2d72fa52c6c3c4fdd79d575a071a0da72ad1`
- `com.android.vending-84022630.apk`
  - SHA-256：`a973e0235a2829773a4faf36d235d5f703d1c04a2adff674ebaa535a2e78f937`
- `com.google.android.gms-250932030.apk`
  - SHA-256：`52597e77fd25fdd347574d0457ed1936a4b9561cf4c8d34e7ac8dd8191dfd4b9`

两个 APK 均使用上游发布物，实验不得修改其字节、签名、服务身份判断或许可证结果。观测可通过宿主 debug 进程、调试器或只读探针完成，但不得替换返回值、调用参数或 Binder 身份。

## Stage 0A：同一 guest 进程拓扑

### 问题

当目标 guest 与 microG Vending 服务被容器安排在同一实际 guest 进程/Kernel Identity 下时，未修改的 `LicensingService` 能否自然看到与其 PackageManager 视图一致的调用者？

### 必须记录

- 实验 run ID、设备构建指纹、Android API、ABI、boot ID；
- 两个锁定 microG APK 的实测 SHA-256；
- 客户端与许可端点的实际 PID、Linux UID、进程启动代次；
- 客户端和许可端点各自调用 `getPackagesForUid(actualUid)` 的完整结果；
- 许可端点视图中目标包的 `ApplicationInfo.uid`、版本、签名证书 SHA-256；
- `ILicensingService` 解析到的 component 与进程；
- bind 建立、断开、重绑、死亡通知和回调/无回调/错误结果；
- microG 是否产生 `ERROR_NON_MATCHING_UID` 或等价身份拒绝；
- 同一实验冷启动至少重复三次，且进程死亡后重新执行一次。

### 通过条件

未修改 microG 在每次运行中实际观察的 calling UID 与其 PackageManager 视图中的目标包 UID 一致；`getPackagesForUid()` 能把该 UID 解析到原签名目标包；绑定和身份判断不依赖 guest 自报包名、伪造 UID 或改变许可结果。真实许可证 verdict 可成功也可失败，但失败必须保持上游语义。

同一 UID 若同时代表多个不受信任 guest，则该结果只证明技术一致性，不自动证明隔离安全；进入下一阶段前必须单独评估单目标 allowlist 是否足以收窄威胁模型。

## Stage 0B：microG 独立进程拓扑

### 问题

当未修改 microG Vending 在独立实际进程运行时，它能否通过容器的完整 Binder/PM 路由自然观察到目标调用者，而不是只看到宿主 UID？

### 记录与重复要求

记录与 Stage 0A 完全相同的一组证据，并额外记录客户端到独立服务进程的实际 Binder 边、系统进程表和两端 PM 映射。

### 通过条件

许可端点的实际 `Binder.getCallingUid/Pid()`、目标包 `ApplicationInfo.uid` 和 `getPackagesForUid()` 在未修改 microG 中自然一致，且跨重启不漂移。任何 wrapper 或 lease 只能选择路由，不能成为 microG 身份判断的替代输入。

### 立即否决条件

如果独立服务只能看到宿主 Linux UID，或只有通过 Hook `Binder.getCallingUid()`、全局 UID 伪装、guest 自报 principal、修改 microG 身份校验才能令结果一致，则否决该拓扑，不进入账号、真实许可或大规模容器开发。

## Stage 0 决策树

1. **0A 通过，0B 失败**：只保留“同一 guest 进程服务”作为候选拓扑；完成共享 UID 威胁模型审查后再决定是否继续。
2. **0B 自然通过**：可考虑具有完整一致身份视图的容器内 Binder/PM 路由；仍须通过后续真实账号与许可证验收。
3. **0A、0B 均失败**：停止应用级 GMS 容器路线，重新评估完整系统虚拟机、受支持系统环境，或官方安装配合外部 OCR/覆盖层。
4. **证据不完整**：状态为 blocked，不把猜测当通过，也不开始 BlackBox 大规模重写。

## 上游采用方式：当前不决策

- **BlackBox**：固定提交仅作为来源审计和容器行为参考。是 fork、选择性迁移、vendor 还是完全重写，待 Stage 0 与人工来源审计后决定。
- **BlackReflection**：固定提交仅作为隐藏接口生成思路参考。直接引入、轻改或构建期重写，待 Stage 0 后决定。
- **microG**：Stage 0 默认使用上述零改动锁定发布物。仅在实机证明存在纯 API 兼容缺口时，才讨论保持失败语义、可审计且优先上游化的最小补丁；身份或许可语义缺口不属于该例外。
- **LSPlant / ShadowHook**：目前只是 Hook Runtime 候选依赖，尚未进入仓库或确定封装方式。
- **VirtualApp / SpaceCore**：旧原型和闭源 SDK 不进入本仓库产品代码。

## 独立证据链

- **A：许可证/GMS**——身份、包视图、服务绑定、真实账号、真实后端 verdict 与必要的 Integrity 结果。
- **B：容器稳定性**——冷/暖启动、进程死亡恢复、前后台、组件、多进程、输入、音频、网络和 Unity 渲染。
- **C：Unity/IL2CPP 翻译效果**——实际文本取得、规则命中、译文呈现和重复进入后的稳定性。

A、B、C 互不替代。native load 证据不能令 A、B 或 C 自动通过。

## Stage 0 后才做的决策

- 项目许可证；
- 最终 Android API/ABI 支持矩阵；
- Gradle/AGP/NDK 等工具链锁定；
- 模块和包目录；
- BlackBox/BlackReflection 的 fork、vendor、直接依赖或 clean-room 重写方式；
- microG 发布物的获取和验证流程；
- LSPlant/ShadowHook 的依赖与封装方式；
- 规则包与翻译内容的发布边界。

## 当前停止条件

Stage 0 身份拓扑未通过前：不创建生产模块树、不复制上游源码、不导入 microG 二进制、不接入 Google 账号、不执行真实许可请求、不扩展翻译实现，也不把旧原型描述为产品基座。
