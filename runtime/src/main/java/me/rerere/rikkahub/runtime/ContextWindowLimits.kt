package me.rerere.rikkahub.runtime

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.limitContext

/**
 * [limitContext] that cannot eat the compacted summary at the head of the request.
 *
 * ## The bug this closes
 *
 * A compacted view is `[summary] + rawTail`. The summary is one synthetic user message
 * standing in for every message before the tail, and it is the *only* remaining record of
 * that conversation. The message-count limit, however, is positional: it drops a leading
 * slice once the list is too long, and it will happily drop that summary while keeping a
 * verbose tail. The result is a request whose history is gone — worse than not compacting,
 * because the user believes the earlier turns are still known to the model.
 *
 * The two knobs are unrelated in intent (one bounds the number of raw turns, the other
 * bounds total size) and both are off by default, which is exactly why the interaction was
 * never noticed. A user who turns both on is the one who loses everything.
 *
 * ## Contract
 *
 * - Leading synthetic messages are pinned and do not count against the limit; the raw tail
 *   is then limited to `limit - pinned` (at least one message, so a limit of 1 still leaves
 *   something to answer).
 * - With no leading synthetic message this is exactly [limitContext].
 * - A disabled or generous limit returns the list untouched, as before.
 */
internal fun List<UIMessage>.limitContextPreservingLeadingSynthetic(limit: Int): List<UIMessage> {
    if (limit <= 0 || size <= limit) return this

    val pinned = indexOfFirst { !it.isSynthetic }.let { if (it < 0) size else it }
    // The ordinary case: the conversation starts with a real user turn.
    if (pinned == 0) return limitContext(limit)
    // Every message is synthetic (request-only scaffolding). Dropping them all would leave
    // nothing to answer, so leave the list alone rather than "limit" it into emptiness.
    if (pinned >= size) return this

    val tailLimit = (limit - pinned).coerceAtLeast(1)
    return subList(0, pinned) + subList(pinned, size).limitContext(tailLimit)
}
