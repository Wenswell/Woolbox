# Records 在线化实施计划

状态：进行中。本文只指导本次迁移；功能完成后，将长期事实同步到主题文档并删除本文。

## Goal

以服务端 SQLite 取代 Android 本地 `records.db`，使 Android 与 PC 管理页访问同一数据源。首版只支持一个用户在线使用，提供日常记录和最小批量管理，不实现离线同步。

## 已确认决策

- 数据唯一事实来源是服务器；App 与网页都通过 HTTPS API 读写。
- 现有本地 Records 数据直接丢弃，不导入、不双写、不保留本地读取路径。
- PC 端：流水支持日期范围和记录项筛选、单条完整编辑、批量删除；记录项支持新建、编辑和批量归档/恢复。
- 入口使用现有 `https://gateway.1662803.xyz/apps/records/`，不新建 DNS。
- 单用户访问由 Caddy Basic Auth 保护；Android 使用同一凭据并经平台 Keystore 保存。
- 后端使用当前服务器的 Node.js + pnpm 基线、Fastify、Drizzle ORM 与 `better-sqlite3`。不使用仍处于 Release Candidate 的 `node:sqlite`。

## 边界

不实现离线写入、操作队列、冲突合并、WebSocket、账户体系、导入导出、统计看板、目标进度、提醒或通用后台。网络不可用时，读取和写入直接失败并显示可重试状态，绝不写入本地备用存储。

## 目标结构

```text
Android Records ─┐
                 ├─ HTTPS ─ Caddy Basic Auth ─ Fastify ─ SQLite
PC 管理页 ────────┘
```

- 新增 `records-server/` pnpm 项目；同一 Fastify 进程提供 `/api/*` 与构建后的网页静态资源。
- 服务器仅监听未占用的 `127.0.0.1:<port>`；Caddy 在 `/apps/records` 专属路由中移除此前缀并反代到该端口，专属路由必须在 Gateway 默认处理器之前。
- 服务数据位于 `/srv/records/records.sqlite`，不放入发布目录。部署前配置定时 SQLite 一致性备份到独立于应用发布目录的位置，并完成一次恢复验证。
- Android 删除 `RecordDatabase`、Room DAO 和本地 `RecordRepository` 写入路径；保留领域模型与页面结构，改由网络 Repository 获取和修改远端数据。

## 数据与 API 契约

服务端保留 `record_items`、`record_windows`、`record_entries` 三表和现有字段语义。整数 ID 由服务端生成；记录项与时间段集合在一个事务内保存。`occurredAt`、`localDate`、`localMinute` 由创建或编辑端一次性提交并原样持久化，历史分组不依赖服务器时区。

服务端是唯一规则执行点：名称忽略大小写唯一、正数校验、时间段校验、归档项不能新增流水、已有流水后类型和单位不可变、永久删除只接受归档项。网页和 App 可做输入提示，但不能拥有不同规则。

| 资源 | 操作 |
| --- | --- |
| `/api/items` | 查询活跃或归档项、新建、更新、批量归档或恢复、删除已归档项。 |
| `/api/entries` | 按日期范围和记录项查询、新建、单条更新、单条删除。 |
| `/api/entries/batch-delete` | 删除所选流水。 |

写操作成功后返回最新资源；客户端随后刷新当前查询。App 在进入或重新回到 Records 页面时刷新，网页在操作成功后刷新；不维持长连接。

## 实施顺序

1. **服务端数据纵切片**：创建 `records-server/`、迁移、领域校验和 Items/Entries API；以 API 测试覆盖全部数据约束与事务。
2. **PC 管理纵切片**：加入流水表格、筛选、单条编辑、选择与批量删除；加入记录项列表、表单、选择与批量归档/恢复。使用现成表格和表单组件，不制作通用 CRUD 框架。
3. **Android 替换纵切片**：接入连接凭据配置和网络 Repository，移除 Records Room 存储与旧数据；保留立即记录、推荐、今天列表、记录项编辑和 Undo 的已有交互语义。
4. **部署纵切片**：在 `/home/ilove/Documents/repos/debian-cloud-server` 更新 Caddy 示例与部署说明；创建 systemd 服务、数据目录、备份任务和 `/apps/records` 路由；通过 Caddy 校验、重载和外网 HTTPS 验证。
5. **端到端收尾**：验证网页修改、App 记录、App 删除后的跨端刷新；同步 `README.md`、`docs/product.md`、`docs/architecture.md`、`docs/design-system.md`、`docs/development.md`，删除本文。

每个纵切片独立验证并提交。任何需要离线能力、多用户、实时推送、批量改写流水字段或新增数据实体的需求，先暂停并重新确认范围。

## 验收

- 新服务器从空数据库启动，Browser 与 Android 都必须经认证访问。
- Android 新建记录后，网页刷新立即显示同一条数据；网页编辑或删除后，Android 回到 Records 即显示结果。
- PC 端可按日期范围和项目筛选，编辑一条流水，选择多条流水删除；可选择多个记录项归档或恢复。
- 服务端拒绝全部既有领域规则的非法写入；非法请求不改变任一表。
- App 断网时不产生本地 Records 数据；恢复网络后刷新只读取服务端结果。
- Caddy、systemd 服务、HTTPS 路由和 SQLite 备份恢复均在服务器上通过验证。
- Android 默认构建与定向 API/网页测试通过，Global Copy 与 OLED 行为无回归。
