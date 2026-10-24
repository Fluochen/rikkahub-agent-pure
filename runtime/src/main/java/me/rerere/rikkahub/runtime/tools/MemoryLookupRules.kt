package me.rerere.rikkahub.runtime.tools

/** Which store a hit came from. The model needs to know, because they are edited differently. */
enum class MemoryLookupType(val wire: String) {
    /** A hot-memory record: free text, editable through `memory_tool`. */
    MEMORY("memory"),

    /** An extracted fact: a triple, read-only for the model. */
    FACT("fact"),
}

/**
 * One searchable record.
 *
 * [vector] is present only when the store could supply one from the *current* embedding model;
 * a null vector simply means "this record competes on words alone", never "skip it". Facts
 * never carry one — they are short triples whose subject and predicate are already keywords,
 * and a vector per triple would cost more to maintain than it retrieves.
 */
data class MemoryLookupCandidate(
    val type: MemoryLookupType,
    val id: Int,
    /** Searchable text: the memory's content, or "subject predicate object" for a fact. */
    val text: String,
    /** Memory kind wire value, or a fact's parts kept for rendering. */
    val kind: String? = null,
    val subject: String? = null,
    val predicate: String? = null,
    val objectValue: String? = null,
    val createdAtMs: Long = 0L,
    val vector: FloatArray? = null,
)

/** A ranked candidate. [score] is the best of its word and vector scores. */
data class MemoryLookupHit(
    val candidate: MemoryLookupCandidate,
    val score: Double,
)

/**
 * Ranking for `memory_lookup`.
 *
 * ## Why two signals instead of one
 *
 * The two stores fail differently. Hot memories are sentences, so a semantic match is what
 * finds "dislikes long answers" from "prefers terse replies" — but only when an embedding model
 * is installed and the row was written since. Facts are triples whose fields are already
 * keywords, so word matching finds them reliably and a vector would add maintenance for
 * nothing.
 *
 * Taking the **best** of the two rather than a weighted sum: a record that matches only
 * semantically and one that matches only by words are both good answers, and averaging would
 * let a strong signal be diluted by a missing one (0.9 semantic + 0 words = 0.45, below
 * [MIN_SCORE]).
 *
 * ## Why the floor
 *
 * Without it every query returns `limit` records, including the five most recent ones that
 * share a stopword. A model that asks "what did we decide about the gateway" and gets back
 * "user prefers brief replies" learns to stop calling the tool.
 */
object MemoryLookupRules {

    const val DEFAULT_LIMIT: Int = 8
    const val MAX_LIMIT: Int = 20

    /**
     * Below this a hit is noise.
     *
     * 0.30 rather than something rounder-looking: containment divides by the *shorter* set, so
     * a two-bigram Chinese query ("回复风格") against a longer record can only reach 1/3 even
     * when the record is clearly the right one. Anything higher silently drops short CJK
     * queries; anything lower lets a single shared word out of three through, which is what the
     * stopword list below is for.
     */
    const val MIN_SCORE: Double = 0.30

    /**
     * Words that carry no lookup signal, stripped from the *query* only.
     *
     * A user asks "what do we know about the gateway"; the tokens `what/do/we/know/about/the`
     * are four fifths of that query and appear in half the stored sentences. Leaving them in
     * both shrinks the score of the right record and inflates the score of every wrong one.
     * Candidates keep their stopwords — they cost nothing on that side, and stripping them
     * would only distort the containment denominator.
     */
    private val QUERY_STOPWORDS: Set<String> = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "do", "does", "did", "what", "which", "who", "whom", "when", "where", "why", "how",
        "about", "of", "for", "to", "in", "on", "at", "by", "with", "from", "and", "or",
        "not", "no", "my", "me", "i", "you", "your", "we", "our", "it", "its", "that", "this",
        "have", "has", "had", "can", "could", "should", "would", "will", "s", "t",
    )

    /** A memory's own tokens are what a query is compared against. */
    fun candidateTokens(candidate: MemoryLookupCandidate): Set<String> =
        MemoryWriteRules.tokens(candidate.text)

    fun score(
        candidate: MemoryLookupCandidate,
        queryTokens: Set<String>,
        queryVector: FloatArray?,
    ): Double {
        // No shared token means no lexical evidence at all; letting containment answer anyway
        // would score an unrelated record 0.0 and waste a slot sorting it.
        val shared = queryTokens.count { token -> token in candidateTokens(candidate) }
        val wordScore = if (shared == 0) {
            0.0
        } else {
            MemoryWriteRules.containment(queryTokens, candidateTokens(candidate))
        }
        val vectorScore = if (queryVector != null && candidate.vector != null) {
            MemoryWriteRules.cosine(queryVector, candidate.vector)
        } else {
            0.0
        }
        return maxOf(wordScore, vectorScore)
    }

    /**
     * Ranks [candidates] against [query].
     *
     * Ties are broken by recency: when two records match equally well, the newer one is the
     * more likely answer to "what do we know about X right now".
     */
    fun rank(
        candidates: List<MemoryLookupCandidate>,
        query: String,
        queryVector: FloatArray? = null,
        limit: Int = DEFAULT_LIMIT,
        minScore: Double = MIN_SCORE,
    ): List<MemoryLookupHit> {
        val queryTokens = MemoryWriteRules.tokens(query)
            .filterNot { token -> token in QUERY_STOPWORDS }
            .toSet()
        // A query made only of stopwords ("what is it") has nothing to match on; returning
        // every record would be worse than returning none.
        if (queryTokens.isEmpty()) return emptyList()
        val effectiveLimit = limit.coerceIn(1, MAX_LIMIT)

        return candidates
            .map { candidate -> MemoryLookupHit(candidate, score(candidate, queryTokens, queryVector)) }
            .filter { hit -> hit.score >= minScore }
            .sortedWith(
                compareByDescending<MemoryLookupHit> { it.score }
                    .thenByDescending { it.candidate.createdAtMs }
                    .thenBy { it.candidate.id }
            )
            .take(effectiveLimit)
    }
}
