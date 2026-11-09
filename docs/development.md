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

项目使用 Gradle Wrapper。标准 macOS 环境变量：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 源码地图

| 路径 | 修改入口 |
| --- | --- |
| `MainActivity.kt` | 普通入口退到后台、指定应用启动、`onStop` 命令提交。 |
| `ui/AppComponents.kt` | 应用页面的稳定通用组件。 |
| `ui/ToolScreens.kt` | 两个工具详情页、历史入口与应用配置列表。 |
| `res/values*/strings.xml`、`res/xml/locales_config.xml` | 默认英文、简体中文资源与系统应用语言声明；可见文案从资源读取。 |
| `update/` | 按 build type 隔离的 `dl.1662803.xyz` 更新 manifest、APK 校验缓存、`PackageInstaller` 与安装状态 Receiver。 |
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

安装到已连接设备：

```bash
./gradlew :app:installDebug
adb shell am start -n com.wswensw.woolbox/.MainActivity
```

## 验证边界

自动测试覆盖、真机场景和诊断命令统一见 [测试与设备验收](testing.md)。JVM 测试不能替代 Android 系统窗口、厂商行为或公开服务验收。

## 功能交付

功能变更按以下顺序完成：

1. 开发并运行与改动相称的本地测试。
2. 创建本地 commit；APK 内容变更已在该提交中递增版本并核对 `output-metadata.json`。
3. 发布与待测 APK 相同的通道：`pnpm run publish:debug` 或 `pnpm run publish:release`。发布脚本必须通过远端 manifest、APK `200` 与 `content-length` 校验。
4. 将构建类型、版本、manifest URL 和 APK 大小校验结果写入 README，提交发布记录。
5. 用户使用已发布 APK 完成[真机验收](testing.md#真机验收)。


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

## 真机与诊断

可执行场景见[真机验收](testing.md#真机验收)，`adb` 命令见[设备诊断](testing.md#设备诊断)。

## 故障定位

| 现象 | 优先检查 | 正确边界 |
| --- | --- | --- |
| 页面持续 `Waiting for service` | `dumpsys accessibility` 的 `Binding`/`Crashed services`，再查 crash buffer。 | 页面状态不能代替系统绑定状态。 |
| 点击工具后服务崩溃 | 栈是否指向 `ComposeOverlayWindow` 或 `WindowManager.addView`。 | 直接使用 `AccessibilityService` 的 `WindowManager` 与 currentWindowMetrics；不要从无 Display 的服务 Context 直接调用 `createWindowContext()`。 |
| Global Copy 列表持续滑动卡顿 | 挂载后的 `ComposeView.isHardwareAccelerated` 是否为 `true`。 | `ComposeOverlayWindow` 必须在 `addView` 前统一设置 `FLAG_HARDWARE_ACCELERATED`。 |
| Tile 长时间 `Preparing` | 当前 root 是否仍为 System UI，是否存在外部 `TYPE_APPLICATION` 窗口。 | 目标在面板收起后实时解析，命令两秒超时。 |
| Global Copy 没有会话 | 节点是否有效，或 `copy_history.db` 写入是否失败。 | 空快照直接结束；非空快照保存成功后才显示会话。 |
| OLED 参数未恢复 | `profiles` 是否包含包名，profile 六个字段是否完整。 | 应用 profile 不存在时读取当前默认值。 |
| 打开 Woolbox 后覆盖层关闭 | 本应用是否成为活动或焦点 `TYPE_APPLICATION` 窗口。 | 这是产品关闭规则；仅覆盖层事件不能触发关闭。 |
