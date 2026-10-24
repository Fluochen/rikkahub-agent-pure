package me.rerere.rikkahub.runtime.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Injection decides what the model is told every turn. Getting it wrong is silent: the model
 * simply asserts something false about the present, or never learns a preference at all.
 */
class MemoryInjectionRulesTest {

    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun memory(
        id: Int,
        content: String,
        kind: MemoryKind = MemoryKind.NOTE,
        ageDays: Long = 0,
        validToMs: Long? = null,
    ) = InjectableMemory(
        id = id,
        content = content,
        kind = kind,
        createdAtMs = now - ageDays * day,
        validToMs = validToMs,
    )

    @Test
    fun `a superseded record is not injected`() {
        val selection = MemoryInjectionRules.select(
            memories = listOf(
                memory(1, "lives in Beijing", validToMs = now - day),
                memory(2, "lives in Shanghai"),
            ),
            nowMs = now,
        )
        assertEquals(listOf(2), selection.memories.map { it.id })
    }

    @Test
    fun `an old state stops being asserted`() {
        val selection = MemoryInjectionRules.select(
            memories = listOf(
                memory(1, "currently moving house", kind = MemoryKind.STATE, ageDays = 45),
                memory(2, "is renovating the kitchen", kind = MemoryKind.STATE, ageDays = 2),
            ),
            nowMs = now,
        )
        assertEquals(listOf(2), selection.memories.map { it.id })
    }

    @Test
    fun `an old plan stops being asserted`() {
        val selection = MemoryInjectionRules.select(
            memories = listOf(
                memory(1, "will migrate the gateway in March", kind = MemoryKind.PLAN, ageDays = 60),
            ),
            nowMs = now,
        )
        assertTrue(selection.memories.isEmpty())
    }

    @Test
    fun `a preference never ages out`() {
        val selection = MemoryInjectionRules.select(
            memories = listOf(
                memory(1, "prefers brief replies", kind = MemoryKind.PREFERENCE, ageDays = 900),
            ),
            nowMs = now,
        )
        assertEquals(listOf(1), selection.memories.map { it.id })
    }

    @Test
    fun `identity comes before transient claims`() {
        val selection = MemoryInjectionRules.select(
            memories = listOf(
                memory(1, "state", kind = MemoryKind.STATE),
                memory(2, "plan", kind = MemoryKind.PLAN),
                memory(3, "note"),
                memory(4, "fact", kind = MemoryKind.FACT),
                memory(5, "preference", kind = MemoryKind.PREFERENCE),
            ),
            nowMs = now,
        )
        assertEquals(listOf(5, 4, 3, 2, 1), selection.memories.map { it.id })
    }

    @Test
    fun `within a kind the newest record comes first`() {
        val selection = MemoryInjectionRules.select(
            memories = listOf(
                memory(1, "old preference", kind = MemoryKind.PREFERENCE, ageDays = 30),
                memory(2, "new preference", kind = MemoryKind.PREFERENCE, ageDays = 1),
            ),
            nowMs = now,
        )
        assertEquals(listOf(2, 1), selection.memories.map { it.id })
    }

    @Test
    fun `the budget trims the tail and reports how many were left out`() {
        val memories = (1..20).map { memory(it, "x".repeat(400)) }
        val selection = MemoryInjectionRules.select(
            memories = memories,
            nowMs = now,
            charBudget = 1_000,
        )
        assertEquals(2, selection.memories.size)
        assertEquals(18, selection.omitted)
    }

    @Test
    fun `a single record larger than the budget is still injected`() {
        val selection = MemoryInjectionRules.select(
            memories = listOf(memory(1, "y".repeat(20_000))),
            nowMs = now,
            charBudget = 100,
        )
        assertEquals(listOf(1), selection.memories.map { it.id })
        assertEquals(0, selection.omitted)
    }

    @Test
    fun `nothing to inject is not an error`() {
        val selection = MemoryInjectionRules.select(emptyList(), nowMs = now)
        assertTrue(selection.memories.isEmpty())
        assertEquals(0, selection.omitted)
    }

    @Test
    fun `ttl zero disables expiry without dropping the record`() {
        val selection = MemoryInjectionRules.select(
            memories = listOf(
                memory(1, "ancient state", kind = MemoryKind.STATE, ageDays = 5_000),
            ),
            nowMs = now,
            ttlDays = 0,
        )
        assertEquals(listOf(1), selection.memories.map { it.id })
    }
}
