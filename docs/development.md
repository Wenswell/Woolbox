# 开发指南

## 环境

| 项目 | 版本或路径 |
| --- | --- |
| Android Gradle Plugin | `9.2.1` |
| Kotlin | `2.3.21` |
| Compose BOM | `2026.05.01` |
| Java | Android Studio JBR `17` |
| Android SDK | `compileSdk 36.1`、`minSdk 31`、`targetSdk 36` |
| Android 数据与测试 | OkHttp `4.12.0`、Room `2.8.4`、KSP `2.3.10`、JUnit `4.13.2` |
| Records server | Node `22+`、pnpm `11.11.0`、Fastify `5.10.0`、Drizzle ORM `0.45.2`、SQLite |

项目使用 Gradle Wrapper。标准 macOS 环境变量：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 源码地图

| 路径 | 修改入口 |
| --- | --- |
| `MainActivity.kt` | 普通入口退到后台、指定应用启动和 `onStop` 命令提交。 |
| `ui/WoolboxApp.kt` | 三个底栏目的地、工具路由、Records 三分区、管理页和 Sheet 接线。 |
| `ui/AppComponents.kt` | 应用页面的稳定通用组件。 |
| `ui/ToolScreens.kt` | 两个工具详情页、历史入口与应用配置列表。 |
| `record/{model,data,ui}/` | 累计/测量记录和待办规则、HTTPS Repository、Keystore 凭据、ViewModel、任务/测量预览 Sheet、管理页、记录 Sheet 和记录项表单。 |
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
| `RecordRulesTest` | 记录项规范化、累计/测量值规则、推荐排序、跨午夜时间段和十进制步进。 |
| `TodoModelsTest` | 待办标题、完成进度和计划日期分组派生。 |
| `RecordFocusTest` | Focus 理由排序、任务计划日期、跨午夜子事项时段和当日环统计。 |
| `RecordsScreenTest` | Records 首字符与标题裁剪、待办排序、管理列表模式隔离、目标周期分组、进度边界、今日累计和下一个子事项。 |
| `RecordsOperationCoordinatorTest` | 根页刷新合并，以及写操作等待当前刷新后获取新快照的顺序。 |
| `AppUpdateClientTest` | 更新 APK 缓存的大小/SHA-256 校验，以及包名、构建类型和下载 host 限制。 |
| `records-server/test/api.test.ts` | 空库迁移、累计/测量规则、流水创建/删除、待办日期与子事项时段约束、审计快照、含多个 Measurement 最新值的根页 overview、模式筛选和批量 API。 |
| `records-server/web/src/App.test.tsx` | 管理页即时筛选、删除确认、审计日志、记录项归档/恢复。 |

JVM 测试不能验证 Android HTTPS、Keystore、窗口、无障碍服务、输入法或厂商系统行为；服务端测试也不能替代 Caddy Basic Auth、公网 HTTPS、systemd 和备份恢复验收。

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

`pnpm run publish:debug` 一次运行 debug 测试、lint 和构建，验证 APK 签名，从 `output-metadata.json` 生成 `latest.json`，上传版本化 APK 与 manifest，并校验远端 manifest 和 APK `content-length`。`pnpm run publish:release` 会执行 `assembleRelease`，且只接受已签名 APK；未配置 release 签名时必须失败，不能上传 unsigned APK。

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
15. 配置 Records 凭据时确认用户名键盘 `Next` 聚焦密码，密码使用 Password 键盘且 `Done` 可连接。默认进入 Records 根页 Tasks，创建标题和至少两个子事项并用箭头调整顺序；依次在进度 Sheet 勾选、重开子事项，确认完成时间、父项状态和已完成折叠区正确。进入 Manage tasks / Edit task，确认 Today、日期选择、Later、Overdue 分组和日期副文案；无日期任务不能编辑 Suggested time，移除所有时段后才能改 Later。创建今日跨午夜时段，次日结束分钟前仍出现在 Focus，结束后不再出现；刷新后服务端顺序一致。
16. 在同一 Records 根页创建累计记录后确认液面、日目标饼或7 日目标分段条、快速记录、`Undo` 与断网错误行为；断网前启动刷新并立即记录一次，确认服务端写入成功不会被报告为写入失败，恢复网络后可刷新到新快照。创建 `Temperature` / `°C` Measurement，设置最小值 `-10`、最大值 `10`、步进 `0.5`。快速切换两个 Measurement，确认预览不被前一个请求覆盖；点卡确认预览 Sheet 显示 24 小时原始点位和流水，Slider 只能选择配置步进，录入后值和当前设备时间更新，短时 `Undo` 可删除该条流水；检查浅色与深色的可读性。
17. 在 Tools 检查 `Access` 位于工具列表之前；未授权时验证错误色图标和设置入口，授权返回后验证成功色图标，再次点击只显示已启用提示且不跳转。
18. 打开两个详情页，确认没有重复权限行，`Start Global Copy`/`Show shield` 为列表条目；Android 13 及以上点击 Quick Settings 整行请求添加且无右侧 `Add` 按钮，Android 12/12L 整行不可用并可在系统编辑页手动添加。
19. 创建一条 Global Copy 历史，确认 Copy History 行显示目标应用图标和名称、块数、时间及预览，不显示目标包名。
20. 发布 debug 通道后，在低版本 debug APK 打开 Setting；确认自动检查显示远端版本。确认下载 Dialog 的版本和大小；下载完成后断网重试安装仍复用并校验缓存。首次安装打开“安装未知应用”授权，返回后再次选择安装，确认 Android 安装器要求用户确认；篡改缓存 APK 后确认安装前会重新下载，非同包名、非同 build type 或非 `dl.1662803.xyz` manifest 均被拒绝。

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
