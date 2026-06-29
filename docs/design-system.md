# 设计系统

## 来源

本模板参考 `/Users/wswensw/Documents/repos/playground/app0603` 的 Compose 主界面风格，保留通用 UI 结构，移除旧应用业务功能。

## 页面结构

页面使用三层结构：

```text
TemplateTheme
└─ TemplateSurface
   ├─ BottomNavigationBar
   └─ TemplatePage
      ├─ TemplatePageHeader
      ├─ TemplateCard
      ├─ TemplateSection
      └─ TemplateOutlineCard
```

## 样式规则

- 使用 Material 3 和动态色。
- 页面背景使用 `surface`。
- 页面水平间距统一为 `16dp`。
- 页面顶部间距统一为 `16dp`。
- 页面底部预留 `96dp`，避免内容贴近底栏。
- 卡片圆角统一为 `16dp`。
- 设置分组使用 `surfaceContainerLow`，强调卡片使用 `primaryContainer`。
- 列表行使用图标、单行标题、最多两行副标题和可选尾部控件。
- 设置行和列表行保持整行点击。
- 图标使用本地 Material vector，避免引入大型图标依赖。

## 当前组件边界

初版只沉淀首页和设置页需要的基础组件。后续新增以下组件时，应先放入组件层：

- 子页面顶部栏和返回结构
- 页面级主按钮
- 空状态
- notice/alert 信息块
- 确认弹窗
- 单选弹窗
- 操作菜单

## Tab 规则

当前底栏只有两个入口：

| Tab | 规则 |
| --- | --- |
| `Home` | 默认入口，用于展示模板和组件样式 |
| `Setting` | 设置入口，用于展示分组设置列表 |

新增 Tab 前先确认它是长期模板入口。临时示例优先放在 `Home` 内容区。
