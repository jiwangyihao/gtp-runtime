# 初步方案：三条机制验证轨道

## 文档状态

本文取代“先让旧基座端到端通过 Stage 0，再做其他工作”的线性计划。现阶段不要求产出容器 MVP；目标是把未知问题拆成三个可独立证伪的机制实验，得到足以指导后续重写范围的证据。

当前三轨均未完成：

- **容器轨**：旧 VirtualApp/AAR 已暴露确定缺口，但尚未验证每项改造能否消除对应失败；
- **GMS 轨**：安装来源归因已完成一个受控闭环，其他检测面仍需逐项枚举和验证；
- **Hook 轨**：设备具备 Root + LSPosed 环境，但尚未在目标进程取得真实文本或呈现译文。

生产集成保持冻结；独立实验可以继续。实验可以修改、替换或重写候选基座的隔离副本，但不得把结果直接宣称为产品可用。

## 共同边界

- 目标包：`jp.gree_ent.mushoku`，versionCode `828343`，versionName `1.0.8`；
- base SHA-256：`50f7dbb7e4c53eb056b1732596298cf4bd70f432da6f4a7d666b4e9edcd724ed`；
- arm64 split SHA-256：`51ccee61fccd3d6f1d309a74503dcde36e0f2e6e936bd2b8fec7627f1fe46b76`；
- 不修改、重签名目标 APK；
- 不伪造许可证结果，不绕过 PAIRIP/Integrity；
- microG 基线锁定 `v0.3.15.250932` / commit `352f2d72fa52c6c3c4fdd79d575a071a0da72ad1`；
- 各轨使用结构化证据记录输入、环境、处理变量、实际观测和失败点；不得用计划或日志标签代替结果。

## 轨道一：容器改造验证

详细矩阵见 [`validation/container-modifications.md`](validation/container-modifications.md)。

目的不是证明现有容器“能用”，而是回答：

1. 缺口位于安装记录、PM、Provider、Binder、进程模型、native loader、Hook Runtime 还是 Android 版本适配的哪一层；
2. 对每个缺口实施一个最小隔离改造后，原失败是否消失且无新增身份矛盾；
3. 哪些改造可沉淀为自有基座，哪些意味着应更换实现材料。

首个改造顺序：

1. 建立 synthetic package/Provider/Service 合约测试，不依赖目标游戏；
2. 修复或替换 Provider calling/source identity 路由；
3. 验证跨 guest `bindService → onServiceConnected → AIDL → callback`；
4. 验证安装记录和 `InstallSourceInfo` 自洽；
5. 删除损坏的 SandHook/Pine/Xposed 路径，以独立 native loader + LSPlant/ShadowHook 候选替代；
6. 再接入冻结 microG 和目标游戏。

单项通过只说明对应机制可改；完整容器稳定性仍需冷/暖启动、进程死亡、组件、前后台、输入、音频、网络和 Unity 渲染矩阵。

## 轨道二：GMS/许可证检测契约

详细矩阵见 [`validation/gms-detection-contracts.md`](validation/gms-detection-contracts.md)。

已验证：在固定 Root 真机、相同原版 APK、账号/GMS/网络不变时，安装会话的 installer attribution 可重复控制当前 PAIRIP 初始启动分支。该结论不等于 Play 所有权、许可证成功或 Integrity 通过。

后续按层验证：

1. 安装记录与查询视图；
2. PAIRIP 初始本地检查；
3. Vending `ILicensingService` 解析、绑定、AIDL 和回调；
4. GMS/GSF Provider 与 Binder 身份；
5. Google 账号与 Play Games；
6. 网络、资源下载和后端授权；
7. Play Integrity / DroidGuard（若真实路径触发）；
8. 支付和更新仅在前述能力需要时研究。

每层只记录真实请求与真实结果；不得用 installer 字段替代许可服务，也不得把 microG 的接口存在误报为服务端会接受。

## 轨道三：Root + LSPosed Unity/IL2CPP Hook

详细方案见 [`validation/root-lsposed-il2cpp.md`](validation/root-lsposed-il2cpp.md)。

当前试验台：arm64、Android 16、KernelSU root、SELinux Enforcing、`zygisk_lsposed` 进程存在，目标游戏已安装。此环境用于剥离容器变量，不是最终免 Root 产品环境。

验证递进：

1. 模块只作用于目标包，记录真实 PID/UID/process/ABI；
2. 在不修改目标 APK 时观测 `libunity.so`、`libil2cpp.so` 加载；
3. 证明 Java 与 native Hook 基础能力，记录重入和卸载行为；
4. 解析固定版本 IL2CPP 元数据并定位一个稳定、只读的文本观测点；
5. 从真实目标界面取得一条日文文本，保留线程、对象生命周期和调用上下文；
6. 将该文本送入确定性规则引擎，并以不修改游戏状态的覆盖层显示一条中文译文；
7. 连续冷启动、场景切换和前后台重复，确认无崩溃、无无限递归、无资源泄漏；
8. 将经过验证的载荷接口迁回容器 native loader。

当前不要求“可用 MVP”或剧情翻译。首次闸门只验证 Hook 机制和一条真实文本的端到端证据。

## 轨道间依赖

```text
容器合约实验 ──┐
                ├─> 最终免 Root 集成 ─> 产品级 A/B/C 验收
GMS 检测矩阵 ──┤
                │
Root Hook 实验 ─┘
```

三轨可并行；最终集成才需要共同满足接口。Hook 轨不等待可用容器，容器轨不等待完整翻译，GMS 轨不等待 Hook。

## 研究归档

- [`research-evaluation.md`](research-evaluation.md)：偏离工作价值评估；
- [`archive/stage0-full-experiment-handoff.md`](archive/stage0-full-experiment-handoff.md)：安装来源归因 handoff 的脱敏原文；
- [`experiments/stage0-result.md`](experiments/stage0-result.md)：旧容器身份路由实验；
- [`experiments/next-base-decision.md`](experiments/next-base-decision.md)：完整系统/AVD 基线；
- `../experiments/screen-overlay-archive/`：被降级的 OCR 旁路工程。

## 当前停止条件

- 不将旧 VirtualApp/AAR 直接作为产品基座；
- 不在缺少逐文件来源审计时复制 BlackBox/BlackReflection 代码；
- 不因某个机制实验失败而停止其他独立轨道；
- 不因某个机制实验通过而宣称游戏可用、已翻译或免 Root 产品可交付；
- 若改造只能依赖伪造 Binder UID、修改许可证结果或欺骗 Integrity，则停止该改造方向并保留失败证据。

## 延后决策

最终代码模块树、BlackBox/BlackReflection 的 fork/vendor/参考重写方式、microG 获取边界、LSPlant/ShadowHook 封装方式和项目许可证，都在对应机制有实证后再决定。
