# Records 界面规则

## 页面结构

Records 根页无大标题，同一 `LazyColumn` 固定为 **Today → Templates → Goals → Accumulate → Measurement**。Today 分开呈现 Focus 与 Tasks；其余 section 使用 `titleSmall` 标题、数量和 chevron 进入管理页。

| 区域 | 职责 |
| --- | --- |
| `Today / Focus` | 单张 `8dp` 卡按 DailyFocus 状态切换操作。`READY` 显示第一步；`ACTIVE` 显示当前任务；到期显示继续、切换和结束；两种 `PAUSED` 进入 Focus 选择；`ENDED` 只表示 Focus 结束。 |
| `Today / Tasks` | Add task 始终可用；任务行使用 checkbox，当前 Focus 使用 `primaryContainer`；清单任务显示完成数并进入进度 Sheet。 |
| `Plan/Edit Focus` | 第一步、未完成主任务和可空次任务；第一步只在创建或 `READY` 可编辑，暂停后只重新选择 Focus。顶栏 Check 保存。 |
| Add task Sheet | `Recommended / Templates / New task` 三个 tabs；推荐包含未采用 Daily 模板和昨日未完成候选；新任务允许可选清单。 |
| `Templates` | 根页只显示活跃数量。管理页 Daily 优先；表单整体保存标题、周期与可选步骤，模板没有 checkbox。 |
| `Goals` | 根页只列进行中的 Goal，显示共享进度和子目标完成数。详情 Sheet 录入正数进度，并仅对已达到目标值的子目标开放确认。管理页保留完成和到期结果。 |
| `Accumulate` | 显示名称与今日累计，进入累计记录 Sheet，不显示任务或 Focus 状态。 |
| `Measurement` | 显示名称与最新值，进入 24 小时预览和离散 Slider。 |

首次加载居中显示进度；未配置凭据、首次请求失败或列表为空时显示描边状态块和唯一主操作。已有内容刷新时保留列表；刷新失败在内容前显示带重试的状态块，不用 loading 或错误页替换快照。

## 状态呈现

- Focus 卡标题使用 `labelLarge` primary；当前动作使用 `titleLarge` SemiBold；说明与轮次使用 `onSurfaceVariant`。卡与任务行统一 `8dp` 圆角。
- Tasks 与 Focus 独立：Add task 和任务 checkbox 不随 Focus 状态隐藏；“今天已足够”与 `ENDED` 后仍显示 Tasks。
- 一个 Focus 状态只显示当前可执行命令。启动、继续、切换和重新选择使用明确按钮；编辑使用铅笔 icon；结束 Focus 使用弱化文本按钮。
- `FOCUS_CLEARED` 且 `completedRounds == 0` 时不渲染“已完成 0 轮”，只显示所选 Focus 已完成及重新选择入口。
- 回顾到期由分钟时钟触发，不显示倒计时或精确工时。通知不可用时只替换 Focus 内容，Tasks 不受影响。
- Today task progress Sheet 显示任务主 checkbox、可选步骤 checkbox 和完成时间。模板修改不影响任务快照。
- Accumulate 与 Measurement 只突出当前数值；累计写入和刚达标沿用记录 Sheet 的触觉、Snackbar 与 `Undo`。
- Goal 详情明确区分“达到目标值”和“已确认完成”；完成或到期时禁用录入和 checkbox，只提供创建独立新目标的“再次挑战”，不提供继续、延期或重开旧目标。

## 管理页

`Manage goals` 保留进行中、完成和到期 Goal；表单使用通用单位、默认值、步进、必填起止日期和一层子目标。产生进度后，固定字段与子目标只读。目标管理卡片、编辑页预览块和表单字段沿用 Accumulate 的页面水平边距、`formField()` 垂直间距、卡片身份区和顶栏 `FilledTonalIconButton` 保存入口。目标详情 Sheet 复用 Records 数值录入控件，提供相同的减号、数值框、加号、步进提示和全宽主操作；完成、未开始或到期时整体禁用录入。`Manage accumulates` 与 `Manage measurements` 使用相同路由骨架，只列出对应模式并提供新建入口。累计表单固定 `ACCUMULATE`；测量表单固定 `MEASUREMENT + Number`。Measurement 预览使用原始 24 小时数据和离散 Slider。

Setting 的 `Records connection` 使用 `AppSection`：Server 明文地址、用户名、密码和全宽连接按钮。连接时禁用凭据输入；用户名键盘动作移到密码，密码使用 Password 键盘并通过 `Done` 提交合法表单；保存成功后返回。

PC 使用响应式 App shell：桌面固定窄侧栏，窄屏为可横向滚动顶部导航。Today 顶部提供日期切换，内部按 Focus 与 Tasks 分区；Tasks 始终可操作，Focus 区提供状态、revision、提前回顾、即时切换、重置与结束。

Templates 使用 Active/Archived 分段、搜索和表格；Goals 使用 Active/Archived/All 分段、目标与子目标搜索和表格，进度 Modal 提供单位、减号、加号与建议步进，编辑 Modal 管理定义并联动校验起止日期。Accumulate 与 Measurement 使用固定模式表单；Entries 提供日期、模式与记录项筛选。Activity 可按 Goal、Goal progress 和 Goal subgoal 筛选，并显示目标名称、进度值与子目标状态变化。

## 修改检查

- Tasks 是否在 Focus 的所有状态下保持可见和可操作。
- 零轮 `FOCUS_CLEARED` 是否避免显示“已完成 0 轮”。
- 最长中英文按钮和任务标题是否在手机与桌面宽度完整显示。
- Today task、模板与 Focus 是否使用各自正确控件，没有重复完成入口。
