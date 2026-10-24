package me.rerere.rikkahub.service.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import me.rerere.rikkahub.data.mission.MissionTaskState

/**
 * 批 F — 交接提示的增量：任务状态块、检查失败提示、系统事实摘要，以及「检查未通过」的返工提示。
 */
class MissionContinueComposerLongRunTest {

    private fun ctx(
        stateBlock: String? = null,
        checkNote: String? = null,
        roundDigest: String? = null,
        tail: String? = "上一轮收尾",
    ) = HandoffContext(
        title = "整理相册",
        goal = "把截图按应用归类",
        goalsChecked = emptyList(),
        artifacts = listOf("/workspace/out/index.md"),
        lastRunTail = tail,
        round = 3,
        maxRounds = 6,
        stateBlock = stateBlock,
        checkNote = checkNote,
        roundDigest = roundDigest,
    )

    @Test
    fun `state block is injected before the artifact list`() {
        val text = MissionContinueComposer.compose(
            ctx(stateBlock = MissionTaskState.composeBlock("## 进行中\n归档第 3 批"))
        )

        assertTrue(text.contains("归档第 3 批"))
        assertTrue(text.indexOf("任务状态") < text.indexOf("已产出清单"))
    }

    @Test
    fun `check failure is placed early and capped`() {
        val longFailure = "F".repeat(MissionContinueComposer.MAX_CHECK_CHARS + 500)
        val text = MissionContinueComposer.compose(ctx(checkNote = longFailure))

        val checkIndex = text.indexOf("检查没有通过")
        assertTrue(checkIndex in 0..text.indexOf("已产出清单"))
        assertTrue(text.length < longFailure.length + 1_000)
    }

    @Test
    fun `round digest is labelled as system facts`() {
        val text = MissionContinueComposer.compose(
            ctx(roundDigest = "状态：succeeded；工具调用：12 次；本轮新增产物：2 件")
        )

        assertTrue(text.contains("系统记录"))
        assertTrue(text.contains("工具调用：12 次"))
    }

    @Test
    fun `absent optional sections leave no empty headers`() {
        val text = MissionContinueComposer.compose(ctx())

        assertFalse(text.contains("任务状态"))
        assertFalse(text.contains("检查没有通过"))
        assertFalse(text.contains("系统记录"))
    }

    @Test
    fun `directive tells the model to maintain the state file`() {
        val directive = MissionContinueComposer.composeDirective("t", "g", emptyList())
        assertTrue(directive.contains(MissionTaskState.PATH))
    }

    @Test
    fun `check failure prompt carries command, output and the rework budget`() {
        val text = MissionContinueComposer.composeCheckFailure(
            command = "./verify.sh",
            output = "FAILED test_report (2 failed)",
            round = 1,
            maxCheckFailures = 2,
        )

        assertTrue(text.startsWith("[检查未通过]"))
        assertTrue(text.contains("./verify.sh"))
        assertTrue(text.contains("FAILED test_report"))
        assertTrue(text.contains("上限 2 次"))
        assertTrue(text.contains("mission_report(done)"))
    }

    @Test
    fun `check failure output is truncated from the head`() {
        val output = "x".repeat(5_000) + "\nFINAL FAILURE"
        val text = MissionContinueComposer.composeCheckFailure("./v.sh", output, 1, 1)

        assertTrue(text.contains("FINAL FAILURE"))
        assertTrue("截断必须留痕", text.contains("省略"))
        assertTrue(text.length < output.length)
    }
}
