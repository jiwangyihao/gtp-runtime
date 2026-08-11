# Root + LSPosed Unity/IL2CPP Hook 机制验证

## 状态

**未完成。** 当前仅确认试验设备具备：

- Android 16 / API 36；
- arm64-v8a；
- KernelSU root，`su -c id` 返回 root；
- SELinux Enforcing；
- `zygisk_lsposed` companion 进程存在；
- 目标包 `jp.gree_ent.mushoku` 已安装。

这些是环境事实，不证明 LSPosed 作用域已配置、模块已注入、Hook 已命中或文本已取得。

## 目的

在不依赖容器的真实目标进程中证明翻译载荷所需的最小机制：

```text
目标进程命中
  → 观察 Unity/IL2CPP 库加载
  → 定位稳定文本点
  → 取得一条真实日文
  → 确定性规则命中
  → 只读呈现一条中文
```

本轨不要求可用 MVP、剧情翻译、通用 IL2CPP 框架或免 Root 运行。

## 固定输入

- 目标 base/split SHA-256 与 `../initial-plan.md` 一致；
- 设备 fingerprint、boot ID、LSPosed/zygisk 模块版本、目标版本、进程 PID/UID 和 APK path 在每次实验开始时记录；
- 初始安装来源状态单独记录，但 Hook 机制判定不由许可证结果代替；
- 模块作用域只能包含目标包，默认不作用于 Play、GMS、system_server 或其他应用。

## 技术边界

### Java/ART Hook

优先使用现有 LSPosed 模块入口验证类加载、Activity 生命周期和 UnityPlayer Java 层事件。产品 Hook Runtime 的长期候选是 LSPlant；本实验可借助 LSPosed 装载，但不得让载荷接口依赖 Xposed 类型。

### Native Hook

优先使用 ShadowHook 或只读 linker/load 观测；不得复用旧 SandHook/Pine。native 载荷只在目标进程初始化一次，并能处理 `libunity.so`/`libil2cpp.so` 晚加载。

### 文本策略

先选择只读观测点，不直接改写游戏内存。候选按风险从低到高：

1. Unity/IL2CPP 对 managed `String` 进入 UI setter 前的参数；
2. TextMeshPro / Unity UI 文本 setter 的 native bridge；
3. 由元数据解析得到的特定方法入口；
4. 最后才考虑引擎内部布局或对象扫描。

不以硬编码偏移作为长期接口；如为固定版本探针使用偏移，必须同时记录 ELF build ID、库 SHA-256 和验证方式。

## 分阶段实验

### H0：环境与作用域

产物：脱敏 JSON，包含 API/ABI、root/SELinux、LSPosed 模块版本、目标版本、作用域配置证明。

通过：模块仅在目标 PID 记录一次 `MODULE_LOADED`；对照应用无日志；三次冷启动 PID 更新但作用域不漂移。

停止：需要全局作用域、关闭 SELinux 或修改目标 APK 才能加载。

### H1：库加载观测

处理：仅注册加载通知，不改函数返回值。

证据：`libunity.so` 与 `libil2cpp.so` 的实际 path、load address、ELF build ID、文件 SHA-256、加载线程和时序。

通过：三次冷启动及一次前后台恢复均各触发一次 ready 事件；重复回调被去重，无崩溃。

### H2：基础 Hook 可靠性

处理：选择无副作用的 Java 生命周期点和一个只读 native 函数，记录进入/退出、线程和递归深度。

通过：调用次数与对照事件相符；无无限递归、ANR 或对象生命周期破坏；禁用模块后日志完全消失。

### H3：IL2CPP 元数据解析

处理：对固定目标版本解析 `global-metadata.dat` 与 `libil2cpp.so`，建立 image/class/method 映射；不扫描任意对象内存。

通过：运行时验证至少一个已知 Unity 类型和一个文本相关方法的名称、参数数量与地址一致；库更新时旧映射明确拒绝运行而不是继续使用。

### H4：真实文本观测

处理：在一个固定、可重复进入的目标界面观察文本 setter 参数。

通过：结构化记录至少一条屏幕上可人工核对的日文原文，包含方法、线程、时间、对象/场景关联和脱敏截图摘要；重复进入三次文本一致。不得把日志常量、APK strings 或 OCR 结果冒充运行时文本。

### H5：翻译载荷接线

处理：将 H4 文本送入确定性规则引擎，未知文本不输出。译文通过独立、不可触摸覆盖层呈现，第一阶段不改写游戏对象。

通过：一条日文真实文本命中一条固定中文；截图/屏幕录制与结构化事件能建立同一 session 的关联；禁用规则后中文消失，游戏状态不变。

### H6：稳定性重复

场景：三次冷启动、五次界面进入/退出、前后台、旋转/分辨率变化（若游戏支持）、目标进程死亡重启。

通过：无 native crash、ANR、无限递归、重复初始化或持续增长的线程/映射/overlay；每次事件序列完整。

### H7：容器可迁移 seam

将 Hook 实现隐藏在三类结构化事件后：

- `LibraryReady(library, buildId, baseAddress)`；
- `TextObserved(source, context, monotonicSequence)`；
- `TranslationPresented(sourceId, ruleId)`。

通过：Root/LSPosed loader 与现有 guest callback/System.load loader 都能驱动同一载荷接口；翻译核心不依赖 LSPosed、microG 或容器内部类型。

## 证据与安全要求

- 不记录 Google 账号、token、设备序列号或代理凭据；
- 截图只保留目标文本区域，并记录 SHA-256；
- 每个事件包含 run ID、monotonic sequence、PID、process、source module 和版本；
- Hook 禁用/卸载是每阶段必做对照；
- 失败日志与 tombstone 先脱敏再归档。

## 停止条件

- 只能通过修改/重签目标 APK、关闭 SELinux、全局注入或 Hook 许可证/Integrity 才能继续；
- 文本点只能依赖不可验证的随机偏移，或在目标更新后静默误命中；
- Hook 引入可重复崩溃、ANR、状态修改或无法卸载的副作用。

## 结果解释

H0–H7 分阶段通过仍只是 C 机制证据，不是完整剧情翻译或免 Root 产品。只有将同一载荷接口迁入通过容器轨验证的 loader，并在真实游戏路径重复验证，才进入产品级 C 证据链。
