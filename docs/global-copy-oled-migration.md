# Global Copy 与 OLED 遮罩迁移计划

## 状态

进行中。本文定义首轮功能迁移的范围、边界和验收标准。

## 目标

当前应用从纯 UI 模板演进为系列工具应用的基础载体。本轮迁入旧应用 `app0603` 的两项系统能力：

- `Global Copy`：读取当前前台应用的可访问文本，支持选择、按捕获顺序复制、按阅读顺序复制和编辑后复制。
- `OLED shield`：在当前前台应用上显示黑色触摸遮罩，长按打开控制面板，按应用保存遮罩参数。

功能页面用于承载和验证新的 Compose 布局。旧应用页面只提供操作场景参考。

## 决策依据

| 原则 | 本轮决策 |
| --- | --- |
| 第一性原理 | 两项功能都需要读取前台窗口并创建无障碍覆盖层，因此共享一个 `AccessibilityService`。 |
| 贝叶斯更新 | 旧应用已经验证文本提取、前台窗口选择和遮罩配置的业务规则；保留这些已验证规则，重建状态边界和 UI。 |
| 奥卡姆剃刀 | 仅保留实现首轮体验所需的服务、领域模型、配置存储、覆盖层和 Tile。 |
| 无历史包袱 | 不迁移旧页面、旧 `View` UI helper、`RecyclerView`、日志、上传、历史记录和旧持久化格式。 |
| 最佳实践 | Compose 页面通过 `ViewModel + StateFlow` 接收业务状态；系统覆盖层通过 `ComposeView` 承载；系统能力由服务和明确的 Controller 边界管理。 |

## 首轮范围

### 包含

- 一个无障碍服务，负责服务连接状态、前台应用识别、文本窗口快照和覆盖层生命周期。
- Global Copy 基础会话：选择文本、全选、捕获顺序复制、阅读顺序复制、编辑后复制、关闭会话。
- OLED 遮罩：显示、隐藏、长按控制面板、透明度/宽度/高度调整、面板位置调整、按包名保存和应用配置。
- `Home` 下的 `Global Copy` 与 `OLED shield` 子页面。
- 共享的无障碍前置条件组件和持续状态展示。
- 两个专用 Quick Settings Tile，分别用于在目标应用前台切换 Global Copy 和 OLED 遮罩。
- 领域规则和配置归一化的单元测试。

### 后续阶段

- Global Copy 滚动采集。
- Global Copy 历史、日志、导出和上传。
- 永驻通知及通知栏动作。
- OLED 配置的应用图标展示、配置删除和独立配置管理页。
- 其他旧应用业务能力。

## 页面与导航

底栏继续只保留 `Home` 和 `Setting`。两个功能作为 `Home` 的业务入口和子页面存在。

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

`Global Copy` 页面承担服务状态、授权入口、添加 Tile 入口和功能说明。`OLED shield` 页面承担服务状态、添加 Tile 入口、默认参数和按应用配置管理。页面不直接启动依赖外部前台上下文的覆盖层，也不直接访问无障碍服务实例或 SharedPreferences。

跨应用动作从应用页面触发时，前台窗口已经变为本应用。系统无法可靠恢复目标应用的原页面，因此两个功能均使用专用 Quick Settings Tile 触发。用户展开快捷设置时，目标应用仍保留为无障碍服务识别的最近外部应用；服务关闭快捷设置面板后执行对应动作。

## 架构

```text
TemplateApp / Route
        │ events, state collection
        ▼
ToolViewModel
        │ StateFlow
        ▼
ToolServiceStateStore
        ▲
        │ service events
GlobalCopyAccessibilityService
├─ ForegroundWindowResolver
├─ TextSnapshotBuilder
├─ CopySessionController
├─ OledShieldController
└─ OverlayHost (ComposeView + TYPE_ACCESSIBILITY_OVERLAY)
        │
        ├─ Copy session overlay
        └─ OLED shield and control panel

Global Copy Tile ──> CopySessionController
OLED shield Tile ──> OledShieldController
```

### 状态职责

| 层级 | 职责 |
| --- | --- |
| `AccessibilityService` | 系统连接、前台窗口、会话生命周期、覆盖层创建和销毁。 |
| 领域对象 | 文本块、窗口快照、复制顺序、OLED 配置及归一化规则。 |
| Controller | 只接收明确命令，调用已连接的服务实例。 |
| `ToolServiceStateStore` | 服务权威状态，向应用与 Tile 发布持续状态。 |
| `ToolViewModel` | 将服务状态投影为页面状态，执行系统设置跳转。 |
| Compose Route / Screen | 导航、渲染、用户事件和局部临时 UI 状态。 |

## 功能契约

### Global Copy

- 仅在无障碍服务连接后允许进入复制会话。
- 优先选择活动、聚焦且层级最高的应用窗口；同包名窗口共同构成快照。
- 文本提取忽略密码节点、不可见节点和空边界节点。
- 每个文本块保留文字、来源、边界、窗口和排序信息。
- 捕获顺序按快照中的稳定顺序复制，并移除重复文本。
- 阅读顺序按视觉行和从左至右顺序复制，并移除重复文本。
- 覆盖层的选择、编辑和复制操作只作用于当前会话快照。
- 关闭会话后释放覆盖层与会话内存。

### OLED shield

- 仅在无障碍服务连接且存在前台目标应用时允许显示遮罩。
- 遮罩从完整显示区域左上角开始，默认值为不透明、全宽、15% 高度。
- 遮罩拦截触摸；长按遮罩显示控制面板。
- 控制面板调整透明度、宽度、高度和面板位置；保存后按目标应用包名持久化。
- 下一次对同一应用显示遮罩时加载已保存配置。
- 服务断开时关闭遮罩和控制面板。

### Quick Settings Tiles

- `Global Copy` Tile 在服务连接时切换复制会话。
- `OLED shield` Tile 在服务连接时切换当前目标应用的遮罩。
- 服务未连接时，任一 Tile 打开应用的 `Home` 页面，用户在页面中完成授权。
- 两个 Tile 分别反映服务连接和对应功能的活动状态。
- 应用页面提供系统添加 Tile 请求入口；已添加时系统保持现有 Tile，不创建重复项。

## 实施步骤

1. 建立领域模型、服务状态、配置存储和无障碍服务声明。
2. 迁入并精简前台窗口选择、文本快照和文本排序规则。
3. 实现 Compose 覆盖层宿主、复制会话覆盖层和 OLED 遮罩控制面板。
4. 建立 Controller、ViewModel、系统设置跳转和两个专用 Quick Settings Tile。
5. 重建 `Home`、Global Copy 页面、OLED 页面和共享前置条件组件。
6. 删除模板示例页面和示例设置项，更新 README、设计系统和开发记录。
7. 编写领域单元测试，执行构建、空白检查和设备验收。

## 验收

- 无障碍服务启用后，Global Copy 和 OLED 页面显示已连接状态。
- 从 `Global Copy` Tile 进入复制会话时，当前目标应用的文本块可选择和复制。
- 捕获顺序与阅读顺序复制符合领域单元测试。
- 编辑后的文本可写入系统剪贴板。
- OLED 遮罩显示在当前目标应用上，长按可显示控制面板。
- 从 `OLED shield` Tile 可直接显示或关闭当前目标应用的遮罩。
- OLED 参数和面板位置保存后，对同一目标应用再次显示时恢复。
- 服务断开后，复制会话、遮罩和控制面板均关闭。
- 日间、夜间和紧凑宽度下，应用页无重叠，覆盖层操作可访问。
- 执行 `:app:assembleDebug`、单元测试和 `git diff --check` 成功。

## 当前确认

- 首轮包含基础复制、OLED 全流程和两个专用 Quick Settings Tile。
- `Scroll capture`、历史、日志、上传和永久通知进入后续阶段。
- 旧页面 UI 不迁移；当前模板的 Compose 组件和页面规范作为 UI 实现依据。
- 跨应用动作不从应用页面直接启动；页面负责授权、添加 Tile、状态与配置，Tile 负责目标应用前台动作。
