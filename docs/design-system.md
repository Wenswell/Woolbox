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
   │     │  ├─ page header (title + date)
   │     │  ├─ Tasks ──> Task progress Sheet ──> Manage tasks ──> Edit task
   │     │  ├─ Accumulate ──> Record Sheet
   │     │  └─ Measurement ──> Measurement preview Sheet
   │     ├─ Tools
   │     │  ├─ Access / Accessibility service entry
   │     │  └─ Available
   │     │     ├─ Global Copy entry
   │     │     └─ OLED shield entry
   │     └─ Setting
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

Setting 的 `Appearance` 区域提供 Theme 和 Motion：Motion 可选 `Follow system`、`On`、`Off`，统一控制 Records 的渐变、弹出和液面动画。`Updates` 区域使用标准 `AppListItem`：`Current version` 的副文案显示构建类型和版本号，并触发对应通道的检查；发现更新后显示一个包含构建类型和版本号的动作行，下载与安装阶段在副文案呈现状态。下载动作先用标准确认 Dialog 显示版本与文件大小；完成下载后同一动作行进入安装。等待来源安装授权时动作行进入系统设置。检查、下载和安装期间禁用重复操作。

Global Copy 的 `History` 位于 Tile 之后，显示采集数量并进入独立历史列表。OLED 的 `Saved apps` 位于默认配置之后，复用 `AppListItem`：40dp 应用图标、名称、状态/参数/时间副文案、Enable/Delete 图标操作；无服务时副文案提示 `Accessibility required`，应用不可用时提示 `Unavailable`。

## Records 组合

Records 根页：无页头大标题；同一 `LazyColumn` 三分区，顺序 **Tasks → Accumulate → Measurement**。区标题为紧凑 `labelLarge` + Manage 图标，无根页新建。区内间距 `8dp`，区间 `16dp`（`8 + 8` spacer）。可出现只读总览行（`Tasks a/b · Goals c/d`）；无任务且无目标时隐藏。分段进度、液面和卡色变化使用短动画；系统关闭动画时瞬时到位。

| 区域 | 唯一职责 |
| --- | --- |
| `Tasks` | `Today`/`Later` 分组；全宽卡按子事项分段竖条，未完成格固定使用 Accumulate 0% 的卡底色，完成格使用任务进度底色并弹出填充，格间透明露出页面底色；预览条数可配；已完成折叠。点卡打开进度 Sheet。 |
| `Manage tasks` / `Edit task` | 管理页展示全部任务（进度色卡 + chevron）；顶栏加号新建；编辑路由负责标题、子事项增删排序和删除。 |
| `Manage accumulates` | 只显示 `ACCUMULATE`；顶栏加号 → `New accumulate`，色卡 → `Edit accumulate`。 |
| `Manage measurements` | 只显示 `MEASUREMENT`；顶栏加号 → `New measurement`，色卡副文案为范围与步进（无类型前缀、无 default）；色卡 → `Edit measurement`。 |
| `Accumulate` | 2 列 grid，卡高约 `120dp`；整卡自下而上液面进度（双层正弦波，可减动画）；弱化名称 + 主值；日目标：旗帜 + 近 7 日达标饼；周目标：日历图标 + `· week` 文案 + 与 7 饼同宽的 7 段独立描边进度（两端 A=6dp、切口/填充 B=1.5dp，高 8dp、缝 2dp，满额完成绿）；点卡打开快速记录 Sheet。 |
| `New/Edit accumulate` | 固定 `ACCUMULATE` 表单；Check 保存。 |
| `Measurement` | 2 列 grid，卡高约 `120dp`；首字圆 + 主值/时间；点卡打开预览 Sheet。 |
| `New/Edit measurement` | 固定 `MEASUREMENT` 表单；Check 保存。 |
| Measurement preview Sheet | 标题、最新值、24 小时图、流水和离散 Slider。 |
| 累计记录 Sheet | 身份与进度同一标题行；下方数值输入与今日流水。 |

未配置凭据、首次远端失败或列表为空时用描边空状态卡 + 全宽主按钮；首次加载中居中进度指示。已有 Records 内容刷新时保留列表；刷新失败在内容前显示带重试的状态卡，不以 loading 或错误替换页面。

### Records 视觉规则

- 字阶：section `labelLarge` primary；Accumulate 名称 `labelMedium` 弱化（约 0.62 alpha）；主值 `titleMedium` SemiBold + accent；次文案 `labelMedium`/`bodySmall`。
- 色：按进度，不用名称哈希，不用 secondary/tertiary（避免动态色偏粉）。进行中为 **primary 单色加深**（浅色 wash 约 7%→46%，深色 10%→36%，文案用 `onSurface`）；`LATE` 仍为 primary；**`DONE` 固定成功绿**（深/浅分套，与 LATE 色相分离）。档位阈值 0 / 1/3 / 2/3 / 1。Tasks 用子事项比；有目标累计用累计/目标；无目标累计用近 7 日有值天数比；Measurement 用最新值在 min–max 位置。Manage/表单无实时进度时 IDLE。卡色随进度 `animateColorAsState`。
- 首字圆：`accent` 16% 底 + `accent` 字，保证在色卡上可辨。
- 动作：根页仅 Manage（小 `IconButton`）；新建只在 Manage 顶栏 tonal 加号。完成折叠为普通 `IconButton`。
- Accumulate 液面表达进度（有目标用累计/目标 0–1；无目标用近 7 日活跃天数/7）；主值只显示当前累计；目标用旗帜图标 + 目标值单独一行；有日目标时近 7 日饼状图另起一行（左旧右今；扇区=当日量/日目标；未满 primary 加深，满额完成绿；今日 15dp）；有周目标时底部为与近 7 日饼行同宽、高 8dp 的 7 段独立描边块（Tasks 同理念：段外框 A=6dp、进度填充 B=1.5dp、段间 2dp 透明；描边同圆饼 track 16%）；目标行用日历图标与 `· week` 区分；不重复百分比或 `current / target`。系统关闭动画时液面静止、高度仍可短动画到位。
- Tasks 分段竖条表达子事项完成比；未完成格固定使用 `recordProgressColors(progress = 0f).container`，与 Accumulate 0% 卡底色一致；完成格使用任务进度底色，并在其上铺同色 `fillColor` 洗色（spring 弹出）。仅在部分完成且 Motion 已启用时，全部完成格共享一组 45° 斜向条纹（条宽 `89dp`，条纹仅为底色上的增量洗色 `alpha 0.1`（避免半透明叠加以免对比翻倍），低对比若有若无），按整条分段条坐标系从左到右匀速滚动，每圈 `5.6s`（`Linear`，滚动一个条纹周期，无可见跳变）。全完成或 Motion 关闭时为静态实心洗色。格间透明，直接露出页面底色。全完成时完成格与强调色为完成绿。标题旁对勾（完成时）、右侧 `N/M`。子事项完成轻触觉，父项刚完成强触觉 + Snackbar `{title} done`。
- 累计写入轻触觉；刚达标强触觉，Snackbar 为 `Goal reached · {name}` 并保留 `Undo`。
- Measurement 与 Accumulate 共用标题/主值字阶，避免网格卡字号分叉。

验收：浅/深色 Tasks 与 Manage tasks 均非灰底；Accumulate 与 Measurement 两列卡高 `120dp`；字阶与色规则一致；点击与数据路径不变。

`Manage accumulates` 与 `Manage measurements` 使用相同的标准路由骨架；每页顶栏仅提供本模式新建入口，编辑路由固定为本模式表单。

Task progress Sheet 只显示子事项复选框和完成时间；标题、子事项增删排序和删除任务只在 `Edit task`。`New task` 同样可调整子事项顺序。Measurement 录入使用与 OLED 同类的 Material `Slider`；当前值、最小值和最大值稳定显示，滑块以配置步进离散取值。

`Records connection` 使用 `AppSection`：Server 明文地址、Credentials 凭据输入 + 全宽连接按钮；用户名/密码在连接中禁用；用户名键盘动作切换到密码，密码使用 Password 键盘并通过 `Done` 提交（仅在合法时）；保存成功后返回。

PC 管理页使用 Entries/Record items/Logs 三个页签。Entries 使用日期范围和记录项筛选、可选择表格与批量删除，不提供编辑入口；Record items 使用可选择表格、新建/编辑 Dialog 和批量归档/恢复；Logs 使用只读表格显示操作时间、对象、动作和变更摘要，并可加载更早日志。操作成功后刷新当前数据，失败时在当前页面或 Dialog 显示错误。


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
