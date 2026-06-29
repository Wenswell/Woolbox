# Template Android App

## 目标

做一个轻量 Android Compose 模板应用，用来存放偏好的样式、页面外壳和基础组件。

当前初版只有两个底栏 Tab：

| Tab | 用途 |
| --- | --- |
| `Home` | 展示模板首页、内容卡片、分组列表、指标行和扩展入口 |
| `Setting` | 展示设置页结构、分组列表、开关行和关于信息 |

参考应用是 `/Users/wswensw/Documents/repos/playground/app0603`。本模板保留旧应用的 Compose Material 3 页面外壳、底栏、页面标题、分组卡片、整行列表项和动态色习惯。QR、Global Copy、OLED、上传、更新、无障碍服务等业务功能已移除。

## 当前状态

| 项目 | 当前值 |
| --- | --- |
| App 名 | `Template` |
| 包名 | `com.wswensw.template` |
| 技术栈 | Kotlin + Jetpack Compose + Material 3 |
| 底栏 | `Home`、`Setting` |
| AGP | `9.2.1` |
| Gradle | `9.4.1` |
| Kotlin | `2.3.21` |
| Compose BOM | `2026.05.01` |
| `compileSdk` | `release(36) + minorApiLevel = 1` |
| `minSdk` | `release(31)` |
| `targetSdk` | `release(36)` |
| `versionCode` | `1` |
| `versionName` | `0.1.0` |

## 组件

初版沉淀以下通用组件：

- `TemplateTheme`：Material 3 主题，优先使用动态色，提供固定色作为基础配置。
- `TemplateSurface`：应用级 `Scaffold`，承载底栏和主内容。
- `TemplatePage`：可滚动页面外壳，包含标题、副标题和页面间距。
- `TemplateSection`：设置页风格分组标题和低色调卡片。
- `TemplateCard`：页面主状态卡片。
- `TemplateListItem`：图标、标题、副标题、可选尾部控件和可选整行点击。
- `TemplateMetricRow`：左右对齐的简洁指标行。
- `TemplateOutlineCard`：用于说明或扩展入口的描边卡片。

组件规范详见 [docs/design-system.md](docs/design-system.md)。

## 开发

构建 debug APK：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

检查 diff 空白：

```bash
git diff --check
```

安装到已连接设备：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:installDebug
```

## 后续方向

- 增加更多模板组件：子页面顶部栏、空状态、确认弹窗、操作菜单、主按钮。
- 为核心组件补 Compose Preview。
- 在有明确需求后再引入导航、持久化和业务依赖。
