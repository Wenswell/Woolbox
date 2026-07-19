# Global Copy 滚动性能优化计划

## 状态

`READY`。本文是本轮实施的唯一目标指南；当前阶段不修改产品行为、视觉或数据规则。

## Goal

让 `TYPE_ACCESSIBILITY_OVERLAY` 中的 Compose 内容使用窗口级硬件加速，消除 Global Copy 列表持续滑动时最可能的渲染瓶颈，同时保持 Global Copy 与 OLED shield 的现有行为和界面不变。

完成条件：共享覆盖层窗口在挂载后确认使用硬件加速，Global Copy 长列表滑动较当前基线明显改善，两个工具的窗口生命周期与交互没有回归。

## 判断依据

### 第一性原理

流畅滑动要求每帧的组合、布局、绘制和系统合成在帧预算内完成。当前列表已使用 `LazyColumn` 和稳定 key，滑动期间业务状态不变化；但 `ComposeOverlayWindow` 直接通过 `WindowManager.addView()` 创建窗口，窗口参数没有 `FLAG_HARDWARE_ACCELERATED`，同时 Global Copy 使用全屏半透明合成、圆角和阴影。

### 有界贝叶斯判断

按 `P(H|E) ∝ P(E|H) × P(H)` 比较当前代码证据：

| 假设 | 与“持续滑动卡顿”的符合度 | 当前判断 |
| --- | --- | --- |
| 覆盖层未启用窗口级硬件加速 | 影响每一帧，且代码明确缺少窗口 flag | 最高 |
| 全屏透明、圆角和阴影成本过高 | 会放大绘制成本，但硬件渲染下通常可接受 | 次要 |
| 行内字符串和文本布局过重 | 主要发生在新列表项进入视口时 | 次要 |
| 状态粒度导致重组 | 状态只在选择或编辑时变化 | 与持续滑动不完全符合 |

### 奥卡姆剃刀

先修正共享窗口的渲染前提，只引入一个平台标准 flag。该修改能直接解释现象，且不需要改变列表结构、业务模型或视觉设计。

Android 官方窗口级硬件加速说明：<https://developer.android.com/develop/ui/views/graphics/hardware-accel#window-level>。

## 实施范围

1. 在 `ComposeOverlayWindow.show()` 调用 `WindowManager.addView()` 前，为传入的 `WindowManager.LayoutParams` 添加 `FLAG_HARDWARE_ACCELERATED`。
2. 由共享窗口组件统一维护该契约，使 Global Copy、OLED 遮罩和 OLED 控制面板使用同一渲染规则。
3. 在 `docs/architecture.md` 记录覆盖层窗口的硬件加速契约。
4. 在 `docs/development.md` 记录对应的真机验收和故障定位方法。

## 明确排除

- 不修改 Global Copy 的布局、圆角、阴影、透明度、文本行数或交互。
- 不限制快照块数量，不截断复制数据。
- 不拆分 `CopySessionUiState`，不增加 UI Model、不可变集合或新依赖。
- 不增加窗口注册中心、备用渲染路径或运行时降级。
- 不为单个 flag 引入测试工厂、Benchmark 模块或 instrumentation 基础设施。
- 本轮不自动实施阴影、文本或状态优化；未达标时重新取证并单独确定下一目标。

## 执行步骤

1. 使用当前版本和同一目标页面确认 Global Copy 长列表滑动基线。
2. 在共享覆盖层窗口中添加硬件加速 flag，保持其他窗口参数不变。
3. 执行定向编译及提交前的全部单元测试、Debug 构建和 `git diff --check`。
4. 在同一设备、同一目标页面复测 Global Copy 快速滑动与慢速拖动。
5. 确认挂载后的 `ComposeView.isHardwareAccelerated == true`。
6. 回归 Global Copy 选择、编辑、复制、关闭，以及 OLED 遮罩、长按面板、Slider 和拖动。
7. 同步长期文档，复核差异后创建本地 commit。

## 验收标准

- `ComposeOverlayWindow` 在 `addView()` 前设置 `FLAG_HARDWARE_ACCELERATED`，没有第二处硬件加速配置源。
- Global Copy 覆盖层挂载后 `ComposeView.isHardwareAccelerated` 为 `true`。
- 相同设备和数据下，长列表连续滑动不再出现修改前可稳定复现的持续卡顿。
- Global Copy 与 OLED shield 的窗口尺寸、透明度、触摸、输入法和关闭行为保持不变。
- `:app:testDebugUnitTest :app:assembleDebug` 与 `git diff --check` 通过。

## 停止条件

验收全部通过即结束本轮，不继续做 P1 优化。若硬件加速已确认生效但滑动仍未达标，本轮停止，不叠加推测性修改；下一轮先取得帧耗时证据，再重新判断瓶颈。

完成后将长期有效结论保留在主题文档，并删除本文，避免计划文档成为第二事实来源。
