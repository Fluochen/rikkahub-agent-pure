package me.rerere.rikkahub.subagent

import kotlin.uuid.Uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G 批 — 嵌套深度规则与深度表。
 *
 * 这一层决定「子代理能不能再派一个」，所以边界要钉死：上限是"允许的最大深度"，不是"允许派
 * 几次"。上限 2 时，深度 1 的 run 可以派出深度 2 的子代理，深度 2 的就不能再派。
 */
class SubAgentNestingRulesTest {

    @Test
    fun `each posture maps to one nesting depth`() {
        assertEquals(0, SubAgentNestingRules.capFor(SubAgentDispatchProfile.NEVER))
        assertEquals(1, SubAgentNestingRules.capFor(SubAgentDispatchProfile.ASSIST))
        assertEquals(2, SubAgentNestingRules.capFor(SubAgentDispatchProfile.NORMAL))
        assertEquals(3, SubAgentNestingRules.capFor(SubAgentDispatchProfile.BURST))
        assertEquals(4, SubAgentNestingRules.capFor(SubAgentDispatchProfile.UNLEASHED))
    }

    @Test
    fun `the caps table and the profile caps agree`() {
        for (profile in SubAgentDispatchProfile.entries) {
            assertEquals(
                "profile $profile",
                SubAgentNestingRules.capFor(profile),
                SubAgentDispatchProfileRules.caps(profile).maxNestingDepth,
            )
        }
    }

    @Test
    fun `a top level conversation may always spawn when dispatch is on`() {
        for (profile in listOf(
            SubAgentDispatchProfile.ASSIST,
            SubAgentDispatchProfile.NORMAL,
            SubAgentDispatchProfile.BURST,
            SubAgentDispatchProfile.UNLEASHED,
        )) {
            assertTrue(
                "profile $profile must allow a first-level dispatch",
                SubAgentNestingRules.canSpawn(SubAgentNestingRules.TOP_LEVEL_DEPTH, SubAgentNestingRules.capFor(profile)),
            )
        }
    }

    @Test
    fun `the cap is a max depth not a spawn count`() {
        val cap = 2
        assertTrue(SubAgentNestingRules.canSpawn(0, cap))
        assertTrue(SubAgentNestingRules.canSpawn(1, cap))
        assertFalse("a run at the cap may not spawn again", SubAgentNestingRules.canSpawn(2, cap))
        assertFalse(SubAgentNestingRules.canSpawn(3, cap))
    }

    @Test
    fun `assist means no nesting at all`() {
        val cap = SubAgentNestingRules.capFor(SubAgentDispatchProfile.ASSIST)
        assertTrue(SubAgentNestingRules.canSpawn(0, cap))
        assertFalse(SubAgentNestingRules.canSpawn(1, cap))
    }

    @Test
    fun `a disabled profile cannot spawn even at the top level`() {
        val cap = SubAgentNestingRules.capFor(SubAgentDispatchProfile.NEVER)
        assertFalse(SubAgentNestingRules.canSpawn(0, cap))
    }

    @Test
    fun `child depth is parent plus one`() {
        assertEquals(1, SubAgentNestingRules.childDepth(0))
        assertEquals(3, SubAgentNestingRules.childDepth(2))
    }

    // ---------------------------------------------------------------- depth table

    @Test
    fun `the depth table only knows conversations that were marked`() {
        SubAgentDepths.clearAll()
        val conv = Uuid.random()
        assertNull(SubAgentDepths.depthOf(conv))

        SubAgentDepths.mark(conv, 2, "root-conv")
        assertEquals(2, SubAgentDepths.depthOf(conv))
        assertEquals(
            SubAgentDepths.Placement(depth = 2, rootConversationId = "root-conv"),
            SubAgentDepths.placementOf(conv),
        )

        SubAgentDepths.release(conv)
        assertNull(SubAgentDepths.depthOf(conv))
    }

    @Test
    fun `a negative depth is clamped to the top level`() {
        SubAgentDepths.clearAll()
        val conv = Uuid.random()
        SubAgentDepths.mark(conv, -3, "root")
        assertEquals(SubAgentNestingRules.TOP_LEVEL_DEPTH, SubAgentDepths.depthOf(conv))
    }
}
