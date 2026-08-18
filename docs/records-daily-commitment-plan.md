# Records 每日承诺重构

Status: planned

## 目标

把 Tasks 从日期待办系统改为任务库存与每日承诺系统。任务只保存可能推进的可完成结果；每天显式选择一个主任务和最多一个次任务，通过独立启动动作进入工作，并在约每 60 分钟回顾一次是否仍在推进承诺。

完成标准是：Records 不再产生计划日期、逾期债务和自动 Focus 排序；Today 始终执行 `1 + 1` 上限，四轮后默认停止；Accumulate 与 Measurement 保持独立记录职责；Android、服务端和 PC 使用同一新契约。

## 已确认决策

| 关注点 | 决策 |
| --- | --- |
| 任务语义 | `todo_tasks` 是库存中的可完成结果，`todo_steps` 是固定位序的执行动作；任务没有日期。 |
| 每日上限 | Today 必须选择一个主任务，可选一个不同的次任务；不存在第三个槽位。 |
| 启动动作 | 每天必须填写独立 `firstMove`，如“带电脑去图书馆”；完成后才开始第一轮。 |
| 中途调整 | 可显式替换尚未完成的主任务或次任务，旧任务回到库存；已完成槽位不补入新任务。 |
| 小时回顾 | 每轮开始约 60 分钟后提醒；只允许继续当前任务、切换至另一项已承诺任务或结束今天，不输入总结。 |
| 每日收口 | 第四轮完成或两个承诺均完成后进入 `ENOUGH` 并停止提醒；额外一轮必须显式开始，且只能继续未完成承诺。 |
| 量化记录 | Accumulate 与 Measurement 不进入 Today，不参与任务排序和每日结束判定。 |
| 客户端范围 | Android 承担每日规划、执行、回顾和提醒；PC 只管理任务库存。 |
| 迁移 | 直接删除旧日期、Focus 和闭环语义，不提供兼容字段、数据推断或双写。 |
| Widgets | 删除依赖旧 Focus/Today 语义的计划；核心模型稳定后按新契约重新规划。 |

## 范围

- 服务端增加每日承诺存储和状态转换 API，overview 返回指定本地日期的计划。
- Android 重建 Records 根页、任务管理、任务表单、每日规划、执行与回顾流程。
- Android 使用 WorkManager 安排一次性后台提醒，并管理通知权限和过期任务。
- PC Tasks 删除日期字段和旧状态分组，只展示 `Open` 与 `Completed`。
- 删除任务侧 `plannedDate`、`TaskDateBucket`、`FocusReason`、`DeferredTargets`、`Not now`、Day ring 和相关测试、文案与 UI。
- 保留任务标题、至少一个一级子事项、固定位序、子事项派生父任务完成状态和现有进度交互。

## 非目标

- 不增加 Project、优先级、标签、截止日、日历、重复任务、多级子事项、任务预估或时间段。
- 不记录回顾正文、偏离原因、专注统计或逐轮历史，不把回顾轮次描述为精确工作时长。
- 不在 PC 实现每日计划、回顾或提醒，不在本轮实现 Widgets。
- 不使用精确闹钟、前台服务、离线写入队列或通知不可用时的替代提醒。

## 状态与行为

每日计划持久化 `READY`、`ACTIVE`、`ENOUGH`、`ENDED` 四个状态；`REVIEW_DUE` 由 `ACTIVE && now >= nextReviewAt` 派生，不写入数据库。

```text
创建计划 -> READY
READY --完成 firstMove--> ACTIVE --约 60 分钟--> REVIEW_DUE
REVIEW_DUE --提交后 completedRounds < 4--> ACTIVE
REVIEW_DUE --提交后 completedRounds >= 4--> ENOUGH
ACTIVE --两个承诺均完成--> ENOUGH
ENOUGH --额外一轮且仍有未完成承诺--> ACTIVE
READY/ACTIVE/ENOUGH --结束今天--> ENDED
```

- Today 未创建时只显示规划入口；`READY` 只突出启动动作；`ACTIVE` 突出当前任务的首个未完成子事项；`ENOUGH` 显示 `Enough for today`；`ENDED` 不再提供执行动作。
- 完成当前任务后自动转到另一项未完成承诺；两个任务均完成时立即进入 `ENOUGH`，不要求补足四轮。
- 回顾只在用户响应后开始下一轮；忽略通知不会连续创建新提醒。
- 显式替换是 Today 独立操作，不出现在小时回顾选项中，只允许修改 `READY` 或 `ACTIVE` 的未完成槽位。替换后仍只有两个槽位，已完成槽位不能复用来扩大当天工作量；若当前任务被替换，新任务成为当前任务并从替换时刻重新计 60 分钟，但不增加轮次，替换非当前任务则保持本轮时间。
- 本地日期变化后旧计划不再进入 Today，未完成任务仍为普通库存；不自动继承、不显示逾期或失败。
- 删除被当日 `READY`、`ACTIVE` 或 `ENOUGH` 计划引用的任务时服务端明确拒绝，用户先替换承诺或结束今天。

## 数据与 API

`todo_tasks` 直接删除 `planned_date` 及其索引。新增 `daily_plans`：

| 字段 | 规则 |
| --- | --- |
| `local_date` | ISO 本地日期，主键；日期由 Android 提交，服务端不推断时区。 |
| `first_move` / `first_move_completed_at` | 非空启动动作及可空完成时间。 |
| `primary_task_id` / `secondary_task_id` | 主任务必填、次任务可空且不得相同；创建或替换槽位时目标任务必须未完成。 |
| `current_task_id` | `READY` 时为空；活动时只能是尚未完成的主任务或次任务。 |
| `state` | `READY`、`ACTIVE`、`ENOUGH`、`ENDED`。 |
| `completed_rounds` / `next_review_at` | 非负轮次；只有 `ACTIVE` 保存下一次回顾时刻。 |
| `revision` | 每次状态转换递增；写请求携带期望 revision，拒绝重复或过期操作。 |
| `ended_at` / `created_at` / `updated_at` | 服务端毫秒时间戳。 |

API 固定为 `POST /api/daily-plans` 创建，`PUT /api/daily-plans/:localDate` 编辑启动动作或替换承诺，`PUT /api/daily-plans/:localDate/{start,review,extra-round,end}` 执行状态转换。启动动作只能在 `READY` 编辑；`review` 携带继续使用或切换后的 `currentTaskId`。所有写入携带 `expectedRevision`，并在事务内校验任务状态、槽位限制和当前状态；overview 使用显式 `planDate` 查询参数返回对应 `dailyPlan`。

`start`、`review` 与 `extra-round` 使用 Android 提交的设备 epoch 毫秒 `occurredAt`，服务端保存并将 `nextReviewAt` 设为该时刻后 60 分钟；日期仍使用计划已有的 `localDate`，不从 epoch 推断。

计划创建、承诺替换和结束写入 `DAILY_PLAN` 审计；高频的继续、切换和额外一轮只更新当前计划，不创建逐轮审计历史。旧迁移直接重建 `todo_tasks` 并丢弃 `planned_date`，现有任务和子事项其余数据保持不变。

## Android 提醒

- 完成启动动作或响应回顾后，用唯一的一次性 WorkManager 任务安排 `nextReviewAt`；替换状态时覆盖，进入 `ENOUGH`/`ENDED` 或跨日时取消。
- Worker 发布前读取服务端当日计划，只在本地日期、`revision`、`nextReviewAt` 与输入一致且状态仍为 `ACTIVE` 时通知；读取失败时按 WorkManager 规则重试，不使用缓存计划继续通知。通知只打开 Records Today，不在后台代替用户提交回顾。
- Android 13+ 在首次开始前按场景请求通知权限；权限被拒绝或系统通知已关闭时拒绝 `start` 并保持 `READY`，提供系统设置入口。运行中关闭通知时计划保持 `ACTIVE`，Today 显示阻塞状态，用户只能恢复通知后继续回顾或结束今天。
- 提醒目标是约 60 分钟，允许系统省电策略造成延迟；页面使用服务端计划状态和当前时刻判断是否应回顾。

## 交付切片

每个切片完成定向测试并创建本地 commit；全仓验证只在最终提交前执行一次。

1. **服务端与 PC 契约。** 新建破坏性迁移和每日计划领域规则/API；overview 增加 `planDate`；PC Tasks 改为 Open/Completed 并删除日期；覆盖迁移、状态转换、revision、任务引用和 API 测试。
2. **Android 领域与数据。** 递增 App 版本；删除日期与旧 Focus 模型；接入 `dailyPlan`、状态转换和错误；更新任务创建/编辑/管理页及对应 JVM 测试。
3. **Today 纵向切片。** 重建 Records 根页的 Today 状态、规划流程、启动动作、主次任务、显式替换、任务自动切换、四轮收口和 Routines/Measurement 分区；完成 Compose 语义测试。
4. **后台提醒。** 接入通知权限、唯一 WorkManager、跨日与结束取消、通知深链；验证 Android 12、Android 13+ 权限拒绝/授权、进程退出和省电延迟后的状态一致性。
5. **交付。** 定向回归后执行服务端与 Android 全量检查并提交；先部署服务端迁移与健康校验，再发布 debug APK，校验远端 manifest、APK `200`、`content-length` 和 SHA-256，最后完成真机验收与文档收尾。

## 验收

- 新建和编辑任务没有日期；Android 与 PC 都只分 Open/Completed，现有任务不因旧日期丢失。
- 每日本地日期最多一份计划；主任务必填，次任务可空且不同，任何入口都不能形成第三个槽位。
- 启动动作未完成时没有回顾任务；完成后只存在一个约 60 分钟提醒。
- 回顾页没有正文输入；继续或纠偏只更新当前任务、轮次和下一次提醒。
- 第四轮、两个任务完成、手动结束和跨日都会停止旧提醒；额外一轮必须显式开始。
- 未完成任务跨日后仍在 Open，不自动进入新 Today，不出现 Overdue、Day closed 或失败状态。
- Accumulate/Measurement 的记录、目标、图表与 Undo 保持可用，但不会进入 Today 或影响 `Enough for today`。
- 服务端拒绝过期 revision、非法状态转换、相同主次任务和删除活动计划引用任务，不创建部分数据。
- `records-server` 测试、Web 测试、`:app:testDebugUnitTest`、`:app:assembleDebug` 与 `git diff --check` 通过。

## 文档收尾

实施前本文件是范围、顺序和验收的唯一依据。完成时把用户行为迁入 `product.md`，表/API/WorkManager 契约迁入 `architecture.md`，Today 页面与状态视觉迁入 `design-system.md`，源码地图和真机检查迁入 `development.md`；同步 README 当前版本与 Records 概览，删除本计划。
