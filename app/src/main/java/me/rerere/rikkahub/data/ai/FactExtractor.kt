package me.rerere.rikkahub.data.ai

import android.util.Log
import me.rerere.rikkahub.data.repository.MemoryFactRepository
import me.rerere.rikkahub.runtime.tools.MemoryFactRules

/**
 * Turns a finished compaction summary into durable facts.
 *
 * ## Why the input is the summary and not the conversation
 *
 * The summary is *already* the compressed, information-dense form of everything before the
 * retained tail, and it exists at the exact moment we want to extract. Feeding raw history would
 * cost an order of magnitude more tokens for information the compressor just discarded — and it
 * would need the messages the compaction was created to forget.
 *
 * ## Why this runs off the critical path
 *
 * Extraction is one model call. Compaction is already the slowest thing in a turn, and a failure
 * here must never fail it: the caller launches this in the app scope, after the compaction is
 * stored, and only logs the outcome. The worst case is that this compaction contributed no
 * facts — which is also what happens when the summary contains none.
 *
 * ## The seam
 *
 * [extract] takes a `generate` lambda rather than a provider. Provider resolution, model choice,
 * usage accounting and timeouts already live in `ChatService` (the same machinery the compaction
 * summariser uses); duplicating them here would let the two drift. This class owns exactly the
 * parts worth testing without a provider: the prompt, the parse, and the commit.
 */
class FactExtractor(
    private val repository: MemoryFactRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        private const val TAG = "FactExtractor"

        /** The summary can be long; facts live in its first screenful of claims. */
        private const val MAX_SUMMARY_CHARS = 6_000

        /** Preferred predicates, given to the model so free text does not fragment the keys. */
        private val PREFERRED_PREDICATES = listOf(
            "prefers", "requires", "uses", "works_on", "lives_in", "decided",
            "depends_on", "blocked_by", "located_at", "named", "has_value", "scheduled_for",
        )
    }

    /**
     * Extracts and stores facts from [summary].
     *
     * Returns the number of rows written (0 when nothing was extracted, when the model failed,
     * or when the response could not be parsed). Never throws for a generation or parse failure:
     * this is a best-effort enrichment of a compaction that has already succeeded.
     */
    suspend fun extract(
        assistantId: String,
        sourceConversationId: String?,
        summary: String,
        generate: suspend (prompt: String) -> String,
    ): Int {
        if (summary.isBlank()) return 0

        val raw = runCatching { generate(buildPrompt(summary)) }
            .onFailure { error -> Log.w(TAG, "extraction call failed", error) }
            .getOrNull()
            ?: return 0

        val facts = MemoryFactRules.parse(raw)
        if (facts.isEmpty()) {
            Log.i(TAG, "no facts in this summary")
            return 0
        }

        return repository.upsert(
            assistantId = assistantId,
            sourceConversationId = sourceConversationId,
            facts = facts,
            nowMs = clock(),
        )
    }

    internal fun buildPrompt(summary: String): String = buildString {
        appendLine("Extract durable facts from the conversation summary below.")
        appendLine()
        appendLine("Return ONLY JSON, no prose and no markdown:")
        appendLine("""{"facts":[{"subject":"...","predicate":"...","object":"...","confidence":0.0}]}""")
        appendLine()
        appendLine("Rules:")
        appendLine("- subject: what the fact is about, lowercase (\"user\", \"gateway\", \"vivo-v2463a\").")
        appendLine(
            "- predicate: snake_case. Prefer one of: " +
                PREFERRED_PREDICATES.joinToString(", ") + "."
        )
        appendLine(
            "- object: the value, short, keep its original casing " +
                "(IDs and header names are case-sensitive)."
        )
        appendLine(
            "- Only facts that will still be true next month. Skip anything about this " +
                "conversation's process, work in progress, or the assistant's own actions."
        )
        appendLine("- Never invent. If the summary does not state it, do not write it.")
        appendLine("- Maximum 8 facts. If nothing qualifies, return {\"facts\":[]}.")
        appendLine()
        appendLine("SUMMARY:")
        append(summary.take(MAX_SUMMARY_CHARS))
    }
}
