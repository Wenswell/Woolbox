# Template

Android Compose 工具应用，用 `Global Copy` 与 `OLED shield` 两个真实场景沉淀页面结构、通用组件和无障碍覆盖层能力。

## 当前能力

| 工具 | 用途 | 启动方式 |
| --- | --- | --- |
| `Global Copy` | 读取目标应用当前窗口中的可访问文本，默认生成可编辑的视觉阅读顺序草稿，也可调整文本选择或切换为捕获顺序。 | 详情页 `Start Global Copy`；Quick Settings Tile。 |
| `OLED shield` | 在目标应用上方显示可触摸黑色遮罩，并按应用保存尺寸、透明度和面板位置。 | 详情页 `Show shield`；Quick Settings Tile。 |

两项工具共用 `Template tools` 无障碍服务。页面入口会先把 Template 移到后台，再对恢复的应用执行工具；Tile 用于在目标应用内快速启动。

## 使用前提

- Android `14` 或更高版本。
- 在系统无障碍设置中启用 `Template tools`。
- Global Copy 只能读取目标应用通过无障碍节点公开的文本。
- 两项工具使用 `TYPE_ACCESSIBILITY_OVERLAY`，无需悬浮窗权限。

## 快速使用

1. 打开 Template，在首页选择 `Accessibility service` 并启用服务。
2. 进入 `Global Copy` 或 `OLED shield` 详情页。
3. 直接选择页面顶部的主操作；Template 进入后台后，工具作用于恢复的应用。
4. 如需快捷入口，在详情页选择 Tile 的 `Add`，以后可从目标应用的 Quick Settings 启动。

完整业务规则、状态语义和操作流程见 [产品说明](docs/product.md)。

## 工程基线

| 项目 | 当前值 |
| --- | --- |
| App 名 | `Template` |
| 包名 | `com.wswensw.template` |
| 技术栈 | Kotlin、Jetpack Compose、Material 3 |
| 导航 | `Home`、`Setting` 底栏；两个工具详情页 |
| 系统能力 | `AccessibilityService`、`TYPE_ACCESSIBILITY_OVERLAY`、Quick Settings Tile |
| `compileSdk` | Android `36.1` |
| `minSdk` | Android `34` |
| `targetSdk` | Android `36` |
| JVM | Java `17` |
| 版本 | `0.1.0` (`versionCode 1`) |

## 目录

```text
app/src/main/java/com/wswensw/template/
├─ ui/                 应用页面、主题和模板组件
└─ tool/
   ├─ model/           文本与 OLED 领域模型
   ├─ overlay/         Compose 无障碍覆盖层
   ├─ service/         无障碍服务、目标解析和状态
   ├─ storage/         OLED 配置持久化
   ├─ trigger/         Quick Settings Tile 与添加请求
   └─ ui/              工具页面 ViewModel
```

模块协作、窗口选择和覆盖层 Context 约束见 [系统架构](docs/architecture.md)。

## 开发

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest :app:assembleDebug

git diff --check
```

安装到已连接设备：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:installDebug
```

环境、源码地图、设备验收和故障定位见 [开发指南](docs/development.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [产品说明](docs/product.md) | 当前范围、功能规则、用户流程、状态和验收。 |
| [系统架构](docs/architecture.md) | 命令状态机、目标窗口、覆盖层、数据与存储契约。 |
| [设计系统](docs/design-system.md) | 页面结构、组件职责、视觉和交互规则。 |
| [开发指南](docs/development.md) | 环境、源码路径、验证、设备验收和排障。 |
| [文档维护](docs/documentation.md) | 文档分工、同步触发条件和质量标准。 |
