package me.rerere.rikkahub.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemInjectionsTest {

    @Test
    fun `default set renders every pack with the authoritative header`() {
        val rendered = SystemInjections.render()
        assertTrue(rendered.startsWith("<system_injections>"))
        assertTrue(rendered.endsWith("</system_injections>"))
        assertTrue(rendered.contains("these win"))
        SystemInjections.ALL.forEach { pack ->
            assertTrue("missing pack: ${pack.id}", rendered.contains("## ${pack.title}"))
        }
    }

    @Test
    fun `render order is the declared order, not set order`() {
        val rendered = SystemInjections.render(
            setOf(SystemInjections.MISSION, SystemInjections.CORE, SystemInjections.SAFETY),
        )
        assertTrue(
            rendered.indexOf("## Core conduct") < rendered.indexOf("## Safety and authorization"),
        )
        assertTrue(
            rendered.indexOf("## Safety and authorization") <
                rendered.indexOf("## Missions and long work"),
        )
        assertFalse(rendered.contains("## Files and workspace"))
    }

    @Test
    fun `empty set renders nothing so the builder can skip the section`() {
        assertEquals("", SystemInjections.render(emptySet()))
    }

    @Test
    fun `normalize drops unknown ids and keeps known ones`() {
        val normalized = SystemInjections.normalize(setOf("core", "not-a-pack"))
        assertEquals(setOf("core"), normalized)
    }

    @Test
    fun `stable bytes across calls with the same input`() {
        val enabled = setOf(SystemInjections.TOOLS, SystemInjections.FILES)
        assertEquals(
            SystemInjections.render(enabled),
            SystemInjections.render(enabled),
        )
    }
}
