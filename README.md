# Template

Android Compose 工具应用，提供跨应用无障碍工具与在线量化记录，用真实场景沉淀页面结构、通用组件、持久化和覆盖层能力。

## 当前能力

| 工具 | 用途 | 启动方式 |
| --- | --- | --- |
| `Global Copy` | 读取目标应用当前窗口中的可访问文本，生成可编辑草稿，并保存可再次编辑、复制或删除的采集历史。 | 详情页 `Start Global Copy`；Quick Settings Tile。 |
| `OLED shield` | 显示可触摸黑色遮罩；无无障碍服务时作用于 Template，有服务时作用于目标应用并覆盖系统栏。 | 详情页 `Show shield`；Quick Settings Tile；已保存应用列表。 |
| `Records` | 创建量化记录项，按当前时间搜索或推荐，并记录今天的数值或时长。 | `Records` 底栏；右下角 `Record`。 |

`Global Copy` 与 OLED 的跨应用模式共用 `Template tools` 无障碍服务。OLED 没有服务时可在 Template 内直接使用；服务连接后，页面入口会先把 Template 移到后台，再对恢复的应用执行系统级遮罩。Tile 用于在目标应用内快速启动。`Records` 通过 HTTPS API 访问服务端 SQLite，不依赖无障碍授权，也不在本地保存记录数据。

## 使用前提

- Android `12` 或更高版本。
- 在系统无障碍设置中启用 `Template tools`。
- Global Copy 只能读取目标应用通过无障碍节点公开的文本。
- `Global Copy` 与 OLED 的跨应用模式使用 `TYPE_ACCESSIBILITY_OVERLAY`，无需悬浮窗权限；OLED 的应用内模式不需要额外权限。

## 快速使用

1. 打开 Template，在首页选择 `Accessibility service` 并启用服务。
2. 进入 `Global Copy` 或 `OLED shield` 详情页。
3. 直接选择页面顶部的主操作；Template 进入后台后，工具作用于恢复的应用。
4. 如需快捷入口，Android 13 及以上可在详情页选择 Tile 的 `Add`；Android 12/12L 在系统快捷设置编辑页手动添加。
5. Global Copy 详情页可管理采集历史；OLED shield 详情页可管理并启用已保存的应用配置。

量化记录从 `Records` 进入；首次使用先在 `Setting` 配置服务端 Basic Auth 凭据，再创建记录项并通过 `Record` 选择项目确认数值。

完整业务规则、状态语义和操作流程见 [产品说明](docs/product.md)。

## 工程基线

| 项目 | 当前值 |
| --- | --- |
| App 名 | `Template` |
| 包名 | `com.wswensw.template` |
| 技术栈 | Kotlin、Jetpack Compose、Material 3 |
| 导航 | `Home`、`Records`、`Setting` 底栏；工具与记录管理使用独立路由 |
| 系统能力 | `AccessibilityService`、`TYPE_ACCESSIBILITY_OVERLAY`、Quick Settings Tile |
| `compileSdk` | Android `36.1` |
| `minSdk` | Android `31` |
| `targetSdk` | Android `36` |
| JVM | Java `17` |
| 版本 | `0.1.0` (`versionCode 1`) |

## 目录

```text
app/src/main/java/com/wswensw/template/
├─ record/             记录领域规则、网络 Repository 和 Compose 页面
├─ ui/                 应用页面、主题和模板组件
└─ tool/
   ├─ model/           文本与 OLED 领域模型
   ├─ overlay/         Compose 无障碍覆盖层
   ├─ service/         无障碍服务、目标解析和状态
   ├─ storage/         Global Copy 历史与 OLED 配置持久化
   ├─ trigger/         Quick Settings Tile 与添加请求
   └─ ui/              工具页面 ViewModel
```

`records-server/` 提供 Fastify API、PC 管理页、SQLite 迁移和部署脚本。

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
| `records-server/deploy/README.md` | 服务端构建、systemd、备份和恢复检查。 |
