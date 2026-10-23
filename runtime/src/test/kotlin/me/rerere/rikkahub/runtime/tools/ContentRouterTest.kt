package me.rerere.rikkahub.runtime.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioural contract of the content router: every strategy must (a) get under the budget,
 * (b) keep the information that made the payload worth reading, and (c) never lose the whole
 * thing. Each test names the failure it prevents.
 */
class ContentRouterTest {

    private val json = Json { isLenient = true }

    // ------------------------------------------------------------------ classification

    @Test
    fun `classifies a json array`() {
        val text = (1..20).joinToString(",", "[", "]") { """{"id":$it}""" }
        assertEquals(ContentKind.JSON_ARRAY, ContentRouter.classify("web_fetch", text))
    }

    @Test
    fun `classifies a json object`() {
        assertEquals(
            ContentKind.JSON_OBJECT,
            ContentRouter.classify("ssh_exec", """{"ok":true,"stdout":"hi"}"""),
        )
    }

    @Test
    fun `classifies a log`() {
        val text = buildString {
            repeat(50) { appendLine("2026-10-23 12:00:0$it INFO starting worker $it") }
            appendLine("ERROR could not connect")
            appendLine("WARN retrying")
            appendLine("FATAL giving up")
        }
        assertEquals(ContentKind.LOG, ContentRouter.classify("workspace_shell", text))
    }

    @Test
    fun `classifies a diff`() {
        val text = "diff --git a/x b/x\n--- a/x\n+++ b/x\n@@ -1,3 +1,3 @@\n-old\n+new"
        assertEquals(ContentKind.DIFF, ContentRouter.classify("workspace_shell", text))
    }

    @Test
    fun `classifies a markdown table`() {
        val text = "| a | b |\n|---|---|\n| 1 | 2 |\n| 3 | 4 |\n"
        assertEquals(ContentKind.TABLE, ContentRouter.classify("read_file", text))
    }

    @Test
    fun `classifies html`() {
        val text = "<html><body><p>hello</p></body></html>"
        assertEquals(ContentKind.HTML, ContentRouter.classify("web_fetch", text))
    }

    @Test
    fun `plain prose stays text`() {
        assertEquals(ContentKind.TEXT, ContentRouter.classify("read_file", "hello world, nothing else"))
    }

    @Test
    fun `blank input is text`() {
        assertEquals(ContentKind.TEXT, ContentRouter.classify("read_file", "   "))
    }

    // ------------------------------------------------------------------ budget respect

    @Test
    fun `content under budget is returned untouched`() {
        val text = "short result"
        val outcome = ContentRouter.compress(text, ContentKind.TEXT, 1_000)
        assertFalse(outcome.truncated)
        assertEquals(text, outcome.text)
    }

    @Test
    fun `non positive budget disables compression`() {
        val text = "x".repeat(50_000)
        val outcome = ContentRouter.compress(text, ContentKind.TEXT, 0)
        assertFalse(outcome.truncated)
        assertEquals(text, outcome.text)
    }

    @Test
    fun `text compression respects the budget and keeps both ends`() {
        val head = "HEAD-MARKER\n"
        val tail = "\nTAIL-MARKER"
        val text = head + "m".repeat(80_000) + tail
        val outcome = ContentRouter.compress(text, ContentKind.TEXT, 500)

        assertTrue(outcome.truncated)
        assertTrue("budget exceeded: ${outcome.resultTokens}", outcome.resultTokens <= 500)
        assertTrue(outcome.text.startsWith("HEAD-MARKER"))
        assertTrue(outcome.text.trimEnd().endsWith("TAIL-MARKER"))
    }

    // ------------------------------------------------------------------ json

    @Test
    fun `json array stays valid json and reports the elision`() {
        val items = (1..400).joinToString(",", "[", "]") { """{"id":$it,"name":"item-$it"}""" }
        val outcome = ContentRouter.compress(items, ContentKind.JSON_ARRAY, 400)

        assertTrue(outcome.truncated)
        val parsed = json.parseToJsonElement(outcome.text) as JsonArray
        assertTrue("expected a still-parseable array", parsed.isNotEmpty())
        assertTrue("elision must be visible to the model", outcome.text.contains("items elided"))
        assertTrue("first record lost", outcome.text.contains("\"id\":1,"))
        assertTrue("last record lost", outcome.text.contains("\"id\":400"))
    }

    @Test
    fun `json array keeps anomalous middle records`() {
        val normal = """{"id":%d,"ok":true}"""
        val body = (1..300).map { normal.format(it) }.toMutableList()
        body[150] = """{"id":151,"error":"permission denied"}"""
        val text = body.joinToString(",", "[", "]")

        val outcome = ContentRouter.compress(text, ContentKind.JSON_ARRAY, 400)
        assertTrue("an error record in the middle is the reason to read the output", outcome.text.contains("permission denied"))
    }

    @Test
    fun `json object compresses the bulk array but keeps siblings`() {
        val blob = "z".repeat(40)
        val items = (1..300).joinToString(",", "[", "]") { """{"id":$it,"blob":"$blob"}""" }
        val text = """{"data":$items,"next_cursor":"abc"}"""

        val outcome = ContentRouter.compress(text, ContentKind.JSON_OBJECT, 500)
        assertTrue(outcome.truncated)
        assertTrue("pagination metadata lost", outcome.text.contains("\"next_cursor\":\"abc\""))
        assertTrue(outcome.resultTokens <= 500)
        assertNotNull(json.parseToJsonElement(outcome.text))
    }

    // ------------------------------------------------------------------ log

    @Test
    fun `log keeps error lines and both ends`() {
        val lines = mutableListOf<String>()
        lines += "CMD: ./build.sh"
        repeat(400) { lines += "info: compiling module $it" }
        lines += "ERROR: undefined reference to foo"
        repeat(200) { lines += "info: retrying $it" }
        lines += "FATAL: build failed"
        val text = lines.joinToString("\n")

        val outcome = ContentRouter.compress(text, ContentKind.LOG, 300)

        assertTrue(outcome.truncated)
        assertTrue(outcome.resultTokens <= 300)
        assertTrue("the command line is the only provenance", outcome.text.startsWith("CMD: ./build.sh"))
        assertTrue("the error must survive", outcome.text.contains("ERROR: undefined reference to foo"))
        assertTrue("the conclusion must survive", outcome.text.trimEnd().endsWith("FATAL: build failed"))
    }

    @Test
    fun `log with no interesting lines still truncates`() {
        val lines = (1..500).map { "line $it of plain output" }
        val text = lines.joinToString("\n")
        val outcome = ContentRouter.compress(text, ContentKind.LOG, 200)
        assertTrue(outcome.truncated)
        assertTrue(outcome.resultTokens <= 200)
    }

    // ------------------------------------------------------------------ diff

    @Test
    fun `diff keeps hunk headers`() {
        val lines = mutableListOf<String>()
        lines += "diff --git a/a.kt b/a.kt"
        repeat(5) { hunk ->
            lines += "@@ -$hunk,3 +$hunk,3 @@"
            repeat(60) { lines += "+    line $it of added code in hunk $hunk" }
        }
        val text = lines.joinToString("\n")

        val outcome = ContentRouter.compress(text, ContentKind.DIFF, 400)
        assertTrue(outcome.truncated)
        assertTrue("hunk boundaries are the map of a diff", outcome.text.contains("@@ -0,3 +0,3 @@"))
        assertTrue(outcome.text.contains("@@ -4,3 +4,3 @@"))
    }

    // ------------------------------------------------------------------ table

    @Test
    fun `table keeps header and both ends`() {
        val lines = mutableListOf("| id | name |", "|---|---|")
        repeat(500) { lines += "| $it | row-$it |" }
        val text = lines.joinToString("\n")

        val outcome = ContentRouter.compress(text, ContentKind.TABLE, 200)
        assertTrue(outcome.truncated)
        assertTrue("header lost", outcome.text.startsWith("| id | name |\n|---|---|"))
        assertTrue(outcome.resultTokens <= 200)
        assertTrue("last row lost", outcome.text.contains("row-499"))
    }

    // ------------------------------------------------------------------ html

    @Test
    fun `html drops scripts and tags`() {
        val text = buildString {
            append("<html><head><script>")
            repeat(500) { append("var a = 1; ") }
            append("</script><style>.x{}</style></head><body>")
            repeat(500) { append("<p>paragraph $it</p>") }
            append("</body></html>")
        }
        val outcome = ContentRouter.compress(text, ContentKind.HTML, 300)
        assertTrue(outcome.truncated)
        assertFalse("script body leaked into context", outcome.text.contains("var a = 1"))
        assertTrue(outcome.resultTokens <= 300)
    }

    // ------------------------------------------------------------------ robustness

    @Test
    fun `malformed json falls back instead of throwing`() {
        val text = "[{broken json" + "x".repeat(50_000)
        val outcome = ContentRouter.compress(text, ContentKind.JSON_ARRAY, 300)
        assertTrue(outcome.truncated)
        assertTrue(outcome.resultTokens <= 300)
    }

    @Test
    fun `tiny payload never grows`() {
        val text = "[1,2,3,4,5,6,7,8,9,10]"
        val outcome = ContentRouter.compress(text, ContentKind.JSON_ARRAY, 5)
        assertTrue("compression must not inflate", outcome.text.length <= text.length)
    }
}
