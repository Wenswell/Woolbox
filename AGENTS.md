# app29 Agent 约束

## 适用范围

本文件适用于仓库根目录及全部子目录。

## 项目定位

本仓库是 Android Compose 工具应用，用于沉淀偏好的页面样式、底栏结构和通用组件。当前以 `Global Copy` 与 `OLED shield` 作为真实业务场景，底栏保留 `Home` 和 `Setting`。

## 开始前

- 先读 `README.md` 和 `docs/design-system.md`，确认当前模板范围。
- 参考旧应用 `/Users/wswensw/Documents/repos/playground/app0603` 时，提取经过验证的业务规则和系统能力边界。
- 旧页面、布局和交互作为场景参考；当前应用以 Compose 重新设计页面与交互。
- `Global Copy` 与 `OLED shield` 的当前范围以 `docs/global-copy-oled-migration.md` 为准。

## 编码约束

- 默认使用 Kotlin、Jetpack Compose、Material 3。
- 保持工程轻量，新增依赖前先确认是否能用 Compose、AndroidX 或本地 vector 解决。
- 应用内页面组件新增优先沉淀到 `app/src/main/java/com/wswensw/template/ui/TemplateComponents.kt`；系统覆盖层组件归属 `tool/overlay`。
- 页面只通过稳定组件组合扩展，避免在页面里重复写样式细节。
- 卡片用于单个内容块或设置分组，页面 section 保持全宽布局。

## 文档同步

- 修改底栏、页面结构、模板组件、样式规则时，同步 `README.md` 和 `docs/design-system.md`。
- 新增可复用开发经验时，写入 `docs/development.md`。
- 每轮功能修改后保留有长期价值的规则和经验。

## 验证

默认验证命令：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
git diff --check
```

## 提交

- 完成一轮可验证修改后创建本地 commit。
- 不自动 push，除非用户明确要求。
