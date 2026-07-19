# 设计系统

## 设计目标

Template 是高频工具应用，界面应安静、直接、便于扫描。应用页面负责启动、授权、状态和配置；全屏复制会话与 OLED 控制面板属于系统覆盖层，不复用应用页面 Scaffold。

基础技术为 Material 3、动态色和 edge-to-edge。组件优先表达操作层级，不使用装饰性卡片、营销式区块或与功能无关的视觉元素。

## 页面层级

```text
TemplateTheme
└─ NavHost
   ├─ root
   │  └─ TemplateSurface
   │     ├─ Home
   │     │  ├─ Global Copy entry
   │     │  ├─ OLED shield entry
   │     │  └─ Accessibility service entry
   │     └─ Setting
   │        └─ Theme entry
   ├─ Global Copy
   │  ├─ primary action
   │  ├─ accessibility state
   │  └─ Tile request
   └─ OLED shield
      ├─ primary action
      ├─ accessibility state
      ├─ Tile request
      └─ default config
```

根页面保留 `Home`、`Setting` 底栏。工具详情页不显示底栏，使用顶部返回栏和独立滚动内容。

## 页面骨架

| 骨架 | 适用场景 | 结构契约 |
| --- | --- | --- |
| `TemplateSurface` | 根页面 | `Scaffold` 处理 safe drawing 与底栏。 |
| `TemplatePage` | Home、Setting | 标题、说明、纵向滚动内容和底部留白。 |
| `TemplateRouteScaffold` | 工具详情页 | 顶部栏、返回动作、滚动内容；滚动超过 `8px` 显示分隔线。 |
| `ComposeOverlayWindow` | 跨应用工具 | 独立 Compose composition 与生命周期，不嵌入应用页面。 |

路由和底栏切换均使用 `220ms` 水平滑动。顺序向后时内容向左，返回或顺序向前时内容向右。

## 稳定组件

| 组件 | 职责 | 使用规则 |
| --- | --- | --- |
| `TemplatePageHeader` | 根页面标题与说明 | 标题最多两行；右侧只放页面级动作。 |
| `TemplateSection` | 设置分组 | 显示分组标题与一个 `surfaceContainerLow` 内容容器。 |
| `TemplateListItem` | 导航、状态或设置行 | 图标、单行标题、最多两行说明、可选尾部控件；有动作时整行可点击。 |
| `TemplatePrimaryAction` | 工具详情页主命令 | 全宽 filled button、图标和明确动词；位于详情页首项。 |
| `TemplateDivider` | 同组列表项分隔 | 从卡片左侧 `16dp` 开始，不贴容器边缘。 |
| `TemplateSingleChoiceDialog` | 单选设置 | 使用 RadioButton；选择后立即应用并关闭。 |
| `TemplateInfoDialog` | 简短说明 | 只承载当前任务需要的信息和关闭动作。 |

新增应用页面能力先判断能否由这些组件组合完成。只有出现第二个稳定使用场景或现有组件职责明显不足时，才扩展 `TemplateComponents.kt`。

## 尺寸与颜色

| 项目 | 值 |
| --- | --- |
| 页面水平间距 | `16dp` |
| 根页面顶部间距 | `16dp` |
| 页面底部留白 | `96dp` |
| 内容块间距 | `16dp` |
| 列表行最小高度 | `56dp` |
| 设置分组圆角 | `16dp` |
| OLED 控制面板圆角 | `12dp` |
| OLED 控制面板最大宽度 | `360dp` |

- 页面背景使用 `surface`。
- 设置分组使用 `surfaceContainerLow`。
- OLED 控制面板使用 `surfaceContainerHigh` 和 `8dp` 阴影。
- 分组标题使用 `primary`；正文使用 `onSurface`，说明和次要状态使用 `onSurfaceVariant`。
- 应用主题支持 `Follow system`、`Light`、`Dark`；覆盖层关闭动态色，保证跨应用显示稳定。

## 工具详情页组合

详情页按固定优先级排列：

1. 主操作。
2. `Access` 服务状态。
3. `Quick settings` Tile 添加入口。
4. 工具专属配置。

主操作按钮使用工具图标和结果导向文案：`Start Global Copy`、`Show shield`。`isServiceConnected == false` 时禁用，不提供替代执行路径；授权入口保持可用。

Tile 行的 `Add` 使用 tonal button，整行与按钮执行同一个系统添加请求。OLED 默认配置使用带百分比值的 Slider，值变化即时更新页面草稿，只有 `Save default` 写入存储。

## 状态呈现

页面只显示 [产品说明](product.md#状态语义) 定义的六个状态词，不在不同页面创建同义文案。

- 服务状态显示在 `Accessibility service` 行。
- 工具状态显示在 Home 工具行和对应 Tile 行。
- `Preparing` 与 `Active` 反映服务状态机，不由页面自行推断。
- 状态变化不得改变列表行、主操作或 Tile 添加按钮的稳定尺寸。

## 覆盖层交互

Global Copy 使用全屏 Scaffold：顶部提供 `Close`/`Back` 与全选切换，底部提供两个复制主动作和编辑入口。文本块使用复选框与整行点击，编辑模式使用全屏文本框。

OLED 遮罩本身拦截触摸，长按打开控制面板。控制面板标题区域负责拖动；`Close` 只关闭面板，`Hide shield` 关闭整个工具，`Save` 保存当前应用配置。

覆盖层交互的业务语义归 [产品说明](product.md)，窗口参数和生命周期归 [系统架构](architecture.md)。

## 修改检查

- 页面是否使用正确骨架，并保持 action、状态、设置的固定顺序。
- 新样式是否能复用现有 token 和组件。
- 文本是否满足标题/说明行数限制，按钮最长文案是否完整显示。
- 主操作、Tile 添加、滑块和二元设置是否使用合适控件。
- 页面、深色模式和覆盖层状态是否需要真机检查。
