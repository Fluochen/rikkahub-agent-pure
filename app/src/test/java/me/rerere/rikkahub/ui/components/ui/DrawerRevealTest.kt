package me.rerere.rikkahub.ui.components.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerRevealTest {

    @Test
    fun closedOffsetIsZeroProgress() {
        assertEquals(0f, DrawerReveal.openFraction(-300f, 300f, false), 0.001f)
    }

    @Test
    fun openOffsetIsFullProgress() {
        assertEquals(1f, DrawerReveal.openFraction(0f, 300f, true), 0.001f)
    }

    @Test
    fun halfwayFollowsTheFinger() {
        assertEquals(0.5f, DrawerReveal.openFraction(-150f, 300f, false), 0.001f)
    }

    @Test
    fun nanFallsBackToDrawerValue() {
        assertEquals(1f, DrawerReveal.openFraction(Float.NaN, 300f, true), 0.001f)
        assertEquals(0f, DrawerReveal.openFraction(Float.NaN, 300f, false), 0.001f)
    }

    @Test
    fun zeroWidthFallsBack() {
        assertEquals(0f, DrawerReveal.openFraction(0f, 0f, false), 0.001f)
    }

    @Test
    fun translationAlignsScaledLeftEdgeToDrawer() {
        val drawer = 300f
        val content = 360f
        val progress = 1f
        val tx = DrawerReveal.translationX(progress, drawer, content)
        val scale = DrawerReveal.scale(progress)
        val left = content * (1f - scale) / 2f + tx
        assertEquals(drawer, left, 0.5f)
    }

    @Test
    fun closedTranslationIsZero() {
        assertEquals(0f, DrawerReveal.translationX(0f, 300f, 360f), 0.001f)
        assertEquals(1f, DrawerReveal.scale(0f), 0.001f)
    }
}
