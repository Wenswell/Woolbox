# Records Today 完整管理

Status: in progress (server and PC complete)

## 目标

把 PC Today 从执行镜像扩展为可纠偏的完整管理界面，同时保留服务端状态机、revision 和任务完成派生规则作为唯一事实来源。

## 已确认范围

| 关注点 | 决策 |
| --- | --- |
| 日期 | 可切换任意 ISO 本地日期；计划、任务状态和写操作统一使用所选日期。 |
| 纠偏 | 提供 `Reset to Ready`，不开放任意状态、轮次或时间戳编辑。 |
| 任务 | Today 内复用现有弹窗快捷新建、编辑任务及步骤。 |
| 步骤 | 两个承诺的步骤始终可处理，不要求先切换为当前任务。 |
| 当前任务 | 活动中可立即切换到另一项未完成承诺，并从操作时刻重新计算 60 分钟回顾。 |
| 提前回顾 | 管理端可显式 `Review now`，提前完成当前轮；普通 Android 回顾仍要求到期。 |

## 服务端命令

- `PUT /api/daily-plans/:localDate/review-now`
  - 仅接受 `ACTIVE`，目标必须是未完成承诺。
  - 完成轮次并按现有四轮规则进入 `ACTIVE` 或 `ENOUGH`，不检查是否到期。
- `PUT /api/daily-plans/:localDate/switch`
  - 仅接受 `ACTIVE`，目标必须是另一项未完成承诺。
  - 不增加轮次；更新当前任务和 `nextReviewAt = occurredAt + 60min`。
- `PUT /api/daily-plans/:localDate/reset`
  - 接受 `ACTIVE`、`ENOUGH`、`ENDED`。
  - 保留 `firstMove` 与主次任务，恢复 `READY`，清空执行与结束字段，轮次归零。
  - 不修改任务步骤完成记录。
- 三个命令均校验 `expectedRevision`，成功后 revision 加一；切换与重置使用现有 `DAILY_PLAN_UPDATED` 审计。

## PC Today

- 顶部提供上一天、日期输入、下一天和返回今天；Overview 与 Tasks 仍固定读取真实今天。
- 所选日期变化时同时刷新计划与该日期任务状态，加载和错误状态不沿用上一日期数据。
- Plan/Edit today 提供 `New task`；创建成功后优先填入空的主任务或次任务槽位。
- 承诺区域展开全部步骤并提供任务编辑入口；步骤完成或重开后刷新所选日期。
- `ACTIVE` 始终显示 `Review now`；存在另一项未完成承诺时显示 `Switch now`。
- `ACTIVE/ENOUGH/ENDED` 提供经确认的 `Reset to Ready`；结束仍使用独立确认。

## 实施顺序

1. 增加三个服务端命令、领域校验与 API 测试。
2. 拆出可复用任务弹窗，增加日期状态、完整承诺操作和 Web 测试。
3. 同步长期文档并删除本计划；执行类型、API、Web 和生产构建验证。
4. 迁移前后均验证远端备份，部署服务端并校验生产 API。此轮不改变 APK。

## 验收

- 普通 `/review` 提前调用仍失败，`/review-now` 可提前完成轮次。
- 即时切换不增加轮次，回顾时间从本次操作重新计算。
- 已结束计划可重置到 `READY`，revision 增加，配置保留，执行字段归零，任务步骤状态保持。
- 日期切换后读取和写入均使用所选日期，Today 不污染 Overview/Tasks 的真实今天状态。
- Today 可创建、编辑任务并处理两个承诺的任意步骤。
- 过期 revision、非承诺任务、已完成任务和非法状态命令被拒绝。
- 服务端与 Web 测试、类型检查、生产构建和 `git diff --check` 通过。
