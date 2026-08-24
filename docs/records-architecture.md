# Records 数据与接口

## 运行边界

`records-server` 使用 Fastify、Drizzle ORM 和 SQLite。生产数据库为 `/srv/records/records.sqlite`，服务监听 `127.0.0.1:4320`，由 Caddy 在 `/apps/records` 提供 HTTPS 和 Basic Auth。Android 由 `RecordRepository` 使用进程级 `OkHttpClient` 访问，凭据由 Keystore 加密保存。

`GET /api/overview` 接受记录起止日期和 `planDate`，返回活跃模板、Goal 及其派生共享进度、Daily 建议、昨日未完成候选、该日期 Today tasks/DailyFocus/Hourly check-in、范围内累计流水和最新 Measurement 流水。ViewModel 串行根页读写；成功写入后刷新，刷新失败不回滚已完成写入。

## 数据表

| 表 | 关键字段与约束 |
| --- | --- |
| `record_items` | `NOCASE` 唯一名称、模式、计量配置、目标、优先级、归档状态和时间戳。 |
| `record_entries` | 发生时刻、固化本地日期/分钟和值；随父项级联删除。 |
| `goals` | 通用共享进度目标的标题、描述、单位、默认值、步进、起止日期、归档状态和时间戳。 |
| `goal_subgoals` | Goal 的一级子目标、目标值、固定位序和完成时间；随父目标级联删除。 |
| `goal_entries` | Goal 共享进度的发生时刻、固化本地日期/分钟和值；随父目标级联删除。 |
| `task_templates` | 标题、`MANUAL/DAILY`、归档状态和时间戳；没有完成状态。 |
| `task_template_steps` | 模板步骤与固定位序；模板更新时整体替换。 |
| `today_tasks` | 日期、可空模板/昨日任务来源、标题快照、位序、完成时刻和 revision。 |
| `today_task_steps` | 任务步骤快照、固定位序和完成时刻；随任务级联删除。 |
| `daily_checkins` | 唯一日期和起床标记时刻；随日期读取。 |
| `hourly_checkins` | 打卡所属本地小时、操作时刻；同一日期与小时唯一，随起床标记级联删除。 |
| `daily_focus` | 唯一日期、主/次/当前任务引用、第一步、状态、暂停原因、轮次、下一回顾时刻和 revision。 |
| `record_audit_logs` | 记录项、Goal、模板、Today task 与 Focus 的动作及前后 JSON 快照。 |

累计流水必须为有限正数；Measurement 流水必须位于闭区间且符合步进。记录项已有流水后，模式、计量类型、单位和 Measurement 范围不可变；归档项不能新增流水，永久删除只接受归档项。写请求携带未定义的 `windows` 字段时明确拒绝。记录项更新先比较规范化快照，无变化或失败时不写业务数据和审计。

客户端一次性提交流水的 `occurredAt`、`localDate`、`localMinute` 和数值，服务端原样保存，不依赖自身时区。流水按日期、记录项和模式筛选并按发生时间倒序；审计按 `id` 倒序，以最多 `100` 条游标分页。Android 不创建 `RecordDatabase`、Room DAO 或本地记录文件，只在进程内保留最近一次成功快照。

## Today API

| 方法与路径 | 作用 | 关键输入/输出 |
| --- | --- | --- |
| `GET /api/today/:localDate` | 读取指定日期。 | 返回 `todayTasks`、`dailySuggestions`、`unfinishedTasks`、`dailyFocus`、`dailyCheckin`。 |
| `POST /api/today-tasks` | 创建临时任务。 | 日期、标题、一个或多个步骤。 |
| `POST /api/today-tasks/from-template` | 从活跃模板创建快照。 | 日期、`templateId`。 |
| `POST /api/today-tasks/from-task` | 续做昨日未完成任务。 | 日期、`sourceTaskId`；只复制未完成步骤。 |
| `PUT /api/today-task-steps/:id/completion` | 完成或重开一个步骤。 | `completed`、任务 `expectedRevision`、`occurredAt`。 |
| `POST /api/daily-checkins/wake` | 标记当天起床。 | 本地日期和发生时刻；同日只能创建一次。 |
| `POST /api/daily-checkins/hourly` | 打卡当前本地整点小时。 | 本地日期、小时和发生时刻；要求已起床、小时已到且同一小时未打卡。 |
| `POST /api/daily-focus` | 创建当天 Focus。 | 日期、第一步、主任务和可空次任务。 |
| `PUT /api/daily-focus/:localDate` | 更新 `READY` Focus。 | 第一步、任务选择、revision 和发生时刻。 |
| `PUT /api/daily-focus/:localDate/{start|review|refocus|continue|end}` | 执行 Android Focus 状态转换。 | revision、发生时刻及对应任务选择。 |
| `PUT /api/daily-focus/:localDate/{review-now|switch|reset}` | 执行 PC 管理命令。 | revision、发生时刻及对应当前任务。 |

所有写入由服务端事务校验日期、状态、引用和 revision；失败请求不产生业务数据或审计。任务创建、子任务完成/重开、起床、每小时打卡及 Focus 状态转换写入审计。轮次只由 `review/review-now` 增加。

模板与临时 Today task 写入要求至少一个步骤。Today task 从模板创建后独立；模板删除只清空其外键。昨日来源使用唯一约束防止同一天重复续做。

## Goals API

| 方法与路径 | 作用 |
| --- | --- |
| `GET/POST /api/goals`、`PUT/DELETE /api/goals/:id` | 查询、新建、更新及删除已归档 Goal；响应内含子目标和 `currentValue`。 |
| `POST /api/goals/batch-archive` | 批量归档或恢复 Goal。 |
| `POST /api/goal-entries` | 在 Goal 日期范围内增加一条正数共享进度。 |
| `PUT /api/goal-subgoals/:id/completion` | 达到子目标值后确认完成；全部完成或日期范围外拒绝变更。 |

Goal、子目标、共享进度流水及完成操作均写入审计。服务端按流水求和派生 `currentValue`；子目标完成不修改流水。存在流水后更新事务锁定单位、步进、日期与完整子目标定义；全部子目标完成或日期过期后拒绝新增进度和完成状态变更。

## Focus 协调

- 创建、更新、开始或恢复 Focus 时，主、次引用必须是同一天未完成任务且互不相同。
- 当前 Focus 完成后，服务端切换到另一项已选未完成任务；没有剩余项时写入 `PAUSED + FOCUS_CLEARED`。
- 第四次及后续回顾写入 `PAUSED + TARGET_REACHED`。恢复 Focus 不增加轮次，任务重开不改变 Focus。
- `ENDED` 不恢复。PC `reset` 保留 Today tasks，只重置 Focus 执行字段。

## 提醒与部署

Android 每次刷新后分别按 DailyFocus 与 Hourly check-in 覆盖唯一 WorkManager 任务。Hourly worker 联网重读当天 check-in：未起床、跨日或状态不匹配时成功结束；送达后计算下一整点并续排一个任务。通知点击通过 `MainActivity` extra 打开对应 Records 页面。通知权限不可用时拒绝起床标记、开始或恢复 Focus；进入 `PAUSED/ENDED`、未起床或跨日时取消对应 WorkManager 任务和可见通知。

PC 管理页静态资源由同一服务提供。Overview 固定读取浏览器真实今天；Today 使用所选日期。备份脚本使用 SQLite 在线备份 API 写入 `/srv/records/backups`；部署与恢复检查见 [`../records-server/deploy/README.md`](../records-server/deploy/README.md)。
