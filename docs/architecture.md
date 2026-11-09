# 系统架构

## 总览

```text
Tool pages ─────> MainActivity ───────────────┐
     │                                        │
     └─ OLED without service ─> InAppOledShield
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

## 系统权限

Tools 页读取两项独立系统权限。`AccessibilityServiceMonitor` 读取无障碍服务开关；`BatteryOptimizationMonitor` 使用 `PowerManager.isIgnoringBatteryOptimizations(packageName)` 读取电池优化豁免。用户点击未授权的电池优化行时，应用通过 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 和当前包名 URI 打开系统授权页，并在 Activity `ON_RESUME` 后重新读取状态；授权页返回后的刷新最多确认 5 次，间隔 `500ms`，不持久化该状态。

一个 `AccessibilityService` 统一拥有外部窗口读取、待执行命令、覆盖层和工具生命周期。工具页面与 Tile 只负责提交 `ExternalToolCommand`，不读取目标窗口、不持有覆盖层。

## 模块职责

| 模块 | 职责 |
| --- | --- |
| `MainActivity` | 承载 Compose；普通入口退到后台，OLED 已保存应用入口启动指定应用，并在 `onStop` 提交命令。 |
| `ToolServiceController` | 通过弱引用把页面与 Tile 命令交给当前服务实例。 |
| `ToolAccessibilityService` | 管理连接、命令状态机、目标变化、控制器生命周期和 Tile 刷新。 |
| `ForegroundWindowResolver` | 从无障碍窗口中选择当前外部 `TYPE_APPLICATION` 目标。 |
| `WindowSnapshotBuilder` | 遍历目标应用节点并生成稳定的文本快照。 |
| `CopySessionController` | 管理文本选择、草稿、剪贴板写入和复制会话窗口。 |
| `OledShieldController` | 管理无障碍遮罩、控制面板、实时配置和按应用保存。 |
| `InAppOledShield` | 服务未连接时在 Activity composition 顶层显示 OLED 遮罩，复用遮罩与控制面板 UI。 |
| `ComposeOverlayWindow` | 把 Compose 内容挂载为 `TYPE_ACCESSIBILITY_OVERLAY` 并管理生命周期。 |
| `ToolServiceStateStore` | 发布页面和 Tile 需要的持续状态。 |
| `BatteryOptimizationMonitor` | 读取应用电池优化豁免，并打开针对当前包名的系统授权页。 |
| `CopyHistoryRepository` | 用 Room 保存完整采集历史及可编辑会话状态。 |
| `CopyHistoryViewModel` / `AppDisplayInfo` | 把历史摘要与当前应用名称、图标组合为页面模型；应用不可用时使用通用展示，不把包名作为历史列表名称。 |
| `OledShieldConfigRepository` | 保存默认配置与按包名唯一的应用配置。 |

## 命令状态机

### 页面触发
1. Global Copy 只在 `isServiceConnected` 时启用主操作；OLED 主操作始终可用。
2. OLED 在服务未连接时直接更新应用内遮罩状态，不提交 `ExternalToolCommand`。
3. 服务连接时，普通入口保存命令并调用 `moveTaskToBack(true)`；OLED 已保存应用入口通过包管理器的 launcher Intent 启动指定包名。
4. Activity 收到 `onStop` 后提交命令，避免本应用窗口参与目标选择。

### Tile 触发
1. Tile 在服务已连接时直接提交命令；未连接时打开应用。
2. 服务提交 `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`，等待 System UI 退出前台。

### 共同执行
1. 服务关闭现有工具，把状态设为对应的 `*_PREPARING`。
2. 首次等待 `120ms`，随后每 `120ms` 解析一次前台窗口。
3. 普通命令只保存工具类型和两秒截止时间；OLED 已保存应用命令还携带包名、配置和五秒截止时间。
4. 普通命令接受任一实时外部目标；已保存应用命令只接受指定包名。匹配后清除待执行命令并启动控制器。
5. 截止时间内没有匹配目标时恢复 `NONE`，不创建覆盖层。

同类工具处于 `PREPARING` 或 `ACTIVE` 时再次提交命令，会取消命令并关闭工具。不同工具命令会先关闭现有工具，再进入新的准备状态。

命令统一投递到服务主线程串行处理。服务是覆盖层的唯一所有者；`CopySessionController.show()` 与 `OledShieldController.show()` 在创建新窗口前释放自身已有窗口，OLED 控制面板在窗口已显示时忽略重复长按。因此任意时刻最多存在一个活动工具、一个 Global Copy 会话窗口、一个 OLED 遮罩和一个 OLED 控制面板。

## 目标窗口选择

候选只包含具有 root 和包名的 `TYPE_APPLICATION` 窗口，并排除 `com.android.systemui` 与 `com.wswensw.woolbox`。

选择顺序：
1. 存在 `isActive` 候选时，只在活动候选中选择。
2. 否则存在 `isFocused` 候选时，只在焦点候选中选择。
3. 再按 `isActive`、`isFocused`、`layer` 降序取首项。

System UI 是否处于前台只检查 `rootInActiveWindow`，普通 System UI 辅助窗口不会阻塞命令。`TYPE_WINDOWS_CHANGED` 事件延迟 `80ms` 复核目标，减少窗口切换中间态。

## 生命周期

- 服务连接：重建两个控制器、附加弱引用、发布连接状态并刷新目标与 Tile。
- Woolbox 前台：取消待执行命令、关闭工具并清空目标包名。
- 外部目标包名变化：关闭活动工具，再发布新目标。
- 找不到外部目标：关闭活动工具并清空目标。
- `onUnbind` 或 `onDestroy`：通过同一幂等清理移除回调、命令、窗口、弱引用和活动状态。

覆盖层可能产生包名为本应用的无障碍事件。只有本应用的 `TYPE_APPLICATION` 窗口实际处于活动或焦点状态时，才按“Woolbox 前台”处理；本应用的 `TYPE_ACCESSIBILITY_OVERLAY` 事件不能关闭自身。

## 覆盖层窗口契约

`ComposeOverlayWindow` 直接使用 `AccessibilityService` 作为 `ComposeView` Context，并从该服务获取 `WindowManager` 与 `currentWindowMetrics.bounds`。不要直接从无 Display 的服务 Context 调用 `createWindowContext()`；该调用会抛出 `UnsupportedOperationException`，终止无障碍服务并使页面停留在 `Waiting for service`。

这项边界来自旧应用 `app0603` 已验证的实现：各覆盖层控制器直接通过服务 Context 获取 `Context.WINDOW_SERVICE` 和资源指标。当前应用在此基础上增加 Compose 所需的 `LifecycleOwner`、`SavedStateRegistryOwner` 与 composition 销毁流程。

每个覆盖层窗口遵循：

- `show` 前状态必须为空；`addView` 失败时立即销毁生命周期所有者并继续抛出原始错误。
- `show` 在 `addView` 前统一添加 `FLAG_HARDWARE_ACCELERATED`，保证直接由服务创建的 Compose 窗口使用硬件渲染。
- 添加成功后生命周期进入 `RESUMED`。
- `dismiss` 使用 `removeViewImmediate`，随后进入 `DESTROYED` 并清空引用。
- Copy 会话使用可聚焦全屏透明窗口，以支持同一窗口内编辑输入；窗口使用 `SOFT_INPUT_ADJUST_RESIZE`，根布局消费 IME Insets，使面板和底部操作保持在输入法上方。窗口内的实体面板水平内缩 `12dp`、垂直内缩 `32dp`，外层 scrim 透出目标应用。
- OLED 遮罩和面板保持非焦点窗口。

OLED 遮罩窗口额外使用 `FLAG_LAYOUT_NO_LIMITS` 和 `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`。遮罩尺寸仍按完整显示指标计算，使顶部遮罩能布局到状态栏和显示刘海区域；控制面板不使用越界布局。

## Global Copy 数据管线
1. 遍历目标包名的全部应用窗口；活动 root 尚未包含时补充遍历一次。
2. 节点必须可见、非密码、边界非空。
3. 每个节点按 `text`、`contentDescription`、`hintText`、`stateDescription`、`error`、`tooltipText`、`paneTitle` 顺序取首个非空值；Android 14 及以上在这些值均为空时再读取 `containerTitle`。
4. 按窗口、来源、NFKC/空白归一化文本和边界去重。
5. 按 `top`、`left`、`bottom`、`right`、归一化文本排序，生成稳定 `captureOrder`。

复制阶段按“来源 + 归一化文本”再次去重。捕获顺序逐块换行；阅读顺序先按几何位置分视觉行，行内从左到右用空格连接。`CopySessionController.show()` 默认全选文本块，同时生成两种可独立编辑的草稿，并以开启 `Reading order` 的编辑页作为首屏。

文本选择变化时立即重新生成两种草稿。顺序切换只改变当前显示的草稿，不覆盖任一草稿中的编辑；复制只读取当前顺序的非空草稿。

`ClipboardManager.setPrimaryClip()` 写入后显示成功 Toast，再通过统一的 `close()` 销毁会话窗口并发布非活动状态。

非空快照先写入 `copy_history.db`，成功后才显示会话。`copy_history_entries` 保存包名、捕获时间、两种草稿和当前顺序，`copy_history_blocks` 保存完整文本块与选择状态并随父记录级联删除。列表只查询最多 `240` 个字符的摘要；`CopyHistoryViewModel` 再通过 `AppDisplayInfo` 解析当前应用名称和图标，应用不可用时使用通用名称和图标，列表不显示包名；打开记录时才加载文本块。跨应用会话与应用内历史 Dialog 共用 `CopySessionUiState` 和编辑面板，窗口宿主分别由服务与 Activity 管理。

## App 更新

`AppUpdateManager` 在 `MainActivity.onCreate` 初始化，设置页进入时读取 `BuildConfig.UPDATE_MANIFEST_URL`。debug 与 release 的 URL 固定为 `https://github.com/Wenswell/Woolbox/releases/download/{buildType}/latest.json`；manifest 必须匹配 `BuildConfig.APPLICATION_ID`、`BuildConfig.BUILD_TYPE`、正版本号、对应构建类型的版本化 APK 路径、SHA-256 和 `1..500MiB` 的大小。初始 URL 仅接受 github.com 指定仓库、通道及无 query/fragment 的预期路径；HTTPS 请求跟随 GitHub 附件重定向。

下载写入 `files/updates/` 的临时文件，大小与 SHA-256 全部通过后才改名为缓存 APK；缓存复用前重新校验。`AppUpdateInstaller` 使用 `MODE_FULL_INSTALL`、目标包名和 `USER_ACTION_REQUIRED` 创建 `PackageInstaller` session；`AppUpdateInstallReceiver` 只处理本应用显式广播并启动系统确认 Intent。安装来源授权由 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` 打开，App 不使用 FileProvider、静默安装或跨 build type 的 APK。

## OLED 配置

配置保存在 `oled_shield_config` SharedPreferences 中。默认键前缀为 `default`；应用包名集合键为 `profiles`，应用键前缀为 `profile.<packageName>`，字段为 `opacity`、`width`、`height`、`panel_x`、`panel_y`、`recorded_at`。

每个包名只有一条完整配置。遮罩窗口显示成功后按包名 upsert 当次规范化参数和时间；控制面板 `Save` 只更新参数。没有应用配置时读取当前默认值。三个百分比限制为 `1..100`，面板位置限制为 `0f..1f`。

页面通过 `MAIN` + `LAUNCHER` 包可见性查询当前应用名称、图标和可启动状态，不持久化图标副本。`Enable` 把列表中的包名与配置写入定向命令；服务等待该包名成为前台后才显示遮罩。

遮罩尺寸按显示区域百分比计算并固定在左上角。面板宽度为 `360dp` 且不超过屏幕，估算高度为 `420dp`；拖动位置限制在显示区域内，再保存为可移动范围的相对比例。

## 扫码、滚动与日志

扫码使用 CameraX 生命周期绑定和 ML Kit 内置二维码模型。分析采用 KEEP_ONLY_LATEST，识别结束关闭 ImageProxy，页面销毁解绑自身 use cases 并关闭 scanner；相册通过系统 GetContent 读取 URI。参考 [ML Kit 官方文档](https://developers.google.com/ml-kit/vision/barcode-scanning/android)。

滚动模式使用底部 WRAP_CONTENT、NOT_FOCUSABLE / NOT_TOUCH_MODAL 无障碍窗口；编辑模式恢复全屏可聚焦窗口。服务对目标滚动和内容变化事件做 500ms debounce，执行时再次检查控制器状态与目标。ScrollCapture 按首次出现顺序去重；跨屏几何纵向偏移防止阅读排序混合不同屏幕。历史更新在事务内替换文本块，并由服务串行写入。

ToolLogs 使用独立 Room 数据库 tool_logs.db；同进程写入顺序串行，原文仅保存在应用私有存储。界面观察最近 200 条；导出等待已排队写入并按 ID 每批 200 条流式写入 JSONL。持久化错误在日志页显示。
