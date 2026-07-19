# 系统架构

## 总览

```text
Compose pages ──> MainActivity ───────────────┐
Quick Settings Tiles ─────────────────────────┤
                                              ▼
                                    ToolServiceController
                                              │ weak reference
                                              ▼
                                   ToolAccessibilityService
                    ┌─────────────────────────┼─────────────────────────┐
                    ▼                         ▼                         ▼
       ForegroundWindowResolver    WindowSnapshotBuilder    ToolServiceStateStore
                    │                         │                         │
                    └──────────────┬──────────┘                         ├─> pages
                                   ▼                                    └─> Tiles
                    CopySessionController / OledShieldController
                                   │
                                   ▼
                        ComposeOverlayWindow
```

一个 `AccessibilityService` 统一拥有外部窗口读取、待执行命令、覆盖层和工具生命周期。应用页面与 Tile 只负责提交 `ExternalToolCommand`，不读取目标窗口、不持有覆盖层。

## 模块职责

| 模块 | 职责 |
| --- | --- |
| `MainActivity` | 承载 Compose；页面触发时将任务移到后台，并在 `onStop` 提交命令。 |
| `ToolServiceController` | 通过弱引用把页面与 Tile 命令交给当前服务实例。 |
| `ToolAccessibilityService` | 管理连接、命令状态机、目标变化、控制器生命周期和 Tile 刷新。 |
| `ForegroundWindowResolver` | 从无障碍窗口中选择当前外部 `TYPE_APPLICATION` 目标。 |
| `WindowSnapshotBuilder` | 遍历目标应用节点并生成稳定的文本快照。 |
| `CopySessionController` | 管理文本选择、编辑、剪贴板写入和复制会话窗口。 |
| `OledShieldController` | 管理遮罩、控制面板、实时配置和按应用保存。 |
| `ComposeOverlayWindow` | 把 Compose 内容挂载为 `TYPE_ACCESSIBILITY_OVERLAY` 并管理生命周期。 |
| `ToolServiceStateStore` | 发布页面和 Tile 需要的持续状态。 |
| `OledShieldConfigRepository` | 保存默认配置与按包名配置。 |

## 命令状态机

### 页面触发

1. 页面只在 `isServiceConnected` 时启用主操作。
2. `MainActivity` 保存命令并调用 `moveTaskToBack(true)`。
3. Activity 收到 `onStop` 后提交命令，避免本应用窗口参与目标选择。

### Tile 触发

1. Tile 在服务已连接时直接提交命令；未连接时打开应用。
2. 服务提交 `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`，等待 System UI 退出前台。

### 共同执行

1. 服务关闭现有工具，把状态设为对应的 `*_PREPARING`。
2. 首次等待 `120ms`，随后每 `120ms` 解析一次前台窗口。
3. 命令只保存工具类型和基于 `SystemClock.uptimeMillis()` 的截止时间，不保存历史包名。
4. 解析到外部目标后立即清除待执行命令，并启动对应控制器。
5. `2,000ms` 内没有目标时恢复 `NONE`，不创建覆盖层。

同类工具处于 `PREPARING` 或 `ACTIVE` 时再次提交命令，会取消命令并关闭工具。不同工具命令会先关闭现有工具，再进入新的准备状态。

命令统一投递到服务主线程串行处理。服务是覆盖层的唯一所有者；`CopySessionController.show()` 与 `OledShieldController.show()` 在创建新窗口前释放自身已有窗口，OLED 控制面板在窗口已显示时忽略重复长按。因此任意时刻最多存在一个活动工具、一个 Global Copy 会话窗口、一个 OLED 遮罩和一个 OLED 控制面板。

## 目标窗口选择

候选只包含具有 root 和包名的 `TYPE_APPLICATION` 窗口，并排除 `com.android.systemui` 与 `com.wswensw.template`。

选择顺序：

1. 存在 `isActive` 候选时，只在活动候选中选择。
2. 否则存在 `isFocused` 候选时，只在焦点候选中选择。
3. 再按 `isActive`、`isFocused`、`layer` 降序取首项。

System UI 是否处于前台只检查 `rootInActiveWindow`，普通 System UI 辅助窗口不会阻塞命令。`TYPE_WINDOWS_CHANGED` 事件延迟 `80ms` 复核目标，减少窗口切换中间态。

## 生命周期

- 服务连接：重建两个控制器、附加弱引用、发布连接状态并刷新目标与 Tile。
- Template 前台：取消待执行命令、关闭工具并清空目标包名。
- 外部目标包名变化：关闭活动工具，再发布新目标。
- 找不到外部目标：关闭活动工具并清空目标。
- `onUnbind` 或 `onDestroy`：通过同一幂等清理移除回调、命令、窗口、弱引用和活动状态。

覆盖层可能产生包名为本应用的无障碍事件。只有本应用的 `TYPE_APPLICATION` 窗口实际处于活动或焦点状态时，才按“Template 前台”处理；本应用的 `TYPE_ACCESSIBILITY_OVERLAY` 事件不能关闭自身。

## 覆盖层窗口契约

`ComposeOverlayWindow` 直接使用 `AccessibilityService` 作为 `ComposeView` Context，并从该服务获取 `WindowManager` 与 `resources.displayMetrics`。不要直接从无 Display 的服务 Context 调用 `createWindowContext()`；该调用会抛出 `UnsupportedOperationException`，终止无障碍服务并使页面停留在 `Waiting for service`。

这项边界来自旧应用 `app0603` 已验证的实现：各覆盖层控制器直接通过服务 Context 获取 `Context.WINDOW_SERVICE` 和资源指标。当前应用在此基础上增加 Compose 所需的 `LifecycleOwner`、`SavedStateRegistryOwner` 与 composition 销毁流程。

每个覆盖层窗口遵循：

- `show` 前状态必须为空；`addView` 失败时立即销毁生命周期所有者并继续抛出原始错误。
- 添加成功后生命周期进入 `RESUMED`。
- `dismiss` 使用 `removeViewImmediate`，随后进入 `DESTROYED` 并清空引用。
- Copy 会话使用可聚焦全屏透明窗口，以支持同一窗口内编辑输入；窗口内的实体面板水平内缩 `12dp`、垂直内缩 `32dp`，外层 scrim 透出目标应用。
- OLED 遮罩和面板保持非焦点窗口。

## Global Copy 数据管线

1. 遍历目标包名的全部应用窗口；活动 root 尚未包含时补充遍历一次。
2. 节点必须可见、非密码、边界非空。
3. 每个节点按 `text`、`contentDescription`、`hintText`、`stateDescription`、`error`、`tooltipText`、`paneTitle`、`containerTitle` 顺序取首个非空值。
4. 按窗口、来源、NFKC/空白归一化文本和边界去重。
5. 按 `top`、`left`、`bottom`、`right`、归一化文本排序，生成稳定 `captureOrder`。

复制阶段按“来源 + 归一化文本”再次去重。捕获顺序逐块换行；阅读顺序先按几何位置分视觉行，行内从左到右用空格连接。编辑模式同时生成两种草稿，`Reading order` 在原始草稿之间切换。

`ClipboardManager.setPrimaryClip()` 写入后显示成功 Toast，再通过统一的 `close()` 销毁会话窗口并发布非活动状态。

## OLED 配置

配置保存在 `oled_shield_config` SharedPreferences 中。默认键前缀为 `default`，应用键前缀为 `package.<packageName>`；字段为 `opacity`、`width`、`height`、`panel_x`、`panel_y`。

读取应用配置时，以 `opacity` 键是否存在判断该应用是否已有配置；没有时返回当前默认配置。所有读写都经过 `OledShieldConfig.normalized()`：三个百分比限制为 `1..100`，面板位置限制为 `0f..1f`。

遮罩尺寸按显示区域百分比计算并固定在左上角。面板宽度为 `360dp` 且不超过屏幕，估算高度为 `420dp`；拖动位置限制在显示区域内，再保存为可移动范围的相对比例。
