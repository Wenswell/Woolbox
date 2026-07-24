# Records 根页密度与任务 horizon

状态：已落地  
范围：任务 TODAY/LATER、根页任务预览条数可配、累计/测量 2 列 grid、进度色深色适配  
非目标：完整 due 日历、提醒、周桶、XP

## 已确认

| 项 | 选择 |
| --- | --- |
| 任务分组 | `TODAY` / `LATER`（当天 / 长期） |
| 累计·测量根页 | 2 列 grid |
| 根页任务条数 | 默认 5，Setting 可配 3 / 5 / 10 / All |
| 颜色 | 深色下文本/底可读；进行中 primary 加深，完成固定绿 |

## 数据

- `todo_tasks.horizon`：`TODAY` \| `LATER`，非空；迁移默认 `TODAY`
- 创建/更新任务 body 含 `horizon`
- 预览条数：App 本地 `records_ui` prefs，不进服务端

## 根页

1. Tasks：`Today` 展开（最多 N 条紧凑卡）；`Later` 默认折叠（有未完成时显示数量）；Completed 仍折叠
2. 超出 N：文案链到 Manage
3. Accumulate / Measurement：2 列等宽卡（行内 chunk），不再横滑大卡
4. 新建默认 `TODAY`；Edit 可改 horizon

## 颜色

- 进行中：surface 系底 + primary 洗色；深色降低 wash、onContainer 用 `onSurface`
- DONE：固定绿，深/浅两套，对比度可读
- 不用 secondary/tertiary 做进度

## 验收

- 新建 TODAY 出现在 Today；改为 LATER 进 Later
- 未完成 Today > N 时只显示 N + Manage 入口
- Setting 改条数即时影响根页
- 累计/测量 ≥2 时同一屏可见 2 张
- 深色：卡内标题与主值可读，完成绿与 LATE 可分

## 落点

- 服务端：`schema` / `0008` / `domain` / `records-service` / 测试
- App：`TodoModels` / Repository / Create·Detail / RecordsScreen / Setting / RecordIcons
- 文档：`product` / `design-system` / `architecture`
