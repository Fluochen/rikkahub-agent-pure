package me.rerere.rikkahub.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAssistantPromptTest {

    @Test
    fun `blank built-in prompt upgrades to the skeleton`() {
        assertEquals(DefaultAssistantPrompt.SKELETON, DefaultAssistantPrompt.upgrade(""))
        assertEquals(DefaultAssistantPrompt.SKELETON, DefaultAssistantPrompt.upgrade("   \n"))
    }

    @Test
    fun `legacy default upgrades even when whitespace differs`() {
        val reformatted = DefaultAssistantPrompt.LEGACY_DEFAULT
            .replace("\n", "\n   ")
            .replace("- Date:", "-  Date:")
        assertEquals(DefaultAssistantPrompt.SKELETON, DefaultAssistantPrompt.upgrade(reformatted))
    }

    @Test
    fun `the skeleton shipped before this one also upgrades`() {
        // Installs that took #393's skeleton never edited it; it is still a default, so it must
        // be replaced rather than kept forever as a half-manual.
        assertEquals(
            DefaultAssistantPrompt.SKELETON,
            DefaultAssistantPrompt.upgrade(DefaultAssistantPrompt.PREVIOUS_SKELETON),
        )
    }

    @Test
    fun `a user-written prompt is left untouched`() {        val custom = "You are my editor. Always answer in 简体中文 and never use em dashes."
        assertEquals(custom, DefaultAssistantPrompt.upgrade(custom))
    }

    @Test
    fun `skeleton stays generic and carries the template placeholders`() {
        val skeleton = DefaultAssistantPrompt.SKELETON
        assertTrue(skeleton.contains("{{char}}"))
        assertTrue(skeleton.contains("{{model_name}}"))
        assertTrue(skeleton.contains("{{cur_date}}"))
        assertTrue(skeleton.contains("{{locale}}"))
        assertTrue(skeleton.contains("{{timezone}}"))
        // Mechanics belong to the injected packs, not to the user-facing skeleton.
        assertTrue(!skeleton.contains("read_window_tree"))
        assertTrue(!skeleton.contains("mission_create"))
        assertTrue(skeleton.length < 1_500)
    }
}
