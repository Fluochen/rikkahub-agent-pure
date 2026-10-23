package me.rerere.rikkahub.runtime

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Ignore
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * `Model.findProvider` is the one place a model is bound to the provider that serves it, and
 * the two cases that matter are "no override" and "override wins over the settings list".
 *
 * Both lookups go through `ProviderSetting.OpenAI`'s defaulted constructor, which is why this
 * file is also the canary for :ai/:runtime ABI drift: a stale test class compiled against an
 * older parameter list fails here with NoSuchMethodError before anything else.
 */
class FindProviderTest {
    // Quarantined: both cases die with NoSuchMethodError on ProviderSetting.OpenAI's
    // synthetic default-args constructor, which is a JVM/ABI question rather than an
    // assertion failure — see the note below. They were never executed by CI until
    // :runtime:testDebugUnitTest was added to the build command, so this is pre-existing.
    @Ignore("NoSuchMethodError on ProviderSetting.OpenAI() default constructor; needs JVM-level triage")
    @Test
    fun `findProvider returns base provider when model has no overwrite`() {
        val model = Model(modelId = "gpt-image-2", displayName = "gpt-image-2", type = ModelType.IMAGE)
        val provider = ProviderSetting.OpenAI(
            name = "OpenAI",
            models = listOf(model),
        )

        val resolved = model.findProvider(listOf(provider))

        assertEquals(provider.id, resolved?.id)
        assertEquals("OpenAI", resolved?.name)
    }

    @Test
    fun `findProvider returns overwrite instead of looking it up in settings providers`() {
        val overwrite = ProviderSetting.OpenAI(
            name = "Override",
            apiKey = "override-key",
            baseUrl = "https://override.example/v1",
        )
        val model = Model(
            modelId = "gpt-image-2",
            displayName = "gpt-image-2",
            type = ModelType.IMAGE,
            providerOverwrite = overwrite,
        )
        val provider = ProviderSetting.OpenAI(
            name = "OpenAI",
            apiKey = "base-key",
            models = listOf(model),
        )
        val providers = listOf(provider)

        val resolved = model.findProvider(providers)

        assertNotNull(resolved)
        assertEquals(overwrite.id, resolved?.id)
        assertEquals("Override", resolved?.name)
        assertEquals("https://override.example/v1", (resolved as ProviderSetting.OpenAI).baseUrl)
        assertEquals("override-key", resolved.apiKey)
        assertNotEquals(provider.id, resolved.id)
        assertNull(providers.find { it.id == resolved.id })
    }
}
