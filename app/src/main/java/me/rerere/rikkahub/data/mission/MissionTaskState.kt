package me.rerere.rikkahub.data.mission

/**
 * 批 F — 任务的跨轮状态文件（`task-state.md`）。
 *
 * 任务是一轮一轮跑的，每一轮其实是一个「新员工接班」：他看到的是别人留下的交接条。以前这个交接
 * 条只有督导拼的 800 字尾部 + 产物清单，跑到十几轮之后基本靠猜。这个文件把「任务现在到哪一步了」
 * 从模型脑子里挪到磁盘上，而且格式固定 —— 它是长任务唯一的长期记忆，压缩摘要只是它的备份。
 *
 * 约定：
 * - 位置：任务工作区的 FILES 根目录，文件名固定 `task-state.md`。
 * - 内容由模型维护（它本来就有工作区文件工具），系统只负责**每轮注入**和**检查有没有变**。
 * - 系统不写这个文件：模型忘了更新时，系统只记一条 journal（`state_unchanged`），不替模型编。
 *
 * 这里是纯字符串逻辑（JVM 可测），IO 走 [me.rerere.rikkahub.service.mission.MissionStateSource]。
 */
object MissionTaskState {

    /** 工作区 FILES 根目录下的状态文件名。 */
    const val PATH: String = "task-state.md"

    /** 每轮注入的最大字符数：够写一份像样的进度，又不至于把提示词撑爆。 */
    const val MAX_INJECT_CHARS: Int = 4_000

    /** 空/缺失状态的指纹（也是 journal 里记录的形式）。 */
    const val EMPTY_FINGERPRINT: String = "empty"

    /**
     * 每轮开头注入的状态块。文件存在 → 贴内容（截断到 [MAX_INJECT_CHARS]，保留尾部，因为「下一步」
     * 通常写在最后）；不存在 → 明确要求本轮建档。
     */
    fun composeBlock(content: String?): String {
        val text = content?.trim().orEmpty()
        if (text.isEmpty()) {
            return buildString {
                appendLine("[任务状态] 还没有 $PATH。")
                appendLine("开工前先在工作区根目录创建它，用简短分节记录：目标 / 已完成 / 进行中 / 待办 / 已知问题 / 下一步。")
                append("每轮结束前更新它 —— 下一轮的你只能靠这份记录接上。")
            }
        }
        val clipped = if (text.length > MAX_INJECT_CHARS) {
            "…（前 ${text.length - MAX_INJECT_CHARS} 字符已省略）\n" + text.takeLast(MAX_INJECT_CHARS)
        } else {
            text
        }
        return buildString {
            appendLine("[任务状态] 工作区的 $PATH 记录了上一轮的进度：")
            appendLine(clipped)
            appendLine()
            append("结束本轮前请更新它（目标 / 已完成 / 进行中 / 待办 / 已知问题 / 下一步）；与实际情况冲突时以实际为准。")
        }
    }

    /** 任务守则里固定出现的一条规则（每轮都注入，压缩掉所有其他东西也要留下这条）。 */
    fun directiveRule(): String =
        "- 跨轮记忆只认工作区的 $PATH：开工先读它，收工前更新它（目标 / 已完成 / 进行中 / 待办 / " +
            "已知问题 / 下一步）。不要只在对话里描述进度 —— 下一轮看不到你的对话。"

    /**
     * 内容指纹，用来判断一轮下来状态文件有没有被动过。
     *
     * 不追求密码学强度：只需要「同一段文本稳定、改一个字就变」。空/缺失统一为 [EMPTY_FINGERPRINT]。
     */
    fun fingerprint(content: String?): String {
        val text = content ?: return EMPTY_FINGERPRINT
        if (text.isBlank()) return EMPTY_FINGERPRINT
        return "${text.length}:${text.hashCode().toUInt().toString(16)}"
    }
}
