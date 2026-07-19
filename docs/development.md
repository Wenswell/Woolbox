# 开发指南

## 环境

| 项目 | 版本或路径 |
| --- | --- |
| Android Gradle Plugin | `9.2.1` |
| Kotlin | `2.3.21` |
| Compose BOM | `2026.05.01` |
| Java | Android Studio JBR `17` |
| Android SDK | `compileSdk 36.1`、`minSdk 34`、`targetSdk 36` |
| 单元测试 | JUnit `4.13.2` |

项目使用 Gradle Wrapper。标准 macOS 环境变量：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 源码地图

| 路径 | 修改入口 |
| --- | --- |
| `MainActivity.kt` | Activity 生命周期、页面触发后退到后台。 |
| `ui/TemplateApp.kt` | 导航、底栏、页面接线和 Tile 添加请求。 |
| `ui/TemplateComponents.kt` | 应用页面的稳定通用组件。 |
| `ui/ToolScreens.kt` | 两个工具详情页的组件组合。 |
| `tool/service/ToolAccessibilityService.kt` | 命令状态机、目标变化和工具生命周期。 |
| `tool/service/ForegroundWindowResolver.kt` | 外部应用窗口候选和选择顺序。 |
| `tool/service/WindowSnapshotBuilder.kt` | Global Copy 节点遍历和文本快照。 |
| `tool/model/CopyTextModels.kt` | 复制排序、视觉行与去重。 |
| `tool/overlay/ComposeOverlayWindow.kt` | Compose 系统窗口与生命周期。 |
| `tool/overlay/CopySessionController.kt` | Global Copy 覆盖层与剪贴板动作。 |
| `tool/overlay/OledShieldController.kt` | OLED 遮罩、面板和实时布局。 |
| `tool/storage/OledShieldConfigRepository.kt` | OLED 默认与按应用配置。 |
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

安装到已连接设备：

```bash
./gradlew :app:installDebug
adb shell am start -n com.wswensw.template/.MainActivity
```

## 自动测试边界

| 测试 | 覆盖内容 |
| --- | --- |
| `CopyTextSelectionTest` | 捕获顺序、阅读顺序、视觉行、NFKC/空白归一化与去重。 |
| `OledShieldConfigTest` | OLED 默认值和参数归一化。 |
| `ForegroundTargetSelectorTest` | 活动、焦点、窗口层级和无候选时的目标选择。 |

这些是纯 JVM 领域测试，不能验证 Android 窗口恢复、无障碍服务绑定、`WindowManager.addView`、输入法、Tile 收起或厂商系统行为。

## 真机验收

涉及入口、服务、窗口或覆盖层的修改，至少执行：

1. 启用 `Template tools`，确认两个详情页为 `Connected`，主操作可用。
2. 从一个包含可访问文本的目标应用进入 Template，点击 `Start Global Copy`；确认目标应用恢复、会话显示且服务未断开。
3. 验证文本选择、`Copy capture`、`Copy reading`、编辑复制和剪贴板结果。
4. 返回 Template，点击 `Show shield`；确认目标应用恢复、遮罩显示且服务未断开。
5. 验证长按面板、三个 Slider、拖动、`Save`、`Close`、`Hide shield` 和按应用恢复。
6. 分别从 Quick Settings 触发两个 Tile，确认 System UI 收起后工具作用于原目标。
7. 工具活动时切换应用、重新打开 Template、关闭无障碍服务，确认覆盖层全部释放。
8. 旋转屏幕或改变显示尺寸，确认 OLED 遮罩和面板按比例重算。

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
| Tile 长时间 `Preparing` | 当前 root 是否仍为 System UI，是否存在外部 `TYPE_APPLICATION` 窗口。 | 目标在面板收起后实时解析，命令两秒超时。 |
| Global Copy 没有会话 | 目标节点是否可见、非密码、有边界和非空可访问文本。 | 空快照直接结束，不创建空覆盖层。 |
| OLED 参数未恢复 | 包名键是否存在 `opacity`，保存是否发生在控制面板 `Save`。 | 应用配置不存在时读取当前默认值。 |
| 打开 Template 后覆盖层关闭 | 本应用是否成为活动或焦点 `TYPE_APPLICATION` 窗口。 | 这是产品关闭规则；仅覆盖层事件不能触发关闭。 |

## 参考旧应用

旧应用位于 `/Users/wswensw/Documents/repos/playground/app0603`。参考时按以下顺序核对：

1. 业务规则：输入、排序、默认值、保存粒度和关闭条件。
2. 平台边界：Context 来源、`WindowManager` 获取、窗口类型/flags、系统动作与生命周期。
3. 当前实现：使用 Compose 和当前组件重新设计页面与覆盖层，不复制旧布局。

已验证的系统边界不能仅凭 API 看似更新而替换。本项目曾因迁移时引入 `AccessibilityService.createWindowContext()` 导致 Android 14 设备在创建两类覆盖层时抛出 `UnsupportedOperationException`；旧应用直接使用服务 Context 的实现才是本项目的基准。

## 收尾

- 按 [文档维护](documentation.md) 同步唯一事实来源，删除已失效过程文档。
- 执行与风险匹配的定向验证，再执行一次提交前验证。
- 检查 `git diff` 和工作区，只提交本轮相关文件。
- 创建本地 commit，不自动 push。
