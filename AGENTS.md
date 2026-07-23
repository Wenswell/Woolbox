# app29 Agent 约束

## 适用范围

本文件适用于仓库根目录及全部子目录。

## 项目定位

本仓库是 Android Compose 工具应用，用 `Global Copy`、`OLED shield` 与 `Records` 沉淀页面样式、底栏结构、通用组件、持久化和无障碍覆盖层能力。底栏固定为 `Records`、`Tools` 与 `Setting`。

## 开始前

按任务读取对应文档：

1. 始终先读 `README.md`，确认项目基线和文档入口。
2. 功能行为、入口或状态变更：读 `docs/product.md`。
3. 服务、窗口、覆盖层、数据或存储变更：读 `docs/architecture.md`。
4. 页面、组件或样式变更：读 `docs/design-system.md`。
5. 构建、测试或设备问题：读 `docs/development.md`。
6. 文档修改：读 `docs/documentation.md`。

参考旧应用 `/Users/wswensw/Documents/repos/playground/app0603` 时，必须同时提取业务规则和系统能力边界，包括 Context、`WindowManager`、窗口类型、触发时机与生命周期。旧页面只作为场景参考，当前应用使用 Compose 重新设计。

## 编码约束

- 默认使用 Kotlin、Jetpack Compose 和 Material 3。
- 保持工程轻量；新增依赖前先确认 Compose、AndroidX 或本地 vector 无法满足需求。
- 应用页面通用组件归属 `app/src/main/java/com/wswensw/woolbox/ui/AppComponents.kt`。
- 量化记录的领域规则、Room 数据层和页面归属 `app/src/main/java/com/wswensw/woolbox/record/`。
- 无障碍覆盖层及其 Compose UI 归属 `app/src/main/java/com/wswensw/woolbox/tool/overlay/`。
- 页面通过稳定组件组合，不在页面内重复样式细节。
- 卡片只用于单个内容块或设置分组；页面 section 保持全宽布局。
- 不增加兼容层、备用路径或静默降级；平台边界不明确时先在真机验证。

## 文档同步

- 按 `docs/documentation.md` 的信息归属更新唯一事实来源。
- 功能修改前先写清范围、实现步骤和可观察验收标准。
- 完成后只保留长期有效的规则、原因和失败模式；删除重复的实施过程。
- 文档中的命名、路径、默认值和状态必须与源码一致。

## 验证

默认提交前验证：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest :app:assembleDebug

git diff --check
```

涉及无障碍窗口、页面退后台、Tile 或覆盖层时，还需按 `docs/development.md` 完成真机验收。JVM 测试不能替代系统窗口验收。

## 提交

- 每轮可验证修改完成后创建本地 commit。
- 不自动 push，除非用户明确要求。
