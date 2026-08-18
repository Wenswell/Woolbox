# 设计系统

## 设计目标

Woolbox 是高频工具应用，界面应安静、直接、便于扫描。应用页面负责启动、授权、历史和配置；跨应用会话使用系统覆盖层，OLED 无服务时在 Activity 顶层复用同一遮罩交互，Global Copy 历史在应用内复用同一编辑面板。

基础技术为 Material 3、动态色和 edge-to-edge。组件优先表达操作层级，不使用装饰性卡片、营销式区块或与功能无关的视觉元素。

## 页面层级

```text
AppTheme
└─ NavHost
   ├─ root
   │  └─ AppSurface
   │     ├─ Records
   │     │  ├─ Today ──> Plan / Edit today
   │     │  ├─ Tasks ──> Manage tasks ──> Edit task
   │     │  ├─ Routines ──> Record Sheet
   │     │  └─ Measurement ──> Measurement preview Sheet
   │     ├─ Tools
   │     │  ├─ Access / Accessibility service entry
   │     │  └─ Available
   │     │     ├─ Global Copy entry
   │     │     └─ OLED shield entry
   │     └─ Setting
   │        ├─ Language entry
   │        ├─ Theme entry
   │        └─ Records connection ──> Credentials form
   ├─ Manage tasks ──> New / Edit task
   ├─ Manage accumulates ──> New / Edit accumulate
   └─ Manage measurements ──> New / Edit measurement
   ├─ Global Copy
   │  ├─ primary action
   │  ├─ accessibility state
   │  ├─ Tile request
   │  └─ History entry ──> Copy history ──> History editor
   └─ OLED shield
      ├─ primary action
      ├─ accessibility state
      ├─ Tile request
      ├─ default config
      └─ saved apps
```

根页面固定显示 `Records`、`Tools`、`Setting` 底栏；图标分别为列表、扳手（`ic_tools_24`）、设置。工具详情页和记录管理页不显示底栏，使用顶部返回栏和独立滚动内容。

## 页面骨架

| 骨架 | 适用场景 | 结构契约 |
| --- | --- | --- |
| `AppSurface` | 根页面 | `Scaffold` 处理 safe drawing 及底栏。 |
| `AppPage` | Tools、Setting | 标题、说明、纵向滚动内容和底部留白。 |
| `AppRouteScaffold` | 工具详情、记录管理与表单 | 顶部栏、返回、可选右侧动作（右内边距与页面 `16dp` 对齐）和滚动内容；滚动超过 `8px` 显示分隔线；输入法展开时用 IME Insets 约束滚动视口，聚焦输入框自动滚动到输入法上方，页面末尾仍可手动滚动访问。 |
| `AppLazyRouteScaffold` | 无上限记录列表 | 与详情页共用顶部栏，正文使用 `LazyColumn`。 |
| `ComposeOverlayWindow` | 跨应用工具 | 独立 Compose composition 与生命周期，不嵌入应用页面。 |

路由和底栏切换均使用 `220ms` 水平滑动。顺序向后时内容向左，返回或顺序向前时内容向右。

## 稳定组件

| 组件 | 职责 | 使用规则 |
| --- | --- | --- |
| `AppPageHeader` | 根页面标题与说明 | 标题 `headlineMedium` SemiBold，说明 `bodyMedium`；标题最多两行；右侧只放页面级动作。 |
| `AppSection` | 设置/表单分组 | 仅分组标题 + 内容，无外框卡片。 |
| `AppListItem` | 导航、状态或设置行 | 标题 `titleMedium` SemiBold，说明 `bodySmall`（`subtitleMaxLines` 默认 2）；图标 24dp，优先语义化 Material icon（`AppListLeadingIcon`，primary 色），可用 `iconRes` 或自定义 `leadingContent`（应用图标 40dp）；有动作时整行可点击并声明 `Role.Button`；`navigate=true` 时 trailing chevron 表示进入下级；禁用时标题/图标 0.38 透明，副文案保持可读。 |
| `AppPrimaryAction` | 突出页面主命令 | 全宽 filled button、图标和明确动词；只在需要突出主命令的页面使用。 |
| `AppDivider` | 同组列表项分隔 | 默认从左侧 `56dp` 起划（24dp icon）；应用图标行用 `72dp`。 |
| `AppSingleChoiceDialog` | 单选设置 | 使用 RadioButton；选择后立即应用并关闭。 |
| `AppInfoDialog` | 简短说明 | 只承载当前任务需要的信息和关闭动作。 |

新增应用页面能力先判断能否由这些组件组合完成。只有出现第二个稳定使用场景或现有组件职责明显不足时，才扩展 `AppComponents.kt`。

## 尺寸与颜色

| 项目 | 值 |
| --- | --- |
| 页面水平间距 | `16dp` |
| 根页面顶部间距 | `16dp` |
| 页面底部留白 | `96dp` |
| 内容块间距 | `16dp` |
| 列表行最小高度 | `56dp` |
| 列表分隔线 inset | 从左侧 `56dp`（对齐 leading 后文字列） |
| 内容卡片圆角（`cardShape`） | `16dp` |
| Records 根页卡片圆角 | `8dp` |
| OLED 控制面板圆角 | `12dp` |
| OLED 控制面板最大宽度 | `360dp` |
| Global Copy 面板外边距 | 水平 `12dp`、垂直 `32dp` |
| Global Copy 面板圆角 | `16dp` |

- 页面背景使用 `surface`。
- 设置/表单分组仅用标题分区，无外框；圆角仅用于内容卡（Records 项目卡、空态/流水中性块）与菜单。
- OLED 控制面板使用 `surfaceContainerHigh` 和 `8dp` 阴影。
- 分组标题使用 `primary`；正文使用 `onSurface`，说明和次要状态使用 `onSurfaceVariant`。
- 应用主题支持 `Follow system`、`Light`、`Dark`；覆盖层关闭动态色，保证跨应用显示稳定。

## 工具详情页组合

详情页按固定优先级排列：

1. `Actions` 工具操作条目。
2. `Quick settings` Tile 添加入口。
3. 工具专属配置。

工具操作使用带图标、状态说明和整行点击的 `AppListItem`：`Start Global Copy` 在 `isServiceConnected == false` 时禁用；`Show shield` 始终可用，无服务时显示应用内遮罩，有服务时启动跨应用遮罩。授权入口只在 Tools 的 `Access` 区域显示。

Quick Settings 行在 Android 13 及以上整行执行系统添加请求，不再显示右侧 `Add` 按钮；Android 12/12L 整行禁用，由系统快捷设置编辑页负责添加。OLED 默认配置使用带百分比值的 Slider，值变化即时更新页面草稿，只有 `Save default` 写入存储。

Setting 的 `Appearance` 区域提供 Language、Theme 和 Motion。Language 使用单选 Dialog，选项为 `Follow system`、`English`、`简体中文`；语言名称使用自身语言显示。Motion 可选 `Follow system`、`On`、`Off`，统一控制 Records 的渐变、弹出和液面动画。`Updates` 区域使用标准 `AppListItem`：`Current version` 的副文案显示构建类型和版本号，并触发对应通道的检查；发现更新后显示一个包含构建类型和版本号的动作行，下载与安装阶段在副文案呈现状态。下载动作先用标准确认 Dialog 显示版本与文件大小；完成下载后同一动作行进入安装。等待来源安装授权时动作行进入系统设置。检查、下载和安装期间禁用重复操作。

用户可见文案从 Android 资源读取，默认英文位于 `values/strings.xml`，简体中文位于 `values-zh-rCN/strings.xml`。数量使用 `plurals`，日期、时间和数值使用当前 Locale；用户输入与服务端返回正文不翻译。后台更新状态使用 `UiText.Resource` 保存资源与参数，切换语言后按新配置重新解析。

Global Copy 的 `History` 位于 Tile 之后，显示采集数量并进入独立历史列表。OLED 的 `Saved apps` 位于默认配置之后，复用 `AppListItem`：40dp 应用图标、名称、状态/参数/时间副文案、Enable/Delete 图标操作；无服务时副文案提示 `Accessibility required`，应用不可用时提示 `Unavailable`。

## Records 组合

Records 根页无大标题，同一 `LazyColumn` 固定为 **Today → Tasks → Routines → Measurement**。Today 是唯一执行区；其余 section 使用中性 `titleSmall` 标题、数量副文案和 chevron 进入管理页，根页不提供新建。

| 区域 | 唯一职责 |
| --- | --- |
| `Today` | 单张 `8dp` 卡按计划状态切换内容。未规划显示主按钮；`READY` 只突出第一步；`ACTIVE` 显示当前任务与首个未完成子事项；到期时显示继续、切换、结束；`ENOUGH` 显示完成轮次与可选额外一轮；`ENDED` 为只读状态。 |
| `Plan/Edit today` | 全屏路由；第一步文本、主任务必选、次任务可空。任务使用选择 Dialog；已完成槽位锁定，次任务可明确清空。顶栏 Check 是唯一保存操作。 |
| `Tasks` | 根页只显示 Open 数量并进入 Manage，不展示任务预览或完成操作。 |
| `Manage tasks` / `Edit task` | 管理页仅按 Open、Completed 分组；顶栏加号新建。新建与编辑使用 Once/Daily 分段控件，并负责标题、子事项增删排序和删除，不显示日期。 |
| `Manage accumulates` | 只显示 `ACCUMULATE`；顶栏加号 → `New accumulate`，色卡 → `Edit accumulate`。 |
| `Manage measurements` | 只显示 `MEASUREMENT`；顶栏加号 → `New measurement`，色卡副文案为范围与步进（无类型前缀、无 default）；色卡 → `Edit measurement`。 |
| `Routines` | 逐行显示名称与今日累计，点行打开累计记录 Sheet；本区不显示任务或回顾状态。 |
| `New/Edit accumulate` | 固定 `ACCUMULATE` 表单；Check 保存。 |
| `Measurement` | 逐行显示名称与最新值，点行打开预览 Sheet。 |
| `New/Edit measurement` | 固定 `MEASUREMENT` 表单；Check 保存。 |
| Measurement preview Sheet | 标题、最新值、24 小时图、流水和离散 Slider。 |
| 累计记录 Sheet | 身份与进度同一标题行；下方数值输入与今日流水。 |

未配置凭据、首次远端失败或列表为空时用描边空状态卡 + 全宽主按钮；首次加载中居中进度指示。已有 Records 内容刷新时保留列表；刷新失败在内容前显示带重试的状态卡，不以 loading 或错误替换页面。

### Records 视觉规则

- Today 卡标题使用 `labelLarge` primary；当前动作使用 `titleLarge` SemiBold；说明与轮次使用 `bodySmall/bodyMedium` 的 `onSurfaceVariant`。卡与记录行统一 `8dp` 圆角。
- 一个状态只显示当前可执行命令。启动、继续、切换和额外一轮使用明确按钮；编辑用铅笔 icon；当前子事项用 checkbox；结束今天使用弱化文本按钮。
- 回顾到期由分钟时钟触发重组，不显示倒计时或精确工时。通知不可用时用阻塞状态替换活动内容，只保留启用通知和结束今天。
- Routines 与 Measurement 行只突出当前数值，不显示 Today 颜色、轮次或承诺标签。累计写入和刚达标的触觉、Snackbar 与 `Undo` 沿用记录 Sheet 规则。

`Manage accumulates` 与 `Manage measurements` 使用相同的标准路由骨架；每页顶栏仅提供本模式新建入口，编辑路由固定为本模式表单。

Task progress Sheet 只显示子事项复选框和完成时间；标题、子事项增删排序和删除任务只在 `Edit task`。任务、累计与测量表单均不出现计划日期或时间段控件。Measurement 录入使用 Material `Slider`，以配置步进离散取值。

`Records connection` 使用 `AppSection`：Server 明文地址、Credentials 凭据输入 + 全宽连接按钮；用户名/密码在连接中禁用；用户名键盘动作切换到密码，密码使用 Password 键盘并通过 `Done` 提交（仅在合法时）；保存成功后返回。

PC 管理页使用响应式 App shell：桌面为固定窄侧栏，窄屏为可横向滚动的顶部导航；入口固定为 Overview、Today、Tasks、Accumulate、Measurement、Entries、Activity。Overview 使用紧凑指标卡和任务、流水、审计预览。Today 按本地日期呈现与 Android 相同的 `READY/ACTIVE/ENOUGH/ENDED` 状态和当前 revision，只显示当前可执行操作；步骤使用 checkbox，结束今天使用确认 Dialog。Tasks 使用 Open/Completed 分段、搜索与表格，表单提供 Once/Daily 并按固定位序管理子事项。Accumulate 与 Measurement 使用固定模式表单。Entries 提供日期、模式与记录项筛选，可新增流水和批量删除。Activity 只读展示对象、动作和变更摘要。


## 状态呈现

页面只显示 [产品说明](product.md#状态语义) 定义的六个状态词，不在不同页面创建同义文案。

- 服务授权状态只显示在 Tools 的 `Access` 区域；详情页不重复显示权限行。
- 工具状态显示在 Tools 工具行和对应 Tile 行。
- `Preparing` 与 `Active` 反映服务状态机，不由页面自行推断。
- 状态变化不得改变列表行、主操作或 Tile 行的稳定尺寸。

## 覆盖层交互

Global Copy 使用全屏透明窗口承载半透明 scrim 和内缩面板，四周必须能辨认目标应用。面板消费 `safeDrawing`/`ime` insets；编辑区用固定最小高度文本框 + 可滚动中部 + 底栏，避免输入框随键盘逐帧缩放。会话默认进入编辑页并开启 `Reading order`；顶部使用 `Close` 图标按钮，底部使用带复制图标和 `Copy` 文案的全宽主按钮，空白草稿时禁用。

编辑页使用整行可点击的 `Reading order` Switch、明确的 `Select text` 次级操作和占满剩余空间的文本框。选择页使用 `Back` 图标按钮与 `Select all`/`Clear` 纯图标动作；历史编辑顶栏 `Delete`/`Save` 同为纯图标。文本块行使用 `48dp` 复选框与整行点击，正文以 `captureOrder` 显示从 `0` 开始的序号并允许四行，副文本只显示 `source · left,top-right,bottom`。

紧凑动作（顶栏、面板关闭/隐藏/保存、列表 trailing）一律纯图标 + `contentDescription`，触控区域至少 `48dp`。全宽主操作与 Dialog 确认/取消保留文案。文本选择即时更新草稿；两种顺序草稿的编辑结果分别保留。

`Copy` 写入剪贴板后显示 `Copied to clipboard.`，随后关闭会话。`Close`、目标变化和共同关闭事件也会结束会话。

历史列表按时间倒序复用 `AppListItem`：目标应用图标、名称、块数/时间与预览副文案，不显示包名。整行进入编辑路由（`global-copy/history/{id}`），与 Activity `adjustResize` 及面板 `safeDrawing`/`imePadding` 共用 IME 适配，不再使用全屏 Dialog。删除入口在编辑顶栏删除图标，确认 Dialog 后返回列表。编辑页保存图标仅在状态改变时可用；复用 `CopySessionPanel` 文本选择、阅读顺序、编辑和 Copy。

OLED 遮罩本身拦截触摸，长按打开控制面板。控制面板标题区域负责拖动；关闭图标只关面板，隐藏图标关闭整个工具，保存图标写入配置（无障碍模式按应用，应用内模式为默认）。详情页默认配置保存同为纯图标。

覆盖层交互的业务语义归 [产品说明](product.md)，窗口参数和生命周期归 [系统架构](architecture.md)。

## 修改检查

- 页面是否使用正确骨架，并保持 action、状态、设置的固定顺序。
- 新样式是否能复用现有 token 和组件。
- 文本是否满足标题/说明行数限制，按钮最长文案是否完整显示。
- 主操作、Tile 添加、滑块和二元设置是否使用合适控件。
- 页面、深色模式和覆盖层状态是否需要真机检查。
