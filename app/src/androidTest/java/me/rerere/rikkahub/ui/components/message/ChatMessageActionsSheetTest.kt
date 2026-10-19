package me.rerere.rikkahub.ui.components.message

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.ui.hooks.CustomTtsState
import me.rerere.tts.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMessageActionsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val tts = TestTtsState()
    private val events = mutableListOf<String>()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun showSheet(
        message: UIMessage = UIMessage.assistant("Hello"),
        display: DisplaySetting = DisplaySetting(),
    ) {
        compose.setContent {
            CompositionLocalProvider(
                LocalSettings provides Settings(displaySetting = display),
                LocalTTSState provides tts,
            ) {
                MaterialTheme {
                    ChatMessageActionsSheet(
                        message = message,
                        model = null,
                        onDelete = { events += "delete" },
                        onEdit = { events += "edit" },
                        onShare = { events += "share" },
                        onFork = { events += "fork" },
                        onSelectAndCopy = { events += "select" },
                        onToggleFavorite = { events += "favorite" },
                        onWebViewPreview = { events += "preview" },
                        onTranslate = { events += "translate" },
                        onDismissRequest = { events += "dismiss" },
                    )
                }
            }
        }
    }

    @Test
    fun translationDismissesMenuBeforeOpeningLanguagePicker() {
        showSheet()
        compose.onNodeWithText(text(R.string.translate)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss", "translate"), events) }
    }

    @Test
    fun deleteRemainsReachableByScrolling() {
        showSheet()
        compose.onNodeWithText(text(R.string.delete)).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss", "delete"), events) }
    }

    @Test
    fun unavailableSpeechIsDisabledButAnActivePlaybackCanStillBeStopped() {
        tts.isAvailable.value = false
        showSheet()
        compose.onNodeWithText(text(R.string.tts)).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { tts.isSpeaking.value = true }
        compose.onNodeWithText(text(R.string.stop)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, tts.stopCalls) }
    }

    @Test
    fun speechStillHonorsTheQuotedTextPreference() {
        val message = UIMessage.assistant("Intro \"Read this\" Outro")
        showSheet(message, DisplaySetting(ttsOnlyReadQuoted = true))
        compose.onNodeWithText(text(R.string.tts)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Read this", tts.spokenText)
            assertEquals(listOf("dismiss"), events)
        }
    }

    @Test
    fun userMessagesDoNotOfferAssistantOnlySpeechOrTranslation() {
        showSheet(UIMessage.user("Hello"))
        compose.onNodeWithText(text(R.string.tts)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.translate)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.edit)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss", "edit"), events) }
    }

    private class TestTtsState : CustomTtsState {
        override val isAvailable = MutableStateFlow(true)
        override val isSpeaking = MutableStateFlow(false)
        override val error = MutableStateFlow<String?>(null)
        override val currentChunk = MutableStateFlow(0)
        override val totalChunks = MutableStateFlow(0)
        override val playbackState = MutableStateFlow(PlaybackState())
        var spokenText: String? = null
        var stopCalls = 0
        override fun speak(text: String, flushCalled: Boolean) { spokenText = text }
        override fun stop() { stopCalls++ }
        override fun pause() = Unit
        override fun resume() = Unit
        override fun skipNext() = Unit
        override fun fastForward(ms: Long) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun cleanup() = Unit
    }
}
