# 测试与设备验收

## 自动测试边界

| 测试 | 覆盖内容 |
| --- | --- |
| `CopyTextSelectionTest` | 捕获/阅读顺序、视觉行、归一化与去重。 |
| `CopyHistoryMappingTest` | 历史摘要、文本块、选择、草稿和顺序映射。 |
| `OledShieldConfigTest` / `OledShieldConfigRepositoryTest` | OLED 参数归一化与 profile 存储。 |
| `ForegroundTargetSelectorTest` / `ToolCommandPolicyTest` | 目标窗口选择和工具切换。 |
| `RecordRulesTest` | 记录项、累计/测量值、目标与十进制步进。 |
| `TodayTaskTest` / `DailyFocusTest` | 任务步骤、当前 Focus、未完成选择与回顾边界。 |
| `DailyReminderSchedulerTest` | 活动当日 Focus 的 revision 绑定提醒。 |
| `RecordsScreenTest` / `RecordsOperationCoordinatorTest` | 排序、累计、目标跨越和读写串行。 |
| `AppUpdateClientTest` | APK 大小/SHA-256、包名、构建类型和 host 限制。 |
| `records-server/test/api.test.ts` | 迁移、Today task 快照/续做、Focus 状态、revision、overview 与审计。 |
| `records-server/web/src/App.test.tsx` | PC 导航、Today 日期、Tasks/Focus、模板与记录管理。 |

JVM 测试不能验证 Android HTTPS、Keystore、窗口、无障碍服务、输入法或厂商系统行为；服务端测试不能替代公网 HTTPS、systemd 和备份恢复验收。

公开服务发布、健康检查与备份恢复步骤见 [`records-server/deploy/README.md`](../records-server/deploy/README.md)。

## 真机验收

1. Tools 的 `Access` 未授权时显示设置入口，授权后显示成功状态；再次点击只提示已启用。两个详情页没有重复权限行。
2. Android 13+ 从详情页请求添加 Tile；Android 12/12L 的入口禁用，并从系统快捷设置编辑页手动添加。
3. 从含可访问文本的应用启动 Global Copy，确认目标应用恢复、编辑页显示且服务未断开。
4. 验证主面板留白、Reading order、两种草稿、Select text、输入法、Copy、历史 Save/Delete。
5. Copy History 行显示目标应用名称和图标，不显示包名；应用不可用时显示通用状态。
6. 关闭无障碍服务启动 OLED，确认仅覆盖 Woolbox、可调整和关闭且不新增 Saved apps。
7. 启用服务后启动 OLED，确认恢复目标应用并覆盖状态栏；验证 Slider、拖动、Save、Close、Hide 和 Saved apps。
8. 连续长按 OLED 只保留一个控制面板；关闭后可再次长按打开。
9. 在 `Preparing/Active` 连续触发同类及不同 Tile，确认取消、关闭和最终工具唯一。
10. 切换应用、重新打开 Woolbox、关闭服务或旋转屏幕，确认窗口释放和布局重算。
11. 配置 Records 凭据并加载远端数据；断网时不写本地记录，已有快照保留并显示重试，首次失败显示连接状态。
12. Records 无 Focus 时新增标题/清单任务，验证部分完成、整体完成、重开和完成时间。
13. 手动采用 Daily 建议和昨日未完成候选，确认不自动创建、只复制未完成步骤且昨日来源不可重复加入。
14. 创建 1+1 Focus；次任务在 `READY` 中完成后不得开始，必须重新选择。
15. Android 13+ 拒绝通知权限时保持 `READY`；授权后开始并确认只有一个 `records-daily-review` 调度。Android 12 检查系统通知开关。
16. 到期通知返回 Records；继续或切换后 revision 与提醒更新。第四次回顾显示“今天已足够”，普通任务仍可操作。
17. 首次回顾前完成全部所选 Focus 时进入 `FOCUS_CLEARED`，不显示“已完成 0 轮”；重开任务不自动恢复。
18. Focus 暂停或结束后仍可新增、完成和重开任务；跨日停止旧提醒。
19. 编辑模板后既有任务快照不变；模板允许零步骤且没有完成入口。
20. Accumulate 验证今日值、记录 Sheet 和 Undo；Measurement 验证范围、离散 Slider、24 小时图与 Undo。
21. 发布后由低版本同通道 APK 验证检查、下载校验、安装来源授权和系统确认。
22. 依次切换 English、简体中文、Follow system，确认 Activity、覆盖层和 Tile 文案与持久化。

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
