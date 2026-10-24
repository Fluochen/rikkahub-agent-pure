package me.rerere.rikkahub.service.mission

/**
 * Built-in workflow documents for the workbench.
 *
 * A mission is only as good as its playbook. Shipping an empty `workflows/` folder forced every
 * user to invent the same six documents, so the app seeds a small library of real ones the first
 * time a mission workspace is created. They are ordinary Markdown files: the user can edit,
 * rename or delete them, and the launcher treats an edit as the new truth on the next round.
 *
 * Conventions shared by every template:
 * - The six sections match [MissionWorkflowDocs.template] so the editor and the model see the
 *   same shape.
 * - Steps are ordered and each one names an observable result, because a step that cannot be
 *   checked is a step the model will skip.
 * - The acceptance section is written as checkboxes so it can be copied into the mission's
 *   acceptance criteria.
 * - The last section always resolves conflict in favour of the mission goal.
 */
object WorkflowTemplates {

    data class Template(
        /** Stable id, also the default file stem. */
        val id: String,
        /** Short name shown in the workflow library. */
        val name: String,
        /** Grouping label in the library. */
        val category: String,
        /** One-line description for the list row. */
        val summary: String,
        /** Full Markdown body written to `workflows/<id>.md`. */
        val body: String,
    ) {
        /** File name the template is seeded under. */
        val fileName: String get() = "$id.md"
    }

    val ALL: List<Template> = listOf(
        Template(
            id = "coding",
            name = "编程实现",
            category = "编程开发",
            summary = "从需求到可运行代码：先读代码库，再小步实现，最后按用户路径验证。",
            body = """
                # 编程实现

                ## 目标
                把需求变成可运行、可维护的代码，并在真实路径上验证通过。

                ## 输入
                - 需求与验收标准（本次任务的目标）
                - 代码库位置、技术栈、构建/测试命令
                - 不可改动的模块、兼容性要求、截止时间

                ## 步骤
                1. 读代码：定位相关模块、现有实现、调用方和测试；先复述当前行为，再动手。
                2. 定方案：列出最小必要改动，写清影响面；不确定的假设先标注。
                3. 小步实现：每步只改一个关注点，保持与项目现有风格一致，不做无关重构。
                4. 自测：运行项目已有的构建、单测、lint 或最小复现脚本；失败先读错误再改。
                5. 回归：检查改动是否影响相邻功能，特别是错误分支和空数据。
                6. 交付：给出改动文件、关键 diff 说明、验证命令与结果。

                ## 产出
                - 代码改动（写清文件路径）
                - 必要的测试或用例
                - `docs/` 或任务时间线里的变更说明：改了什么、为什么、怎么验证

                ## 验收
                - [ ] 需求中的每个验收点都有对应行为
                - [ ] 构建/测试命令实际运行过，结果记录在案
                - [ ] 没有未说明的破坏性改动或无关文件
                - [ ] 已知限制与剩余风险已写明

                ## 注意事项
                - 不在没有读代码的情况下重写模块。
                - 不宣称未运行的测试已通过。
                - 流程与任务目标冲突时，以任务目标为准，并在时间线里说明。
            """.trimIndent(),
        ),
        Template(
            id = "bugfix",
            name = "缺陷排查",
            category = "编程开发",
            summary = "先复现再修复：固定最小复现，定位根因，修复后证明同类问题不再出现。",
            body = """
                # 缺陷排查

                ## 目标
                找到缺陷的根因并修复，同时证明修复有效、没有引入新的回归。

                ## 输入
                - 现象、报错、复现步骤、发生频率
                - 相关日志、版本、设备/环境信息
                - 期望行为与业务影响

                ## 步骤
                1. 复现：用最小步骤稳定复现；不能复现时先补日志或缩小条件。
                2. 定位：从报错点向外追调用链，区分「触发条件」和「根因」，不要停在第一个可疑行。
                3. 假设与验证：每次只验证一个假设，记录证据（日志、断点、实验）。
                4. 修复：做最小根因修复；必要时加防御，但不要用吞异常掩盖问题。
                5. 回归：补一个能覆盖该缺陷的测试或可重复检查，运行相关测试集。
                6. 复盘：写明根因、影响范围、修复点和后续预防措施。

                ## 产出
                - 修复 diff 与说明
                - 最小复现步骤（修复前失败、修复后通过）
                - 回归测试或检查脚本

                ## 验收
                - [ ] 原始复现步骤在修复后通过
                - [ ] 根因有证据支持，不只是「改了就好了」
                - [ ] 相关测试通过，未运行的部分已说明
                - [ ] 影响范围与回滚方式已记录

                ## 注意事项
                - 不在未复现的情况下盲改。
                - 不把一个缺陷的修复扩散成大规模重构。
                - 流程与任务目标冲突时，以任务目标为准，并在时间线里说明。
            """.trimIndent(),
        ),
        Template(
            id = "visual",
            name = "视觉创作",
            category = "艺术创作",
            summary = "从风格板到成套成图：先定视觉语言，再生成、筛选、统一、交付。",
            body = """
                # 视觉创作

                ## 目标
                产出一套风格统一、可直接使用的视觉作品（插画、海报、角色、界面概念等）。

                ## 输入
                - 用途、尺寸/比例、交付格式
                - 主题、情绪、受众、必须出现的元素
                - 参考图或风格关键词；禁止出现的元素

                ## 步骤
                1. 定方向：用 3-5 个关键词描述视觉语言（构图、光线、材质、色彩、质感）。
                2. 做小样：先出低分辨率草图或 2-3 个方向，比较后再投入精修。
                3. 定稿：确定构图与配色，记录可复用的提示词/参数/素材来源。
                4. 成套：按同一视觉语言扩展需要的尺寸与变体，保持角色、光源、色板一致。
                5. 检查：放大看手部、文字、边缘、重复纹理等常见瑕疵；必要时局部重绘。
                6. 交付：导出目标格式，附源文件、提示词和授权/素材说明。

                ## 产出
                - 成图（按约定尺寸与格式）
                - 源文件/工程文件
                - 提示词、参数、参考素材清单

                ## 验收
                - [ ] 主题与必须元素齐全，禁止元素未出现
                - [ ] 成套作品风格一致
                - [ ] 目标尺寸下无模糊、错字、明显结构错误
                - [ ] 交付格式与命名符合约定

                ## 注意事项
                - 不直接使用来源不明的受版权保护素材。
                - 不把低分辨率草图当作最终交付。
                - 流程与任务目标冲突时，以任务目标为准，并在时间线里说明。
            """.trimIndent(),
        ),
        Template(
            id = "writing",
            name = "文学创作",
            category = "文学创作",
            summary = "从设定到成稿：先立人物与冲突，再写场景，最后统一语气与节奏。",
            body = """
                # 文学创作

                ## 目标
                完成一篇语气统一、人物可信、有明确推进的文学作品（短篇、章节、剧本等）。

                ## 输入
                - 体裁、篇幅、目标读者、发布场景
                - 主题与情绪基调；必须出现/禁止出现的内容
                - 已有设定、人物小传、前文（如有）

                ## 步骤
                1. 立骨架：一句话故事（谁想要什么、什么阻挡、代价是什么）。
                2. 定人物：主要人物的欲望、恐惧、说话方式；配角只保留推动情节的功能。
                3. 排场景：每个场景写清「进入状态 → 冲突 → 变化 → 退出状态」，删掉不改变任何东西的场景。
                4. 写初稿：一次写完一个场景，不在初稿阶段反复润色开头。
                5. 修订：检查视角是否一致、时间线是否自洽、对话是否符合人物、结尾是否兑现开头。
                6. 朗读：通读一遍，删掉重复解释和形容词堆叠，确认节奏。

                ## 产出
                - 成稿（Markdown 或约定格式）
                - 人物/设定小传
                - 修订说明：改了什么、为什么

                ## 验收
                - [ ] 开头建立的问题在结尾得到回应
                - [ ] 主要人物有可辨认的欲望与变化
                - [ ] 视角、时态、人称前后一致
                - [ ] 篇幅与格式符合约定

                ## 注意事项
                - 不用总结代替场景，不用解释代替行动。
                - 不模仿在世作者的独特文风到可识别的程度。
                - 流程与任务目标冲突时，以任务目标为准，并在时间线里说明。
            """.trimIndent(),
        ),
        Template(
            id = "research",
            name = "调研分析",
            category = "调研分析",
            summary = "先问对问题，再找一手来源，交叉验证后给出可执行结论。",
            body = """
                # 调研分析

                ## 目标
                回答一个明确的决策问题，结论有可靠来源支撑，并给出可执行的建议。

                ## 输入
                - 决策问题与使用场景
                - 时间范围、地域、对象范围
                - 已有资料、必须排除的来源

                ## 步骤
                1. 拆问题：把大问题拆成 3-6 个可验证的子问题，写清每个子问题需要什么证据。
                2. 找来源：优先一手来源（官方文档、原始数据、当事人声明），记录发布日期。
                3. 交叉验证：关键数字至少两个独立来源；冲突时说明差异与采信理由。
                4. 建结构：按子问题组织证据，区分事实、推断、假设。
                5. 出结论：给出推荐方案、适用条件、主要风险和不建议的做法。
                6. 留痕：保存链接、访问日期、关键摘录，方便复核。

                ## 产出
                - 调研报告（结论先行，附证据）
                - 来源清单（链接 + 日期 + 可信度说明）
                - 可执行建议与下一步

                ## 验收
                - [ ] 每个结论都能追溯到来源或计算过程
                - [ ] 关键数字经过交叉验证
                - [ ] 事实、推断、假设分开标注
                - [ ] 报告回答了原始决策问题

                ## 注意事项
                - 不把搜索摘要当作原文，不引用无法打开的链接。
                - 不隐瞒与结论冲突的证据。
                - 流程与任务目标冲突时，以任务目标为准，并在时间线里说明。
            """.trimIndent(),
        ),
        Template(
            id = "media",
            name = "视频与音频",
            category = "内容制作",
            summary = "从脚本到成片：先定节奏与分镜，再配音/配乐、渲染、检查、交付。",
            body = """
                # 视频与音频制作

                ## 目标
                产出一条节奏完整、音画同步、可直接发布的视频或音频作品。

                ## 输入
                - 平台、时长、画幅、交付格式
                - 主题、受众、必须出现的信息
                - 素材、配音/配乐、字幕要求

                ## 步骤
                1. 写脚本：按「钩子 → 展开 → 转折 → 收束」写成分镜或段落，标好每段时长。
                2. 备素材：画面、配音、音乐、音效分别建目录，记录来源与授权。
                3. 粗剪：先按脚本拼结构，确认信息完整，再处理节奏。
                4. 精修：统一音量、字幕、转场、色彩；检查音画同步与黑帧。
                5. 导出：按平台要求导出，检查分辨率、码率、响度、首尾帧。
                6. 交付：附脚本、素材清单、工程文件与成片。

                ## 产出
                - 成片（约定格式与分辨率）
                - 脚本/分镜
                - 素材与授权清单

                ## 验收
                - [ ] 时长、画幅、格式符合平台要求
                - [ ] 音画同步，无爆音、黑帧、错字
                - [ ] 字幕与口播一致
                - [ ] 素材来源可追溯

                ## 注意事项
                - 不使用未授权的音乐、字体和影像素材。
                - 不在未检查响度的情况下直接发布。
                - 流程与任务目标冲突时，以任务目标为准，并在时间线里说明。
            """.trimIndent(),
        ),
        Template(
            id = "automation",
            name = "运营与自动化",
            category = "运营自动化",
            summary = "把重复流程变成可定时、可检查、失败可恢复的自动任务。",
            body = """
                # 运营与自动化

                ## 目标
                把一个重复性流程变成稳定、可观测、失败可恢复的自动任务。

                ## 输入
                - 触发条件（时间、事件、手动）
                - 输入数据与输出目标
                - 失败时的处理方式与通知对象

                ## 步骤
                1. 画流程：把现状写成步骤，标出人工判断点、外部依赖和失败点。
                2. 定边界：明确自动任务做什么、不做什么；需要人确认的步骤单独标出。
                3. 先手动跑通：用真实数据手动执行一遍，记录每步耗时和输出。
                4. 自动化：脚本/工具化，加入输入校验、幂等、超时和重试上限。
                5. 加观测：记录每次运行的状态、耗时、产物位置；失败要能看出卡在哪一步。
                6. 试运行：连续跑 2-3 次，覆盖空数据、重复触发和外部失败。
                7. 交付：运行说明、回滚方式、维护入口。

                ## 产出
                - 自动化脚本/任务配置
                - 运行日志与产物目录
                - 操作手册（如何暂停、重跑、回滚）

                ## 验收
                - [ ] 连续多次运行结果一致（幂等）
                - [ ] 外部失败有明确报错和恢复路径
                - [ ] 关键步骤有日志可查
                - [ ] 人工确认点没有被静默跳过

                ## 注意事项
                - 不在未备份的情况下批量修改生产数据。
                - 不设置无上限的重试或无限循环。
                - 流程与任务目标冲突时，以任务目标为准，并在时间线里说明。
            """.trimIndent(),
        ),
    )

    /** Lookup by id or Chinese/English name, case-insensitive. Null when unknown. */
    fun find(key: String): Template? {
        val normalized = key.trim().removeSuffix(".md")
        if (normalized.isBlank()) return null
        return ALL.firstOrNull { template ->
            template.id.equals(normalized, ignoreCase = true) ||
                template.name.equals(normalized, ignoreCase = true)
        }
    }

    /** `id.md` for every template, in declaration order. */
    fun fileNames(): List<String> = ALL.map { it.fileName }
}
