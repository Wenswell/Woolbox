# Records 根页与编辑页重设计

状态：已完成
范围：Records 根页 IA/版式、三类列表卡、三类独立新建/编辑路由  
不做：改业务规则/API；Goals、Manage、快速记录 Sheet 全套视觉重做

## 目标

1. Records 根页去掉 Tasks/Trackers 双 Tab，单页展示三类内容。
2. 版式：Tasks 全宽竖列表；Accumulate / Measurement 为 section + 横向卡片轨。
3. 顺序：Tasks → Accumulate → Measurement。
4. 新建/编辑按类型拆分独立全屏路由；顶栏标题 New/Edit + 纯 icon 保存；不与列表页混用骨架。
5. 三类列表卡与对应表单视觉重设计；表单不共用带 mode 切换的单屏。

## 根页结构

```text
Records
├─ Tasks section header [+]
│  ├─ open task rows (vertical)
│  └─ completed (collapsed)
├─ Accumulate section header [Goals] [Manage] [+]
│  └─ horizontal LazyRow of accumulate cards
└─ Measurement section header [+]
   └─ horizontal LazyRow of measurement cards
```

- 连接错误/加载态仍在根页顶层展示。
- 无活跃累计/测量项时，对应轨显示空态卡 + 创建入口。
- 点击累计卡：快速记录 Sheet（保持）。
- 点击测量卡：测量详情路由（保持）。
- 测量卡上的新增：测量录入路由（保持）。

## 路由拆分

| 用途 | 路由 | 屏幕 |
| --- | --- | --- |
| 新建待办 | `task/new` | `TaskCreateScreen` |
| 编辑待办 | `task/{id}` | `TaskDetailScreen`（编辑态标题） |
| 新建累计项 | `record-item/accumulate/new` | `AccumulateItemFormScreen` |
| 编辑累计项 | `record-item/accumulate/{id}` | `AccumulateItemFormScreen` |
| 新建测量项 | `record-item/measurement/new` | `MeasurementItemFormScreen` |
| 编辑测量项 | `record-item/measurement/{id}` | `MeasurementItemFormScreen` |
| 测量详情/录入 | 现有 | 保持 |

删除根页与 Manage 上「单一 New record item + mode 切换」路径。Manage 按 `item.mode` 进入对应编辑路由。

## 表单边界

### Accumulate（固定 mode=ACCUMULATE）

- 字段：名称、描述、数值类型、默认/步进/单位、目标、推荐时段、优先级。
- 标题：`New accumulate` / `Edit accumulate`。

### Measurement（固定 mode=MEASUREMENT）

- 字段：名称、描述、默认(可选)/步进/单位、优先级；无目标与推荐时段；数值类型固定 Number。
- 标题：`New measurement` / `Edit measurement`。

### Task

- 新建：仅标题 + 保存。
- 详情：标题可编辑保存、子事项、删除；顶栏体现编辑态。

## 列表卡重设计要点

| 类型 | 主信息 | 次信息 | 主操作 |
| --- | --- | --- | --- |
| Task | 标题 | 子事项进度（若有） | 勾选；点正文进详情 |
| Accumulate | 名称 | 今日累计/目标进度/连击 | 点卡快速记录 |
| Measurement | 名称 | 最新值+单位、发生时间 | 点卡详情；+ 录入 |

横滑卡固定宽度约 `220–260dp`，高度一致，间距 `12dp`，左右与页边对齐。

## 实现顺序

1. 根页：去 Tab，三分区 + 横滑轨 + 新卡片。
2. 拆分累计/测量表单与导航路由；Manage 按 mode 跳转。
3. Task 新建/详情顶栏与表单分区对齐编辑态。
4. 同步 `product.md`、`design-system.md`、`README.md`；删除本计划或标完成并提炼长期规则。
5. 验证：单元测试 + assembleDebug + `git diff --check`；提交。

## 验收

1. 打开 Records：无双 Tab；先 Tasks 竖列表，再 Accumulate 横滑，再 Measurement 横滑。
2. 三类卡视觉可辨；勾选、记累计、看/加测量可用。
3. 三类新建/编辑为独立路由；顶栏 New/Edit；无 mode 切换控件。
4. 既有业务规则与 API 行为不变。
