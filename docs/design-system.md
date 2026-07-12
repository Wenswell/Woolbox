# 设计系统

## 页面结构

```text
TemplateTheme
└─ NavHost
   ├─ root
   │  └─ TemplateSurface
   │     ├─ Home
   │     │  ├─ Global Copy entry
   │     │  ├─ OLED shield entry
   │     │  └─ Accessibility service state
   │     └─ Setting
   ├─ Global Copy
   └─ OLED shield
```

根页面保留底栏。工具详情页使用 `TemplateRouteScaffold` 的顶部栏、返回动作和滚动内容。路由切换使用 `220ms` 水平动画。

## 组件职责

| 组件 | 职责 |
| --- | --- |
| `TemplateTheme` | Material 3 主题、动态色与固定基础色。 |
| `TemplateSurface` | 根页面 `Scaffold`、安全区与底栏。 |
| `TemplatePage` | 根页面标题、状态和可滚动内容。 |
| `TemplateRouteScaffold` | 工具详情页顶部栏、返回动作和内容安全区。 |
| `TemplateSection` | 设置式分组与分组内容容器。 |
| `TemplateListItem` | 图标、标题、副标题、尾部控制和整行动作。 |
| `ToolScreens` | Global Copy、OLED shield 的页面级组合与状态呈现。 |
| `tool/overlay` | 系统覆盖层专用 Compose UI 与会话控制。 |

## 样式规则

- 使用 Material 3 和动态色。
- `Theme` 设置提供 `Follow system`、`Light`、`Dark` 三种模式。
- 根页面背景使用 `surface`，页面水平间距为 `16dp`。
- 页面顶部间距为 `16dp`，底部预留 `96dp`。
- 设置分组使用 `surfaceContainerLow`，单个状态块使用 `primaryContainer`。
- 卡片圆角为 `16dp`；工具控制面板圆角为 `12dp`。
- 列表行使用图标、单行标题、最多两行副标题和稳定的整行点击区域。
- 状态使用简短文本：`Not enabled`、`Waiting for service`、`Connected`、`Preparing`、`Active`、`Ready`。
- 系统 Tile 添加请求由详情页中的 `Add` 控制触发。
- OLED 默认参数使用滑块；遮罩运行时参数由覆盖层控制面板使用滑块。

## 跨应用交互

```text
目标应用前台
      │
      ▼
Quick Settings Tile
      │  收起系统面板
      ▼
AccessibilityService 解析目标窗口
      │
      ├─ Global Copy session overlay
      └─ OLED shield overlay
```

应用页面承载设置和状态。系统 Tile 承载需要目标应用前台上下文的动作。复制会话、OLED 遮罩和 OLED 控制面板各自使用独立的 `TYPE_ACCESSIBILITY_OVERLAY` 窗口。

## 组件扩展

新增页面级 UI 优先复用 `TemplatePage`、`TemplateRouteScaffold`、`TemplateSection` 和 `TemplateListItem`。跨应用工具优先使用独立的系统入口，并在服务层维护目标窗口和覆盖层生命周期。
