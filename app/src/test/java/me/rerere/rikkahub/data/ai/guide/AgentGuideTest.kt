package me.rerere.rikkahub.data.ai.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content tests for the on-demand manual.
 *
 * They cannot prove the text is right, but they do pin the three things that break silently: an
 * id that stops resolving, a chapter that lost its body, and a manual that grew until it costs
 * what it was meant to save.
 */
class AgentGuideTest {

    @Test
    fun `every chapter has a unique id, a title, and a body`() {
        val ids = AgentGuide.ALL.map { it.id }
        assertEquals("duplicate ids: $ids", ids.size, ids.toSet().size)
        AgentGuide.ALL.forEach { topic ->
            assertTrue("blank title: ${topic.id}", topic.title.isNotBlank())
            assertTrue("blank summary: ${topic.id}", topic.summary.isNotBlank())
            assertTrue("short body: ${topic.id}", topic.body.length > 200)
        }
    }

    @Test
    fun `render without a topic returns the table of contents`() {
        val rendered = AgentGuide.render(null)
        assertTrue(rendered.contains("<moxw_guide>"))
        AgentGuide.ALL.forEach { topic ->
            assertTrue("missing chapter in index: ${topic.id}", rendered.contains("- ${topic.id} — "))
        }
        // The index is a menu, not the manual: it must not carry any chapter body.
        AgentGuide.ALL.forEach { topic ->
            assertFalse(rendered.contains(topic.body))
        }
    }

    @Test
    fun `render with a topic returns that chapter and a way onward`() {
        val rendered = AgentGuide.render(AgentGuide.MISSION)
        assertNotNull(AgentGuide.topic(AgentGuide.MISSION))
        assertTrue(rendered.contains("<moxw_guide topic=\"mission\">"))
        assertTrue(rendered.contains("mission_create"))
        assertTrue(rendered.contains("Related:"))
        assertTrue(rendered.contains("moxw_guide"))
    }

    @Test
    fun `an unknown topic answers with the index instead of an error`() {
        val rendered = AgentGuide.render("does-not-exist")
        assertTrue(rendered.contains("No topic named"))
        assertTrue(rendered.contains("- ${AgentGuide.OVERVIEW} — "))
    }

    @Test
    fun `a topic id is matched case-insensitively and trimmed`() {
        assertEquals(AgentGuide.DEVICE, AgentGuide.topic("  Device ")?.id)
    }

    @Test
    fun `no single chapter blows the budget it exists to protect`() {
        // The manual is paid for only when read, but a 3,000-character chapter would still be a
        // context bomb in the middle of a task. 1,800 characters is roughly 450 tokens.
        AgentGuide.ALL.forEach { topic ->
            assertTrue(
                "chapter ${topic.id} is ${topic.body.length} chars",
                topic.body.length < 1_800,
            )
        }
    }

    @Test
    fun `the setup chapter names the fix for an install without an embedding model`() {
        val setup = AgentGuide.render(AgentGuide.SETUP)
        assertTrue(setup.contains("NOT ready"))
        assertTrue(setup.contains("tool_browse"))
    }
}
