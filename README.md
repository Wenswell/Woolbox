# Woolbox

Android Compose 工具应用，提供跨应用无障碍工具与在线 Records，用真实场景沉淀页面结构、通用组件、持久化和覆盖层能力。

## 当前能力

| 工具 | 用途 | 启动方式 |
| --- | --- | --- |
| `Global Copy` | 读取目标应用当前窗口中的可访问文本，生成可编辑草稿，并保存可再次编辑、复制或删除的采集历史。 | 详情页 `Start Global Copy`；Quick Settings Tile。 |
| `OLED shield` | 显示可触摸黑色遮罩；无无障碍服务时作用于 Woolbox，有服务时作用于目标应用并覆盖系统栏。 | 详情页 `Show shield`；Quick Settings Tile；已保存应用列表。 |
| `Records` | 管理 Today tasks、每小时打卡、非周期 Goals 与量化记录。 | `Records` 底栏；Android 与 PC。 |

`Global Copy` 与 OLED 的跨应用模式共用 `Woolbox tools` 无障碍服务。OLED 没有服务时可在 Woolbox 内直接使用；服务连接后，页面入口会先把 Woolbox 移到后台，再对恢复的应用执行系统级遮罩。Tile 用于在目标应用内快速启动。`Records` 通过 HTTPS API 访问服务端 SQLite，不依赖无障碍授权，也不在本地保存记录数据。Setting 可选择跟随系统、English 或简体中文，并仅检查当前构建类型的版本化 APK：debug 只更新 debug，release 只更新 release。

## 使用前提

- Android `12` 或更高版本。
- 在系统无障碍设置中启用 `Woolbox tools`。
- Global Copy 只能读取目标应用通过无障碍节点公开的文本。
- `Global Copy` 与 OLED 的跨应用模式使用 `TYPE_ACCESSIBILITY_OVERLAY`，无需悬浮窗权限；OLED 的应用内模式不需要额外权限。

## 快速使用

1. 打开 Woolbox（默认 `Records`），切到 `Tools` 选择 `Accessibility service` 并启用服务。
2. 进入 `Global Copy` 或 `OLED shield` 详情页。
3. 直接选择页面顶部的主操作；Woolbox 进入后台后，工具作用于恢复的应用。
4. 如需快捷入口，Android 13 及以上可在详情页选择 Tile 的 `Add`；Android 12/12L 在系统快捷设置编辑页手动添加。
5. Global Copy 详情页可管理采集历史；OLED shield 详情页可管理并启用已保存的应用配置。

从 `Records` 进入后，同一页按 Today、Templates、Goals、Accumulate、Measurement 展示。Goals 使用一个非周期共享进度池和一层子目标：共享进度达到子目标值后由用户确认完成，不扣减进度；全部完成或到期后只读。Today tasks 可临时新建，也可手动采用 Daily 模板建议或续做昨日未完成项；每个任务由有序子任务派生完成状态。起床标记后，从下一本地整点开始以 WorkManager 尽量提醒每小时打卡，打卡记录与任务进展独立。首次使用先在 `Setting` 配置服务端 Basic Auth 凭据。

Records 的业务规则、状态语义和操作流程见 [Records 产品规则](docs/records-product.md)；应用共享规则见 [产品说明](docs/product.md)。

## 工程基线

| 项目 | 当前值 |
| --- | --- |
| App 名 | `Woolbox` |
| 包名 | `com.wswensw.woolbox` |
| 技术栈 | Kotlin、Jetpack Compose、Material 3 |
| 导航 | `Records`、`Tools`、`Setting` 底栏；工具与记录管理使用独立路由 |
| 系统能力 | `AccessibilityService`、`TYPE_ACCESSIBILITY_OVERLAY`、Quick Settings Tile、WorkManager 通知、`PackageInstaller` 更新 |
| `compileSdk` | Android `36.1` |
| `minSdk` | Android `31` |
| `targetSdk` | Android `36` |
| JVM | Java `17` |
| 版本 | `0.1.46` (`versionCode 47`) |

## 目录

```text
app/src/main/java/com/wswensw/woolbox/
├─ record/             Today 实例、任务模板、量化记录、提醒、网络 Repository 和 Compose 页面
├─ ui/                 应用页面、主题和模板组件
└─ tool/
   ├─ model/           文本与 OLED 领域模型
   ├─ overlay/         Compose 无障碍覆盖层
   ├─ service/         无障碍服务、目标解析和状态
   ├─ storage/         Global Copy 历史与 OLED 配置持久化
   ├─ trigger/         Quick Settings Tile 与添加请求
   └─ ui/              工具页面 ViewModel
```

`records-server/` 提供 Fastify API、覆盖全部 Records 对象的 PC 管理页、SQLite 迁移和部署脚本。

模块协作、窗口选择和覆盖层 Context 约束见 [系统架构](docs/architecture.md)。

## 开发

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest :app:assembleDebug

git diff --check
```

发布 debug 更新通道：

```bash
pnpm run publish:debug
```

`pnpm run publish:debug` 不依赖当前 shell 的 Java/Android 环境，一次完成 debug APK 构建、`latest.json` 生成、版本化 APK 上传和远端校验。release 使用 `pnpm run publish:release`，只处理 release 包。远端校验要求见 [开发指南](docs/development.md)。

功能交付顺序为开发、测试、提交、服务迁移（涉及服务端契约时）、对应通道 APK 发布和真机验收；完整命令与边界见[开发指南](docs/development.md#功能交付)。

## 更新版本

| 构建类型 | 当前发布版本 |
| --- | --- |
| debug | `0.1.46` (`versionCode 47`)；已发布，[manifest](https://dl.1662803.xyz/d/woolbox/releases/debug/latest.json)，APK `200` / `69,268,200` bytes。 |
| release | 尚未发布。 |

安装到已连接设备：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:installDebug
```

环境、源码地图、构建交付和故障定位见 [开发指南](docs/development.md)；可执行验证见 [测试与设备验收](docs/testing.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [产品说明](docs/product.md) | 应用共享范围、工具规则、状态和验收。 |
| [Records 产品规则](docs/records-product.md) | Today task、每小时打卡、模板与量化记录规则。 |
| [系统架构](docs/architecture.md) | 命令状态机、目标窗口、覆盖层与工具存储契约。 |
| [Records 数据与接口](docs/records-architecture.md) | Records 表、API、事务、提醒和部署边界。 |
| [设计系统](docs/design-system.md) | 共享组件、工具页面、视觉和交互规则。 |
| [Records 界面规则](docs/records-design.md) | Records 页面结构、状态呈现和管理端规则。 |
| [开发指南](docs/development.md) | 环境、源码路径、构建交付、发布和排障。 |
| [测试与设备验收](docs/testing.md) | 自动测试覆盖、真机场景和诊断命令。 |
| [文档维护](docs/documentation.md) | 文档分工、同步触发条件和质量标准。 |
| `records-server/deploy/README.md` | 服务端构建、systemd、备份和恢复检查。 |
