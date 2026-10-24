package me.rerere.rikkahub.runtime.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * One durable fact, as a subject–predicate–object triple.
 *
 * A triple rather than a sentence because the only operation that matters is *superseding*: when
 * the user moves, "user lives_in Shanghai" must replace "user lives_in Beijing" rather than sit
 * next to it. Sentences cannot be compared; a normalised triple can.
 */
data class ExtractedFact(
    val subject: String,
    val predicate: String,
    val objectValue: String,
    val confidence: Double,
)

/**
 * Extraction parsing + the merge contract for the bi-temporal fact table.
 *
 * ## Why the parser lives here
 *
 * Extraction is one model call whose output is JSON. Every failure mode of that call — prose
 * before the JSON, a trailing comma, a missing field, a confidence of "high" — has to degrade to
 * "fewer facts", never to an exception, because this runs in a background coroutine after a
 * compaction the user is waiting on. Keeping it in `:runtime` means it is unit-testable with a
 * string literal and no provider.
 *
 * ## Why supersede instead of update
 *
 * The row is never rewritten: the old one gets `valid_to = now` and the new one is inserted.
 * That is what makes "what did we believe last week" answerable, and it is what stops two
 * conflicting facts from both being injected — only rows with `valid_to IS NULL` are.
 */
object MemoryFactRules {

    /** An extraction call that returns more than this is hallucinating; keep the best few. */
    const val MAX_FACTS_PER_EXTRACTION: Int = 8

    /** Below this the triple is not worth storing. */
    const val MIN_CONFIDENCE: Double = 0.55

    /**
     * What a fact with no stated confidence is assumed to be. The model was asked for one, but a
     * missing field must not discard a perfectly good triple — only an explicitly low value
     * should.
     */
    private const val ASSUMED_CONFIDENCE: Double = 0.7

    private const val MAX_SUBJECT_CHARS = 64
    private const val MAX_PREDICATE_CHARS = 48
    private const val MAX_OBJECT_CHARS = 200

    private val WHITESPACE = Regex("\\s+")
    private val JSON = Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * Parses an extraction response.
     *
     * The model is asked for `{"facts":[…]}` and nothing else, but prompts are not contracts:
     * the JSON is located inside whatever prose came back, and each entry is validated on its
     * own so one malformed fact does not discard the whole batch.
     */
    fun parse(
        raw: String,
        maxFacts: Int = MAX_FACTS_PER_EXTRACTION,
        minConfidence: Double = MIN_CONFIDENCE,
    ): List<ExtractedFact> {
        val body = extractJsonObject(raw) ?: return emptyList()
        val parsed = runCatching { JSON.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return emptyList()
        val array = parsed["facts"] as? JsonArray ?: return emptyList()

        val seen = HashSet<String>()
        val facts = ArrayList<ExtractedFact>(array.size)
        for (element in array) {
            val entry = element as? JsonObject ?: continue
            val subject = normalizeSubject(entry.string("subject")) ?: continue
            val predicate = normalizePredicate(entry.string("predicate")) ?: continue
            val objectValue = normalizeObject(entry.string("object")) ?: continue
            val confidence = entry.confidence()
            if (confidence < minConfidence) continue

            val fact = ExtractedFact(subject, predicate, objectValue, confidence)
            if (!seen.add(keyOf(fact))) continue
            facts += fact
            if (facts.size >= maxFacts) break
        }
        return facts
    }

    /**
     * The identity of a fact for supersede purposes: (subject, predicate).
     *
     * The object is deliberately *not* part of the key — a changed object is exactly the case
     * that has to replace the old row rather than coexist with it.
     */
    fun keyOf(fact: ExtractedFact): String = "${fact.subject}\u0000${fact.predicate}"

    /**
     * True when [existingObject] (the live row) already says the same thing as [candidate].
     *
     * Case-insensitive because the two sides come from different model calls: one wrote
     * "Shanghai", the next wrote "shanghai", and re-inserting the same fact under a new casing
     * would invalidate the old row for no reason.
     */
    fun isSameAssertion(existingObject: String, candidate: ExtractedFact): Boolean =
        normalizeObject(existingObject)?.lowercase() == candidate.objectValue.lowercase()

    /** Subjects are a key, so they are lowercased: "User" and "user" are the same entity. */
    fun normalizeSubject(raw: String?): String? {
        val value = normalize(raw) ?: return null
        return value.lowercase().take(MAX_SUBJECT_CHARS).trim().ifEmpty { null }
    }

    /**
     * Predicates are `snake_case` so that "requires" written three ways cannot become three
     * live facts. A free-text predicate is kept (lowercased, spaces to underscores) rather than
     * dropped: an unusual predicate is still better than a lost fact, and the extraction prompt
     * lists the preferred vocabulary.
     */
    fun normalizePredicate(raw: String?): String? {
        val value = normalize(raw) ?: return null
        val collapsed = WHITESPACE.replace(value.lowercase(), "_")
        return collapsed.take(MAX_PREDICATE_CHARS).trim('_').ifEmpty { null }
    }

    fun normalizeObject(raw: String?): String? {
        val value = normalize(raw) ?: return null
        return value.take(MAX_OBJECT_CHARS).trim().ifEmpty { null }
    }

    private fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        // Strip quotes the model sometimes wraps values in, then collapse control whitespace.
        return WHITESPACE.replace(trimmed.trim('"', '\''), " ").trim()
    }

    /** First `{` to last `}` — the model's JSON, whatever it wrapped it in. */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.confidence(): Double {
        val primitive = this["confidence"] as? JsonPrimitive ?: return ASSUMED_CONFIDENCE
        primitive.doubleOrNull?.let { return it.coerceIn(0.0, 1.0) }
        // "high"/"medium"/"low" and friends: a model that ignores the numeric instruction still
        // gets a usable answer instead of losing every fact in the batch.
        return when (primitive.contentOrNull?.trim()?.lowercase()) {
            "high", "certain", "confident" -> 0.9
            "medium", "moderate", "likely" -> 0.7
            "low", "uncertain", "guess" -> 0.4
            else -> ASSUMED_CONFIDENCE
        }
    }
}
