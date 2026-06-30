# 设计系统

## 来源

本模板参考 `/Users/wswensw/Documents/repos/playground/app0603` 的 Compose 主界面风格，保留通用 UI 结构，移除旧应用业务功能。

## 页面结构

页面使用三层结构：

```text
TemplateTheme
└─ NavHost
   ├─ root
   │  └─ TemplateSurface
   │     ├─ BottomNavigationBar
   │     └─ TemplatePage
   │        ├─ TemplatePageHeader
   │        ├─ TemplateCard
   │        ├─ TemplateSection
   │        └─ TemplateOutlineCard
   └─ child routes
      └─ TemplateRouteScaffold
         ├─ TopAppBar
         ├─ Back action
         ├─ Optional primary action
         └─ TemplateSection / TemplateCard / TemplateOutlineCard
```

## 样式规则

- 使用 Material 3 和动态色。
- `Theme` 设置提供 `Follow system`、`Light`、`Dark` 三种模式。
- 状态栏和导航栏图标样式跟随应用内主题模式。
- 页面安全区由页面外壳统一处理。
- 右下角主按钮跟随输入法和导航栏保持可点击位置。
- 页面背景使用 `surface`。
- 页面水平间距统一为 `16dp`。
- 页面顶部间距统一为 `16dp`。
- 页面底部预留 `96dp`，避免内容贴近底栏。
- 卡片圆角统一为 `16dp`。
- 设置分组使用 `surfaceContainerLow`，强调卡片使用 `primaryContainer`。
- 列表行使用图标、单行标题、最多两行副标题和可选尾部控件。
- 有动作的设置行和列表行保持整行点击。
- 列表行最小高度为 `56dp`。
- 开关设置行由整行处理状态切换，尾部 `Switch` 只展示当前状态。
- 图标使用本地 Material vector，避免引入大型图标依赖。

## 当前组件边界

当前沉淀首页、设置页和示例子页面需要的基础组件。后续新增以下组件时，应先放入组件层：

- 空状态
- notice/alert 信息块
- 确认弹窗
- 操作菜单

## 设置弹窗规则

| 设置项 | 交互 | 组件 |
| --- | --- | --- |
| `Theme` | 整行点击打开单选弹窗 | `TemplateSingleChoiceDialog` |
| `Template options` | 整行点击打开输入框弹窗 | `TemplateTextInputDialog` |
| `Initial version` | 整行点击打开信息弹窗 | `TemplateInfoDialog` |

单选弹窗使用 `BasicAlertDialog` + `Surface`，圆角 `16dp`，最大宽度 `560dp`。选项行最小高度 `56dp`，水平边距 `24dp`，点击选项后立即保存并关闭。

## Tab 规则

当前底栏只有两个入口：

| Tab | 规则 |
| --- | --- |
| `Home` | 默认入口，用于展示模板和组件样式 |
| `Setting` | 设置入口，用于展示分组设置列表 |

新增 Tab 前先确认它是长期模板入口。临时示例优先放在 `Home` 内容区。

## 子页面规则

- 子页面使用 `TemplateRouteScaffold`。
- 根页面保留底栏，子页面使用顶部栏和返回按钮。
- 子页面之间使用横向滑动动画，进入向左，返回向右，时长 `220ms`。
- 右下角主按钮使用 `TemplatePageAction` 传入子页面。
- 主按钮右锚定，页面接近顶部时显示图标和文字，离开顶部后显示纯图标。
- 主按钮示例页使用蓝底对钩和红底错误两种状态交替。
- 示例子页面当前包括 `Components`、`Primary Action`、`Template Rules`、`Extension` 和 `Settings Pattern`。
