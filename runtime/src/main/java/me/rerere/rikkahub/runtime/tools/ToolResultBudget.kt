package me.rerere.rikkahub.runtime.tools

/**
 * How many tokens one tool result is allowed to occupy.
 *
 * ## Why a derived default instead of "off"
 *
 * Turning this off was the old default, and it only worked because truncation was gated on
 * the workspace shell: without a shell the result went in whole. That is exactly backwards —
 * the conversation that cannot `cat` the spill is the one that most needs a bound, because
 * there is no way to get the bytes back. So the budget is now derived from the model window
 * and applies whether or not any shell exists.
 *
 * One twentieth of the window: a result must not be able to eat more than 5% of the context,
 * because a normal turn executes several tools and the remainder has to hold the prompt, the
 * tool schemas and the conversation itself. The floor keeps a small window from collapsing
 * into uselessness (a 400-token result is still a screen of text); the ceiling stops a huge
 * window from tolerating results no model needs in one piece.
 *
 * ## Escape hatches
 *
 * An explicit per-assistant value always wins. An explicit value of `0` or less means "off",
 * which restores the legacy 32 KB character gate rather than disabling truncation entirely —
 * an unbounded result is never a safe outcome, only a slower failure.
 */
object ToolResultBudget {

    /** Below this a budget stops being a summary and starts being a teaser. */
    const val MIN_TOKENS: Int = 400

    /** Above this a single result is worth more as a spilled file than as context. */
    const val MAX_TOKENS: Int = 2_000

    /**
     * Used when the provider publishes no window. Deliberately mid-range rather than [MIN_TOKENS]:
     * assuming the smallest window would truncate aggressively for models that are almost
     * certainly 8K or larger, and every unnecessary cut costs a round trip to read it back.
     */
    const val UNKNOWN_CONTEXT_TOKENS: Int = 1_500

    private const val DIVISOR = 20

    /** The derived budget for a model window, clamped into `[MIN_TOKENS, MAX_TOKENS]`. */
    fun forContextLength(contextLength: Int?): Int {
        val window = contextLength?.takeIf { it > 0 } ?: return UNKNOWN_CONTEXT_TOKENS
        return (window / DIVISOR).coerceIn(MIN_TOKENS, MAX_TOKENS)
    }

    /**
     * The effective budget: [explicit] when the user set one, otherwise the derived default.
     *
     * Returns `null` only for an explicit non-positive value, which callers must read as
     * "fall back to the legacy character gate" — never as "send the result unbounded".
     */
    fun resolve(explicit: Int?, contextLength: Int?): Int? =
        if (explicit != null) explicit.takeIf { it > 0 } else forContextLength(contextLength)
}
