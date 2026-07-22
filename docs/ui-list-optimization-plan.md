# UI 列表与可点击条目优化计划

状态：包1+包2 已落地；包3 未做  
范围：Home / 工具详情 / Setting / Records 可点击行与表单交互  
目标：对齐 Material 3 与常见工具/设置应用做法；不扩大功能范围

## 结论摘要

整体结构合理：设置/工具用 `TemplateSection` + `TemplateListItem`，Records 用色卡整卡入口，与设计系统分层一致。

需要优化的不是“整行可点”本身，而是：

1. 导航 / 立即动作 / 只读状态的视觉与语义未分层
2. 禁用态把说明文案一并淡化
3. 部分列表行未复用统一组件，affordance 不一致
4. 表单控件有几处明确反模式（键盘类型、Switch 行、提交态）

主操作用列表行（`Start Global Copy` / `Show shield`）是已确认产品选择，本次默认保留。

## 问题分级

### A. 应修（不改产品行为）

| ID | 问题 | 位置 | 推荐 |
| --- | --- | --- | --- |
| A1 | 可点击 `TemplateListItem` 未声明 `Role` / disabled 语义 | `TemplateComponents.kt` | `Role.Button`；禁用时 `semantics { disabled() }` |
| A2 | 禁用行副文案与图标同步 0.38 透明，说明不可读 | Tile 等 `enabled=false` 行 | 副文案保持可读；仅降低标题/图标或去掉点击 |
| A3 | `TemplateDivider` 从 16dp 起划，易切 leading icon | Home Available 等 | inset 对齐文字列（约 56dp） |
| A4 | 导航 affordance 双标准：Manage 有 chevron，Home/Setting/History 无 | 多页 | 导航进下级的行统一 trailing chevron；即时动作行不加 |
| A5 | Priority 用文本键盘 | `RecordItemFormScreen` | `KeyboardType.Number` |
| A6 | Goal Switch 点文案不切换 | 表单 | 整行 `toggleable` + `Role.Switch` |
| A7 | Connection 手写 section，未复用 `TemplateSection` | `RecordsConnectionScreen` | 改用 `TemplateSection` |
| A8 | 文档：设置分组“卡片/圆角”与实现不符 | `design-system.md` | 同步为无外框；Divider 对齐规则 |

### B. 建议修（交互形态变化，语义可保留）

| ID | 问题 | 推荐 | 产品影响 |
| --- | --- | --- | --- |
| B1 | Copy history 整行点击 + 尾部删除 IconButton 嵌套 | 侧滑删除，或删除移入编辑 Dialog | 删除入口位置变 |
| B2 | OLED / History 自建行与 `TemplateListItem` 分叉 | 扩展统一行组件（40dp app icon、trailing actions） | 无业务变化 |
| B3 | Sheet / Connection / 表单：提交启用与 saving 规则不统一 | 校验通过才启用主操作；saving 禁编辑 | 反馈时机统一 |
| B4 | 表单错误只在页底 | 字段 `isError` + supportingText | 否 |

### C. 产品决策（先确认再动）

| ID | 问题 | 常见做法 | 当前产品 |
| --- | --- | --- | --- |
| C1 | 授权已启用仍可点，Toast “already enabled” | 只读状态行，或提供“在系统中管理” | 可点 + Toast |
| C2 | 工具主操作用列表行 | 主按钮 / 更强强调 | 列表行（验收已写死） |
| C3 | 运行中状态 Ready/Preparing/Active 无视觉权重 | badge / 色标签 | 纯副文案 |
| C4 | 底栏文案 `Setting` 单数 | 常见 `Settings` | 当前 `Setting` |

## 推荐实施包

### 包 1：组件与导航一致性（推荐优先）

含 A1–A4、A8。  
验收：

- 禁用 Tile 行仍可读到系统手动添加说明
- 导航行（工具入口、History、Connection 等）有 chevron；主操作/状态/弹层行无
- TalkBack 将可点行读为按钮；禁用行可读 disabled
- Divider 不切图标
- design-system 与实现一致

### 包 2：表单反模式

含 A5–A7、B3–B4。  
验收：

- Priority 数字键盘
- Goal 开关整行可切换
- Connection 使用 `TemplateSection`
- 主操作仅在合法输入时可用；saving 时控件禁用
- 字段错误就近显示

### 包 3：列表行收敛

含 B1–B2。  
验收：

- History / Saved apps 触控目标与字阶对齐设计系统
- 删除与打开无嵌套冲突

### 不做

- 不把 Records 色卡改成 `TemplateListItem`
- 不改工具主操作为 filled 按钮（除非确认 C2）
- 不扩大 Records 业务能力

## 实施顺序

1. 确认包与 C 项决策  
2. 包 1 → 验证  
3. 包 2 → 验证  
4. 包 3（可选）→ 验证  
5. 提交 commit；长期规则并入 design-system；删除本计划

## 验证

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" \
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
./gradlew :app:testDebugUnitTest :app:assembleDebug

git diff --check
```

涉及覆盖层/Tile 的改动需真机；包 1/2 以页面扫描与 TalkBack 为主。


## 落地记录

- 范围：包1 + 包2；授权已启用行保持 Toast；导航行统一 chevron。
- `TemplateListItem`：`Role.Button`、`navigate` chevron、禁用副文案可读、iconRes 24dp。
- `TemplateDivider` inset `56dp`。
- Home 工具入口、History、Connection：`navigate=true`。
- 表单：Priority 数字键盘、Goal 整行 Switch、字段错误、合法才可保存、saving 禁编辑。
- Sheet：正数才可 Record；saving 禁步进/输入。
- Connection：`TemplateSection`；saving 禁输入。
- design-system 已同步。
- 包3（History 侧滑删除、OLED/History 行组件收敛）未做。
