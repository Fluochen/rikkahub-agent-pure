package me.rerere.rikkahub.runtime.tools

/**
 * The one token estimator every "does this fit?" decision shares.
 *
 * ASCII is counted at three characters per token and every non-ASCII character at one token
 * each. That deliberately over-counts mixed CJK/ASCII text: an over-estimate costs one
 * unnecessary truncation (the full text is still on disk), while an under-estimate costs a
 * provider 400 or an unrecoverable overflow in the middle of a turn.
 *
 * Previously this lived only inside [me.rerere.rikkahub.runtime.ContextCompactionPlanner].
 * The tool-result budget needs the same arithmetic, and two copies of it would drift — a
 * truncation that believes it saved 4K tokens while compaction believes it saved 6K makes
 * every downstream budget decision wrong. The planner now delegates here.
 */
internal object TokenEstimator {

    /** Estimated tokens in [text]; never zero, so callers can divide by it. */
    fun estimate(text: String): Int = estimate(text, 0, text.length).coerceAtLeast(1)

    /**
     * Estimated tokens in `text[startIndex, endIndex)`. Exposed for callers that scan a
     * string incrementally (splitting, slicing) and cannot afford to copy a substring first.
     */
    fun estimate(text: String, startIndex: Int, endIndex: Int): Int {
        var asciiChars = 0L
        var nonAsciiChars = 0L
        for (index in startIndex until endIndex) {
            if (text[index].code <= 0x7F) asciiChars++ else nonAsciiChars++
        }
        return (nonAsciiChars + (asciiChars + 2L) / 3L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    /**
     * The index reached by walking [text] from [from] towards [to] while the running estimate
     * stays within [budget]. Walking forward yields the end of a head slice; walking backward
     * from `text.length` yields the start of a tail slice.
     */
    fun advanceByTokens(
        text: String,
        from: Int,
        to: Int,
        budget: Int,
        forward: Boolean,
    ): Int {
        if (budget <= 0) return from
        var ascii = 0L
        var nonAscii = 0L
        var index = from
        while (index != to) {
            val char = text[if (forward) index else index - 1]
            if (char.code <= 0x7F) ascii++ else nonAscii++
            val tokens = nonAscii + (ascii + 2L) / 3L
            if (tokens > budget) break
            index += if (forward) 1 else -1
        }
        return index
    }
}
