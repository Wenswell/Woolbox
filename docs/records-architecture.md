# Records 数据与接口

## 运行边界

`records-server` 使用 Fastify、Drizzle ORM 和 SQLite。生产数据库为 `/srv/records/records.sqlite`，服务监听 `127.0.0.1:4320`，由 Caddy 在 `/apps/records` 提供 HTTPS 和 Basic Auth。Android 由 `RecordRepository` 使用进程级 `OkHttpClient` 访问，凭据由 Keystore 加密保存。

`GET /api/overview` 接受记录起止日期和 `planDate`，返回活跃模板、Daily 建议、昨日未完成候选、该日期 Today tasks/DailyFocus、范围内累计流水和最新 Measurement 流水。ViewModel 串行根页读写；成功写入后刷新，刷新失败不回滚已完成写入。

## 数据表

| 表 | 关键字段与约束 |
| --- | --- |
| `record_items` | `NOCASE` 唯一名称、模式、计量配置、目标、优先级、归档状态和时间戳。 |
| `record_entries` | 发生时刻、固化本地日期/分钟和值；随父项级联删除。 |
| `task_templates` | 标题、`MANUAL/DAILY`、归档状态和时间戳；没有完成状态。 |
| `task_template_steps` | 模板步骤与固定位序；模板更新时整体替换。 |
| `today_tasks` | 日期、可空模板/昨日任务来源、标题快照、位序、完成时刻和 revision。 |
| `today_task_steps` | 任务步骤快照、固定位序和完成时刻；随任务级联删除。 |
| `daily_focus` | 唯一日期、主/次/当前任务引用、第一步、状态、暂停原因、轮次、下一回顾时刻和 revision。 |
| `record_audit_logs` | `ITEM/ENTRY/TEMPLATE/TODAY_TASK/TODAY_TASK_STEP/FOCUS` 的动作及前后 JSON 快照。 |

累计流水必须为有限正数；Measurement 流水必须位于闭区间且符合步进。记录项已有流水后，模式、计量类型、单位和 Measurement 范围不可变；归档项不能新增流水，永久删除只接受归档项。写请求携带未定义的 `windows` 字段时明确拒绝。记录项更新先比较规范化快照，无变化或失败时不写业务数据和审计。

客户端一次性提交流水的 `occurredAt`、`localDate`、`localMinute` 和数值，服务端原样保存，不依赖自身时区。流水按日期、记录项和模式筛选并按发生时间倒序；审计按 `id` 倒序，以最多 `100` 条游标分页。Android 不创建 `RecordDatabase`、Room DAO 或本地记录文件，只在进程内保留最近一次成功快照。

## Today API

| 方法与路径 | 作用 | 关键输入/输出 |
| --- | --- | --- |
| `GET /api/today/:localDate` | 读取指定日期。 | 返回 `todayTasks`、`dailySuggestions`、`unfinishedTasks`、`dailyFocus`。 |
| `POST /api/today-tasks` | 创建临时任务。 | 日期、标题、零个或多个步骤。 |
| `POST /api/today-tasks/from-template` | 从活跃模板创建快照。 | 日期、`templateId`。 |
| `POST /api/today-tasks/from-task` | 续做昨日未完成任务。 | 日期、`sourceTaskId`；只复制未完成步骤。 |
| `PUT /api/today-tasks/:id/completion` | 完成或重开整个任务。 | `completed`、`expectedRevision`、`occurredAt`。 |
| `PUT /api/today-task-steps/:id/completion` | 完成或重开一个步骤。 | `completed`、任务 `expectedRevision`、`occurredAt`。 |
| `POST /api/daily-focus` | 创建当天 Focus。 | 日期、第一步、主任务和可空次任务。 |
| `PUT /api/daily-focus/:localDate` | 更新 `READY` Focus。 | 第一步、任务选择、revision 和发生时刻。 |
| `PUT /api/daily-focus/:localDate/{start|review|refocus|continue|end}` | 执行 Android Focus 状态转换。 | revision、发生时刻及对应任务选择。 |
| `PUT /api/daily-focus/:localDate/{review-now|switch|reset}` | 执行 PC 管理命令。 | revision、发生时刻及对应当前任务。 |

所有写入由服务端事务校验日期、状态、引用和 revision；失败请求不产生业务数据或审计。任务创建、完成、重开及 Focus 状态转换写入审计。轮次只由 `review/review-now` 增加。

模板写入允许零步骤。Today task 从模板创建后独立；模板删除只清空其外键。昨日来源使用唯一约束防止同一天重复续做。

## Focus 协调

- 创建、更新、开始或恢复 Focus 时，主、次引用必须是同一天未完成任务且互不相同。
- 当前 Focus 完成后，服务端切换到另一项已选未完成任务；没有剩余项时写入 `PAUSED + FOCUS_CLEARED`。
- 第四次及后续回顾写入 `PAUSED + TARGET_REACHED`。恢复 Focus 不增加轮次，任务重开不改变 Focus。
- `ENDED` 不恢复。PC `reset` 保留 Today tasks，只重置 Focus 执行字段。

## 提醒与部署

Android 每次刷新后按 DailyFocus 覆盖唯一 WorkManager 任务。Worker 联网重读 DailyFocus：读取失败返回 retry；本地日期、revision、状态或时间不匹配时成功结束，不从缓存发通知。通知送达标识为 `(localDate, revision, nextReviewAt)`，点击通知通过 `MainActivity` extra 返回 Records，跨日后失效。通知权限不可用时拒绝开始或恢复 Focus；进入 `PAUSED/ENDED` 或跨日时取消 WorkManager 任务和可见通知。

PC 管理页静态资源由同一服务提供。Overview 固定读取浏览器真实今天；Today 使用所选日期。备份脚本使用 SQLite 在线备份 API 写入 `/srv/records/backups`；部署与恢复检查见 [`../records-server/deploy/README.md`](../records-server/deploy/README.md)。
