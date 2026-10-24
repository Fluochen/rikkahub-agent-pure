package me.rerere.rikkahub.runtime.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `memory_lookup` is the only way back to a record the prompt left out, so its ranking decides
 * whether a stored memory is reachable or merely present. The failure that matters is not "found
 * nothing" — it is "returned eight unrelated records", which teaches the model to stop asking.
 */
class MemoryLookupRulesTest {

    private fun memory(
        id: Int,
        text: String,
        createdAt: Long = 0L,
        vector: FloatArray? = null,
    ) = MemoryLookupCandidate(
        type = MemoryLookupType.MEMORY,
        id = id,
        text = text,
        kind = "note",
        createdAtMs = createdAt,
        vector = vector,
    )

    private fun fact(
        id: Int,
        subject: String,
        predicate: String,
        objectValue: String,
        createdAt: Long = 0L,
    ) = MemoryLookupCandidate(
        type = MemoryLookupType.FACT,
        id = id,
        text = "$subject $predicate $objectValue",
        subject = subject,
        predicate = predicate,
        objectValue = objectValue,
        createdAtMs = createdAt,
    )

    @Test
    fun `keyword match ranks its record first`() {
        val hits = MemoryLookupRules.rank(
            candidates = listOf(
                memory(1, "User prefers brief replies"),
                memory(2, "The gateway needs the X-Admin-Token header"),
            ),
            query = "gateway header",
        )
        assertEquals(2, hits.first().candidate.id)
    }

    @Test
    fun `a fact matches on its subject and predicate`() {
        val hits = MemoryLookupRules.rank(
            candidates = listOf(
                fact(7, "gateway", "requires_header", "X-Admin-Token"),
                memory(3, "User is renovating the kitchen"),
            ),
            query = "which header does the gateway need",
        )
        assertEquals(7, hits.first().candidate.id)
        assertEquals(MemoryLookupType.FACT, hits.first().candidate.type)
    }

    @Test
    fun `chinese queries match bigrams`() {
        val hits = MemoryLookupRules.rank(
            candidates = listOf(memory(5, "用户偏好简洁的回复方式")),
            query = "回复风格",
        )
        assertEquals(5, hits.first().candidate.id)
    }

    @Test
    fun `a semantic-only match still qualifies`() {
        val hits = MemoryLookupRules.rank(
            candidates = listOf(
                memory(9, "dislikes long answers", vector = floatArrayOf(1f, 0f)),
            ),
            query = "prefers terse replies",
            queryVector = floatArrayOf(0.98f, 0.02f),
        )
        assertEquals(9, hits.first().candidate.id)
    }

    @Test
    fun `a strong vector score is not diluted by missing words`() {
        // best-of, not average: 0.95 semantic + 0.0 lexical must still pass the floor.
        val hits = MemoryLookupRules.rank(
            candidates = listOf(memory(4, "unrelated wording", vector = floatArrayOf(1f, 0f))),
            query = "zzz qqq",
            queryVector = floatArrayOf(0.99f, 0.01f),
        )
        assertEquals(1, hits.size)
        assertTrue(hits.first().score > 0.9)
    }

    @Test
    fun `unrelated records are filtered out instead of filling the limit`() {
        val hits = MemoryLookupRules.rank(
            candidates = listOf(
                memory(1, "User prefers brief replies"),
                memory(2, "User lives in Shanghai"),
                memory(3, "The build takes four minutes"),
            ),
            query = "kubernetes ingress",
        )
        assertTrue("noise must not be returned: $hits", hits.isEmpty())
    }

    @Test
    fun `the limit is respected and clamped`() {
        val candidates = (1..30).map { memory(it, "gateway detail number $it") }
        assertEquals(3, MemoryLookupRules.rank(candidates, "gateway", limit = 3).size)
        assertEquals(
            MemoryLookupRules.MAX_LIMIT,
            MemoryLookupRules.rank(candidates, "gateway", limit = 1_000).size,
        )
    }

    @Test
    fun `equal scores prefer the newer record`() {
        val hits = MemoryLookupRules.rank(
            candidates = listOf(
                memory(1, "gateway header", createdAt = 100),
                memory(2, "gateway header", createdAt = 200),
            ),
            query = "gateway header",
        )
        assertEquals(2, hits.first().candidate.id)
    }

    @Test
    fun `a blank query returns nothing rather than everything`() {
        val candidates = listOf(memory(1, "anything"))
        assertTrue(MemoryLookupRules.rank(candidates, "   ").isEmpty())
    }

    @Test
    fun `an empty store is not an error`() {
        assertTrue(MemoryLookupRules.rank(emptyList(), "gateway").isEmpty())
    }

    @Test
    fun `a vector of a different width never scores`() {
        val hits = MemoryLookupRules.rank(
            candidates = listOf(memory(1, "unrelated text", vector = floatArrayOf(1f, 0f, 0f))),
            query = "something else",
            queryVector = floatArrayOf(1f, 0f),
        )
        assertTrue(hits.isEmpty())
    }
}

class MemoryLookupRenderingTest {

    @Test
    fun `memories and facts are rendered as separate arrays`() {
        val hits = listOf(
            MemoryLookupHit(
                MemoryLookupCandidate(
                    type = MemoryLookupType.MEMORY,
                    id = 12,
                    text = "User prefers brief replies",
                    kind = "preference",
                ),
                0.82,
            ),
            MemoryLookupHit(
                MemoryLookupCandidate(
                    type = MemoryLookupType.FACT,
                    id = 3,
                    text = "gateway requires_header X-Admin-Token",
                    subject = "gateway",
                    predicate = "requires_header",
                    objectValue = "X-Admin-Token",
                ),
                0.67,
            ),
        )
        val json = renderLookup("gateway", hits)
        assertTrue(json.contains("\"memories\""))
        assertTrue(json.contains("\"facts\""))
        assertTrue(json.contains("\"preference\""))
        assertTrue(json.contains("\"X-Admin-Token\""))
        // Two decimals, so the number orders the list without pretending to be precise.
        assertTrue(json.contains("0.82"))
    }

    @Test
    fun `an empty result says so explicitly`() {
        val json = renderLookup("kubernetes", emptyList())
        assertTrue("a silent empty result reads like a broken tool", json.contains("\"note\""))
    }
}
