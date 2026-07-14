# Template Android App

## 目标

这是一个 Android Compose 工具应用，用真实系统场景沉淀页面布局、底栏结构和通用组件。首轮提供两项无障碍服务能力：

| 工具 | 用途 | 外部入口 |
| --- | --- | --- |
| `Global Copy` | 选择当前应用中的可访问文本，以捕获顺序、阅读顺序或编辑后的文本复制到剪贴板 | `Global Copy` Quick Settings Tile |
| `OLED shield` | 在当前应用上显示黑色触摸遮罩，调整并保存尺寸、透明度与控制面板位置 | `OLED shield` Quick Settings Tile |

工具页面负责授权、状态、默认配置与系统 Tile 添加请求。跨应用动作由 Quick Settings Tile 发起，目标应用保持在前台上下文中。

## 当前状态

| 项目 | 当前值 |
| --- | --- |
| App 名 | `Template` |
| 包名 | `com.wswensw.template` |
| 技术栈 | Kotlin + Jetpack Compose + Material 3 |
| 底栏 | `Home`、`Setting` |
| 子页面 | `Global Copy`、`OLED shield` |
| 系统能力 | AccessibilityService、`TYPE_ACCESSIBILITY_OVERLAY`、Quick Settings Tile |
| `compileSdk` | `release(36) + minorApiLevel = 1` |
| `minSdk` | `release(34)` |
| `targetSdk` | `release(36)` |
| `versionName` | `0.1.0` |

Android 14 作为最低版本，使应用可以直接使用系统 Tile 添加请求和 `PendingIntent` Tile 跳转 API。

## 文档

- [Global Copy 与 OLED 迁移计划](docs/global-copy-oled-migration.md)：范围、架构、状态和验收。
- [Global Copy 与 OLED 覆盖层触发修复计划](docs/global-copy-oled-overlay-fix-plan.md)：Quick Settings 收起后的目标窗口解析与验收。
- [Global Copy 与 OLED 使用指南](docs/global-copy-oled-usage.md)：授权、Tile、复制、遮罩和配置操作。
- [设计系统](docs/design-system.md)：页面结构、组件职责和交互规则。
- [开发记录](docs/development.md)：系统能力边界、依赖和验证规则。

## 开发

构建 debug APK：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

执行领域单元测试：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

检查 diff 空白：

```bash
git diff --check
```

安装到已连接设备：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:installDebug
```
