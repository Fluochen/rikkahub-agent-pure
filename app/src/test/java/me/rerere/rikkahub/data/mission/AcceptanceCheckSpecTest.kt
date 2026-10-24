package me.rerere.rikkahub.data.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 批 H — 验收条件的清洗、可判定性与提示词块。 */
class AcceptanceCheckSpecTest {

    @Test
    fun `empty checks are dropped`() {
        val cleaned = AcceptanceCheckSpec.sanitize(
            listOf(
                AcceptanceCheck(text = "", kind = AcceptanceKind.MANUAL),
                AcceptanceCheck(text = "报告存在", kind = AcceptanceKind.FILE_EXISTS, target = "report.md"),
            )
        )
        assertEquals(1, cleaned.size)
        assertEquals("报告存在", cleaned.single().text)
    }

    @Test
    fun `text falls back to the target when the label is blank`() {
        val cleaned = AcceptanceCheckSpec.sanitize(
            listOf(AcceptanceCheck(text = "  ", kind = AcceptanceKind.COMMAND, target = "pytest -q"))
        )
        assertEquals("pytest -q", cleaned.single().text)
    }

    @Test
    fun `command without a target is dropped`() {
        val cleaned = AcceptanceCheckSpec.sanitize(
            listOf(AcceptanceCheck(text = "跑测试", kind = AcceptanceKind.COMMAND, target = " "))
        )
        assertTrue(cleaned.isEmpty())
    }

    @Test
    fun `timeouts are clamped`() {
        val cleaned = AcceptanceCheckSpec.sanitize(
            listOf(
                AcceptanceCheck(text = "a", kind = AcceptanceKind.COMMAND, target = "x", timeoutSec = 0),
                AcceptanceCheck(text = "b", kind = AcceptanceKind.COMMAND, target = "y", timeoutSec = 99_999),
            )
        )
        assertEquals(AcceptanceCheckSpec.MIN_TIMEOUT_SEC, cleaned[0].timeoutSec)
        assertEquals(AcceptanceCheckSpec.MAX_TIMEOUT_SEC, cleaned[1].timeoutSec)
    }

    @Test
    fun `manual checks survive but are not machine checkable`() {
        val cleaned = AcceptanceCheckSpec.sanitize(
            listOf(AcceptanceCheck(text = "语气像人写的", kind = AcceptanceKind.MANUAL))
        )
        assertEquals(1, cleaned.size)
        assertFalse(cleaned.single().machineCheckable)
    }

    @Test
    fun `list is capped`() {
        val many = (1..50).map {
            AcceptanceCheck(text = "条件$it", kind = AcceptanceKind.FILE_EXISTS, target = "f$it")
        }
        assertEquals(AcceptanceCheckSpec.MAX_CHECKS, AcceptanceCheckSpec.sanitize(many).size)
    }

    @Test
    fun `min bytes needs a positive threshold`() {
        val check = AcceptanceCheck(
            text = "报告不是空的",
            kind = AcceptanceKind.MIN_BYTES,
            target = "report.md",
            value = 0L,
        )
        assertFalse(check.machineCheckable)
    }

    @Test
    fun `prompt block lists every check with its judgement method`() {
        val block = AcceptanceCheckSpec.composePromptBlock(
            listOf(
                AcceptanceCheck(text = "测试通过", kind = AcceptanceKind.COMMAND, target = "pytest -q"),
                AcceptanceCheck(text = "有总结", kind = AcceptanceKind.FILE_EXISTS, target = "SUMMARY.md"),
            )
        )
        assertTrue(block.contains("验收条件"))
        assertTrue(block.contains("pytest -q"))
        assertTrue(block.contains("SUMMARY.md"))
    }

    @Test
    fun `describe includes the target for humans`() {
        val check = AcceptanceCheck(
            text = "有总结",
            kind = AcceptanceKind.FILE_EXISTS,
            target = "SUMMARY.md",
        )
        assertTrue(check.describe().contains("SUMMARY.md"))
    }
}
