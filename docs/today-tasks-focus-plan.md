# Today tasks 与 Focus 重构计划

状态：已确认，待实施。完成后删除本文件，长期规则归入主题文档。

## 目标

将“今天要做什么”与“当前专注什么”分开：普通任务负责完成与历史，Focus 只负责第一步、小时回顾和四轮投入目标。周期模板只提供候选，不自动创建当天任务。

## 已确认规则

- Today task 可临时创建，也可从模板或昨日未完成任务创建。
- 模板步骤可为空；Today task 保存标题和可选清单快照。
- `DAILY` 表示每天推荐，加入 Today 必须由用户确认。
- 昨日未完成任务保留在原日期，并作为次日候选；不自动顺延。
- “今天已足够”只由 Focus 的四次回顾判断，普通任务完成率不参与。
- 保存任务完成、重开和完成时间；本轮不增加统计页面。
- Android 的 `Routines` 改名为 `Accumulate / 累计`，继续只记录次数或时长。

## 领域模型

### TaskTemplate

- 字段：标题、`MANUAL/DAILY`、归档状态、零个或多个有序步骤。
- 只提供定义和候选，不保存完成状态。
- 编辑或删除模板不改变已生成的 Today task 快照。

### TodayTask

- 字段：日期、可空模板来源、可空昨日任务来源、标题、位置、完成时间、revision、创建/更新时间和有序步骤。
- 无步骤时，任务 checkbox 直接设置完成或重开。
- 有步骤时，单个步骤可独立完成；全部步骤完成时任务完成。
- 点击有步骤任务的主 checkbox 时一次完成全部未完成步骤；重开时清空全部步骤完成状态。
- 同一天同一昨日来源只允许生成一次，防止候选重复加入。
- Today task 不依赖 Focus 存在；Focus 暂停或结束后仍可完成和重开。

### DailyFocus

- 每个日期至多一个，引用当天一个主任务和至多一个不同的次任务，不复制任务内容。
- 字段保留第一步、当前任务、`READY/ACTIVE/PAUSED/ENDED`、暂停原因、完成轮数、下一回顾时间、revision 和结束时间。
- 暂停原因只有 `FOCUS_CLEARED` 与 `TARGET_REACHED`。
- 选择或恢复 Focus 时，引用的任务必须属于同一天且未完成；普通 Today task 没有主次角色。

## 状态转换

1. 用户先创建或加入 Today tasks，再选择 1 个主 Focus、可选 1 个次 Focus，并填写第一步。
2. 完成第一步后进入 `ACTIVE`，从操作时刻计算 60 分钟。
3. 到期回顾只能继续当前 Focus、切换到另一个未完成的已选 Focus，或结束 Focus；回顾使轮数加一。
4. 当前 Focus 完成时自动切到另一个未完成的已选 Focus；两项都完成且尚未达到四轮时进入 `PAUSED + FOCUS_CLEARED`。
5. `FOCUS_CLEARED` 可从任意未完成 Today task 重新选择 1+1 并恢复 `ACTIVE`；该操作不增加轮数。
6. 第四次及之后每次回顾进入 `PAUSED + TARGET_REACHED`，此时“今天已足够”，与全部 Today tasks 是否完成无关。
7. `TARGET_REACHED` 可从任意未完成 Today task 选择下一轮 Focus；默认预选仍未完成的原 Focus。继续不增加轮数，下一次回顾再增加。
8. `READY` 中 Focus task 被提前完成时保持 `READY`，开始前必须改选未完成任务。
9. 重开任务不自动启动或恢复 Focus；用户显式选择 Focus 后才创建新的回顾计时。
10. `ENDED` 只结束 Focus；当天任务仍可操作，Focus 不再恢复。

## 候选与页面

- Today 固定为 `Focus`、`Tasks` 两个区；Tasks 分为未完成与已完成。
- 添加任务入口始终可用，提供 `Recommended`、`Templates`、`New task`。
- Recommended 由当日尚未使用的 Daily 模板和昨日尚未完成且今日尚未复制的任务组成。
- Focus 只从当日未完成任务中选择；普通任务可随时成为下一次 Focus。
- PC Today 保留日期切换并显示历史日期任务；Activity 显示任务创建、完成与重开审计。
- Templates 保留 Active/Archived 管理，表单允许空步骤。
- Android 根页顺序改为 Today、Templates、Accumulate、Measurement。

## 数据与接口

- 新表使用 `today_tasks`、`today_task_steps`、`daily_focus`；删除 `today_commitments`、`today_commitment_steps`、`daily_plans`。
- 审计实体改为 `TODAY_TASK`、`TODAY_TASK_STEP`、`FOCUS`，动作同步改名。
- overview 返回活跃模板、指定日期 Today tasks、昨日未完成候选、DailyFocus 和现有量化数据。
- Today task API 提供内联创建、模板创建、昨日复制、任务完成/重开和步骤完成/重开。
- Focus API 提供创建/更新 READY、开始、到期回顾、PC 提前回顾/即时切换、重新选择、继续、结束和重置。
- 所有任务和 Focus 写入使用 revision 或当前快照约束；冲突明确返回，不静默覆盖。

## 迁移

- 保留模板及步骤，移除“至少一个步骤”的约束。
- 现有 commitment 按计划日期迁移为 Today task，步骤完成时间原样保留。
- 原主、次 commitment 映射为 Focus 引用；当前 additional commitment 若仍活动则成为主 Focus，其余 additional 只作为普通任务。
- 原 `COMMITMENTS_COMPLETED` 映射为 `FOCUS_CLEARED`，`TARGET_REACHED` 原样保留。
- 任务与 Focus 审计实体和动作转换为新命名；Record items、entries 及其审计不变。
- 迁移后只保留新表和新 API，不保留旧字段、旧路由或客户端兼容分支。

## 实施顺序

1. 服务端 schema、迁移、领域规则、API 和审计测试。
2. PC Today、Templates、Overview、Activity 与响应式测试。
3. Android 模型、Repository、提醒、Today/模板页面和中英文文案；递增版本。
4. 同步 README、产品、架构、设计和开发文档，删除本计划。
5. 执行服务端和 Android 全量验证，提交后部署服务端，再发布 debug APK。

## 验收标准

- 无 Focus 时也能新增、完成、重开 Today task，并在历史日期读取结果。
- Daily 模板和昨日未完成项只作为候选；用户确认后才出现今天实例，昨日来源不能重复加入。
- 标题任务与清单任务均能正确完成、部分完成和重开，完成时间可见。
- 只有已选 Focus 影响自动切换和 `FOCUS_CLEARED`；普通任务状态不改变 Focus。
- 第四次回顾稳定进入 `TARGET_REACHED`，即使仍有普通任务也显示今天已足够。
- Focus 暂停或结束后，普通任务 checkbox 和添加入口仍可使用。
- 模板允许零步骤且编辑不影响 Today 快照。
- 迁移保留现有模板、Today 完成进度、量化数据及其历史，生产备份恢复检查通过。
- Android 和 PC 使用同一状态；定向测试、全量测试、debug 构建、远端服务校验和 APK 发布通过。

## 不在本轮

- 完成率、连续天数、图表、周/月统计。
- 每周或自定义周期、截止时间、提醒时间、优先级、标签和自动顺延。
- Today task 编辑、删除、拖拽排序、子任务层级和跨日批量操作。
