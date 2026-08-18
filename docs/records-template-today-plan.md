# Records 模板与 Today 实例重构计划

状态：实施中

## 目标

拆分可复用定义与当天执行状态：Templates 只保存配方，Today 只执行当天实例，DailyPlan 只管理轮次与暂停。

## 产品规则

- `Tasks` 更名为 `Templates`；模板保存标题、步骤、`MANUAL/DAILY` 和 `ACTIVE/ARCHIVED`，没有完成状态。
- 初始规划从模板创建一个 `PRIMARY` 和最多一个不同的 `SECONDARY` Today 实例；实例复制当时的标题、类型和步骤，后续模板修改不影响实例。
- Daily 模板只在选择器中优先显示，不自动加入 Today；同一模板当天可重复创建实例。
- 初始实例全部完成且不足四轮时暂停为 `COMMITMENTS_COMPLETED`，可追加 `ADDITIONAL` 实例；已完成实例继续显示。
- 第四轮及以后每轮回顾后暂停为 `TARGET_REACHED`；有未完成实例时可“再投入一轮”，全部完成时先从模板追加实例。
- 重新打开 `COMMITMENTS_COMPLETED` 实例时自动恢复：第一动作未完成则 `READY`，否则 `ACTIVE` 并从操作时刻计算 60 分钟。
- 同一时刻只推进一个实例；完成当前实例后切换到下一未完成实例，全部完成则暂停。`ENDED` 不自动恢复。

## 数据模型

- `task_templates`、`task_template_steps`：模板定义。
- `daily_plans`：`READY/ACTIVE/PAUSED/ENDED`、`COMMITMENTS_COMPLETED/TARGET_REACHED`、轮次、当前实例、回顾时间和 revision。
- `today_commitments`、`today_commitment_steps`：日期计划下的实例快照与完成状态。
- 删除旧 `todo_tasks`、`todo_steps`、`todo_step_completions` 和旧 `daily_plans` 数据；删除对应 Task、Step、DailyPlan 审计。保留 Record items、entries 及其审计。

## API

- Templates：列表、创建、编辑、归档、恢复、删除；步骤随模板表单整体保存。
- Today：创建/读取/编辑计划，开始、回顾、提前回顾、即时切换、追加实例、再投入一轮、结束、重置。
- Today 实例步骤：完成与重新打开；所有写操作由服务端事务协调计划状态和 revision。
- Overview 返回模板摘要、当天计划及其实例；Android 与 PC 使用相同契约。

## 实施顺序

1. 替换服务端 schema、迁移、领域模型、API、审计和测试。
2. 重构 PC Templates 与 Today 管理界面及测试。
3. 重构 Android 模型、Repository、ViewModel、Templates 与 Today UI 及测试。
4. 递增版本，更新长期文档并删除本计划。
5. 完成本地验证和提交；备份生产数据，先部署服务端，再发布 debug APK。

## 验收

- 模板没有完成入口，Daily 模板次日仍可选择，同一模板可创建多个 Today 实例。
- Today 初始限制 1+1；全部完成后可追加实例，已完成实例保留。
- 不足四轮完成承诺时显示“添加新承诺”；四轮后显示“再投入一轮”。
- 轮次只在回顾时增加；添加、完成或重新打开实例不增加轮次。
- 模板修改不改变已有实例快照。
- 部署后 Templates 与 Today 为空，Routines、Measurement、流水及其审计保持不变。
