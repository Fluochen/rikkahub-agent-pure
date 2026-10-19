package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMessageActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val branches = listOf(UIMessage.assistant("First"), UIMessage.assistant("Second"))

    @Test
    fun toolbarKeepsOnlyThreePrimaryActionsWithAccessibleTouchTargets() {
        val message = UIMessage.assistant("Hello")
        var menuOpened = false
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    Column {
                        ChatMessageActionButtons(
                            message = message,
                            node = MessageNode.of(message),
                            onUpdate = {},
                            onRegenerate = {},
                            onOpenActionSheet = { menuOpened = true },
                        )
                    }
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(3)
        listOf(R.string.copy, R.string.regenerate, R.string.more_options).forEach { id ->
            compose.onNodeWithContentDescription(text(id))
                .assertHasClickAction()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription(text(R.string.more_options)).performClick()
        compose.runOnIdle { assertEquals(true, menuOpened) }
    }

    @Test
    fun assistantRegenerateRemainsOneTap() {
        val message = UIMessage.assistant("Hello")
        var regenerations = 0
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    Column {
                        ChatMessageActionButtons(
                            message = message,
                            node = MessageNode.of(message),
                            onUpdate = {},
                            onRegenerate = { regenerations++ },
                            onOpenActionSheet = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.regenerate)).performClick()
        compose.runOnIdle { assertEquals(1, regenerations) }
    }

    @Test
    fun userRegenerateStillRequiresConfirmation() {
        val message = UIMessage.user("Hello")
        var regenerations = 0
        compose.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    Column {
                        ChatMessageActionButtons(
                            message = message,
                            node = MessageNode.of(message),
                            onUpdate = {},
                            onRegenerate = { regenerations++ },
                            onOpenActionSheet = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.regenerate)).performClick()
        compose.onNodeWithText(text(R.string.regenerate_confirm_message)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, regenerations) }
        compose.onNodeWithText(text(R.string.confirm)).performClick()
        compose.runOnIdle { assertEquals(1, regenerations) }
    }

    @Test
    fun firstBranchDisablesPreviousAndNextSelectsSecond() {
        var updated: MessageNode? = null
        compose.setContent {
            MaterialTheme {
                ChatMessageBranchSelector(MessageNode(messages = branches)) { updated = it }
            }
        }
        compose.onNodeWithText("1/2").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.accessibility_previous_branch)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.accessibility_next_branch)).performClick()
        compose.runOnIdle { assertEquals(1, updated?.selectIndex) }
    }

    @Test
    fun lastBranchDisablesNextAndPreviousSelectsFirst() {
        var updated: MessageNode? = null
        compose.setContent {
            MaterialTheme {
                ChatMessageBranchSelector(MessageNode(messages = branches, selectIndex = 1)) { updated = it }
            }
        }
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.accessibility_next_branch)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.accessibility_previous_branch)).performClick()
        compose.runOnIdle { assertEquals(0, updated?.selectIndex) }
    }

    @Test
    fun staleSelectionMatchesTheLastMessageFallback() {
        var updated: MessageNode? = null
        compose.setContent {
            MaterialTheme {
                ChatMessageBranchSelector(MessageNode(messages = branches, selectIndex = 99)) { updated = it }
            }
        }
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.accessibility_next_branch)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.accessibility_previous_branch)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(0, updated?.selectIndex) }
    }

    @Test
    fun noBranchControlsForASingleOrEmptyNode() {
        compose.setContent {
            MaterialTheme {
                Column {
                    ChatMessageBranchSelector(MessageNode(messages = emptyList()), onUpdate = {})
                    ChatMessageBranchSelector(MessageNode(messages = branches.take(1)), onUpdate = {})
                }
            }
        }
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
}
