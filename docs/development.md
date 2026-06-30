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

## 设置页交互规则

- 设置项存在动作时，整行作为点击目标。
- 开关类设置由整行切换状态，尾部 `Switch` 展示当前状态。
- 小型互斥选项使用单选弹窗。
- 单选弹窗沿用 app0603 的 `BasicAlertDialog + Surface` 结构，点击选项后立即保存并关闭。
- 短文本配置使用输入框弹窗，弹窗支持主输入框和可展开输入框。
- 版本、包名和只读说明使用信息弹窗。

## 导航规则

- 根页面保留 `Home` 和 `Setting` 两个底栏入口。
- 示例详情和规则说明使用子页面承载。
- 子页面使用 Navigation Compose string route，避免为模板示例引入序列化依赖。
- 页面切换动画使用横向滑动，进入向左，返回向右。
- 需要右下角主按钮的子页面使用 `TemplatePageAction`。

## 系统栏规则

- `MainActivity` 开启 edge-to-edge。
- 页面外壳统一处理安全区。
- 状态栏和导航栏图标样式跟随应用内 `Theme` 设置。

## 输入框与主按钮规则

- 页面内输入框使用 `TemplateInputField`，保持 `16dp` 水平页面间距。
- 主按钮右锚定，页面接近顶部时显示图标和文字，离开顶部后显示纯图标。
- 主按钮状态示例使用蓝底对钩和红底错误两种状态交替。
