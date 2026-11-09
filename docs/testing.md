# 测试与设备验收

## 自动测试边界

| 测试 | 覆盖内容 |
| --- | --- |
| `CopyTextSelectionTest` | 捕获/阅读顺序、视觉行、归一化与去重。 |
| `CopyHistoryMappingTest` | 历史摘要、文本块、选择、草稿和顺序映射。 |
| `OledShieldConfigTest` / `OledShieldConfigRepositoryTest` | OLED 参数归一化与 profile 存储。 |
| `ForegroundTargetSelectorTest` / `ToolCommandPolicyTest` | 目标窗口选择和工具切换。 |
| `AppUpdateClientTest` | APK 大小/SHA-256、包名、构建类型和 host 限制。 |

JVM 测试不能验证 Android HTTPS、Keystore、窗口、无障碍服务、输入法或厂商系统行为。

## 真机验收

1. Tools 的 `Access` 中无障碍服务和电池优化未授权时显示错误色并可打开对应系统设置；分别授权后返回应用显示成功状态，再次点击只提示已授权。两个详情页没有重复权限行。
2. 电池优化系统页确认目标包名为 Woolbox；拒绝或关闭后状态保持未授权，再次点击可重新发起请求。
3. Android 13+ 从详情页请求添加 Tile；Android 12/12L 的入口禁用，并从系统快捷设置编辑页手动添加。
4. 从含可访问文本的应用启动 Global Copy，确认目标应用恢复、编辑页显示且服务未断开。
5. 验证主面板留白、Reading order、两种草稿、Select text、输入法、Copy、历史 Save/Delete。
6. Copy History 行显示目标应用名称和图标，不显示包名；应用不可用时显示通用状态。
7. 关闭无障碍服务启动 OLED，确认仅覆盖 Woolbox、可调整和关闭且不新增 Saved apps。
8. 启用服务后启动 OLED，确认恢复目标应用并覆盖状态栏；验证 Slider、拖动、Save、Close、Hide 和 Saved apps。
9. 连续长按 OLED 只保留一个控制面板；关闭后可再次长按打开。
10. 在 `Preparing/Active` 连续触发同类及不同 Tile，确认取消、关闭和最终工具唯一。
11. 切换应用、重新打开 Woolbox、关闭服务或旋转屏幕，确认窗口释放和布局重算。
20. 发布后由低版本同通道 APK 验证检查、下载校验、安装来源授权和系统确认。
21. 依次切换 English、简体中文、Follow system，确认 Activity、覆盖层和 Tile 文案与持久化。

## 设备诊断

```bash
adb shell dumpsys accessibility \
  | rg "Bound services|Enabled services|Binding services|Crashed services|Woolbox tools"

adb logcat -d -b crash \
  | rg -n -C 20 "com.wswensw.woolbox|FATAL EXCEPTION"

adb shell dumpsys activity activities \
  | rg "topResumedActivity|mResumedActivity"

adb exec-out uiautomator dump /dev/tty
```

## 本次新增验收

- ScrollCaptureTest 验证跨屏重叠、往返滚动、归一化、来源区分与阅读顺序。
- 默认进入扫一扫；相机/相册识别二维码，复制原文、打开链接，拒绝权限、无二维码、离开页面释放相机。
- 长页面手动滚动停稳后追加文本，回滚不重复；退出和切换应用停止采样，历史保存同一条完整会话。
- 三个工具日志可查看完整详情并导出；JSONL 包含扫码原文、复制采样及去重明细、OLED 参数与启停原因。
- 无障碍 OLED 与应用内 OLED 均记录日志。

## 0.1.51 模拟器验证记录

- GitHub 发布 APK 已匿名下载，SHA-256 与 manifest 一致；安装成功，Setting 在线检查显示最新版本 0.1.51 (52)。
- 默认扫一扫；拒绝相机权限有提示，相机授权后 CameraX 成功绑定。相册识别测试链接成功，复制和打开动作保留原文日志。
- 日志经系统文件选择器导出 JSONL，读回确认操作、会话与结果字段。
- 系统设置页采集初始 11 块，滚动停稳增加到 15 块；回滚 11 块全部判重，日志保留 settled/sample/merged，复制后记录原文并结束会话。
- OLED 覆盖系统设置页，透明度由 100 调到 49，保存配置；回到 Woolbox 自动关闭，日志包含 start/config/save/end 和 own_app_foreground 原因。
- UI Automator dump 会影响无障碍服务绑定，覆盖层验收应使用截图、adb input 和日志读取，避免把测试工具造成的解绑误判为应用问题。
- 待实机确认：真实镜头二维码识别、相机离页释放、低版本应用内下载到系统安装确认的完整升级流程；当前验证不覆盖这些场景。
