package me.rerere.rikkahub.service

/**
 * Split [s] into pieces no longer than [n] code units, preferring natural break
 * points and never slicing through a UTF-16 surrogate pair.
 *
 * Cut preference (highest to lowest):
 *  1. Last "\n\n" paragraph break inside the trailing window of [n].
 *  2. Last single "\n" newline inside the trailing window.
 *  3. Hard `n`, walked back by 1 if it would land between a high surrogate at
 *     `n - 1` and its low surrogate at `n` — without this, multi-byte emoji get
 *     sliced (current chunk ends with an orphaned high surrogate, next chunk
 *     starts with a dangling low surrogate, both render as tofu boxes).
 *
 * Internal so the unit test in `:app`'s test source set can exercise it directly
 * — the production caller is `TelegramBotService.chunk`, which is a thin wrapper.
 */
internal fun chunkForTelegram(s: String, n: Int): List<String> {
    // n < 2 can never honour both "at most n" and "never split a surrogate pair"
    // (the walk-back below would land on a zero cut and spin forever). Clamp instead
    // of trusting every caller to pass a sane cap.
    val limit = n.coerceAtLeast(2)
    if (s.length <= limit) return listOf(s)
    val out = mutableListOf<String>()
    var rem = s
    while (rem.length > limit) {
        val window = limit / 2
        // Search from limit - 2: lastIndexOf's start index is inclusive, so searching
        // from `limit` lets a "\n\n" START at `limit` and the `+ 2` then emits a chunk
        // of limit + 2 chars - over the very cap this function exists to enforce.
        val paraCut = rem.lastIndexOf("\n\n", limit - 2).let { if (it > window) it + 2 else -1 }
        val nlCut = if (paraCut < 0) rem.lastIndexOf('\n', limit).let { if (it > window) it else -1 } else -1
        var cut = when {
            paraCut > 0 -> paraCut
            nlCut > 0 -> nlCut
            else -> limit
        }
        if (cut in 1 until rem.length &&
            rem[cut - 1].isHighSurrogate() &&
            rem[cut].isLowSurrogate()
        ) {
            cut -= 1
        }
        // Progress guarantee: a zero cut would loop forever on the same remainder.
        if (cut < 1) cut = 1
        out.add(rem.substring(0, cut))
        rem = rem.substring(cut).trimStart('\n')
    }
    if (rem.isNotEmpty()) out.add(rem)
    return out
}
