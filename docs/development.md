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
| `ui/TemplateApp.kt` | 三个底栏目的地、工具路由、Records Goals/Sheet/Snackbar 及页面接线。 |
| `ui/TemplateComponents.kt` | 应用页面的稳定通用组件。 |
| `ui/ToolScreens.kt` | 两个工具详情页、历史入口与应用配置列表。 |
| `record/{model,data,ui}/` | 记录规则、HTTPS Repository、Keystore 凭据、ViewModel、焦点与目标页面、记录 Sheet 和记录项表单。 |
| `records-server/src/`、`drizzle/` | Fastify API、审计日志、服务端规则、SQLite schema 和迁移。 |
| `records-server/web/`、`deploy/` | PC 管理页、systemd、备份及恢复验证。 |
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
adb shell am start -n com.wswensw.template/.MainActivity
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
| `RecordRulesTest` | 记录项规范化与正数校验、推荐排序、跨午夜时间段和十进制步进。 |
| `RecordsScreenTest` | Records 首字符与标题裁剪、焦点全量排序、目标周期分组、进度边界、今日累计和日期文案。 |
| `records-server/test/api.test.ts` | 空库迁移、服务端数据规则、审计快照、事务、筛选和批量 API。 |
| `records-server/web/src/App.test.tsx` | 管理页筛选、只读流水、审计日志和记录项归档/恢复。 |

JVM 测试不能验证 Android HTTPS、Keystore、窗口、无障碍服务、输入法或厂商系统行为；服务端测试也不能替代 Caddy Basic Auth、公网 HTTPS、systemd 和备份恢复验收。

## 真机验收

涉及入口、服务、窗口或覆盖层的修改，至少执行：

1. 启用 `Template tools`，确认两个详情页为 `Connected`，主操作可用。
2. 从一个包含可访问文本的目标应用进入 Template，点击 `Start Global Copy`；确认目标应用恢复、会话显示且服务未断开。
3. 确认主面板左右 `12dp`、上下 `32dp` 留白；首屏为编辑页，`Reading order` 默认开启且草稿符合视觉阅读顺序。
4. 编辑两种顺序草稿并往返切换，确认修改分别保留；进入 `Select text` 验证逐项选择、全选、清空和返回后的草稿。
5. 确认 `Close`/`Back`、输入法和空白禁用；复制后检查历史新增，重开后验证 Save、Copy 和 Delete。
6. 关闭无障碍服务，在 OLED 页面点击 `Show shield`；确认遮罩留在 Template 内，长按后可调整、保存默认值和关闭，且不新增 Saved apps。
7. 启用无障碍服务后返回 Template，点击 `Show shield`；确认目标应用恢复、遮罩显示且服务未断开，顶部遮罩覆盖状态栏和显示刘海区域。
8. 验证长按面板、三个 Slider、拖动、`Save`、`Close`、`Hide shield`，并检查 Saved apps 的图标、参数和时间。
9. 从 Saved apps 执行 Enable/Delete，确认只对指定应用显示记录参数；再验证两个 Tile 仍作用于原目标。
10. 在 `Preparing` 和 `Active` 状态分别连续触发同类 Tile，确认命令取消或活动工具关闭，且没有重复窗口。
11. 依次快速触发两个不同 Tile，确认前一个工具关闭，最终只显示后触发的工具。
12. OLED 遮罩上连续长按，确认只显示一个控制面板；关闭面板后可再次长按打开。
13. 工具活动时切换应用、重新打开 Template、关闭无障碍服务，确认覆盖层全部释放。
14. 旋转屏幕或改变显示尺寸，确认 Global Copy 内容保持可操作，OLED 遮罩和面板按比例重算。
15. 配置 Records 凭据时确认用户名键盘 `Next` 聚焦密码，密码使用 Password 键盘且 `Done` 可连接；准备至少一个日目标、一个周目标和四个有目标记录项，确认主页横向显示全部焦点目标并露出下一项，Goals 页面按 Daily/Weekly 分组，日目标使用圆形进度、周目标使用横向进度。确认快速记录卡片无灰色填充且上下留白一致；点击项目后 Sheet 使用首字符图标和裁剪名称，目标信息只出现一次，数值键盘 `Done` 与 Record 按钮执行相同提交。记录正数值后确认 Sheet 关闭、产生一次确认触感且当前页面短时显示 `Undo`，点击后只删除刚创建的流水；确认周目标使用本周累计、Sheet 的今日流水无删除按钮；进入任一记录项编辑页，确认首帧类型和单位已禁用，Priority 可保存负整数；输入法保持展开时依次聚焦表单顶部和底部输入框，确认页面自动滚动并将当前输入框完整显示在输入法上方，且页面末尾仍可手动滚动访问；断网失败后 Sheet 保持打开并显示错误，服务端数据不变，恢复网络后重新记录成功。
16. 在 Home 检查 `Access` 位于工具列表之前；未授权时验证错误色图标和设置入口，授权返回后验证成功色图标，再次点击只显示已启用提示且不跳转。
17. 打开两个详情页，确认没有重复权限行，`Start Global Copy`/`Show shield` 为列表条目；Android 13 及以上点击 Quick Settings 整行请求添加且无右侧 `Add` 按钮，Android 12/12L 整行不可用并可在系统编辑页手动添加。
18. 创建一条 Global Copy 历史，确认 Copy History 行显示目标应用图标和名称、块数、时间及预览，不显示目标包名。

## 设备诊断

服务绑定状态：

```bash
adb shell dumpsys accessibility \
  | rg "Bound services|Enabled services|Binding services|Crashed services|Template tools"
```

应用崩溃栈：

```bash
adb logcat -d -b crash | rg -n -C 20 "com.wswensw.template|FATAL EXCEPTION"
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
| 打开 Template 后覆盖层关闭 | 本应用是否成为活动或焦点 `TYPE_APPLICATION` 窗口。 | 这是产品关闭规则；仅覆盖层事件不能触发关闭。 |
| Records 返回 `iconKey is invalid` | 运行中的服务端是否仍为删除固定图标前的版本。 | 部署当前 `records-server` 并执行 `0003_drop_record_item_icons.sql`；客户端不恢复旧字段。 |

## 参考旧应用

旧应用位于 `/Users/wswensw/Documents/repos/playground/app0603`。参考时同时核对业务规则和 Context、`WindowManager`、窗口类型、触发时机与生命周期；当前页面使用 Compose 重新设计。无 Display 的服务 Context 调用 `createWindowContext()` 会使 Android 14 无障碍服务崩溃，覆盖层继续直接使用服务 Context。

## 收尾

按 [文档维护](documentation.md) 同步唯一事实来源并删除失效过程文档；执行定向验证和一次提交前验证；检查差异后创建本地 commit，不自动 push。
