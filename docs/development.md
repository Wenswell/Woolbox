# 开发记录

## 当前架构规则

- `ToolAccessibilityService` 是 Global Copy 与 OLED shield 的系统能力入口。
- `ToolServiceStateStore` 只发布页面和 Tile 需要的持续状态。
- `ToolServiceController` 使用单一服务弱引用分发 Tile 命令。
- `ForegroundWindowResolver` 只选择外部 `TYPE_APPLICATION` 窗口，并将 System UI 与本应用排除在目标范围外。
- 两个 Tile 使用同一待执行命令状态机：收起 Quick Settings 面板，解析目标窗口，执行工具动作。
- 待执行命令只保存工具类型和截止时间；系统面板收起后轮询外部应用窗口，准备状态再次点击会取消命令。
- `TYPE_WINDOWS_CHANGED` 即使没有事件包名也会触发目标窗口复核。
- `ComposeOverlayWindow` 为每个系统覆盖层建立 `LifecycleOwner` 与 `SavedStateRegistryOwner`，并在窗口移除时销毁 composition。
- 服务在 `onUnbind` 与 `onDestroy` 通过同一幂等清理释放覆盖层、弱引用和全局状态。
- OLED 配置使用类型化 `SharedPreferences` 键，默认配置与按包名配置独立保存。

## 业务规则

- Global Copy 文本块从可见、非密码、具有屏幕边界的无障碍节点生成。
- 文本候选按 `text`、`contentDescription`、`hint`、`stateDescription`、`error`、`tooltip`、`paneTitle`、`containerTitle` 的优先级选择。
- 捕获顺序使用快照稳定顺序；阅读顺序按视觉行和从左至右顺序生成。
- 复制前使用 NFKC 和空白归一化进行重复文本判断。
- OLED 默认值为 `100%` 不透明、`100%` 宽、`15%` 高。
- OLED 控制面板位置使用显示区域比例保存，屏幕尺寸变化后保持相对位置。
- 屏幕配置变化时重新计算 OLED 遮罩尺寸和控制面板位置；遮罩窗口接收触摸并保持非焦点窗口。

## 版本与依赖规则

- `minSdk` 为 `34`，系统 Tile 交互直接使用 Android 14 API。
- 依赖保持在 Compose、Navigation、Core KTX、Lifecycle 和 JUnit 范围内。
- 覆盖层使用 Android 平台 `TYPE_ACCESSIBILITY_OVERLAY`，无需悬浮窗权限。
- 当前产品定位为通用工具，无障碍服务声明不使用 `isAccessibilityTool`。
- 图标使用本地 vector 资源。

## 验证规则

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest :app:assembleDebug
git diff --check
```

设备验收覆盖以下场景：

1. 启用无障碍服务后，两个工具页面显示 `Connected`。
2. 从目标应用打开 Quick Settings，分别添加并触发两个 Tile。
3. Global Copy 选择、两种顺序复制、编辑复制和关闭会话。
4. OLED 遮罩长按、滑块、面板拖动、保存、关闭和按应用恢复。
5. 从目标应用切换到另一应用时，活动覆盖层关闭。

## 文档规则

- 新功能先在 `docs/` 写明范围、交互和验收标准。
- 业务规则、用户流程和开发规则按主题拆分。
- 每轮可验证修改完成后更新文档并创建本地 commit。
