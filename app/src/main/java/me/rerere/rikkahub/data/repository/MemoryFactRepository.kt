package me.rerere.rikkahub.data.repository

import androidx.room.withTransaction
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.MemoryFactDAO
import me.rerere.rikkahub.data.db.entity.MemoryFactEntity
import me.rerere.rikkahub.runtime.tools.ExtractedFact
import me.rerere.rikkahub.runtime.tools.MemoryFactRules

/** What the prompt may assert about the user right now. */
data class FactInjection(
    val facts: List<MemoryFactEntity>,
    val omitted: Int,
)

/**
 * The bi-temporal fact store.
 *
 * One row per *claim*, not per subject: a change invalidates the old row and inserts a new one,
 * so `valid_to IS NULL` is exactly "what we currently believe". The write path is a single
 * transaction because a half-applied supersede would leave two live rows saying different things
 * — the one state the injection query must never return.
 */
class MemoryFactRepository(
    private val database: AppDatabase,
    private val dao: MemoryFactDAO,
) {

    /**
     * Applies [facts] in one transaction and returns how many rows were written.
     *
     * A fact identical to the live row is skipped rather than re-inserted: re-writing the same
     * claim on every compaction would grow the history with rows that carry no information, and
     * would move `valid_from` forward for something that never changed.
     */
    suspend fun upsert(
        assistantId: String,
        sourceConversationId: String?,
        facts: List<ExtractedFact>,
        nowMs: Long,
    ): Int = database.withTransaction {
        var written = 0
        for (fact in facts) {
            val live = dao.findLive(assistantId, fact.subject, fact.predicate)
            if (live != null && MemoryFactRules.isSameAssertion(live.objectValue, fact)) continue
            if (live != null) dao.invalidate(live.id, nowMs)
            dao.insert(
                MemoryFactEntity(
                    assistantId = assistantId,
                    subject = fact.subject,
                    predicate = fact.predicate,
                    objectValue = fact.objectValue,
                    confidence = fact.confidence,
                    sourceConversationId = sourceConversationId,
                    validFrom = nowMs,
                    validTo = null,
                    createdAt = nowMs,
                )
            )
            written++
        }
        written
    }

    /**
     * The facts worth putting in the prompt, newest first.
     *
     * Ordering is by recency rather than by similarity to the current message: similarity would
     * change the block on every turn, and a block that changes on every turn is a block the
     * provider's prefix cache cannot hold. Recency is the stable proxy — the fact someone just
     * told us is the one most likely to matter.
     */
    suspend fun injectable(assistantId: String, limit: Int = DEFAULT_LIMIT): FactInjection {
        val live = dao.getLiveFacts(assistantId, limit)
        val total = dao.countLive(assistantId)
        return FactInjection(facts = live, omitted = (total - live.size).coerceAtLeast(0))
    }

    suspend fun countLive(assistantId: String): Int = dao.countLive(assistantId)

    /** Wholesale reset, used when an assistant's memory is cleared. */
    suspend fun clear(assistantId: String) = dao.deleteAllFor(assistantId)

    companion object {
        /**
         * Twelve triples is roughly 300 tokens — enough to carry the domain, small enough that
         * the block cannot crowd out the conversation it is supposed to inform.
         */
        const val DEFAULT_LIMIT: Int = 12
    }
}
