# Records 界面规则

## 页面结构

Records 根页无大标题，同一 `LazyColumn` 固定为 **Today → Templates → Goals → Accumulate → Measurement**。Today 分开呈现 Hourly check-in 与 Tasks；其余 section 使用 `titleSmall` 标题、数量和 chevron 进入管理页。

| 区域 | 职责 |
| --- | --- |
| `Today / Hourly check-in` | 单张 `8dp` 卡在未起床时提供起床标记；已起床时显示下次整点与当天已打卡小时，到点后进入打卡 Sheet。 |
| `Today / Tasks` | Add task 始终可用；任务行显示下一个未完成子任务和完成数。点按任务打开详情，可修改全部子任务。 |
| Hourly check-in Sheet | 依任务和子任务顺序显示当天所有子任务，可自由完成或重开；底部主操作独立完成当前整点打卡。 |
| Add task Sheet | `Recommended / Templates / New task` 三个 tabs；推荐包含未采用 Daily 模板和昨日未完成候选；新任务要求至少一个子任务。 |
| `Templates` | 根页只显示活跃数量。管理页 Daily 优先；表单整体保存标题、周期与至少一个子任务，模板没有 checkbox。 |
| `Goals` | 根页只列进行中的 Goal，以最大子目标值为目标显示共享进度；详情 Sheet 录入正数进度，并仅对已达到目标值的子目标开放确认。管理页保留完成和到期结果。 |
| `Accumulate` | 显示名称与当前周期累计，进入累计记录 Sheet，不显示任务状态；无目标项显示今日累计。 |
| `Measurement` | 显示名称与最新值，进入 24 小时预览和离散 Slider。 |

首次加载居中显示进度；未配置凭据、首次请求失败或列表为空时显示描边状态块和唯一主操作。已有内容刷新时保留列表；刷新失败在内容前显示带重试的状态块，不用 loading 或错误页替换快照。

## 状态呈现

- Today task 没有父级完成控件；已完成的子任务显示完成时间。根页仅显示每项任务的下一个未完成子任务，任务详情和打卡 Sheet 都提供子任务 completion。模板修改不影响任务快照。
- 打卡入口在未起床时显示起床标记；起床后显示下一整点与当天已打卡小时。当前小时可打卡时，通知和卡片均打开相同 Sheet；同一小时完成后不再显示重复入口。
- Accumulate 与 Measurement 只突出当前数值；累计写入和刚达标沿用记录 Sheet 的触觉、Snackbar 与 `Undo`。
- Goals 与 Accumulate 的根页行统一使用 `8dp` 圆角的两色 45° 斑马纹连续进度背景，标题居左，右侧显示 `当前值/目标值 unit`。Goal 的目标值为最大子目标值；有目标 Accumulate 使用目标周期累计，无目标项显示 `当前值/-- unit` 且不填充进度。进度超出目标后背景封顶为整行。
- Goal 详情明确区分“达到目标值”和“已确认完成”；完成或到期时禁用录入和 checkbox，只提供创建独立新目标的“再次挑战”，不提供继续、延期或重开旧目标。

## 管理页

`Manage goals` 保留进行中、完成和到期 Goal；表单使用通用单位、默认值、步进、必填起止日期和一层子目标。产生进度后，固定字段与子目标只读。目标管理卡片、编辑页预览块和表单字段沿用 Accumulate 的页面水平边距、`formField()` 垂直间距、卡片身份区和顶栏 `FilledTonalIconButton` 保存入口。目标详情 Sheet 复用 Records 数值录入控件，提供相同的减号、数值框、加号、步进提示和全宽主操作；完成、未开始或到期时整体禁用录入。`Manage accumulates` 与 `Manage measurements` 使用相同路由骨架，只列出对应模式并提供新建入口。累计表单固定 `ACCUMULATE`；测量表单固定 `MEASUREMENT + Number`。Measurement 预览使用原始 24 小时数据和离散 Slider。

Setting 的 `Records connection` 使用 `AppSection`：Server 明文地址、用户名、密码和全宽连接按钮。连接时禁用凭据输入；用户名键盘动作移到密码，密码使用 Password 键盘并通过 `Done` 提交合法表单；保存成功后返回。

PC 使用响应式 App shell：桌面固定窄侧栏，窄屏为可横向滚动顶部导航。Today 顶部提供日期切换，内部按 Hourly check-in 与 Tasks 分区；Tasks 的详情和打卡区都可修改子任务。

Templates 使用 Active/Archived 分段、搜索和表格；Goals 使用 Active/Archived/All 分段、目标与子目标搜索和表格，进度 Modal 提供单位、减号、加号与建议步进，编辑 Modal 管理定义并联动校验起止日期。Accumulate 与 Measurement 使用固定模式表单；Entries 提供日期、模式与记录项筛选。Activity 可按 Goal、Goal progress 和 Goal subgoal 筛选，并显示目标名称、进度值与子目标状态变化。

## 修改检查

- 最长中英文按钮和任务标题是否在手机与桌面宽度完整显示。
- 起床标记后是否只在下一个整点及以后开放打卡；同一小时成功打卡后是否隐藏重复入口，并在通知点击后打开 Sheet。
- Today task 和模板是否使用各自正确控件，父任务没有完成入口，子任务 completion 可在任务详情和打卡 Sheet 中完成或重开。
- Goals 与 Accumulate 的主页斑马纹、右侧进度格式和周期累计来源是否一致，无目标累计是否显示 `当前值/-- unit`。
