# 屏幕 OCR / 悬浮层历史旁路

## 状态

**已归档，不属于当前产品主线，也不继续扩展。**

该工程曾用于独立验证以下组合机制：

- `MediaProjection` 屏幕捕获；
- ML Kit 日文 OCR；
- 确定性词典规则；
- 不可触摸悬浮层呈现。

它不验证 Unity/IL2CPP 进程内取文、Java/ART Hook、native Hook、容器注入或 GMS 兼容性。OCR 命中不得作为 C 证据链中的 `TextObserved`，悬浮层显示也不得被描述为游戏已经汉化。

## 为什么保留

- 屏幕捕获、规则引擎和覆盖层的独立实现仍可作为呈现与交互参考；
- `DictionaryTranslatorTest` 保留确定性规则的历史行为；
- 当 Root Hook 轨完成真实文本观测后，可参考覆盖层的窗口参数，但不直接把该工程重新并入主构建。

## 为什么停止

产品目标是进程内取得 Unity/IL2CPP 文本。OCR 会丢失对象/场景/说话人上下文，受字体、动画、遮挡和分辨率影响，也无法形成稳定的原文标识。因此它只能作为历史旁路，不能替代 [`docs/validation/root-lsposed-il2cpp.md`](../../docs/validation/root-lsposed-il2cpp.md)。

## 构建边界

此目录是独立 Gradle 工程，不被仓库根构建包含。仓库只保留源码、资源、构建描述和行为测试；`build/`、APK 和本机 `local.properties` 均由根 `.gitignore` 排除。
