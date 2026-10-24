package me.rerere.rikkahub.runtime.tools

/** One memory as the injection decision sees it (ids and timestamps included). */
data class InjectableMemory(
    val id: Int,
    val content: String,
    val kind: MemoryKind,
    val createdAtMs: Long,
    /** Null means "still true". A value in the past means superseded. */
    val validToMs: Long? = null,
)

/**
 * What to put in the `<memories>` block this turn, and what was left out.
 *
 * [omitted] is not decoration: the prompt states it, because a model that does not know the
 * bank is bigger will confidently answer "you never told me that" instead of saying it cannot
 * see that record.
 */
data class InjectionSelection(
    val memories: List<InjectableMemory>,
    val omitted: Int,
)

/**
 * Chooses which memories are worth re-sending on every turn.
 *
 * ## Why not simply send everything
 *
 * Hot memory is injected into the system prompt of *every* request. Sending the whole bank
 * means the prompt grows without bound — the thing that turns a helpful memory into a cost
 * centre — and it puts the least durable records first in line for the model's attention. A
 * record from eight months ago that says "user is currently moving house" is worse than noise:
 * it is a false statement about the present.
 *
 * ## Ordering
 *
 * Preference and fact first (they describe who the user is), then unclassified notes, then
 * plans and states, which are the records most likely to be stale. Within one kind, newest
 * first — a corrected preference should win over the one it replaced.
 *
 * ## Budget
 *
 * A character budget rather than a count, because records vary by an order of magnitude
 * between English and Chinese. The first record is always included even if it alone exceeds
 * the budget: a model that can see no memory at all is worse than one that sees one long one.
 */
object MemoryInjectionRules {

    /**
     * ~2.5K tokens of Chinese, ~2.5K tokens of English. Large enough to hold a realistic bank,
     * small enough that it cannot dominate a 32K window.
     */
    const val DEFAULT_CHAR_BUDGET: Int = 8_000

    /**
     * How long a [MemoryKind.STATE] or [MemoryKind.PLAN] stays injectable without being
     * refreshed. The record is not deleted — it stays stored and visible in the UI; it just
     * stops being asserted to the model as current.
     */
    const val TRANSIENT_TTL_DAYS: Int = 30

    /** Rough per-record overhead of the JSON envelope the prompt wraps each record in. */
    private const val PER_RECORD_OVERHEAD_CHARS = 24

    private const val MILLIS_PER_DAY = 86_400_000L

    /** Injection order: identity first, transient claims last. */
    fun priority(kind: MemoryKind): Int = when (kind) {
        MemoryKind.PREFERENCE -> 0
        MemoryKind.FACT -> 1
        MemoryKind.NOTE -> 2
        MemoryKind.PLAN -> 3
        MemoryKind.STATE -> 4
    }

    /**
     * Filters, orders and budgets [memories] for this turn.
     *
     * @param nowMs wall clock, injected so the decision is testable.
     */
    fun select(
        memories: List<InjectableMemory>,
        nowMs: Long,
        charBudget: Int = DEFAULT_CHAR_BUDGET,
        ttlDays: Int = TRANSIENT_TTL_DAYS,
    ): InjectionSelection {
        val ttlMs = ttlDays.coerceAtLeast(0).toLong() * MILLIS_PER_DAY

        val injectable = memories
            .filter { memory -> memory.validToMs == null || memory.validToMs > nowMs }
            .filterNot { memory ->
                val transient = memory.kind == MemoryKind.STATE || memory.kind == MemoryKind.PLAN
                transient && ttlMs > 0 && nowMs - memory.createdAtMs > ttlMs
            }
            .sortedWith(
                compareBy<InjectableMemory> { priority(it.kind) }
                    .thenByDescending { it.createdAtMs }
            )

        if (injectable.isEmpty()) return InjectionSelection(emptyList(), omitted = 0)

        val budget = charBudget.coerceAtLeast(0)
        val kept = ArrayList<InjectableMemory>(injectable.size)
        var used = 0
        for (memory in injectable) {
            val cost = memory.content.length + PER_RECORD_OVERHEAD_CHARS
            if (kept.isNotEmpty() && used + cost > budget) break
            kept += memory
            used += cost
        }

        return InjectionSelection(
            memories = kept,
            omitted = injectable.size - kept.size,
        )
    }
}
