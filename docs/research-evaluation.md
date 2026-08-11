# 偏离阶段成果价值评估

## 结论

后续工作没有完成原先设想的“旧容器上端到端 Stage 0”，但并非无价值。它把一个线性、过早的交付闸门拆成了三类可复用机制证据：

1. 安装来源归因是否影响目标的 PAIRIP 初始启动分支；
2. 完整 Android 系统与应用级容器在 Binder/Provider/PackageManager 身份上的差异；
3. 原版 arm64 Unity/IL2CPP 输入能否在官方 native bridge 环境加载。

这些结果应归档为研究材料，不应被包装成容器 MVP、GMS 通过或翻译完成。

## 偏离点与纠正

### 偏离一：把容器阻断误当成容器路线终止

旧 VirtualApp/AAR 的三次实验稳定复现了 Provider 路由错误：guest-visible virtual UID `10004` 与宿主 Linux UID `10379` 混用，Vending 在返回 Binder 前失败；同一基座的 0A 进程模型也不能把目标和 Vending 调度到同一实际进程。

这证明了**当前基座缺少哪些契约**，没有证明“容器不值得重写”。正确动作是把失败拆成改造假设，先用 synthetic Provider/Service/AIDL 合约测试验证局部修复，再评估是否值得吸收 BlackBox 材料。

### 偏离二：把 GMS 轨道窄化为一次 Stage 0 成败

纯 AOSP x86_64 AVD 上 synthetic client 成功完成未修改 Vending 的 `bindService → onServiceConnected → checkLicenseV2`，并保留“用户未登录”的原始负结果；Google APIs 36.1 AVD 能加载目标 arm64 Unity/IL2CPP，但预装包阻止冻结 microG 安装。

这两条不能拼成目标游戏通过，却有明确价值：它们分别给出了完整系统 Binder 基线和目标 native bridge 基线。安装归因 A/B/A/B/A 还证明 installer attribution 是当前 PAIRIP 初始路径的受控输入。下一步应枚举检测契约，而非把所有检测压成一个“GMS 通过/失败”开关。

### 偏离三：过早把 OCR 旁路当成主线

`experiments/screen-overlay-archive/` 中的 MediaProjection + ML Kit 日文 OCR + 确定性词典 + 不可触摸悬浮层组合，具有独立的屏幕捕获和呈现验证价值。但它不验证进程内取文，也不满足产品目标，因此从主线移出并保留为历史旁路；不得继续扩展，也不得将其 OCR 命中当作 Unity/IL2CPP 翻译证据。

### 偏离四：把“native 载荷入口已验证”与 Hook 机制混为一谈

旧 probe 证明宿主 `.so` 可进入 guest，并看见 Unity/IL2CPP 库；它没有证明 Java/ART Hook、native inline Hook、文本定位、规则接线、译文呈现或稳定性。Root + LSPosed 真机应作为独立试验台，先验证这些机制，再考虑容器迁移。

## 证据等级

- **E0：计划/假设**——尚未运行，不能作为能力声明。
- **E1：环境事实**——设备、版本、进程或安装状态的直接观测。
- **E2：局部机制**——单一调用链或组件在受控条件下成功。
- **E3：重复机制**——控制变量重复且结果稳定，但仍非产品验收。
- **E4：产品证据**——支持矩阵内的真实端到端验收；当前没有任何“游戏已翻译/可交付”E4。

当前成果：

| 成果 | 证据等级 | 可支持的结论 | 不可支持的结论 |
| --- | --- | --- | --- |
| 宿主 native probe 进入 guest | E2 | 存在 native 载荷入口 | Hook 已可靠、游戏已翻译 |
| 旧容器 Provider/进程失败 | E3 | 当前基座的具体改造缺口 | 所有容器路线不可行 |
| synthetic client → microG Binder | E2 | 完整系统可建立基础调用链 | 目标游戏身份链通过 |
| A/B/A/B/A installer attribution | E3 | 该环境下 PAIRIP 初始分支受安装归因控制 | Play ownership、Integrity 或长期运行通过 |
| Google APIs native bridge 加载 arm64 Unity/IL2CPP | E2 | 原版 native 输入可被该环境加载 | 翻译 Hook 已验证 |
| OCR 旁路词典测试/屏幕呈现 | E2 | 屏幕辅助机制可独立运行 | 进程内翻译机制或产品交付 |
| Root + LSPosed 环境盘点 | E1 | 具备后续 Hook 试验台 | LSPosed 已命中目标 Hook |

## 当前研究决策

- 主线恢复为“容器改造 + GMS 检测契约 + Root Hook 机制”三轨；
- OCR 工程保留归档，不加入主构建和主线能力声明；
- 生产集成冻结，独立改造实验允许继续；
- Stage 0 旧身份闸门仍记为 blocked，不能改写成通过；
- 只有当某轨获得新的局部证据，才决定是否创建产品模块或吸收上游代码。
