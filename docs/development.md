# 开发指南

## 环境

| 项目 | 版本或路径 |
| --- | --- |
| Android Gradle Plugin | `9.2.1` |
| Kotlin | `2.3.21` |
| Compose BOM | `2026.05.01` |
| Java | Android Studio JBR `17` |
| Android SDK | `compileSdk 36.1`、`minSdk 31`、`targetSdk 36` |
| Android 数据与测试 | OkHttp `4.12.0`、Room `2.8.4`、WorkManager `2.11.2`、KSP `2.3.10`、JUnit `4.13.2` |
| Records server | Node `22+`、pnpm `11.11.0`、Fastify `5.10.0`、Drizzle ORM `0.45.2`、SQLite |

项目使用 Gradle Wrapper。标准 macOS 环境变量：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 源码地图

| 路径 | 修改入口 |
| --- | --- |
| `MainActivity.kt` | 普通入口退到后台、指定应用启动、`onStop` 命令提交和每日回顾通知入口。 |
| `ui/WoolboxApp.kt` | 三个底栏目的地、工具路由、Records Today/Tasks/Routines/Measurement、通知权限和 Sheet 接线。 |
| `ui/AppComponents.kt` | 应用页面的稳定通用组件。 |
| `ui/ToolScreens.kt` | 两个工具详情页、历史入口与应用配置列表。 |
| `res/values*/strings.xml`、`res/xml/locales_config.xml` | 默认英文、简体中文资源与系统应用语言声明；可见文案从资源读取。 |
| `record/{model,data,ui}/` | 每日承诺、任务库存、量化记录、HTTPS Repository、Keystore 凭据、ViewModel、管理页、Sheet 和表单。 |
| `record/reminder/` | 唯一 WorkManager 调度、服务端二次校验、通知权限/Channel 和回顾通知。 |
| `update/` | 按 build type 隔离的 `dl.1662803.xyz` 更新 manifest、APK 校验缓存、`PackageInstaller` 与安装状态 Receiver。 |
| `records-server/src/`、`drizzle/` | Fastify API、审计日志、服务端规则、SQLite schema 和迁移。 |
| `records-server/web/`、`deploy/` | PC 管理页（`App` shell、`views/`、`components/`、`lib/`）、systemd、备份及恢复验证。 |
| `tool/service/ToolAccessibilityService.kt` | 命令状态机、目标变化和工具生命周期。 |
| `tool/service/ForegroundWindowResolver.kt` | 外部应用窗口候选和选择顺序。 |
| `tool/service/WindowSnapshotBuilder.kt` | Global Copy 节点遍历和文本快照。 |
| `tool/model/CopyTextModels.kt`、`CopySessionState.kt` | 复制排序、选择、草稿和共享编辑状态。 |
| `tool/overlay/ComposeOverlayWindow.kt`、`CopySessionController.kt` | 系统窗口生命周期与 Global Copy 共享编辑面板。 |
| `tool/overlay/OledShieldController.kt` | OLED 遮罩、面板和实时布局。 |
| `tool/storage/CopyHistory*` | Global Copy Room 历史、摘要与完整会话映射。 |
| `tool/storage/OledShieldConfigRepository.kt` | OLED 默认配置与按包名 profile。 |
| `tool/ui/` | 历史页、工具 ViewModel、应用名称和图标解析；`AppDisplayInfo.kt` 统一提供应用展示信息。 |
| `tool/trigger/` | Tile Service、Tile 状态刷新和添加请求。 |

行为契约先查 [产品说明](product.md)，跨模块与平台契约先查 [系统架构](architecture.md)。

## 常用命令

定向编译：

```bash
./gradlew :app:compileDebugKotlin
```

提交前验证：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
git diff --check
```

Records server 定向验证：

```bash
cd records-server
node --check scripts/*.mjs
pnpm typecheck && pnpm test && pnpm test:web && pnpm build
```

部署路径、`127.0.0.1:4320` 服务、备份与恢复检查见 [`records-server/deploy/README.md`](../records-server/deploy/README.md)。

安装到已连接设备：

```bash
./gradlew :app:installDebug
adb shell am start -n com.wswensw.woolbox/.MainActivity
```

Android 12/12L 不支持应用内请求添加 Tile，详情页的 `Quick settings` 行保持禁用；在系统快捷设置编辑页手动添加两个 Tile。

## 自动测试边界

| 测试 | 覆盖内容 |
| --- | --- |
| `CopyTextSelectionTest` | 捕获顺序、阅读顺序、视觉行、NFKC/空白归一化与去重。 |
| `CopyHistoryMappingTest` | 历史摘要及文本块、选择、草稿和顺序的持久化映射。 |
| `OledShieldConfigTest` | OLED 默认值和参数归一化。 |
| `OledShieldConfigRepositoryTest` | OLED profile upsert、时间、排序、保存和删除。 |
| `ForegroundTargetSelectorTest` | 活动、焦点、窗口层级和无候选时的目标选择。 |
| `ToolCommandPolicyTest` | 同类命令匹配与不同工具切换判断。 |
| `RecordRulesTest` | 记录项规范化、累计/测量值规则、目标累计和十进制步进。 |
| `TodoModelsTest` | 待办子事项完成进度和下一动作派生。 |
| `DailyPlanTest` | 回顾到期边界与主次承诺解析。 |
| `DailyReminderSchedulerTest` | 仅活动当日计划产生 revision 绑定提醒，送达标识随 revision 或回顾时刻变化。 |
| `RecordsScreenTest` | 记录项排序、今日累计和目标跨越。 |
| `RecordsTodaySemanticsTest` | Today 无计划、待启动、回顾到期、足量和结束状态的可见语义。 |
| `RecordsOperationCoordinatorTest` | 根页刷新合并，以及写操作等待当前刷新后获取新快照的顺序。 |
| `AppUpdateClientTest` | 更新 APK 缓存的大小/SHA-256 校验，以及包名、构建类型和下载 host 限制。 |
| `records-server/test/api.test.ts` | 七表迁移、按日期的每日任务完成作用域、每日计划状态/revision、overview、审计和批量 API。 |
| `records-server/web/src/App.test.tsx` | PC 七区导航、Today 全状态操作、Once/Daily 任务、记录项、流水和审计日志。 |

JVM 测试不能验证 Android HTTPS、Keystore、窗口、无障碍服务、输入法或厂商系统行为；服务端测试也不能替代 Caddy Basic Auth、公网 HTTPS、systemd 和备份恢复验收。

## 功能交付

功能变更按以下顺序完成：

1. 开发并运行与改动相称的本地测试。
2. 创建本地 commit；APK 内容变更已在该提交中递增版本并核对 `output-metadata.json`。
3. 涉及服务端 schema 或 API 契约时，先部署服务端：

   ```bash
   cd records-server
   pnpm deploy
   ```

   对不可逆数据迁移，先按 [`records-server/deploy/README.md`](../records-server/deploy/README.md) 创建并验证一份新的远端备份。该命令执行服务端测试、构建、远端同步、服务重启、迁移 schema 和 overview 契约校验。
4. 发布与待测 APK 相同的通道：`pnpm run publish:debug` 或 `pnpm run publish:release`。发布脚本必须通过远端 manifest、APK `200` 与 `content-length` 校验。
5. 将构建类型、版本、manifest URL 和 APK 大小校验结果写入 README，提交发布记录。
6. 用户使用已发布 APK 完成真机验收。真机验收不由 JVM、服务端或发布脚本替代。

服务端契约变更必须在 APK 发布前完成第 3 步，防止客户端调用尚未上线的 API。

Git push 不属于上述交付前置条件；只在用户明确要求且已有明确 remote 时执行，不猜测或临时创建 remote。

## 版本与发布

任何 APK 内容或默认行为变更都必须递增 `app/build.gradle.kts` 的 `versionCode` 和 `versionName`，并同步 README。debug 和 release 使用独立更新通道：

```text
https://dl.1662803.xyz/d/woolbox/releases/debug/latest.json
https://dl.1662803.xyz/d/woolbox/releases/release/latest.json
```

`latest.json` 与版本化 APK 由发布工具生成。发布 debug：

```bash
pnpm run publish:debug
```

`pnpm run publish:debug` 固定使用当前用户的 `~/Library/Android/sdk` 和 Android Studio JBR `/Applications/Android Studio.app/Contents/jbr/Contents/Home`，不读取调用 shell 的 `ANDROID_HOME/JAVA_HOME`。脚本在构建前验证 JBR 与 build-tools，再运行 debug 测试、lint 和构建，验证 APK 签名，从 `output-metadata.json` 生成 `latest.json`，上传版本化 APK 与 manifest，并校验远端 manifest 和 APK `content-length`。`pnpm run publish:release` 使用同一环境执行 `assembleRelease`，且只接受已签名 APK；未配置 release 签名时必须失败，不能上传 unsigned APK。

```bash
pnpm run publish:release
```

修改发布脚本后执行：

```bash
node --check tools/create-update-manifest.mjs
node --check tools/publish-update.mjs
```

## 真机验收

涉及入口、服务、窗口或覆盖层的修改，至少执行：

1. 启用 `Woolbox tools`，确认两个详情页为 `Connected`，主操作可用。
2. 从一个包含可访问文本的目标应用进入 Woolbox，点击 `Start Global Copy`；确认目标应用恢复、会话显示且服务未断开。
3. 确认主面板左右 `12dp`、上下 `32dp` 留白；首屏为编辑页，`Reading order` 默认开启且草稿符合视觉阅读顺序。
4. 编辑两种顺序草稿并往返切换，确认修改分别保留；进入 `Select text` 验证逐项选择、全选、清空和返回后的草稿。
5. 确认 `Close`/`Back`、输入法和空白禁用；复制后检查历史新增，重开后验证 Save、Copy 和 Delete。
6. 关闭无障碍服务，在 OLED 页面点击 `Show shield`；确认遮罩留在 Woolbox 内，长按后可调整、保存默认值和关闭，且不新增 Saved apps。
7. 启用无障碍服务后返回 Woolbox，点击 `Show shield`；确认目标应用恢复、遮罩显示且服务未断开，顶部遮罩覆盖状态栏和显示刘海区域。
8. 验证长按面板、三个 Slider、拖动、`Save`、`Close`、`Hide shield`，并检查 Saved apps 的图标、参数和时间。
9. 从 Saved apps 执行 Enable/Delete，确认只对指定应用显示记录参数；再验证两个 Tile 仍作用于原目标。
10. 在 `Preparing` 和 `Active` 状态分别连续触发同类 Tile，确认命令取消或活动工具关闭，且没有重复窗口。
11. 依次快速触发两个不同 Tile，确认前一个工具关闭，最终只显示后触发的工具。
12. OLED 遮罩上连续长按，确认只显示一个控制面板；关闭面板后可再次长按打开。
13. 工具活动时切换应用、重新打开 Woolbox、关闭无障碍服务，确认覆盖层全部释放。
14. 旋转屏幕或改变显示尺寸，确认 Global Copy 内容保持可操作，OLED 遮罩和面板按比例重算。
15. 配置 Records 后确认根页顺序为 Today、Tasks、Routines、Measurement。分别新建 Once 与 Daily 任务，确认表单无日期、Manage tasks 只有 Open/Completed；每日任务同日完成后进入 Completed，下一本地日期重新 Open。PC Today 可创建和调整同一计划，Android 刷新后状态一致；主任务必选、次任务可空且不能相同，第一步完成前不出现回顾。
16. Android 13+ 拒绝通知权限时确认计划保持 `READY`，设置入口可恢复权限；授权后开始第一轮，使用 `adb shell dumpsys jobscheduler | rg "com.wswensw.woolbox|records-daily-review"` 确认只有一个调度。到期通知点击后应回到 Records Today，点击或忽略通知后刷新均不重发同一 revision；继续/切换后 revision 和下一提醒更新，第四轮、两项完成、结束今天及跨日均停止旧提醒。Android 12 直接检查系统通知开关。
17. 在 Routines 创建累计记录后确认今日值、记录 Sheet、`Undo` 与断网错误行为。创建 `Temperature` / `°C` Measurement，设置最小值 `-10`、最大值 `10`、步进 `0.5`；确认 24 小时预览、离散 Slider、当前设备时间和短时 `Undo`。
18. 在 Tools 检查 `Access` 位于工具列表之前；未授权时验证错误色图标和设置入口，授权返回后验证成功色图标，再次点击只显示已启用提示且不跳转。
19. 打开两个详情页，确认没有重复权限行，`Start Global Copy`/`Show shield` 为列表条目；Android 13 及以上点击 Quick Settings 整行请求添加且无右侧 `Add` 按钮，Android 12/12L 整行不可用并可在系统编辑页手动添加。
20. 创建一条 Global Copy 历史，确认 Copy History 行显示目标应用图标和名称、块数、时间及预览，不显示目标包名。
21. 发布 debug 通道后，在低版本 debug APK 打开 Setting；确认自动检查、下载校验、安装来源授权和系统确认符合更新规则。
22. 在 Setting → Language 依次选择 `English`、`简体中文` 和 `Follow system`，确认 Activity 重建后页面与系统 Tile 标签使用目标语言；重启应用确认选择保持。

## 设备诊断

服务绑定状态：

```bash
adb shell dumpsys accessibility \
  | rg "Bound services|Enabled services|Binding services|Crashed services|Woolbox tools"
```

应用崩溃栈：

```bash
adb logcat -d -b crash | rg -n -C 20 "com.wswensw.woolbox|FATAL EXCEPTION"
```

当前前台 Activity：

```bash
adb shell dumpsys activity activities \
  | rg "topResumedActivity|mResumedActivity"
```

页面语义树：

```bash
adb exec-out uiautomator dump /dev/tty
```

## 故障定位

| 现象 | 优先检查 | 正确边界 |
| --- | --- | --- |
| 页面持续 `Waiting for service` | `dumpsys accessibility` 的 `Binding`/`Crashed services`，再查 crash buffer。 | 页面状态不能代替系统绑定状态。 |
| 点击工具后服务崩溃 | 栈是否指向 `ComposeOverlayWindow` 或 `WindowManager.addView`。 | 直接使用 `AccessibilityService` 的 `WindowManager` 与资源指标；不要从无 Display 的服务 Context 直接调用 `createWindowContext()`。 |
| Global Copy 列表持续滑动卡顿 | 挂载后的 `ComposeView.isHardwareAccelerated` 是否为 `true`。 | `ComposeOverlayWindow` 必须在 `addView` 前统一设置 `FLAG_HARDWARE_ACCELERATED`。 |
| Tile 长时间 `Preparing` | 当前 root 是否仍为 System UI，是否存在外部 `TYPE_APPLICATION` 窗口。 | 目标在面板收起后实时解析，命令两秒超时。 |
| Global Copy 没有会话 | 节点是否有效，或 `copy_history.db` 写入是否失败。 | 空快照直接结束；非空快照保存成功后才显示会话。 |
| OLED 参数未恢复 | `profiles` 是否包含包名，profile 六个字段是否完整。 | 应用 profile 不存在时读取当前默认值。 |
| 打开 Woolbox 后覆盖层关闭 | 本应用是否成为活动或焦点 `TYPE_APPLICATION` 窗口。 | 这是产品关闭规则；仅覆盖层事件不能触发关闭。 |

## 参考旧应用

旧应用位于 `/Users/wswensw/Documents/repos/playground/app0603`。参考时同时核对业务规则和 Context、`WindowManager`、窗口类型、触发时机与生命周期；当前页面使用 Compose 重新设计。无 Display 的服务 Context 调用 `createWindowContext()` 会使 Android 14 无障碍服务崩溃，覆盖层继续直接使用服务 Context。

## 收尾

按 [文档维护](documentation.md) 同步唯一事实来源并删除失效过程文档；执行定向验证和一次提交前验证；检查差异后创建本地 commit，不自动 push。
