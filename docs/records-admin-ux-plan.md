# Records PC 管理端 UX 优化

状态：已完成  
范围：`records-server/web` UI/UX 与信息架构；不改 API 与业务规则。

## 目标

把现有三页签管理页提升为常见后台数据管理范式：清晰壳层、可扫读表格、可预期筛选、明确批量操作、可读审计。

## 不改

- Entries 仍只读 + 批量删除
- Record items 仍新建/编辑/归档/恢复
- Logs 仍只读 + 游标加载更早记录
- 不增加待办管理、流水编辑、导出、图表

## 设计范式

| 模式 | 采用方式 |
| --- | --- |
| App shell | 顶栏标题/刷新 + 分段页签 + 主内容区 |
| Filter bar | Entries 日期预设 + 起止日期 + 记录项；Items 状态分段 |
| Selection bar | 有选中时显示计数与批量动作 |
| Data table | 紧凑表头、行悬停、选中高亮、状态/模式徽章 |
| Dialog | 表单与危险确认走模态层，支持 Esc/遮罩关闭 |
| Feedback | 全局错误条、操作成功 toast、明确空态与加载态 |
| Form sections | 记录项按 Mode 显示字段分组 |

## 实现步骤

1. 建立 CSS token 与壳层样式（间距、字号、色板、表密度、焦点环）。
2. 拆分 `App.tsx` 为 shell / views / components / format 工具。
3. Entries：Today / 7d / 30d 预设、筛选即时生效、结果计数、删除确认。
4. Items：Active/Archived/All 过滤、模式徽章、表单分区、归档确认。
5. Logs：动作徽章、变更摘要可读化、加载更多。
6. 更新 `App.test.tsx`、`docs/design-system.md` PC 管理页描述。

## 验收

- 三个页签功能与现有 API 契约一致。
- Entries 筛选变更后请求带正确 `startDate/endDate/itemId`。
- 删除流水与归档记录项使用应用内确认，不再依赖 `window.confirm`。
- 空态、加载态、错误重试可观察。
- `pnpm test:web`、`pnpm typecheck`、`pnpm build` 通过。
- 部署后 `https://gateway.1662803.xyz/apps/records/` 可见新壳层与交互。
