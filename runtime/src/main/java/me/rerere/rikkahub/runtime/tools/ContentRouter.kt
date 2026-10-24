package me.rerere.rikkahub.runtime.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a tool result looks like, judged from the text alone.
 *
 * The kind decides *how* an over-budget result is cut, because one strategy for every shape
 * is what makes naive truncation useless: a 4 KB head of a 200 KB JSON array is ten records
 * and no idea how many were dropped, while a 4 KB head of a build log is the ten lines
 * before the interesting part. Nothing here calls a model — this runs on every tool result,
 * so it must stay pure string work.
 */
enum class ContentKind { JSON_ARRAY, JSON_OBJECT, LOG, DIFF, TABLE, HTML, CODE, TEXT }

/**
 * A compressed tool result plus the arithmetic behind it.
 *
 * [elidedChars] is approximate for the structured kinds (a JSON array is re-serialised, not
 * sliced), and exact for the head/tail kinds. It exists for the log line and the model-facing
 * notice ("how much was dropped"), never for a correctness decision.
 */
data class RoutedText(
    val text: String,
    val kind: ContentKind,
    val truncated: Boolean,
    val originalTokens: Int,
    val resultTokens: Int,
    val elidedChars: Int,
)

/**
 * Cuts an oversized tool result down to a token budget in a shape-aware way.
 *
 * ## Why this exists
 *
 * The loop used one gate for everything: keep the first 32 KB of characters, and only when a
 * workspace shell happened to be on the surface (so that the model could `cat` the rest). Two
 * problems with that. Without a shell there was no truncation at all — a single `read_file` or
 * screenshot description could carry the conversation past the window. And the preview was
 * head-only, which is exactly the half of a log that does not contain the exit status, the
 * stack trace, or the conclusion.
 *
 * ## Contract
 *
 * - Pure and deterministic: no IO, no model, no Android. Safe on the hot path.
 * - Never throws. A strategy that fails (malformed JSON, absurd input) falls back to
 *   [truncateToolResult]'s head/tail cut, and [compress] is itself wrapped so that even that
 *   cannot escape as an exception.
 * - Never returns something bigger than the input. Every strategy is allowed to decline
 *   (`null`), in which case the next one down the ladder runs.
 * - The caller owns the spill: this class does not know where the full text went, only that
 *   the caller is responsible for making it retrievable.
 */
object ContentRouter {

    /** Items kept from either end of a JSON array; the middle is sampled, not sliced. */
    private const val ARRAY_HEAD_ITEMS = 3
    private const val ARRAY_TAIL_ITEMS = 3

    /**
     * How far into the array the "interesting item" scan reaches. Records that matter (a
     * failed entry, an error envelope) are realistically near an end; scanning 20 000 items
     * would serialise the whole document twice to find them.
     */
    private const val ARRAY_SCAN_HEAD = 200
    private const val ARRAY_SCAN_TAIL = 50

    /** Upper bound on how many anomalous middle items are kept on top of both ends. */
    private const val ARRAY_MAX_INTERESTING = 6

    /** Above this size a `[`-prefixed payload is not JSON worth parsing on the hot path. */
    private const val MAX_JSON_PARSE_CHARS = 2_000_000

    private val ERROR_MARKERS = listOf(
        "\"error\"",
        "\"failed\"",
        "\"failure\"",
        "\"exception\"",
        "\"success\":false",
        "\"success\": false",
        "\"ok\":false",
        "\"ok\": false",
        "\"status\":5",
        "\"status\": 5",
    )

    private val JSON = Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * Classifies [text]. Order matters: JSON is tried first because a serialized payload
     * frequently *contains* log-looking words, while a log almost never parses as JSON.
     */
    fun classify(toolName: String, text: String): ContentKind {
        if (text.isBlank()) return ContentKind.TEXT
        val trimmed = text.trimStart()

        if (trimmed.length <= MAX_JSON_PARSE_CHARS && (trimmed.startsWith('[') || trimmed.startsWith('{'))) {
            val parsed = runCatching { JSON.parseToJsonElement(trimmed) }.getOrNull()
            if (parsed is JsonArray) return ContentKind.JSON_ARRAY
            if (parsed is JsonObject) return ContentKind.JSON_OBJECT
        }

        if (looksLikeDiff(text)) return ContentKind.DIFF
        if (looksLikeLog(text)) return ContentKind.LOG
        if (looksLikeTable(text)) return ContentKind.TABLE
        if (looksLikeHtml(trimmed)) return ContentKind.HTML
        if (looksLikeCode(text)) return ContentKind.CODE
        return ContentKind.TEXT
    }

    /**
     * Compresses [text] to roughly [maxTokens], choosing the strategy from [kind].
     *
     * Returns the input untouched (`truncated = false`) when it already fits or the budget is
     * not positive, and leaves it untouched when no strategy manages to shrink it.
     */
    fun compress(text: String, kind: ContentKind, maxTokens: Int): RoutedText {
        val originalTokens = TokenEstimator.estimate(text)
        if (maxTokens <= 0 || originalTokens <= maxTokens) {
            return RoutedText(text, kind, false, originalTokens, originalTokens, 0)
        }

        val candidate = runCatching { compressWith(text, kind, maxTokens) }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length < text.length }
            ?: text

        // A strategy can restructure the text without getting under the budget (a JSON array
        // of 500 one-token records keeps 12 records but is still mostly marker overhead). The
        // final guard is shape-blind on purpose: respecting the budget outranks keeping the
        // shape, because an over-budget result is what we were asked to prevent.
        val finalText = if (TokenEstimator.estimate(candidate) > maxTokens) {
            runCatching { truncateToolResult(candidate, maxTokens).text }.getOrDefault(candidate)
        } else {
            candidate
        }

        val resultTokens = TokenEstimator.estimate(finalText)
        // A tiny input with a tiny budget can be "compressed" into a marker that is longer
        // than what it replaced. Returning the original is strictly better: the caller saves
        // nothing by the cut and loses the content, so decline instead of inflating.
        if (finalText.length >= text.length) {
            return RoutedText(text, kind, false, originalTokens, originalTokens, 0)
        }
        return RoutedText(
            text = finalText,
            kind = kind,
            truncated = resultTokens < originalTokens,
            originalTokens = originalTokens,
            resultTokens = resultTokens,
            elidedChars = (text.length - finalText.length).coerceAtLeast(0),
        )
    }

    // ---------------------------------------------------------------------------------
    // Strategy selection
    // ---------------------------------------------------------------------------------

    private fun compressWith(text: String, kind: ContentKind, maxTokens: Int): String? = when (kind) {
        ContentKind.JSON_ARRAY -> compressJsonArray(text, maxTokens)
        ContentKind.JSON_OBJECT -> compressJsonObject(text, maxTokens)
        ContentKind.LOG -> selectLines(
            text = text,
            maxTokens = maxTokens,
            isPriority = ::isLogPriorityLine,
            headRatio = 0.15,
            tailRatio = 0.15,
        )
        ContentKind.DIFF -> selectLines(
            text = text,
            maxTokens = maxTokens,
            isPriority = ::isDiffPriorityLine,
            headRatio = 0.08,
            tailRatio = 0.08,
        )
        ContentKind.TABLE -> selectTableRows(text, maxTokens)
        ContentKind.HTML -> stripHtml(text).takeIf { it.isNotBlank() }?.let { stripped ->
            truncateToolResult(stripped, maxTokens).text
        }
        ContentKind.CODE, ContentKind.TEXT -> truncateToolResult(text, maxTokens).text
    }

    // ---------------------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------------------

    /**
     * Keeps both ends of an array plus any anomalous middle record, and marks each gap with a
     * string element so the result is *still valid JSON*. That matters: a model handed
     * `[1,2,{…` back cannot parse it, retries, and burns more tokens than the cut saved.
     */
    private fun compressJsonArray(text: String, maxTokens: Int): String? {
        val array = runCatching { JSON.parseToJsonElement(text.trim()) }.getOrNull() as? JsonArray
            ?: return null
        val size = array.size
        if (size <= ARRAY_HEAD_ITEMS + ARRAY_TAIL_ITEMS + 1) return null

        val keep = sortedSetOf<Int>()
        for (index in 0 until minOf(ARRAY_HEAD_ITEMS, size)) keep += index
        for (index in maxOf(0, size - ARRAY_TAIL_ITEMS) until size) keep += index

        var interesting = 0
        val scanHeadEnd = minOf(size, ARRAY_SCAN_HEAD)
        for (index in 0 until scanHeadEnd) {
            if (interesting >= ARRAY_MAX_INTERESTING) break
            if (index in keep) continue
            if (looksAnomalous(array[index])) {
                keep += index
                interesting++
            }
        }
        for (index in maxOf(scanHeadEnd, size - ARRAY_SCAN_TAIL) until size) {
            if (interesting >= ARRAY_MAX_INTERESTING) break
            if (index in keep) continue
            if (looksAnomalous(array[index])) {
                keep += index
                interesting++
            }
        }

        if (keep.size >= size) return null

        val rebuilt = buildJsonArray {
            var previous = -1
            for (index in keep) {
                if (index > previous + 1) {
                    add(JsonPrimitive(gapMarker(index - previous - 1, "items")))
                }
                add(array[index])
                previous = index
            }
            if (previous < size - 1) {
                add(JsonPrimitive(gapMarker(size - 1 - previous, "items")))
            }
        }
        return rebuilt.toString()
    }

    /**
     * The common wrapper shape — `{"data": [...], "next_cursor": …}` — where the bulk sits in
     * one array value. Compresses that array in place and keeps every other key untouched, so
     * pagination metadata survives.
     */
    private fun compressJsonObject(text: String, maxTokens: Int): String? {
        val obj = runCatching { JSON.parseToJsonElement(text.trim()) }.getOrNull() as? JsonObject
            ?: return null
        if (obj.isEmpty()) return null

        val largest = obj.entries
            .filter { it.value is JsonArray }
            .maxByOrNull { (it.value as JsonArray).size }
            ?: return null
        val array = largest.value as JsonArray
        if (array.size <= ARRAY_HEAD_ITEMS + ARRAY_TAIL_ITEMS + 1) return null

        // Only worth restructuring when the array is where the bulk lives; otherwise the
        // surrounding scalars are the payload and the head/tail cut is the better answer.
        val arrayText = array.toString()
        if (TokenEstimator.estimate(arrayText) * 2 < TokenEstimator.estimate(text)) return null

        val compressedArray = compressJsonArray(arrayText, (maxTokens / 2).coerceAtLeast(64)) ?: return null
        val replacement = runCatching { JSON.parseToJsonElement(compressedArray) }.getOrNull()
            ?: return null

        val rebuilt = buildJsonObject {
            for ((key, value) in obj) {
                if (key == largest.key) put(key, replacement) else put(key, value)
            }
        }
        return rebuilt.toString()
    }

    private fun looksAnomalous(element: JsonElement): Boolean {
        val rendered = element.toString()
        return ERROR_MARKERS.any { marker -> rendered.contains(marker, ignoreCase = true) }
    }

    private fun gapMarker(count: Int, unit: String): String = "…[$count $unit elided]…"

    // ---------------------------------------------------------------------------------
    // Line-oriented shapes (log / diff)
    // ---------------------------------------------------------------------------------

    /**
     * Keeps the lines that carry meaning, then fills the remaining budget from both ends.
     *
     * Selection is by importance, emission is by position: the caller must be able to read the
     * result top-to-bottom and still see what happened first and last, with the dropped runs
     * marked where they were.
     */
    private fun selectLines(
        text: String,
        maxTokens: Int,
        isPriority: (String) -> Boolean,
        headRatio: Double,
        tailRatio: Double,
    ): String? {
        val lines = text.split('\n')
        if (lines.size < 8) return null

        val headCount = (lines.size * headRatio).toInt().coerceAtLeast(1)
        val tailCount = (lines.size * tailRatio).toInt().coerceAtLeast(1)
        val tailStart = (lines.size - tailCount).coerceAtLeast(headCount)

        // Importance order: priority lines first (they are why we are looking), then the ends.
        // Budget is spent in this order, so a tiny budget keeps the signal rather than an
        // arbitrary prefix.
        val picks = ArrayList<Pair<Int, Int>>(lines.size) // index to class
        lines.forEachIndexed { index, line ->
            when {
                isPriority(line) -> picks += index to 0
                index < headCount -> picks += index to 1
                index >= tailStart -> picks += index to 2
            }
        }
        if (picks.size >= lines.size) return null

        // Markers cost tokens too; hold a slice of the budget back for them rather than
        // discovering afterwards that the last run did not fit.
        val markerReserve = (maxTokens / 10).coerceAtLeast(16)
        var remaining = (maxTokens - markerReserve).coerceAtLeast(1)

        // Within one class, closer to an edge is more important: a log's last lines are the
        // crash, its first lines are the command that produced it.
        val ordered = picks.sortedWith(
            compareBy<Pair<Int, Int>> { it.second }
                .thenBy { (index, _) -> minOf(index, lines.lastIndex - index) }
        )

        val kept = sortedSetOf<Int>()
        for ((index, _) in ordered) {
            val cost = TokenEstimator.estimate(lines[index]) + 1
            if (cost > remaining) continue
            remaining -= cost
            kept += index
        }
        if (kept.isEmpty()) return null

        return renderSelectedLines(lines, kept)
    }

    private fun renderSelectedLines(lines: List<String>, kept: Set<Int>): String = buildString {
        var previous = -1
        for (index in kept) {
            if (index > previous + 1) {
                if (isNotEmpty()) append('\n')
                append(gapMarker(index - previous - 1, "lines"))
            }
            if (isNotEmpty()) append('\n')
            append(lines[index])
            previous = index
        }
        if (previous < lines.lastIndex) {
            if (isNotEmpty()) append('\n')
            append(gapMarker(lines.lastIndex - previous, "lines"))
        }
    }

    private fun isLogPriorityLine(line: String): Boolean {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("ERROR") || trimmed.startsWith("WARN") ||
            trimmed.startsWith("FATAL") || trimmed.startsWith("SEVERE")
        ) {
            return true
        }
        // logcat / log4j style prefixes: "E/AndroidRuntime", "[error]"
        if (trimmed.length > 1 && trimmed[1] == '/' && trimmed[0] in setOf('E', 'W', 'F', 'A')) {
            return true
        }
        if (trimmed.startsWith("[error]") || trimmed.startsWith("[warn]") ||
            trimmed.startsWith("[fatal]")
        ) {
            return true
        }
        if (trimmed.startsWith("Caused by:") || trimmed.startsWith("Traceback (most recent")) {
            return true
        }
        if (trimmed.startsWith("Exception in thread")) return true
        // Stack frames: the tail of a JVM failure is all frames, and dropping them loses the
        // only evidence of where it broke.
        if (trimmed.startsWith("at ") && trimmed.contains('(') && trimmed.endsWith(")")) return true
        return false
    }

    private fun isDiffPriorityLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return trimmed.startsWith("@@") ||
            trimmed.startsWith("diff --git") ||
            trimmed.startsWith("+++ ") ||
            trimmed.startsWith("--- ") ||
            trimmed.startsWith("index ") ||
            trimmed.startsWith("Binary files")
    }

    // ---------------------------------------------------------------------------------
    // Table / HTML / code detection
    // ---------------------------------------------------------------------------------

    /**
     * Keeps the header plus rows from both ends. A markdown table's value is its columns and
     * its total count; the middle rows are the cheapest thing to lose.
     */
    private fun selectTableRows(text: String, maxTokens: Int): String? {
        val lines = text.split('\n')
        if (lines.size < 6) return null
        val headerCount = if (lines.size > 1 && lines[1].contains("---")) 2 else 1

        val markerReserve = (maxTokens / 10).coerceAtLeast(16)
        var remaining = (maxTokens - markerReserve).coerceAtLeast(1)

        val kept = sortedSetOf<Int>()
        for (index in 0 until headerCount) {
            remaining -= TokenEstimator.estimate(lines[index]) + 1
            kept += index
        }

        var head = headerCount
        var tail = lines.lastIndex
        var takeHead = true
        while (head <= tail) {
            val index = if (takeHead) head else tail
            val cost = TokenEstimator.estimate(lines[index]) + 1
            if (cost > remaining) break
            remaining -= cost
            kept += index
            if (takeHead) head++ else tail--
            takeHead = !takeHead
        }
        if (kept.size >= lines.size) return null
        return renderSelectedLines(lines, kept)
    }

    private fun stripHtml(text: String): String {
        var working = text
        REPLACED_BLOCKS.forEach { (open, close) ->
            var start = working.indexOf(open, ignoreCase = true)
            while (start >= 0) {
                val end = working.indexOf(close, startIndex = start, ignoreCase = true)
                working = if (end < 0) {
                    working.substring(0, start)
                } else {
                    working.removeRange(start, end + close.length)
                }
                start = working.indexOf(open, ignoreCase = true)
            }
        }
        val withoutTags = StringBuilder(working.length)
        var insideTag = false
        for (char in working) {
            when {
                char == '<' -> insideTag = true
                char == '>' && insideTag -> {
                    insideTag = false
                    withoutTags.append(' ')
                }
                !insideTag -> withoutTags.append(char)
            }
        }
        return WS_RUN.replace(withoutTags, " ").trim()
    }

    private val REPLACED_BLOCKS = listOf(
        "<script" to "</script>",
        "<style" to "</style>",
        "<!--" to "-->",
    )
    private val WS_RUN = Regex("\\s+")

    private fun looksLikeDiff(text: String): Boolean =
        text.contains("@@ -") || text.startsWith("diff --git") || text.contains("\ndiff --git")

    private fun looksLikeLog(text: String): Boolean {
        var matches = 0
        var lines = 0
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            lines++
            if (isLogPriorityLine(line)) {
                matches++
                if (matches >= 3) return true
            }
            if (lines > 400) break
        }
        return false
    }

    private fun looksLikeTable(text: String): Boolean {
        var headerSeen = false
        for (line in text.lineSequence().take(40)) {
            val trimmed = line.trim()
            if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
                if (headerSeen && trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' }) {
                    return true
                }
                headerSeen = true
            }
        }
        return false
    }

    private fun looksLikeHtml(trimmed: String): Boolean =
        trimmed.startsWith("<") && trimmed.contains("</") && !trimmed.startsWith("<<")

    private fun looksLikeCode(text: String): Boolean {
        var hits = 0
        for (line in text.lineSequence().take(80)) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("package ") ||
                trimmed.startsWith("import ") ||
                trimmed.startsWith("#include") ||
                trimmed.startsWith("def ") ||
                trimmed.startsWith("fn ") ||
                trimmed.startsWith("func ") ||
                trimmed.startsWith("public class ") ||
                trimmed.startsWith("private class ") ||
                trimmed.startsWith("class ") ||
                trimmed.startsWith("interface ")
            ) {
                hits++
                if (hits >= 3) return true
            }
        }
        return false
    }
}
