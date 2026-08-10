# 上游逐文件来源与许可证预审

## 目的与边界

本文记录在决定 fork、vendor、直接依赖或重写之前，对两个候选上游固定提交进行的来源预审。仓库当前不包含这些上游的源码或二进制。

本预审结合固定 Git 提交、逐文件 SHA-256、文件头声明、根许可证、README credits 和少量二进制包内容检查。它不是法律意见，也不能把“根目录存在 Apache-2.0 文本”推导为每个文件都由同一权利人以 Apache-2.0 独立授权。没有显式文件头的条目保持“项目声明存在、逐文件来源未独立确认”。

逐文件结果：

- [`blackbox-files.csv`](blackbox-files.csv)
- [`blackreflection-files.csv`](blackreflection-files.csv)

每行记录上游仓库、固定提交、路径、SHA-256、大小、二进制标记、检测到的声明、初始归属分类、许可证状态和当前处置。自动分类必须经人工复核后才能用于代码导入。

## 固定对象

### BlackBox

- 仓库：`https://github.com/BlackBoxing/BlackBox`
- 固定提交：`a0a330732c51dd9476c618ede31ec7d4a168a5d5`
- GitHub fork network 记录的上游：`https://github.com/FBlackBox/BlackBox`
- 根 `LICENSE` SHA-256：`313605fbac6945e9324d4825470796b5b7dbc012f523fdc181f6e6fd234eb88f`
- README 项目声明：`Copyright 2022 BlackBox`、Apache License 2.0。

### BlackReflection

- 仓库：`https://github.com/CodingGay/BlackReflection`
- 固定提交：`5eadef2dd322964b7fa61246ba58e02bbb864c8a`
- 根 `LICENSE` SHA-256：`313605fbac6945e9324d4825470796b5b7dbc012f523fdc181f6e6fd234eb88f`
- README 项目声明：`Copyright 2022 Milk`、Apache License 2.0。

## 清单统计

| 上游 | 文件数 | 总字节 | 二进制文件 | 文件头或独立声明命中 |
| --- | ---: | ---: | ---: | ---: |
| BlackBox | 854 | 9,482,138 | 42 | 57 |
| BlackReflection | 71 | 243,229 | 11 | 1（根 LICENSE） |

“声明命中”只表示扫描到许可证或版权文本，不等于已经判定完整许可证兼容性。

## BlackBox 关键发现

1. **不能整体笼统标记为 Apache-2.0。** README credits 明确列出 VirtualApp、VirtualAPK、BlackReflection、AndroidHiddenApiBypass 和 Pine；代码树同时包含 AOSP、Apache Commons Lang、OpenJDK、SandHook/Pine 衍生代码和其他第三方文件。
2. `android-mirror` 中多份 AIDL，以及部分 PM/User 工具文件，带 Android Open Source Project 的 Apache-2.0 文件头。未来若需要同类接口，优先从对应 AOSP 版本直接取得并保留原始声明，而不是从 BlackBox 转抄。
3. `Bcore/pine-xposed/src/main/apacheCommonsLang/` 带独立 Apache-2.0 `LICENSE.txt` 和 `NOTICE.txt`。NOTICE 记载 Apache Commons Lang 2001–2011 以及 Spring Framework 的 `StringUtils.containsWhitespace()` 来源。若未来需要该能力，优先使用受锁定依赖而不是复制内嵌源码。
4. `CompoundEnumeration.java` 明确来自 Oracle/OpenJDK，采用 **GPL v2 only with Classpath Exception**，不能被根 Apache 声明覆盖。
5. `ModuleClassLoader.java` 带 AOSP Apache-2.0 文件头，并注明 Pine 修改；必须同时保留 AOSP 声明和修改来源。
6. `pine-core/.../elf_img.cpp` 与对应头文件注明来自 SandHook、经 Pine 修改，许可证为 **Anti 996 License Version 1.0**。在完成兼容性和法律复核前禁止复制；当前方案倾向完全排除并使用维护中的 Hook 库。
7. `RockerView.java` 标注 `Copyright (C) 2016 GcsSloop`，文件内没有授权文本。它与运行时目标无关，禁止复制。
8. `Bcore/src/main/assets/junit.jar` 内含 Hamcrest BSD License，但该单个文件不能证明整个 JAR 的完整组成与授权；`empty.jar` 也缺少可确认来源。二者均禁止 vendor。
9. Gradle wrapper JAR、图片和其他二进制资产未完成独立来源确认；本项目不复制它们。
10. 大量文件只有根项目声明，没有逐文件来源头。CSV 将其标记为“根项目声明存在、逐文件来源未独立验证”，只能作为参考。

## BlackReflection 关键发现

1. 根 LICENSE 与 README 声明 Apache-2.0 / `Copyright 2022 Milk`。
2. 除根 LICENSE 外，扫描的源码、资源和构建文件没有独立许可证头；因此不能把逐文件作者和来源视为已验证。
3. 工程包含 `app`、`core`、`compiler`、Gradle wrapper JAR 和被跟踪的 `local.properties`。样例 App、wrapper 和机器配置都不是未来导入候选。
4. `core` 与 `compiler` 可作为“注解描述隐藏接口、构建时生成桥接代码”的设计参考；是否直接依赖、fork 或 clean-room 重写延后到 Stage 0 后，并要求逐文件历史追溯。

## 当前处置

- 两个固定树均为 **reference-only**；本仓库不复制其文件。
- 不创建 BlackBox 或 BlackReflection 的 fork，不把它们作为 Git submodule/subtree，也不发布修改版二进制。
- 若未来选择复制或修改某个文件，必须先为该文件补充：直接来源 URL、原始提交、原路径、原始摘要、权利人、许可证原文、NOTICE 要求、修改说明和新摘要。
- 优先从真正的直接上游取得 AOSP、Pine、Commons Lang 等文件，避免通过聚合仓库继承模糊来源。
- 明确排除旧 Pine/Xposed 兼容层、SandHook 衍生 ELF 工具、RockerView 和不明二进制资产，除非新的 ADR 在完整审计后推翻该处置。
- 项目自身许可证暂不选择；公开仓库不因此获得或授予任何未明确写出的许可。

## 后续人工审计

在 Stage 0 通过、准备决定代码结构时：

1. 对候选文件执行 Git blame/history 和直接上游比对；
2. 解析所有 Gradle/Maven/native 依赖并生成 SBOM；
3. 核验二进制签名、摘要、许可证和可再现来源；
4. 为最终导入清单生成 NOTICE 与改动边界；
5. 再决定项目许可证及 fork/vendor/依赖形态。
