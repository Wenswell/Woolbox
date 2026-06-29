# app29 Agent 约束

## 适用范围

本文件适用于仓库根目录及全部子目录。

## 项目定位

本仓库是 Android Compose demo/template 模板应用，用于沉淀偏好的页面样式、底栏结构和通用组件。当前只保留两个底栏入口：`Home` 和 `Setting`。

## 开始前

- 先读 `README.md` 和 `docs/design-system.md`，确认当前模板范围。
- 参考旧应用 `/Users/wswensw/Documents/repos/playground/app0603` 时，只提取通用 UI 外壳、设计规范和组件习惯。
- 旧应用中的 QR、Global Copy、OLED、上传、更新、无障碍服务等业务能力不进入本模板。

## 编码约束

- 默认使用 Kotlin、Jetpack Compose、Material 3。
- 保持工程轻量，新增依赖前先确认是否能用 Compose、AndroidX 或本地 vector 解决。
- 组件新增优先沉淀到 `app/src/main/java/com/wswensw/template/ui/TemplateComponents.kt`。
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
