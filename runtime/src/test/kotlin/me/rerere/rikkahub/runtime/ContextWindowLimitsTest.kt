package me.rerere.rikkahub.runtime

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.limitContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The interaction that no single knob's tests could catch: compaction writes a synthetic
 * summary at the head of the request, and the message-count limit drops from that same head.
 * Turning both on used to erase the history the summary was supposed to preserve.
 */
class ContextWindowLimitsTest {

    private fun message(text: String, synthetic: Boolean = false) =
        UIMessage.user(text).copy(isSynthetic = synthetic)

    private fun UIMessage.text(): String =
        (parts.first() as UIMessagePart.Text).text

    @Test
    fun `without a synthetic prefix the behaviour is exactly the plain limit`() {
        val messages = (1..40).map { message("m$it") }
        val expected = messages.limitContext(10)

        assertEquals(expected, messages.limitContextPreservingLeadingSynthetic(10))
    }

    @Test
    fun `the compaction summary survives a limit far smaller than the tail`() {
        val messages = listOf(message("SUMMARY", synthetic = true)) + (1..40).map { message("m$it") }

        val limited = messages.limitContextPreservingLeadingSynthetic(5)

        assertEquals("SUMMARY", limited.first().text())
        // The limit is an upper bound with hysteresis, so the tail lands at or below
        // 5 - 1 messages; what matters is that the summary is not the thing that paid for it.
        assertTrue("limit overshot: ${limited.size}", limited.size <= 5)
        assertTrue("tail was erased", limited.size >= 2)
        assertEquals(messages.last(), limited.last())
    }

    @Test
    fun `a limit of one still leaves the summary plus something to answer`() {
        val messages = listOf(message("SUMMARY", synthetic = true)) + (1..10).map { message("m$it") }

        val limited = messages.limitContextPreservingLeadingSynthetic(1)

        assertEquals(2, limited.size)
        assertEquals("SUMMARY", limited.first().text())
    }

    @Test
    fun `a disabled limit leaves everything alone`() {
        val messages = listOf(message("SUMMARY", synthetic = true)) + (1..100).map { message("m$it") }

        assertEquals(messages, messages.limitContextPreservingLeadingSynthetic(0))
        assertEquals(messages, messages.limitContextPreservingLeadingSynthetic(-1))
    }

    @Test
    fun `a generous limit leaves everything alone`() {
        val messages = listOf(message("SUMMARY", synthetic = true)) + (1..5).map { message("m$it") }

        assertEquals(messages, messages.limitContextPreservingLeadingSynthetic(100))
    }

    @Test
    fun `a list made only of synthetic messages is never emptied`() {
        val messages = (1..20).map { message("s$it", synthetic = true) }

        val limited = messages.limitContextPreservingLeadingSynthetic(5)

        assertTrue("must not return an empty request", limited.isNotEmpty())
        assertEquals(messages, limited)
    }
}
