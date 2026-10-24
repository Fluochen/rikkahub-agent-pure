package me.rerere.rikkahub.data.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批 F — task-state.md 的注入块与指纹。 */
class MissionTaskStateTest {

    @Test
    fun `missing state turns into a build-it-first instruction`() {
        val block = MissionTaskState.composeBlock(null)

        assertTrue(block.contains(MissionTaskState.PATH))
        assertTrue(block.contains("还没有"))
        assertTrue(block.contains("下一步"))
    }

    @Test
    fun `present state is injected verbatim`() {
        val content = "## 目标\n归档截图\n## 已完成\n扫描 120 张"
        val block = MissionTaskState.composeBlock(content)

        assertTrue(block.contains("归档截图"))
        assertTrue(block.contains("扫描 120 张"))
        assertTrue(block.contains("结束本轮前请更新它"))
    }

    @Test
    fun `long state is clipped from the head and keeps the tail`() {
        val head = "H".repeat(MissionTaskState.MAX_INJECT_CHARS + 200)
        val tail = "下一步：补齐 2024 年的截图"
        val content = head + tail

        val block = MissionTaskState.composeBlock(content)

        assertTrue(block.contains(tail))
        assertTrue(block.contains("省略"))
    }

    @Test
    fun `fingerprint is stable and changes with content`() {
        val a = MissionTaskState.fingerprint("same")
        assertEquals(a, MissionTaskState.fingerprint("same"))
        assertNotEquals(a, MissionTaskState.fingerprint("same!"))
    }

    @Test
    fun `blank and missing share the empty fingerprint`() {
        assertEquals(MissionTaskState.EMPTY_FINGERPRINT, MissionTaskState.fingerprint(null))
        assertEquals(MissionTaskState.EMPTY_FINGERPRINT, MissionTaskState.fingerprint("   \n "))
    }

    @Test
    fun `directive rule names the state file`() {
        assertTrue(MissionTaskState.directiveRule().contains(MissionTaskState.PATH))
    }
}
