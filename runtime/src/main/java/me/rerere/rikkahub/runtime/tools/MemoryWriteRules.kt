package me.rerere.rikkahub.runtime.tools

/**
 * What kind of thing a memory record is.
 *
 * The kind exists to decide two things later: whether the record is worth re-sending on every
 * turn (a preference is, a transient state is not), and how long it stays true. It is *not* a
 * taxonomy for its own sake — [NOTE] is the honest default for everything the model could not
 * classify, and a wrong [NOTE] costs nothing.
 */
enum class MemoryKind(val wire: String) {
    /** How the user wants to be talked to / what they like. Long-lived, always injected. */
    PREFERENCE("preference"),

    /** A durable fact about the user or their setup. Long-lived, always injected. */
    FACT("fact"),

    /** Something the user intends to do. Injected until it goes stale. */
    PLAN("plan"),

    /** Where things stand right now. First to expire. */
    STATE("state"),

    /** Unclassified. Treated as long-lived, injected after preferences and facts. */
    NOTE("note"),
    ;

    companion object {
        /** Unknown or absent values fall back to [NOTE] rather than failing the write. */
        fun fromWire(value: String?): MemoryKind {
            val needle = value?.trim()?.lowercase().orEmpty()
            if (needle.isEmpty()) return NOTE
            return entries.firstOrNull { it.wire == needle } ?: NOTE
        }

        /** Wire names in the order the tool schema offers them. */
        fun wireNames(): List<String> = entries.map { it.wire }
    }
}

/** One stored memory, as the gate sees it. */
data class MemoryRecord(
    val id: Int,
    val content: String,
    val kind: MemoryKind,
)

enum class MemoryRejectReason(val wire: String) {
    /** Nothing to store. */
    BLANK("blank"),

    /** The same thing is already stored; editing that record is the right move. */
    DUPLICATE("duplicate"),

    /** The assistant's memory bank is full. */
    CAPACITY("capacity"),
}

sealed interface MemoryWriteDecision {
    data object Accept : MemoryWriteDecision

    /** [existing] is the record the model should edit instead, when there is one. */
    data class Reject(
        val reason: MemoryRejectReason,
        val existing: MemoryRecord? = null,
    ) : MemoryWriteDecision
}

/**
 * The write gate for hot memory.
 *
 * ## Why a gate at all
 *
 * Memory written by a model is a stream of near-restatements: the same preference reworded
 * every few conversations, the same fact split into three records, a stale plan that was
 * superseded months ago. Left unchecked the bank grows linearly while its information content
 * does not, and every one of those records is re-sent to the model on every turn. That is the
 * failure mode that made one widely used memory library's rows mostly noise; the fix is not a
 * better extraction prompt, it is a refusal at the write door.
 *
 * ## Two tiers
 *
 * 1. **Deterministic, always on.** Normalised equality, then token containment for a reworded
 *    restatement. No model, no embedding, no IO — this is the tier that keeps working on a
 *    phone with no embedding model installed.
 * 2. **Semantic, when an embedding model exists.** Cosine similarity above
 *    [EMBEDDING_DUPLICATE_COSINE] catches what wording cannot ("prefers terse answers" vs
 *    "dislikes long replies"). The caller runs this tier; this file only owns the arithmetic.
 *
 * A rejection is not an error: it carries the existing record so the tool can tell the model
 * "edit #12 instead", which is a much cheaper correction than a delete-and-rewrite.
 */
object MemoryWriteRules {

    /**
     * A hard ceiling that keeps the bank bounded. 200 records at ~40 tokens each is already
     * ~8K tokens of prompt, which is why the injection side has a character budget as well.
     */
    const val DEFAULT_CAP: Int = 200

    /**
     * Containment at or above this is treated as the same memory. Tuned for short records:
     * a preference is one sentence, so a paraphrase keeps most of its content tokens.
     */
    const val NEAR_DUPLICATE_OVERLAP: Double = 0.85

    /** Cosine similarity at or above this is the same memory, semantically. */
    const val EMBEDDING_DUPLICATE_COSINE: Double = 0.92

    /**
     * Below this many tokens the overlap ratio is meaningless (two one-word records would
     * "contain" each other at 1.0), so short records fall back to exact equality.
     */
    private const val MIN_TOKENS_FOR_OVERLAP = 3

    private val WHITESPACE = Regex("\\s+")
    private val TRAILING_PUNCTUATION = Regex("[\\s.。,，;；:：!！?？]+$")
    private val ASCII_WORD = Regex("[a-z0-9]+")
    private val CJK_RUN = Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff\\u3040-\\u30ff]+")

    /** Case-, whitespace- and trailing-punctuation-insensitive form used for equality. */
    fun normalize(text: String): String =
        WHITESPACE.replace(text.trim().lowercase(), " ")
            .replace(TRAILING_PUNCTUATION, "")
            .trim()

    /**
     * Content tokens: ASCII words, plus character bigrams for every CJK/Japanese run.
     *
     * Bigrams rather than single characters because Chinese has no word boundaries here and
     * single-character overlap is almost always a false positive (a common character appears
     * in everything).
     */
    fun tokens(text: String): Set<String> {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptySet()
        val tokens = LinkedHashSet<String>()
        ASCII_WORD.findAll(normalized).forEach { tokens += it.value }
        CJK_RUN.findAll(normalized).forEach { run ->
            val chars = run.value
            if (chars.length == 1) {
                tokens += chars
            } else {
                for (index in 0 until chars.length - 1) {
                    tokens += chars.substring(index, index + 2)
                }
            }
        }
        // A record with no alphanumeric content at all (emoji only) still deserves equality.
        if (tokens.isEmpty()) tokens += normalized
        return tokens
    }

    /**
     * |A ∩ B| / min(|A|, |B|): how much of the *shorter* record is present in the other.
     *
     * Containment rather than Jaccard because the common case is "the model restated a record
     * with one extra clause" — Jaccard would dilute that with the added words, containment does
     * not.
     */
    fun containment(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val smaller = minOf(a.size, b.size)
        var shared = 0
        for (token in a) if (token in b) shared++
        return shared.toDouble() / smaller
    }

    /**
     * The deterministic half of the gate.
     *
     * Order matters: a duplicate that also happens to hit the cap should be reported as a
     * duplicate, because "edit #12" is actionable while "memory is full" is not.
     */
    fun decide(
        content: String,
        existing: List<MemoryRecord>,
        cap: Int = DEFAULT_CAP,
    ): MemoryWriteDecision {
        val normalized = normalize(content)
        if (normalized.isEmpty()) {
            return MemoryWriteDecision.Reject(MemoryRejectReason.BLANK)
        }

        existing.firstOrNull { normalize(it.content) == normalized }?.let { match ->
            return MemoryWriteDecision.Reject(MemoryRejectReason.DUPLICATE, match)
        }

        val candidateTokens = tokens(content)
        if (candidateTokens.size >= MIN_TOKENS_FOR_OVERLAP) {
            existing
                .map { record -> record to containment(candidateTokens, tokens(record.content)) }
                .filter { (_, score) -> score >= NEAR_DUPLICATE_OVERLAP }
                .maxByOrNull { (_, score) -> score }
                ?.let { (record, _) ->
                    return MemoryWriteDecision.Reject(MemoryRejectReason.DUPLICATE, record)
                }
        }

        if (existing.size >= cap) {
            return MemoryWriteDecision.Reject(MemoryRejectReason.CAPACITY)
        }

        return MemoryWriteDecision.Accept
    }

    /**
     * Cosine similarity, or 0.0 when the two vectors cannot be compared.
     *
     * A width mismatch returns 0.0 instead of throwing: it means the stored vector came from a
     * different model, and "no opinion" is the correct answer — the caller then stores the new
     * vector with its own model id.
     */
    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (index in a.indices) {
            val x = a[index].toDouble()
            val y = b[index].toDouble()
            dot += x * y
            normA += x * x
            normB += y * y
        }
        if (normA <= 0.0 || normB <= 0.0) return 0.0
        return dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }

    /**
     * The semantic half: the closest stored record at or above [threshold], or null.
     *
     * [stored] pairs a record with a vector produced by the *same* model as [candidate]; the
     * caller filters by model id, because comparing vectors from two models is meaningless
     * (see [cosine]).
     */
    fun duplicateByEmbedding(
        candidate: FloatArray,
        stored: List<Pair<MemoryRecord, FloatArray>>,
        threshold: Double = EMBEDDING_DUPLICATE_COSINE,
    ): MemoryRecord? = stored
        .map { (record, vector) -> record to cosine(candidate, vector) }
        .filter { (_, score) -> score >= threshold }
        .maxByOrNull { (_, score) -> score }
        ?.first

    /** The model-facing sentence for a rejection. Kept here so both tool paths say the same thing. */
    fun rejectionHint(reason: MemoryRejectReason, existing: MemoryRecord?): String = when (reason) {
        MemoryRejectReason.BLANK -> "Nothing to store: the content was empty after trimming."
        MemoryRejectReason.DUPLICATE -> if (existing != null) {
            "This is already stored as memory #${existing.id}. Call memory_tool with " +
                "action=\"edit\", id=${existing.id} and the corrected content instead of creating " +
                "a new record."
        } else {
            "This is already stored. Edit the existing record instead of creating a new one."
        }
        MemoryRejectReason.CAPACITY ->
            "The memory bank is full. Delete or merge an outdated record first, then retry."
    }
}
