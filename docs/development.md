# 开发记录

## 初版决策

- 使用全新空仓库实现模板应用，避免从旧应用复制业务包袱。
- 沿用旧应用的页面壳、底栏、分组卡片、整行列表项和 Material 3 动态色。
- 只实现 `Home` 和 `Setting` 两个底栏 Tab。
- 设置页里的开关使用中性示例文案，避免表达具体业务能力。
- 图标使用本地 vector 资源，保持模板依赖轻量。

## 依赖规则

AGP 9 已内置 Kotlin Android 支持。模块只应用 `com.android.application` 和 `org.jetbrains.kotlin.plugin.compose`，不再单独应用 `org.jetbrains.kotlin.android`。

## 验证规则

新仓库没有 `local.properties` 时，命令显式设置 Android SDK：

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```
