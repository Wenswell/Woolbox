# Global Copy 与 OLED 覆盖层触发修复计划

## 状态

计划确认，待实施。

## 问题

两个 Quick Settings Tile 点击后都没有进入覆盖层。当前服务在收起 Quick Settings 之前调用 `ForegroundWindowResolver.resolve()`，并把此时解析到的包名写入待执行命令。点击发生在 Quick Settings 已经显示之后，此时前台窗口可能仍是 System UI，导致命令在覆盖层创建前被丢弃或超时。

## 决策

### 第一性原理

工具必须作用于 Quick Settings 收起后实际恢复前台的外部应用。目标应用应由执行时的无障碍窗口状态确定，而不是由 Quick Settings 打开前的历史状态推断。

### 有界贝叶斯判断

两个功能在同一个覆盖层创建前分支同时失败，优先修复共享的 Tile 命令状态机和窗口解析顺序；暂不修改复制文本提取、OLED 配置和 Compose 覆盖层布局。

### 奥卡姆剃刀

待执行命令只保存工具命令和超时时间。Tile 点击后收起 Quick Settings，按固定间隔轮询非 System UI 的 `TYPE_APPLICATION` 窗口；解析成功后立即执行对应工具。

## 实施范围

1. 移除点击时的目标包名预解析，以及待执行命令中的包名匹配条件。
2. 保留 `Preparing` 状态、面板收起动作、轮询间隔和超时机制。
3. 将 System UI 判断收窄为当前活动根窗口，避免普通 System UI 窗口阻塞命令完成。
4. 轮询得到外部应用后：Global Copy 构建该应用的文本快照；OLED shield 按该包名加载配置并显示遮罩。
5. 同步迁移计划、开发记录和使用指南中的触发流程说明。
6. 增加或调整纯 JVM 可验证的窗口选择/状态规则测试；不引入设备测试替代品。

## 非目标

- 不保存 Quick Settings 打开前的前台包名。
- 不修改 Global Copy 文本排序、去重和复制交互。
- 不修改 OLED 参数、面板长按交互和配置存储格式。
- 不增加日志、通知或备用触发路径。
- 不处理无障碍服务未连接时的其他产品流程。

## 验收标准

- 从任意外部应用打开 Quick Settings，点击 Global Copy 后，面板收起并进入复制会话。
- 从任意外部应用打开 Quick Settings，点击 OLED shield 后，面板收起并显示黑色遮罩。
- Quick Settings 尚未完全收起时，命令保持 `Preparing`，不提前创建覆盖层。
- 未解析到外部应用时，命令按现有超时规则结束，不创建错误覆盖层。
- 已有覆盖层时再次点击同一 Tile，仍按现有逻辑关闭工具。
- `:app:testDebugUnitTest`、`:app:assembleDebug` 和 `git diff --check` 成功。

