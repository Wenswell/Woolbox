# 测试与设备验收

## 自动测试边界

| 测试 | 覆盖内容 |
| --- | --- |
| `CopyTextSelectionTest` | 捕获/阅读顺序、视觉行、归一化与去重。 |
| `CopyHistoryMappingTest` | 历史摘要、文本块、选择、草稿和顺序映射。 |
| `OledShieldConfigTest` / `OledShieldConfigRepositoryTest` | OLED 参数归一化与 profile 存储。 |
| `ForegroundTargetSelectorTest` / `ToolCommandPolicyTest` | 目标窗口选择和工具切换。 |
| `RecordRulesTest` / `GoalModelsTest` | 记录项、累计/测量值、周期目标、共享进度目标校验及完成/到期状态。 |
| `TodayTaskTest` | 任务步骤、完成派生和重开边界。 |
| `DailyCheckinTest` | 起床后下一整点、当前小时打卡和不补记边界。 |
| `RecordsScreenTest` / `RecordsOperationCoordinatorTest` | 排序、累计、目标跨越和读写串行。 |
| `AppUpdateClientTest` | APK 大小/SHA-256、包名、构建类型和 host 限制。 |
| `records-server/test/api.test.ts` | Goal 生命周期与定义锁定、迁移、Today task 快照/续做、起床/整点打卡、overview 与审计。 |
| `records-server/web/src/App.test.tsx` | PC 导航、Today 日期、Tasks/打卡、Goal 管理与审计、模板及记录管理。 |

JVM 测试不能验证 Android HTTPS、Keystore、窗口、无障碍服务、输入法或厂商系统行为；服务端测试不能替代公网 HTTPS、systemd 和备份恢复验收。

公开服务发布、健康检查与备份恢复步骤见 [`records-server/deploy/README.md`](../records-server/deploy/README.md)。

## 真机验收

1. Tools 的 `Access` 中无障碍服务和电池优化未授权时显示错误色并可打开对应系统设置；分别授权后返回应用显示成功状态，再次点击只提示已授权。两个详情页没有重复权限行。
2. 电池优化系统页确认目标包名为 Woolbox；拒绝或关闭后状态保持未授权，再次点击可重新发起请求。
3. Android 13+ 从详情页请求添加 Tile；Android 12/12L 的入口禁用，并从系统快捷设置编辑页手动添加。
4. 从含可访问文本的应用启动 Global Copy，确认目标应用恢复、编辑页显示且服务未断开。
5. 验证主面板留白、Reading order、两种草稿、Select text、输入法、Copy、历史 Save/Delete。
6. Copy History 行显示目标应用名称和图标，不显示包名；应用不可用时显示通用状态。
7. 关闭无障碍服务启动 OLED，确认仅覆盖 Woolbox、可调整和关闭且不新增 Saved apps。
8. 启用服务后启动 OLED，确认恢复目标应用并覆盖状态栏；验证 Slider、拖动、Save、Close、Hide 和 Saved apps。
9. 连续长按 OLED 只保留一个控制面板；关闭后可再次长按打开。
10. 在 `Preparing/Active` 连续触发同类及不同 Tile，确认取消、关闭和最终工具唯一。
11. 切换应用、重新打开 Woolbox、关闭服务或旋转屏幕，确认窗口释放和布局重算。
12. 配置 Records 凭据并加载远端数据；断网时不写本地记录，已有快照保留并显示重试，首次失败显示连接状态。
13. Records 新增标题/清单任务，验证部分完成、整体完成、重开和完成时间；任务详情和打卡 Sheet 都可修改全部子任务。
14. 手动采用 Daily 建议和昨日未完成候选，确认不自动创建、只复制未完成步骤且昨日来源不可重复加入。
15. Android 13+ 授予通知权限后，起床标记从下一整点建立一个 `records-hourly-checkin` 调度；通知和卡片均打开当前小时打卡 Sheet。
16. 打卡不改任务仍可成功；同一小时不能再次打卡，不补记错过小时，跨日后旧调度和通知消失。
17. 编辑模板后既有任务快照不变；模板和 Today task 都要求至少一个子任务，父任务没有完成入口。
18. Goals 与 Accumulate 主页行验证两色斑马纹、`当前值/目标值 unit`、目标周期累计和无目标 `当前值/-- unit`；再验证 Accumulate 记录 Sheet 与 Undo，以及 Measurement 范围、离散 Slider、24 小时图与 Undo。
19. PC Goals 验证 Active/Archived/All、搜索、新建/编辑、步进录入、子目标确认、再次挑战、归档/恢复/删除，以及 Activity 的三类 Goal 筛选和摘要。
20. 发布后由低版本同通道 APK 验证检查、下载校验、安装来源授权和系统确认。
21. 依次切换 English、简体中文、Follow system，确认 Activity、覆盖层和 Tile 文案与持久化。

## 设备诊断

```bash
adb shell dumpsys accessibility \
  | rg "Bound services|Enabled services|Binding services|Crashed services|Woolbox tools"

adb logcat -d -b crash \
  | rg -n -C 20 "com.wswensw.woolbox|FATAL EXCEPTION"

adb shell dumpsys activity activities \
  | rg "topResumedActivity|mResumedActivity"

adb exec-out uiautomator dump /dev/tty
```
