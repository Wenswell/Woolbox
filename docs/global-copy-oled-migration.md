# Global Copy 与 OLED 遮罩迁移计划

## 状态

首轮实现完成，等待设备验收。本文件记录范围、架构、交互决策和验收标准。

## 目标

当前应用作为系列工具应用的基础载体，以实际跨应用场景沉淀 Compose 页面布局。首轮迁入旧应用 `app0603` 的两项系统能力：

- `Global Copy`：读取当前前台应用的可访问文本，支持选择、捕获顺序复制、阅读顺序复制和编辑后复制。
- `OLED shield`：在当前前台应用上显示黑色触摸遮罩，长按打开控制面板，按应用保存遮罩参数。

旧应用提供已经验证的文本提取、窗口选择和配置规则。当前工程以新的服务状态、Compose 覆盖层和页面交互实现这些规则。

## 决策

| 原则 | 当前实现 |
| --- | --- |
| 第一性原理 | 两项能力共享前台窗口读取和无障碍覆盖层，因此使用一个 `AccessibilityService`。 |
| 贝叶斯更新 | 保留旧应用已验证的业务规则，使用当前实现验证新的 UI 和交互。 |
| 奥卡姆剃刀 | 首轮包含服务、领域模型、配置存储、覆盖层、Tile 和工具页面。 |
| 交互上下文 | 目标应用上下文由 Quick Settings Tile 触发并保持；页面承担授权、状态、默认配置和 Tile 添加。 |
| API 基线 | `minSdk` 为 Android 14（API 34），系统 Tile 使用直接 API。 |
| 配置模型 | OLED 默认参数和按包名参数使用独立类型化键保存，控制面板位置以显示区域比例保存。 |

## 范围

### 本轮功能

- 一个无障碍服务，负责连接状态、目标窗口识别、文本快照和覆盖层生命周期。
- Global Copy 会话：选择文本、全选、捕获顺序复制、阅读顺序复制、编辑后复制和关闭会话。
- OLED 遮罩：显示、隐藏、长按控制面板、透明度/宽度/高度调整、控制面板拖动和按应用保存。
- `Home` 下的 `Global Copy` 与 `OLED shield` 详情页。
- 两个专用 Quick Settings Tile。
- OLED 默认参数编辑与保存。
- 文本排序、去重与 OLED 参数归一化单元测试。

### 后续阶段

- Global Copy 滚动采集。
- Global Copy 历史、日志、导出和上传。
- 永驻通知及通知栏动作。
- OLED 配置的应用图标、配置列表和独立管理页。

## 页面与导航

```text
TemplateApp
└─ root NavHost
   ├─ root
   │  └─ Home
   │     ├─ Global Copy entry
   │     └─ OLED shield entry
   ├─ global-copy
   └─ oled-shield
```

底栏保留 `Home` 和 `Setting`。工具详情页显示无障碍服务状态和各自的系统 Tile 添加入口。OLED 详情页提供默认参数滑块。

## 跨应用交互

```text
目标应用前台
      │
      ▼
Quick Settings Tile
      │  GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE
      ▼
待执行命令状态机
      │  解析 TYPE_APPLICATION 目标窗口
      ▼
Global Copy 会话 / OLED 遮罩
```

待执行命令最多等待两秒。服务只选择外部应用窗口，System UI 与应用自身窗口不参与目标选择。目标窗口切换后，活动会话和遮罩关闭。
命令保存触发时的目标包名，系统面板收起后只接受同一包名；超时后取消命令。

## 架构

```text
TemplateApp / Tool routes
        │ StateFlow collection
        ▼
ToolViewModel
        │
ToolServiceStateStore
        ▲
        │ service events
ToolAccessibilityService
├─ ForegroundWindowResolver
├─ WindowSnapshotBuilder
├─ CopySessionController
├─ OledShieldController
└─ ComposeOverlayWindow
        │
        ├─ Copy session overlay
        └─ OLED shield and control panel

Global Copy Tile ──┐
OLED shield Tile ─┴─> ToolServiceController
```

| 层级 | 职责 |
| --- | --- |
| `ToolAccessibilityService` | 系统连接、目标窗口、待执行 Tile 命令和覆盖层生命周期。 |
| 领域对象 | 文本块、窗口快照、复制顺序和 OLED 参数归一化。 |
| `ToolServiceStateStore` | 页面和 Tile 所需的持续状态。 |
| `ToolViewModel` | 无障碍设置跳转、默认 OLED 参数与页面状态。 |
| `ComposeOverlayWindow` | 覆盖层窗口、`LifecycleOwner`、`SavedStateRegistryOwner` 和 composition 生命周期。 |
| Compose 页面 | 导航、渲染、Tile 添加请求和本地输入状态。 |

## 领域契约

### Global Copy

- 无障碍服务连接后可以进入复制会话。
- 文本块来自可见、非密码、具有非空屏幕边界的节点。
- 文本候选使用稳定优先级，快照按屏幕几何排序。
- 捕获顺序按快照顺序生成；阅读顺序按视觉行和从左至右顺序生成。
- 复制去重使用 NFKC 与空白归一化键。
- 关闭会话后释放覆盖层和会话数据。

### OLED shield

- 遮罩从显示区域左上角开始，默认值为 `100%` 不透明、`100%` 宽、`15%` 高。
- 遮罩拦截触摸，长按显示控制面板。
- 控制面板调整透明度、宽度、高度和位置。
- `Save` 按目标应用包名保存当前参数。
- 同一应用下次启用时加载已保存参数。
- 服务断开或目标应用切换时关闭遮罩与控制面板。
- Tile 准备状态支持再次点击取消，服务解绑通过幂等清理释放全部窗口和状态。
- 遮罩保持非焦点窗口，屏幕配置变化时按百分比重新计算遮罩和面板布局。

## 验收

- 无障碍服务启用后，两个工具页面显示 `Connected`。
- 两个 Tile 可以通过系统添加请求加入 Quick Settings。
- `Global Copy` Tile 进入当前目标应用文本的复制会话。
- 捕获顺序和阅读顺序通过领域单元测试。
- 编辑后的文本写入系统剪贴板。
- `OLED shield` Tile 在当前目标应用显示遮罩，长按打开控制面板。
- 保存 OLED 参数后，同一应用再次显示时恢复参数。
- 服务断开和目标应用切换会关闭所有覆盖层。
- `:app:testDebugUnitTest`、`:app:assembleDebug` 和 `git diff --check` 成功。

## 本轮复盘

- 外部应用动作具有前台上下文依赖，独立 Quick Settings Tile 提供稳定入口。
- 页面中的授权、状态和配置保持可见且可复用，覆盖层动作保持在服务层。
- OLED 面板使用相对位置保存，适配不同显示尺寸与方向。
- `ComposeView` 作为系统窗口内容时需要完整的生命周期树所有者。
- 无障碍覆盖层可能产生本应用包名的窗口事件；仅本应用的 `TYPE_APPLICATION` 窗口处于前台时关闭外部工具。
