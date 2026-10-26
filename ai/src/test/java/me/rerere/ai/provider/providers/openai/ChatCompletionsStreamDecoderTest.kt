package me.rerere.ai.provider.providers.openai

import me.rerere.ai.provider.stream.SseEvent
import me.rerere.ai.ui.StreamChunk
import me.rerere.ai.ui.UIMessageAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Providers keep adding annotation types (`file_citation`, `container_file_citation`, ...).
 * The decoder must skip the ones it does not render instead of failing the whole stream —
 * an annotation is a footnote, and losing the model's answer to protect one is backwards.
 */
class ChatCompletionsStreamDecoderTest {

    private fun deltaEvent(annotations: String): SseEvent = SseEvent(
        data = """{"id":"chatcmpl-1","model":"gpt-test","choices":[{"index":0,"delta":{"role":"assistant","content":"hello","annotations":$annotations}}]}"""
    )

    @Test
    fun `unknown and malformed annotations are skipped, not fatal`() {
        val decoder = ChatCompletionsStreamDecoder()
        val chunks = decoder.accept(
            deltaEvent(
                """[
                  {"type":"url_citation","url_citation":{"title":"Example","url":"https://example.com"}},
                  {"type":"file_citation","file_citation":{"filename":"report.pdf"}},
                  {"type":"some_future_type"},
                  {"no_type_here":true},
                  "not even an object"
                ]"""
            )
        ).chunks

        val texts = chunks.filterIsInstance<StreamChunk.TextDelta>()
        assertEquals(listOf("hello"), texts.map { it.text })

        val annotations = chunks.filterIsInstance<StreamChunk.Annotations>()
            .flatMap { it.annotations }
        assertEquals(
            listOf(UIMessageAnnotation.UrlCitation(title = "Example", url = "https://example.com")),
            annotations,
        )
    }

    @Test
    fun `a stream with only unknown annotations still finishes normally`() {
        val decoder = ChatCompletionsStreamDecoder()
        decoder.accept(deltaEvent("""[{"type":"file_path","file_id":"file-123"}]"""))
        val result = decoder.accept(SseEvent(data = "[DONE]"))

        assertTrue(result.completed)
        assertTrue(result.chunks.any { it is StreamChunk.Finish })
    }
}
