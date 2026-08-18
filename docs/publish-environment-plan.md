# APK 发布环境自包含

Status: planned

## 目标

让 `pnpm run publish:debug` 与 `pnpm run publish:release` 不依赖调用 shell 预先设置 Java 或 Android SDK 环境。

## 方案

- 发布脚本固定使用 `/Applications/Android Studio.app/Contents/jbr/Contents/Home`。
- Android SDK 固定使用当前用户的 `~/Library/Android/sdk`。
- 构建前验证 JBR 的 `bin/java` 和 SDK 的 `build-tools`，缺失时直接报告唯一配置错误。
- Gradle 与 APK 签名检查使用同一 SDK/JDK 环境，不读取调用 shell 中的同名变量，不增加备用路径。

## 验收

- 清除 `JAVA_HOME` 与 `ANDROID_HOME` 后，`pnpm run publish:debug` 仍能完成构建、签名检查、上传和远端校验。
- 路径缺失时在构建前失败，并显示具体缺失路径。
- 脚本语法检查、debug 单元测试、lint 与构建通过。
