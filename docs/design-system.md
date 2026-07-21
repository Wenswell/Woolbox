# 设计系统

## 设计目标

Template 是高频工具应用，界面应安静、直接、便于扫描。应用页面负责启动、授权、历史和配置；跨应用会话使用系统覆盖层，OLED 无服务时在 Activity 顶层复用同一遮罩交互，Global Copy 历史在应用内复用同一编辑面板。

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
   │     ├─ Records
   │     │  ├─ Today list
   │     │  └─ Record sheet
   │     └─ Setting
   │        ├─ Theme entry
   │        └─ Records connection ──> Credentials form
   ├─ Manage records ──> New / Edit record item
   ├─ Global Copy
   │  ├─ primary action
   │  ├─ accessibility state
   │  ├─ Tile request
   │  └─ History entry ──> Copy history
   └─ OLED shield
      ├─ primary action
      ├─ accessibility state
      ├─ Tile request
      ├─ default config
      └─ saved apps
```

根页面固定显示 `Home`、`Records`、`Setting` 底栏。工具详情页和记录管理页不显示底栏，使用顶部返回栏和独立滚动内容。

## 页面骨架

| 骨架 | 适用场景 | 结构契约 |
| --- | --- | --- |
| `TemplateSurface` | 根页面 | `Scaffold` 处理 safe drawing、底栏及可选 FAB 和 Snackbar。 |
| `TemplatePage` | Home、Setting | 标题、说明、纵向滚动内容和底部留白。 |
| `TemplateRouteScaffold` | 工具详情、记录管理与表单 | 顶部栏、返回动作、可选标题动作和滚动内容；滚动超过 `8px` 显示分隔线。 |
| `TemplateLazyRouteScaffold` | 无上限记录列表 | 与详情页共用顶部栏，正文使用 `LazyColumn`。 |
| `ComposeOverlayWindow` | 跨应用工具 | 独立 Compose composition 与生命周期，不嵌入应用页面。 |

路由和底栏切换均使用 `220ms` 水平滑动。顺序向后时内容向左，返回或顺序向前时内容向右。

## 稳定组件

| 组件 | 职责 | 使用规则 |
| --- | --- | --- |
| `TemplatePageHeader` | 根页面标题与说明 | 标题最多两行；右侧只放页面级动作。 |
| `TemplateSection` | 设置分组 | 显示分组标题与一个 `surfaceContainerLow` 内容容器。 |
| `TemplateListItem` | 导航、状态或设置行 | 资源图标或自定义前导内容、单行标题、最多两行说明、可选尾部控件；有动作时整行可点击。Records 管理和选择列表使用项目图标，根页面目标与流水使用项目名称首个 Unicode 字符，流水时间置于尾部。 |
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
| Global Copy 面板外边距 | 水平 `12dp`、垂直 `32dp` |
| Global Copy 面板圆角 | `16dp` |

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

主操作按钮使用工具图标和结果导向文案：`Start Global Copy`、`Show shield`。`Start Global Copy` 在 `isServiceConnected == false` 时禁用；`Show shield` 始终可用，无服务时显示应用内遮罩，有服务时启动跨应用遮罩。授权入口保持可用。

Tile 行的 `Add` 使用 tonal button。Android 13 及以上整行与按钮执行同一个系统添加请求；Android 12/12L 保留稳定尺寸并禁用整行与按钮。OLED 默认配置使用带百分比值的 Slider，值变化即时更新页面草稿，只有 `Save default` 写入存储。

Global Copy 的 `History` 位于 Tile 之后，显示采集数量并进入独立历史列表。OLED 的 `Saved apps` 位于默认配置之后，每行显示应用图标、名称、三项百分比、记录时间以及 Enable/Delete 图标操作；参数在窄屏可自然换行，不截断隐藏。

## Records 组合

Records 根页面使用 `LazyColumn`，顶部不显示页面标题和描述，只保留右侧 `Manage` 操作；未配置凭据或远端读取失败时显示错误与 `Configure connection`/`Retry` 主操作，没有活跃记录项时显示 `Create record item`，否则先显示独立的 `Goals` 区域，再显示 `Today` 分组。目标行显示项目名称、累计值和下方线性进度条；日目标累计当天值，周目标累计周一至周日值，进度最多显示至 `100%`，变化使用 Compose 动画。根页面目标与今天流水的左侧前导显示项目名称首个 Unicode 字符，支持文字、单码点 emoji 和组合字符；今天流水右侧显示时间，管理与选择列表继续使用配置的 Material 图标。今天流水切换使用 `180ms` 淡入和 `90ms` 淡出。只有存在活跃记录项时，根 `Scaffold` 才显示带图标和 `Record` 文案的扩展 FAB。

`Record` 打开单个 `ModalBottomSheet`，不叠加第二个 Sheet 或 Dialog。第一阶段使用搜索框和无卡片列表选择记录项，空搜索标题为 `Suggested now` 且最多显示五项；第二阶段显示记录项名称、说明、已配置目标、数值输入、单位、步进提示以及减号/加号图标按钮，底部使用全宽确认按钮。

`Manage records` 使用独立路由，顶部 `New` 动作创建记录项，`Active` 分组中的行进入编辑并显示已配置目标。记录项表单按 Icon、Details、Measurement、Goal、Recommendation 分段；Icon 使用固定 Material 图标选择网格，类型、时长单位和目标周期使用 segmented control，目标开关使用 Switch，时间使用 TimePicker，增删时间段使用明确按钮。已有流水时类型与单位控件禁用。

`Records connection` 使用独立路由，只显示固定服务地址、用户名和密码；保存成功后返回上一页。Android Records 只呈现今天流水和活跃项目标进度，不显示归档项。

PC 管理页使用 Entries/Record items/Logs 三个页签。Entries 使用日期范围和记录项筛选、可选择表格与批量删除，不提供编辑入口；Record items 使用可选择表格、新建/编辑 Dialog 和批量归档/恢复；Logs 使用只读表格显示操作时间、对象、动作和变更摘要，并可加载更早日志。操作成功后刷新当前数据，失败时在当前页面或 Dialog 显示错误。

## 状态呈现

页面只显示 [产品说明](product.md#状态语义) 定义的六个状态词，不在不同页面创建同义文案。

- 服务状态显示在 `Accessibility service` 行。
- 工具状态显示在 Home 工具行和对应 Tile 行。
- `Preparing` 与 `Active` 反映服务状态机，不由页面自行推断。
- 状态变化不得改变列表行、主操作或 Tile 添加按钮的稳定尺寸。

## 覆盖层交互

Global Copy 使用全屏透明窗口承载半透明 scrim 和内缩面板，四周必须能辨认目标应用。会话默认进入编辑页并开启 `Reading order`；顶部使用 `Close` 图标按钮，底部使用带复制图标和 `Copy` 文案的全宽主按钮，空白草稿时禁用。

编辑页使用整行可点击的 `Reading order` Switch、明确的 `Select text` 次级操作和占满剩余空间的文本框。选择页使用 `Back` 图标按钮与 `Clear`/`Select all` 文字动作；文本块行使用 `48dp` 复选框与整行点击，正文以 `captureOrder` 显示从 `0` 开始的序号并允许四行，副文本只显示 `source · left,top-right,bottom`。

`Close` 与 `Back` 图标按钮保持至少 `48dp` 触控区域，并分别提供 `Close`、`Back` 无障碍说明。文本选择即时更新草稿；两种顺序草稿的编辑结果分别保留。

`Copy` 写入剪贴板后显示 `Copied to clipboard.`，随后关闭会话。`Close`、目标变化和共同关闭事件也会结束会话。

历史列表按时间倒序使用无卡片列表行，显示包名、文本块数量、时间和两行预览。点击后打开全屏 Dialog，并复用文本选择、阅读顺序、编辑和 Copy 内容；编辑页顶部增加 `Save`，只有状态改变时可用。删除使用确认 Dialog。

OLED 遮罩本身拦截触摸，长按打开控制面板。控制面板标题区域负责拖动；`Close` 只关闭面板，`Hide shield` 关闭整个工具。无障碍模式的 `Save` 保存当前应用配置，应用内模式的 `Save` 保存默认配置。

覆盖层交互的业务语义归 [产品说明](product.md)，窗口参数和生命周期归 [系统架构](architecture.md)。

## 修改检查

- 页面是否使用正确骨架，并保持 action、状态、设置的固定顺序。
- 新样式是否能复用现有 token 和组件。
- 文本是否满足标题/说明行数限制，按钮最长文案是否完整显示。
- 主操作、Tile 添加、滑块和二元设置是否使用合适控件。
- 页面、深色模式和覆盖层状态是否需要真机检查。
