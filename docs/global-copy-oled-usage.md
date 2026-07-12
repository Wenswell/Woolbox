# Global Copy 与 OLED shield 使用指南

## 准备

1. 打开应用首页，选择 `Accessibility service`。
2. 在系统无障碍设置中启用 `Template tools`。
3. 打开 `Global Copy` 或 `OLED shield` 页面，选择对应 Tile 的 `Add`。
4. 在系统对话框中确认添加 Tile。

## Global Copy

1. 保持目标应用在前台。
2. 展开 Quick Settings，选择 `Global Copy` Tile。
3. 在复制会话中选择文本块。
4. 选择 `Copy capture`、`Copy reading`，或 `Edit before copy`。

`Copy capture` 按快照顺序写入剪贴板。`Copy reading` 按视觉阅读顺序写入剪贴板。复制完成后会话关闭，目标应用恢复交互。

## OLED shield

1. 在 `OLED shield` 页面设置默认的透明度、宽度和高度，选择 `Save default`。
2. 保持目标应用在前台。
3. 展开 Quick Settings，选择 `OLED shield` Tile。
4. 长按黑色遮罩，打开控制面板。
5. 调整透明度、宽度、高度和控制面板位置，选择 `Save`。

`Save` 将当前参数保存到目标应用包名。下次为同一应用开启遮罩时会加载该参数。`Hide shield` 关闭遮罩；再次选择 Tile 可重新打开。

## 状态说明

| 状态 | 含义 |
| --- | --- |
| `Not enabled` | 系统无障碍服务等待授权。 |
| `Waiting for service` | 系统设置已经启用，服务正在连接。 |
| `Connected` | 服务已连接。 |
| `Preparing` | Tile 正在收起系统面板并解析目标窗口。 |
| `Active` | 对应工具正在运行。 |
| `Ready` | 对应 Tile 已准备执行。 |
