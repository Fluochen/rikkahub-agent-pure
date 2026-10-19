# 思考深度 Hero 卡片：优化方案

> **状态**：设计提案，未实施 · **基线**：`7a7660e` · **日期**：2026-10-19
> **范围**：对话框 → 模型选择抽屉顶部的 `AgentControlCard`（以及与其重复的设置页深度选择器）
> **可交互原型**：[`thinking-depth-card.html`](./thinking-depth-card.html)（亮/暗切换、可拖动、可切 AUTO）

---

## 0. 一页结论

这张卡目前同时承担三件事：**当前模型的思考深度**、**全局审批模式**、**全局子代理派发策略**。三者没有标题、没有作用域说明，被压进同一行；深度轴还把 `AUTO` 当成第二档。结果是：

- 用户不知道自己改的是「这次对话」「这个模型」还是「整个应用」；
- `AUTO` 在轴上不是单调的，从 AUTO 往右拖反而可能变浅；
- 7 个档位在视觉上只有一条连续色带，没有刻度、没有过档触感，吸附只是一个隐式的 Slider 默认；
- 亮色主题下最深用的「沉思」对比度只有 **1.46:1**，几乎读不出来；
- 两套几乎相同的滑杆实现（抽屉 / 设置页）各自演进。

**建议做六件事，按优先级：**

1. **把 AUTO 移出深度轴**，改为标题行上的独立模式 chip；轴只保留 6 个真实档位：关闭 → 快速 → 均衡 → 沉思 → 研究 → 极致。
2. **让 6 个档位可见、可感**：轨道上画节点；把 M3 的隐式吸附显式化并测试；跨档触发一次触感；拇指上方显示当前档位气泡；松手才写入。
3. **文字色分主题取对比安全变体**，保留同一套冷 → 暖色相；MAX 的彩虹只留在轨道，文字用实色。
4. **给 hero 补上下文**：标题行写「思考深度 · 模型名」，档位词旁复用现成的 `reasoning_*_desc` 作一句解释，信息按钮说明「仅对此模型生效」。
5. **把运行策略降级**：深度卡只做深度；审批 / 派发移到卡片下方的「运行策略 · 全局」一行（或收进抽屉右上角的策略图标），保留一键访问，但不再抢第一行。
6. **抽出一个共享控件** `ThinkingDepthSection`，抽屉与设置页共用；同时修 RTL、`stateDescription`、字体缩放与常驻动画。

这套方案**不改数据格式**：`ReasoningLevel` 的 7 个枚举值与持久化不变，没有迁移；改动集中在 UI 层、字符串和预览/测试。

**成本预估**：PR1（视觉 / 可达性）1.5–2 天，PR2（轴语义 / 信息架构）2 天，可选 PR3（provider 真实档位）1–2 天；翻译 0.5 天；真机 QA 1 天。

---

## 1. 卡片现在是什么

### 1.1 调用链

```
ChatInput.ModelReasoningChip                 ChatInput.kt:435-531
  └─ ModelListSheet(header = AgentControlCard)   ModelList.kt:272-330
       └─ AgentControlCard                     AgentControlCard.kt:69-197
            ├─ ApprovalModeChip   (全局)        AgentControlCard.kt:227
            ├─ DispatchProfileChip (全局)       AgentControlCard.kt:258
            ├─ ReasoningIcon + ReasoningLevelWord (按模型)
            └─ Slider(track = ReasoningSurgeTrack)  ReasoningPicker.kt:334
```

卡片是 **模型选择抽屉的 header**：抽屉主体是搜索框 + 模型列表，header 固定在上方，不随列表滚动（`ModelList.kt:301-307`）。抽屉高度固定为 0.8 屏。

### 1.2 三个控件，三种作用域

| 控件 | 状态来源 | 作用域 | 写入 |
|---|---|---|---|
| 审批模式 | `ToolApprovalPreferences.modeFlow` | **全局** | `approvalPrefs.setMode()` |
| 子代理派发 | `Settings.subAgentDispatchProfile` | **全局** | `settingsStore.update {}` |
| 思考深度 | `model.reasoningLevel ?: assistant.reasoningLevel` | **按模型** | `settingsStore.updateModelReasoningLevel()`（`PreferencesStore.kt:950-963`） |

深度在读取时还有第三层回退：`conversationChatModelId → assistant.chatModelId → 全局 chatModelId`（`PreferencesStore.kt:1407`）。但写入只落到当前 `Model` 上，**不会**写回助手默认值。卡片对此没有任何说明。

### 1.3 尺寸与视觉

| 元素 | 现状 |
|---|---|
| 卡片 | `surfaceContainerHigh`，圆角 20dp，内边距 16×10dp；行高 48dp（M3 最小触控），间距 2dp |
| 策略胶囊 | 视觉高 26dp（`10dp` 横 + `5dp` 纵 + `labelMedium`），`ArrowDown01` 12dp；M3 `Surface(onClick)` 会把触控补到 48dp |
| 档位词 | `titleLarge` 22sp / Bold，颜色 = 当前档位色；MAX 用彩虹渐变刷 |
| 滑杆 | 7 个值，`steps = 5`；自定义 32dp Canvas、8dp 轨道、20dp 拇指；M3 `Slider` 自身仍有 48dp 最小触控 |
| 卡片总高 | ≈ **118dp**（10 + 48 + 2 + 48 + 10），约占 640dp 抽屉的 18% |
| 图标 | `OFF` 用 `Idea`，`AUTO` 用 `Idea01`，`LOW/MEDIUM/HIGH` 用自绘 `ReasoningLow/Medium/High`；`XHIGH/MAX` 复用 `ReasoningHigh` |

> 在 androidx-main 的 M3 1.5 源码中核对：`Slider`/`Surface` 都带 `minimumInteractiveComponentSize()`，所以**触控面积本身不是 20–26dp**；问题在可见 affordance、语义与命中后的反馈，而不是 48dp 缺失。落地前请用 1.5.0-alpha27 的实际 artifact 再确认一次。

### 1.4 档位模型与 provider 的真实映射

`ReasoningLevel`（`ai/core/Reasoning.kt:7-44`）：

| 档位 | `budgetTokens` | `effort` | 备注 |
|---|---:|---|---|
| OFF | 0 | `none` | `isEnabled = false` |
| AUTO | **−1** | `auto` | 哨兵值，不是真实预算 |
| LOW | 1 000 | `low` | |
| MEDIUM | 2 000 | `medium` | |
| HIGH | 8 000 | `high` | |
| XHIGH | 16 000 | `xhigh` | |
| MAX | 32 000 | `max` | |

各 provider 的实际请求并不都是 7 档：

| Provider / 路径 | 实际行为 | 手动档位的实际区分度 |
|---|---|---:|
| OpenAI 官方 Chat Completions | `reasoning_effort`；AUTO 不发；**OFF 被映射成 `low`**；xhigh/max 原样转发（官方只保证 low/medium/high） | 3–5 |
| OpenRouter | `reasoning.effort`；AUTO 发 `{enabled:true}`（`ChatCompletionsAPI.kt:352-360`） | 6 |
| DashScope（百炼） | `enable_thinking` + `thinking_budget`；AUTO 不发 budget | 6 |
| Ark / 智谱 / 小米 / Kimi / 书生 / aiping / SiliconFlow | 只发 `thinking.type` / `enable_thinking` 等开/关 → **所有非 OFF 档位请求相同** | **2** |
| DeepSeek | `thinking.type` + `reasoning_effort`（MEDIUM/HIGH→high、MAX→max，LOW/XHIGH 原样） | 5 |
| NVIDIA | 按模型映射；DeepSeek V4 的 XHIGH/MAX→max，其余→high；非 V4 模型原样转发 | 2–6 |
| Mistral | 显式不支持（空分支） | **0** |
| Claude | OFF `disabled`；AUTO `adaptive` 不带 effort；其余 `adaptive + output_config.effort`（`ClaudeProvider.kt:545-567`） | 6 |
| Google Gemini 3 系列 | `LOW→LOW`、`MEDIUM→MEDIUM`、**HIGH/XHIGH/MAX 全部→HIGH**（`GoogleProvider.kt:449-471`） | **4** |
| Google 非 Gemini 3 | OFF → 0（非 Pro），Pro 不设参数；AUTO 不设参数；其余发 `thinkingBudget` | 6 |
| Responses API | AUTO 不发 effort；其余发 `level.effort`（`ResponseAPI.kt:437-451`） | 6 |
| Codex | AUTO → `null`；MAX → `xhigh`（`CodexProvider.kt:245-254`），且 UI 对 AUTO 有单独警告 | 5（无 AUTO，MAX=XHIGH） |

> 完整的 host 分支在 `ChatCompletionsAPI.kt:349-490`，上表只列代表路径。「区分度」按请求体是否不同计，不代表 provider 一定接受或生效。

**含义**：UI 显示 7 个等宽档位，但在多个 provider 上只有 2–5 个是真正不同的请求，甚至 OFF 在 OpenAI 官方路径上被映射成 `low`。要么在 UI 里说明「实际档位由提供商映射，部分档位会合并」，要么在有 provider 信息时显示 effective 档位。前者是 PR2 的低成本做法，后者是 PR3。

---

## 2. 问题清单

| ID | 级别 | 问题 | 证据 | 用户影响 |
|---|---|---|---|---|
| P0-1 | 语义 | AUTO 是模式却占轴位，轴不单调 | `Reasoning.kt:14-15`、`AgentControlCard.kt:129-133` | 从 AUTO 往右拖可能变浅；无法预测结果 |
| P0-2 | 可读性 | 亮色主题下档位词对比度 1.46–2.45:1 | `ReasoningPicker.kt:232-238`、卡片 `surfaceContainerHigh` | 最关键的当前档位几乎读不出；WCAG 失败 |
| P0-3 | 可操作性 | 7 档无刻度、无过档触感；自定义 track 不画节点 | `steps=5` + 自定义 track；吸附依赖隐式 `shouldAutoSnap` | 用户不知道有几档、没有「过了一档」的反馈 |
| P1-1 | 信息架构 | 深度与两枚全局策略混排，无标题/作用域 | `AgentControlCard.kt:56-66` | 不知道改动落到哪里；误触全局策略 |
| P1-2 | 空状态 | 模型不支持推理时，词与滑杆整段消失，右侧留空 | `showReasoning` 分支 `AgentControlCard.kt:153-193` | 像是渲染错误；没有解释 |
| P1-3 | 可达性 | Slider 只报百分比，不报档位名；无 `stateDescription` | M3 `sliderSemantics` + 自定义 track | TalkBack 用户无法知道「沉思」 |
| P1-4 | RTL | M3 会镜像拇指与拖动方向，自定义 Canvas 从左往右填充 | `Slider.kt` `placeRelative` / `state.isRtl`；`ReasoningSurgeTrack` 以 `x=0..activeWidth` 绘制 | 阿拉伯语下拇指与填充反向，拖动方向相反 |
| P1-5 | 字体缩放 | 两枚胶囊 + 22sp 词在同一行；长语言（ru「Очень высокий」）会换行/挤压 | `Row` + `Spacer(weight)`，Text 无 `maxLines` | 大字或长文案时布局跳动 |
| P1-6 | 动效 | 3 条无限动画 + 最多 18 个粒子常驻；无条件分支 | `ReasoningPicker.kt:347-372`、`263-285` | 抽屉打开期间持续重绘；不尊重「减少动画」 |
| P2-1 | 维护 | 两套滑杆 / 两套拇指 / 两套预览 / 重复的 `levels` | `AgentControlCard.kt:162-192`、`ReasoningPicker.kt:178-207` | 加一个档位要改 6 处 |
| P2-2 | 测试 | 无卡片预览、无档位映射单测 | `ReasoningPicker.kt:490` 只有整页预览 | 回归只能靠真机目测 |
| P2-3 | 空间 | 0.8 抽屉 + 固定 header；键盘弹出时列表只剩 3–4 行 | `ModelList.kt:301` + `imePadding()` | 搜索时视线被挤压 |
| P2-4 | 流程 | 选中模型即关闭抽屉，想调新模型深度必须重开 | `ModelList.kt:314-321` | 多一步；不符合「选完就调」的直觉 |
| P2-5 | 真实性 | 7 档暗示了不存在的粒度 | §1.4 | 用户以为在调 7 档，实际可能只有 3 档 |

### 2.1 P0-1：AUTO 不在深度轴上

滑杆的索引就是 `ReasoningLevel.entries` 的下标：

```
0 OFF(0)  1 AUTO(-1)  2 LOW(1k)  3 MEDIUM(2k)  4 HIGH(8k)  5 XHIGH(16k)  6 MAX(32k)
```

`AUTO` 的 `budgetTokens = −1` 只是「没有预算」的哨兵。把它画在 LOW 的左边，视觉上等于「比关闭多一点、比快速少一点」，这与它的真实含义「让模型自己决定」正交。更糟的是，DashScope 在 AUTO 下不传 budget，而 LOW 会显式传 `thinking_budget = 1000`；用户以为往右拖是加深，实际可能把预算钉死在 1k。

**这是本卡片最需要先修的语义问题。** 推荐把 AUTO 变成标题行右侧的模式 chip（形状与现在的策略胶囊一致），轴只保留 6 个手动档位；打开 AUTO 时轨道变暗并保留上次手动档位的虚线圈，点任意档位即切回手动。

### 2.2 P0-2：亮色主题下的对比度

以 Autumn 预设的 `surfaceContainerHigh = #F0E7D9` 为底，当前固定色板的实测值（WCAG 2.1）：

| 档位 | 当前色 | 对比度 | 结果 |
|---|---|---:|---|
| OFF | `#8B96A3` | 2.45:1 | ❌ 3:1 |
| AUTO | `#29B6F6` | 1.88:1 | ❌ |
| LOW | `#26C6DA` | 1.68:1 | ❌ |
| MEDIUM | `#66BB6A` | 1.93:1 | ❌ |
| HIGH | `#FFB300` | **1.46:1** | ❌ 最差 |
| XHIGH | `#FB8C00` | 1.94:1 | ❌ |
| MAX | `#E53935` | 3.45:1 | ⚠️ 仅过「大字 3:1」，彩虹字更差 |

深色底 `#2D2A21` 上多数能过（4.77–7.99:1），MAX 3.39:1 过 3:1 但不到 4.5:1。**问题集中在亮色主题**，而这也是默认用户的常见主题。

档位词是 22sp Bold（WCAG 大字），阈值是 3:1；即便如此，HIGH 也只有 1.46:1。MAX 的彩虹渐变包含 `#FFEB3B`（黄）与 `#4CAF50`（绿），在浅底上会掉到 1.2–1.5:1，比单色回退更差。

### 2.3 P0-3：七个档位在视觉上不存在，也没有过档反馈

- `steps = 5` 只把 5 个内部刻度交给 `SliderState`；真正绘制刻度的是 `SliderDefaults.Track` 里的 `drawTick`。
- 本卡片传入的是自定义 `track = ReasoningSurgeTrack`，它只画一条连续色带（`ReasoningPicker.kt:397-470`），**没有任何节点**。
- 吸附本身在 M3 1.5 里是 `SliderState.shouldAutoSnap = true` 的默认行为，拖动时值会被 snap 到最近的 tick——但这是**隐式默认**，当前代码既没有显式表达，也没有测试；一旦升级/换 overload 就可能退回连续值。
- 没有 `HapticFeedback`。项目里已有 `GestureThresholdActivate / GestureEnd` 的先例（`ChatInput.kt:197`、`ModelList.kt:556`）。

结果：用户看不到「有几档」，拖动时也没有「过了一档」的触感或就地数值；只能盯着卡片右上角的小字确认。

### 2.4 P1-1 / P1-2：不知道自己改的是什么

- 卡片没有标题；两个策略 chip 是 `labelMedium` 12sp，深度词是 22sp，视觉层级告诉用户「深度是主角」，但语义上三者平级地挤在一行。
- 没有模型名，没有「仅对此模型生效」，也没有「运行策略是全局的」。
- `showReasoning = false` 时，`if` 直接把图标 + 词 + 滑杆全部拿掉（`AgentControlCard.kt:153-193`）。卡片右半边变成空白，用户只会觉得坏了。应显示一行「该模型不支持思考深度调节」+ 信息入口，并保持卡片高度可动画。
- `reasoning_picker_title` / `reasoning_picker_hint` 已在 7 个语言里存在，却只在设置页的独立 picker 中使用；抽屉里的用户看不到「并非所有模型都支持深度调整」这条重要提示。

### 2.5 P1-3 / P1-4 / P1-5：可达性、RTL、字体

**语义**：M3 `Slider` 会提供 `progressBarRangeInfo`（百分比、steps）与 `setProgress`，但不会把「关闭 / 快速 / … / 极致」作为值朗读。当前卡片没有 `contentDescription` / `stateDescription`，TalkBack 只会读「66 percent」。

**RTL**：M3 `SliderImpl` 用 `placeRelative` 放拇指，`SliderState` 用 `isRtl` 反转拖动方向（androidx-main `Slider.kt:893, 1050-1090`）；自定义 track 的 `drawRoundRect` 从 `x = 0` 画到 `activeWidth`，粒子也从左边缘起算。阿拉伯语下拇指在右、填充在左，拖动方向与视觉相反。

**字体缩放**：`Row` 里两个 26dp 胶囊 + 18dp 图标 + 22sp 词，靠 `Spacer(weight(1f))` 撑开。`Text` 没有 `maxLines`，俄语「Очень высокий」在 1.3× 就会换行；韩语的 XHIGH 甚至还是未翻译的「XHigh」。2.0× 时整行会挤压甚至溢出。

### 2.6 P1-6：动效常驻

`ReasoningSurgeTrack` 里有一个 `rememberInfiniteTransition` 驱动 3 条动画（flow / pulse / particles），另有一个独立的彩虹 transition 给 MAX 档位词。最多 18 个粒子 + 两个渐变在每帧重绘。抽屉打开时一直运行，且没有「系统动画关闭」的分支。对一个控制件来说，这是不必要的功耗与干扰；运动应该确认「变化」，而不是持续宣告「我在这里」。

### 2.7 P2：重复实现与空间

`AgentControlCard` 与 `ReasoningPicker` 各自实现了：`levels` / `levelCount`、滑杆、拇指、预览值、图标映射、文字取色。`ReasoningPicker` 还有一份完整的标题/提示布局。任何档位改动都要同步 6 处以上。

抽屉固定 0.8 高，header 固定不滚动。键盘弹出时 `imePadding()` 压缩可用高度，header 仍占 ~118dp，列表只剩 3–4 行。

---

## 3. 目标与非目标

**目标**

1. 一眼读出「当前模型 → 当前档位 → 这意味着什么」。
2. 一次手势改档：可见、可吸附、有触感、有就地反馈。
3. AUTO 是一个模式，不是刻度。
4. 亮/暗主题下文字与图形都达到可读阈值，且不依赖颜色单一编码。
5. 卡片高度不涨（不含可选的策略行）；抽屉列表至少保留 4 行（640dp 屏）。
6. 一个控件、一个色板、一个图标/文案映射，抽屉与设置页共用。

**非目标**

- 不改 `ReasoningLevel` 枚举、序列化格式或写入路径；无数据迁移。
- 不在本期改 provider 请求构造；不承诺「7 档在所有 provider 上都不同」。
- 不重做模型列表、搜索与收藏交互。
- 不把 surge 视觉拿掉；它是这张卡的身份，只需要更克制。

---

## 4. 方案对比

| | A. 精准修补 | B. 深度优先（推荐） | C. 流程重做 |
|---|---|---|---|
| 轴语义 | 保留 7 档，AUTO 仍在轴上 | AUTO 独立，6 档 | 深度移出抽屉，输入栏独立入口 |
| 刻度/吸附/触感 | ✅ | ✅ | ✅ |
| 对比度 | ✅ | ✅ | ✅ |
| 作用域说明 | 加一行字 | 标题 + 模型名 + 信息按钮 | 独立页面，天然清楚 |
| 策略胶囊 | 原位 | 降到卡下「运行策略 · 全局」 | 留在抽屉 |
| 卡片高度 | 不变 | ≈ 不变（策略行另计） | 抽屉更矮 |
| 代码重复 | 仍在 | 收敛为一个控件 | 收敛 |
| 风险/成本 | 低 / 1–2 天 | 中 / 3–4 天 | 高 / 1 周+ |
| 适合 | 只想先止血 | 一次做对 | 深度是高频操作时 |

**推荐 B**。A 是 B 的子集：PR1 先把对比度、刻度、触感、语义、动效门控这些独立修复落地；PR2 再做 AUTO 与信息架构。C 需要先有数据证明「用户频繁只为调深度打开抽屉」，否则会给已经很密的输入栏再加一个按钮。

---

## 5. 推荐方案 B 详设

### 5.1 信息架构

```
┌─────────────────────────────────────────────────────┐
│ 思考深度 · Claude Sonnet 4.5        [● 自动]  [ⓘ]   │ ← 48dp：标题 11sp + hero 24sp
│ 💡 沉思   模型将使用大量推理来回答问题。              │
│ ○────○────●────○────○────○                         │ ← 48dp 轨道 + 6 节点
│ 关闭                                       极致      │
└─────────────────────────────────────────────────────┘
  运行策略 · 全局        [审批 通用 ▾]  [派发 常规 ▾]     ← 可选，独立一行
  🔍 搜索模型
  … 模型列表 …
```

四个决定：

1. **标题与 hero 合并到一行 48dp**：左列上下两行（小标题 + 24sp 档位词 + 一句描述），右侧 AUTO chip 与信息按钮。这样既补了上下文，又**不增加卡片高度**（12 + 48 + 2 + 48 + 10 ≈ 120dp）。
2. **AUTO 移到标题行右侧**，形状与策略胶囊一致（26dp 视觉 / 48dp 触控）。轴只画 6 个手动档位。
3. **运行策略独立成行**，带「全局」字样。它在视觉上不属于 hero；如果高度预算不足，再把它收进抽屉右上角的「策略」图标（见 5.10 的 B2）。
4. **不支持推理的模型**：标题行保留模型名，hero 换成一行 `该模型不支持思考深度调节` + 信息入口，轨道隐藏，卡片高度用 `animateContentSize()` 平滑收缩。

### 5.2 档位状态机

| 起始 | 操作 | 结果 |
|---|---|---|
| 手动 `LOW…MAX` | 拖动 / 点击节点 | 预览吸附；松手写入该档；AUTO 保持关闭 |
| 手动 | 点 AUTO chip | 写入 `AUTO`；记录 `lastManual`（仅 UI 状态）；轨道变暗，显示虚线圈 |
| AUTO | 点 AUTO chip | 写回 `lastManual`（没有则 MEDIUM） |
| AUTO | 点 / 拖任意档位 | 退出 AUTO，写入该档 |
| AUTO | 打开抽屉 | 显示「自动 / 由模型决定」；虚线圈停在 `lastManual` |
| 任意 | 外部（设置页 / 同步）改档 | 非拖动状态下动画跟随；拖动中忽略，避免手指被抢 |

不持久化 `lastManual`，它只是本次交互的视觉记忆；重开抽屉时若当前是 AUTO，则不画虚线圈（或从 `assistant.reasoningLevel` 取一个非 AUTO 的默认值，若产品希望）。

**`AUTO` 的可用性**：Codex 明确不支持 AUTO（`codex_reasoning_auto_unsupported`）。PR2 先通过信息文案提示；PR3 在有 provider 信息时禁用 chip 并给出原因。

### 5.3 尺寸与 token

| 元素 | 规格 |
|---|---|
| 卡片 | `surfaceContainerHigh`，圆角 20dp；padding 水平 12dp / 上 12dp / 下 10dp |
| 标题行 | 48dp 高；标题 `labelSmall` 11sp / `onSurfaceVariant`；模型名同色 72% 透明，`maxLines=1` + Ellipsis |
| AUTO chip | 视觉 26dp / 触控 48dp；选中 `primaryContainer` + check，未选透明 + outline；`Role.Switch` 或 `FilterChip` 语义 |
| 档位词 | `headlineSmall` 24sp / Bold；颜色 = 当前档位色 |
| 描述 | `bodySmall` 12sp / `onSurfaceVariant`；`maxLines=1`，`TextOverflow.Ellipsis` |
| 轨道 | 48dp 触控；8dp 高圆角条；左右各内缩 10dp（拇指半径） |
| 节点 | 未选 6dp，选中 10dp + 2dp 卡片色描边；6 个等距 |
| 拇指 | 22dp；内点 8dp；卡片色 2dp 外环 |
| 气泡 | 拖动时出现在拇指上方 28dp 高，显示档位词；距顶部空间不足时翻到下方 |
| 端点标签 | `labelSmall` 11sp，左「关闭」右「极致」 |
| 策略行 | `labelSmall` + 两个 24dp 视觉胶囊；与卡片间距 4dp |

> 节点用等距而不是按 token 预算成比例：这是一条**序数**轴，不是对数预算轴；按 1k/2k/8k/16k/32k 画会让前两档挤在一起。等距 + 端点标签 + 当前词，信息足够。

### 5.4 颜色：同一色相，按主题取亮度

保留「冷 → 暖」的身份，但为亮/暗主题各取一套满足对比度的变体（同一色相）。下表以 `#F0E7D9` / `#2D2A21` 为底：

| 档位 | 亮色（新） | 对比度 | 暗色（新） | 对比度 |
|---|---|---:|---|---:|
| OFF | `#455A64` | 5.91 | `#B0BEC5` | 7.52 |
| AUTO | `#01579B` | 6.04 | `#4FC3F7` | 7.16 |
| LOW | `#006064` | 6.00 | `#4DD0E1` | 7.80 |
| MEDIUM | `#1B5E20` | 6.42 | `#81C784` | 7.12 |
| HIGH | `#7A4F00` | 5.82 | `#FFCA28` | 9.37 |
| XHIGH | `#A02C00` | 6.00 | `#FFA726` | 7.38 |
| MAX | `#B71C1C` | 5.36 | `#FF8A80` | 6.28 |

规则：

- 用 `LocalDarkMode`（主题层已有）选表，而不是 `isSystemInDarkTheme`，这样 AMOLED / 强制深色 / 动态色都一致。
- **文字与图标**用上表；**轨道填充**用同一颜色，光晕可以用更亮/更饱和的同色相低透明度叠加，保住 surge 的观感。
- **MAX**：彩虹只画在轨道与火花上，档位词用实色。若设计坚持彩虹字，至少给文字加与底色同色的 1.5dp 描边（不推荐，边缘会脏）。
- 动态取色下 `surfaceContainerHigh` 会变，但明暗方向与上表一致；实现时跑一遍实际的三个预设（Autumn / Black / Claude）+ 动态色，记录到 QA 表。
- **不依赖颜色**：档位词、节点位置、`stateDescription` 三者在，颜色只是加速识别。HIGH/XHIGH/MAX 的图标目前相同，可以保留（词是主标签）；如果要做冗余，加 1–3 根小竖条徽标，而不是再造一套不透明图标。

### 5.5 交互

- **拖动**：不要只依赖 M3 的隐式 `shouldAutoSnap`；在 `onValueChange` 里显式 `roundToInt()` 并加单测。与上一次吸附值不同时触发 `HapticFeedbackType.GestureThresholdActivate`（一次/档）；`onValueChangeFinished` 才调用 `onUpdateReasoningLevel`。点击轨道/节点立即写入。
- **气泡**：拖动时显示当前档位词，`pointerUp` 300ms 后淡出；点击节点也闪一次。避免手指遮住轨道。
- **AUTO**：点击立即写入（无二次确认——它不是危险操作）；从 AUTO 点任意档位时先切手动再写入。
- **信息按钮**：`AlertDialog` 或 `DropdownMenu`，文案复用 `reasoning_picker_hint` + 新增「仅对当前模型生效」+（PR3）当前 provider 的映射说明。不要在抽屉里再套 `ModalBottomSheet`。
- **写入节流**：拖动中不写 settings；只写一次。避免 settings flow 回灌造成拇指跳动。
- **外部更新**：`LaunchedEffect(level)` 里加 `if (!isDragging)`；或者用 `SliderState.isDragging`/自维护的 `dragging` 标记。当前实现没有这个保护。
- **键盘/搜索**：搜索框获得焦点时，用 `AnimatedVisibility` 把描述行和策略行收起，只留标题 + 词 + 轨道，给列表让出空间（P2-3）。可选。

语义草稿：

```kotlin
var snappedIndex by remember { mutableIntStateOf(initialIndex) }
val title = stringResource(R.string.reasoning_depth_title)
val label = stops[snappedIndex].label()  // @Composable，需在 semantics 之外取值

Slider(
    value = sliderValue,
    onValueChange = { raw ->
        val next = raw.roundToInt().coerceIn(0, stops.lastIndex)
        if (next != snappedIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            snappedIndex = next
        }
        sliderValue = next.toFloat()
    },
    onValueChangeFinished = { onLevelChange(stops[snappedIndex]) },
    valueRange = 0f..stops.lastIndex.toFloat(),
    steps = stops.size - 2,
    modifier = Modifier
        .fillMaxWidth()
        .semantics {
            contentDescription = title
            stateDescription = "$label，${snappedIndex + 1}/${stops.size}"
        },
    // thumb / track ...
)
```

> 如果要做 6 个完全对齐的节点、逐档触感和 RTL 填充，最稳的是自绘 rail + `pointerInput`，自己维护 `setProgress` 语义。用 M3 `Slider` + 自定义 track 时，拇指对内部档位的定位会参考 track 的 corner size（默认 track 会提供，自绘 track 不提供则为 0）；PR1 在真机上做一次像素对齐验证，PR2 再决定是否换自绘。

### 5.6 动效

| 状态 | 表现 |
|---|---|
| 静止 · OFF / AUTO | 完全静态；AUTO 只显示模式色与虚线圈 |
| 静止 · LOW–HIGH | 静态填充 + 极轻的呼吸光晕（6s，α 0.04–0.10），最多 1 条 transition |
| 拖动 | 光带沿轨道流动 + 6–8 个火花；停留 1.2s 内能量最高，松手后 400ms 内衰减回静止 |
| MAX | 轨道彩虹 8s 循环；火花最多 10 个；文字保持实色 |
| 减少动画 | 系统 `ANIMATOR_DURATION_SCALE == 0` 或无障碍「移除动画」→ 全部静态，只保留颜色与节点 |
| 抽屉隐藏 | 组合被移除即可，无需额外处理；不要在 `ModelListSheet` 之外继续跑 |

实现上：

- 合并为 **1 个** `rememberInfiniteTransition`，用同一 phase 派生 pulse / particles，避免 3 条曲线。
- `animate` 只为 `isDragging || level in HIGH..MAX`；OFF/AUTO 不创建 transition。
- 粒子列表 `remember(level)` 缓存；不要在 draw 里分配。
- 只有在动画启用时才在 Canvas 里读取 phase；否则提前返回，避免每帧 draw invalidation。
- 彩虹刷按主题缓存（或至少在 `drawWithCache` 里按 phase 派生），不要每帧新建 `Brush` + 7 个 Color。

### 5.7 文案与 i18n

现有可复用（7 个语言都已存在）：

- `reasoning_picker_hint`：「并非所有模型都支持深度调整。请参考模型和提供商文档。」
- `reasoning_off/auto/light/medium/heavy/xhigh/max`
- 5 条 `reasoning_*_desc`（off / auto / light / medium / heavy）目前**零引用**，正好用作 hero 描述。
- 缺少 `reasoning_xhigh_desc` / `reasoning_max_desc`（7 个语言都要补）。

建议新增（每个语言 1 份，共 7 个文件）：

| key | zh | en |
|---|---|---|
| `reasoning_depth_title` | 思考深度 | Thinking depth |
| `reasoning_scope_model` | 仅对此模型生效 | Applies to this model only |
| `reasoning_auto_manual_hint` | 轻触档位可手动指定 | Tap a level to set it manually |
| `reasoning_unsupported` | 该模型不支持思考深度调节 | This model does not support thinking depth |
| `reasoning_run_policy` | 运行策略 | Run policy |
| `reasoning_run_policy_global` | 全局 | Global |
| `reasoning_level_position` | %1$d / %2$d | %1$d / %2$d |

文案原则：

- 标题里带模型名，明确「当前模型」；信息弹窗里写「仅对当前模型生效」，因为写入是 model 级。
- 策略行必须写「全局」，避免用户把审批/派发当成单次对话设置。
- 档位词保持现状（关闭/自动/快速/均衡/沉思/研究/极致），避免一次改 7 个语言的命名；但建议另开一次 copy pass 处理两个已知问题：韩语 XHIGH 未翻译（`XHigh`），以及「研究」与应用内 deep research 功能的撞名。这个 pass 不阻塞本方案。

### 5.8 可达性、RTL 与字体缩放

**可达性**

- 所有可点元素 ≥ 48×48dp；不要用固定 `height` 压缩（`ChatInput` 里为紧凑而压到 30dp 的先例是特例，不适用于卡片）。
- Slider 补 `contentDescription` + `stateDescription`（「沉思，4/6」）；节点作为装饰不单独获取焦点（`Modifier.clearAndSetSemantics {}` 或 `invisibleToUser`）。
- AUTO chip 用 `Role.Switch` / `toggleableState`；`ControlChip` 已有 `contentDescription` 的先例。
- 拖动中不要每档都触发 TalkBack announcement（会吵）；只在 `onValueChangeFinished` 更新 `stateDescription`，或依赖焦点时的状态朗读。
- 对比度：文字 ≥ 4.5:1（本方案全部 ≥ 5.36）；轨道/节点等图形 ≥ 3:1。

**RTL**

- 用 `LayoutDirection` 镜像所有自绘坐标：填充起点、节点顺序、气泡、火花；端点标签用 `Row` 自动镜像，不要画进 Canvas 文本。
- 拖动方向：M3 Slider 已处理；自绘 rail 需要 `value = max - raw`。
- 测试：`ar` locale + RTL 强制，检查填充是否仍从拇指的「起点」增长。

**字体缩放**

- 标题、模型名、描述、端点：`maxLines=1` + Ellipsis。
- 档位词：允许 2 行（`maxLines=2`），不要截断；卡片 `animateContentSize()`。
- 策略行：`FlowRow` 或允许换行；2.0× 时上下堆叠而不是挤出屏幕。
- 抽屉的 `fillMaxHeight(0.8f)` 在 2.0× 下给 header 更多高度，列表仍可滚动。

### 5.9 代码结构

新增两个文件，替换重复实现：

```
ui/components/ai/reasoning/
  ReasoningVisuals.kt      // 颜色（亮/暗）、图标、label、description 的唯一映射
  ThinkingDepthSection.kt  // 标题 + AUTO + hero + 6 档轨道 + 气泡 + 语义
```

保留：

- `AgentControlCard.kt` → 变成「模型上下文 + `ThinkingDepthSection` + 运行策略行」的薄壳。
- `ReasoningPicker.kt` → 设置页的 sheet 壳复用 `ThinkingDepthSection`（去掉自己的滑杆 / 拇指 / 预览状态）。
- `ModelList.kt` → 不变；header 仍由 `ChatInput` 传入。

API 草案：

```kotlin
@Composable
fun ThinkingDepthSection(
    level: ReasoningLevel,
    onLevelChange: (ReasoningLevel) -> Unit,
    modifier: Modifier = Modifier,
    modelName: String? = null,
    autoSupported: Boolean = true,
    onInfoClick: (() -> Unit)? = null,
    compact: Boolean = true,
)
```

职责：

- 内部持有 `previewLevel` / `isDragging` / `lastManual`；`level` 是唯一真相，外部变化时非拖动状态下同步。
- `manualStops = ReasoningLevel.entries.filter { it != AUTO }`；`ReasoningLevel.entries` 不再在两个文件各存一份。
- `ReasoningVisuals` 提供 `color(level)`, `icon(level)`, `label(level)`, `description(level)`；`color` 读取 `LocalDarkMode`。
- `AgentControlCard` 保留审批/派发逻辑（或抽 `AgentRunPolicyRow`），只把布局降级。

**预览**（这是本次最划算的回归保险）：

```kotlin
@Preview(name = "Manual – HIGH", showBackground = true)
@Preview(name = "Auto", showBackground = true)
@Preview(name = "Unsupported", showBackground = true)
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Preview(name = "Font 2.0", fontScale = 2f)
@Preview(name = "Russian", locale = "ru")
@Preview(name = "RTL", locale = "ar", layoutDirection = LayoutDirection.Rtl)
```

**单测**（JVM，进现有 `testDebugUnitTest` 门槛）：

- `manualStops` 顺序稳定且不含 AUTO；
- `levelToStopIndex` / `stopIndexToLevel` 互逆；
- AUTO → `lastManual` 的回退逻辑；
- 色板对比度最小值的纯函数测试（项目已有 `WallpaperContrastTest` 这类先例）。

### 5.10 落地顺序与工作量

| PR | 内容 | 估算 |
|---|---|---|
| **PR1 · 止血** | 抽 `ReasoningVisuals` + `ThinkingDepthSection` 骨架；对比安全色；节点 + 吸附 + 触感 + 气泡；`stateDescription`；拖动期间不重置；动效门控；不支持状态；预览 | 1.5–2 天 |
| **PR2 · 轴语义 / IA** | AUTO chip + 6 档；标题/模型名/描述/信息弹窗；策略行降级；7 语言新增字符串 + 2 条 desc；设置页共用 | 2 天 |
| **PR3 · provider 真实性（可选）** | `ReasoningProfile`（支持的档位、AUTO 是否支持、是否合并）；Gemini 3 / Codex / Ark / Mistral 映射；effective 档位说明 + 单测 | 1–2 天 |
| 翻译 / QA | 7 个语言 review；360/412dp、1.0/1.3/2.0×、亮/暗/AMOLED/动态色、ru/ar/ko、TalkBack、减少动画 | 1.5 天 |

PR1 与 PR2 可以合并成一个 PR，但建议分两次 review：第一次只看「可读性/可达性」，第二次再看「产品语义」。

**高度预算**（关键约束）：

| 方案 | 卡片 | 策略行 | 合计 |
|---|---:|---:|---:|
| 现状 | ≈118dp | 含在卡内 | ≈118dp |
| B1 推荐（策略独立一行） | ≈120dp | ≈42dp | ≈162dp |
| B2 紧凑（策略收进抽屉右上角图标） | ≈120dp | 0 | ≈120dp |

B1 在 640dp 屏上仍能留出约 3–4 行模型（取决于键盘）；如果 QA 发现不足，直接切 B2，不要压缩触控或字号。

### 5.11 验收标准

- [ ] 6 个档位在轨道上可见；点击/拖动精确落到档位；每过一档触发一次触感；松手后拇指与文字一致。
- [ ] 拖动中显示档位气泡；松手 300ms 内淡出。
- [ ] AUTO 不在轴上；打开/关闭 AUTO 的三种路径（chip、点档位、外部更新）都正确；重启后值不变。
- [ ] 亮/暗主题下 7 个档位词、图标、节点对 `surfaceContainerHigh` 的对比度分别 ≥ 4.5:1 / ≥ 3:1；MAX 的彩虹不出现在文字上。
- [ ] TalkBack 读「思考深度，沉思，4/6」，而不是「66 percent」；AUTO chip 读「自动，开关」。
- [ ] RTL 下填充、节点、气泡、端点都镜像；拖动方向与视觉一致。
- [ ] 200% 字体、320dp 宽、ru/ko/ar 下无裁切/溢出；档位词最多 2 行。
- [ ] 模型不支持推理时显示明确文案，而不是空白；卡片高度平滑变化。
- [ ] OFF/AUTO 无无限动画；其余状态 ≤ 1 条 infinite transition；系统关闭动画时完全静态。
- [ ] 抽屉与设置页使用同一个 `ThinkingDepthSection`；没有第二份 `levels` / 拇指 / 预览状态。
- [ ] 新增预览覆盖 manual / auto / unsupported / dark / 2.0× / ru / RTL；单测覆盖档位映射；`assembleDebug testDebugUnitTest` 绿。
- [ ] 卡片高度不超过现状 + 2dp（B2）或 +44dp（B1）；640dp 屏抽屉列表 ≥ 4 行。

### 5.12 风险与缓解

| 风险 | 缓解 |
|---|---|
| 推翻 2026-10-16/17 的「三种控件一张卡」拍板 | 保留 surge、保留策略一键访问；只在视觉上分级。把本提案作为「同一意图的下一版」，不是回滚 |
| 用户已习惯 AUTO 在第二档 | AUTO chip 仍在标题行右侧、位置醒目；文案从「自动」保持不变 |
| 卡片变高挤压列表 | B1/B2 二选一；先量 640dp 真机再定 |
| 共享控件让设置页回归 | 设置页保留标题/提示，只替换滑杆与视觉；预览矩阵覆盖两种壳 |
| 7 语言翻译量 | 先补 zh/en/zh-TW/ja，其余缺失回退英文；发布前补齐 |
| provider 映射做不准 | PR3 可选；PR2 只用现有 `reasoning_picker_hint` 说明「部分档位会合并」 |
| 动态色导致对比度漂移 | 用实际主题跑对比度单测；失败时退回 `onSurface` + 色点，不硬编码 |

---

## 6. 备选 A：一天版止血补丁

如果暂时不想动信息架构，按下面 7 条改，风险最低：

1. **对比度**：`reasoningLevelColor` 读取 `LocalDarkMode`，亮色用 §5.4 的深色变体；MAX 词改实色。
2. **刻度**：在 `ReasoningSurgeTrack` 里按 7 个等距位置画 6dp 节点（选中 10dp）；端点加「关闭 / 极致」。
3. **吸附与触感**：`onValueChange` round 到整数；跨档触发 `GestureThresholdActivate`；`onValueChangeFinished` 写入。
4. **拖动保护**：`LaunchedEffect(currentIndex)` 加 `if (!isDragging)`。
5. **语义**：Slider 加 `contentDescription` + `stateDescription`；不支持时显示一行说明。
6. **动效门控**：只在 `isDragging || level >= HIGH` 时创建 transition；减少粒子到 ≤ 8。
7. **气泡**：拖动时在拇指上方显示档位词（可选，但收益最大）。

这 7 条能关掉 P0 与大部分 P1，但 AUTO 仍在轴上、两套实现仍在、策略仍抢第一行。

---

## 7. 待确认

1. **AUTO 的定位**：接受「模式 chip」，还是坚持留在轴上（若坚持，至少要加视觉分隔与说明）？
2. **策略行**：B1（卡下独立一行）还是 B2（抽屉右上角图标）？需要 640dp 真机的列表行数数据。
3. **档位命名**：是否借这次统一「研究 / 极致」等词？这会改 7 个语言，建议单独一次 copy pass。
4. **provider 真实性**：PR3 是否要做？如果做，Gemini 3 的 HIGH/XHIGH/MAX 合并要不要在 UI 上标成「高（合并）」？
5. **流程**：选完模型后是否要保持抽屉打开以便调深度？还是维持「选中即关」？

---

## 附录 A：当前 vs 建议色板的完整对比度

在 `#F0E7D9`（Autumn light）、`#2D2A21`（Autumn dark）上计算；方法为 WCAG 2.1 相对亮度。

| 档位 | 当前 | 亮底 | 暗底 | 建议亮色 | 亮底 | 建议暗色 | 暗底 |
|---|---|---:|---:|---|---:|---|---:|
| OFF | `#8B96A3` | 2.45 | 4.77 | `#455A64` | 5.91 | `#B0BEC5` | 7.52 |
| AUTO | `#29B6F6` | 1.88 | 6.22 | `#01579B` | 6.04 | `#4FC3F7` | 7.16 |
| LOW | `#26C6DA` | 1.68 | 6.94 | `#006064` | 6.00 | `#4DD0E1` | 7.80 |
| MEDIUM | `#66BB6A` | 1.93 | 6.06 | `#1B5E20` | 6.42 | `#81C784` | 7.12 |
| HIGH | `#FFB300` | **1.46** | 7.99 | `#7A4F00` | 5.82 | `#FFCA28` | 9.37 |
| XHIGH | `#FB8C00` | 1.94 | 6.04 | `#A02C00` | 6.00 | `#FFA726` | 7.38 |
| MAX | `#E53935` | 3.45 | 3.39 | `#B71C1C` | 5.36 | `#FF8A80` | 6.28 |

Black / Claude 预设的 `surfaceContainerHigh` 分别为 `#EBEBEB` / `#EDEAE0`（亮）与 `#343434` / `#343230`（暗），结论方向相同；实现后用实际主题复跑。

## 附录 B：代码位置

| 内容 | 位置 |
|---|---|
| 档位枚举 / 预算 | `ai/src/main/java/me/rerere/ai/core/Reasoning.kt:7-44` |
| 卡片本体 | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/AgentControlCard.kt:69-197` |
| 策略 chip | 同上 `:200-292` |
| 独立 picker | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ReasoningPicker.kt:108-207` |
| 色板 / 彩虹 | 同上 `:232-249` |
| 档位词 / 彩虹刷 | 同上 `:291-315` |
| surge track | 同上 `:334-476` |
| 抽屉 / header 插槽 | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ModelList.kt:272-330` |
| 输入栏入口 | `app/src/main/java/me/rerere/rikkahub/ui/components/ai/ChatInput.kt:435-531` |
| 深度写入 | `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt:950-963` |
| 模型解析回退 | 同上 `:1407-1413` |
| Gemini 3 映射 | `ai/src/main/java/me/rerere/ai/provider/providers/google/GoogleProvider.kt:449-471` |
| Codex 映射 | `app/src/main/java/me/rerere/rikkahub/data/codex/CodexProvider.kt:245-254` |
| 文案 | `app/src/main/res/values/strings.xml:732-746`、`values-zh/strings.xml:595-608` |

## English summary

The thinking-depth card at the top of the model-selection sheet mixes three scopes (global approval, global dispatch, per-model depth) and treats `AUTO` as the second stop of a 7-step slider. That axis is not monotonic: `AUTO` means "let the provider decide", while moving right to `LOW` pins a 1k-token budget. Seven stops are invisible because the custom track replaces Material's tick marks, and the thumb moves continuously while the label snaps to the nearest integer. In light themes the level word measures 1.46–2.45:1 against `surfaceContainerHigh`; only MAX passes 3:1, and its rainbow text is worse.

This proposal: move `AUTO` to a mode chip; expose six real stops with visible nodes, per-stop haptics and a thumb bubble (making the implicit `shouldAutoSnap` behaviour explicit and tested); use per-theme, same-hue contrast-safe palettes; add a title/model line, the unused `reasoning_*_desc` copy, and an "applies to this model only" info dialog; demote the two run-policy chips to a clearly labelled global row; and extract one `ThinkingDepthSection` shared by the drawer and the settings picker, with RTL, `stateDescription`, font-scale and motion gating fixed. The card keeps its current height; the policy row is the only optional addition. No enum or storage migration is required.

---

*配套原型：[thinking-depth-card.html](./thinking-depth-card.html)。原型按 360dp 宽、1× 字体、1 CSS px ≈ 1dp 绘制（描边、阴影与标注点除外），仅用于评审，不是 Compose 实现。*
