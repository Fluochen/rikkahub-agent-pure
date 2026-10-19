package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.uuid.Uuid
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationDrawerTest {
    @get:Rule val compose = createComposeRule()

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val conversation = Conversation.ofId(Uuid.random()).copy(title = "A conversation worth finding")

    @Test
    fun searchAndNewChatAreSeparateAccessibleActions() {
        var searches = 0
        var newChats = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(280.dp)) {
                    DrawerSearchActions(onSearch = { searches++ }, onNewChat = { newChats++ })
                }
            }
        }
        compose.onNodeWithText(text(R.string.chat_page_search_chats))
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithContentDescription(text(R.string.chat_page_new_chat))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(1, searches)
            assertEquals(1, newChats)
        }
    }

    @Test
    fun selectedConversationExposesItsStateAndHasAComfortableRowHeight() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(280.dp)) {
                    ConversationItem(conversation, selected = true, loading = false, onClick = {})
                }
            }
        }
        compose.onNodeWithText(conversation.title).assertIsSelected().assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun runningConversationHasTextStatusNotJustAColourDot() {
        compose.setContent {
            MaterialTheme {
                ConversationItem(conversation, selected = false, loading = true, onClick = {})
            }
        }
        compose.onNodeWithText(conversation.title).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text(R.string.accessibility_loading))
        )
        compose.onNodeWithText(text(R.string.accessibility_loading)).assertIsDisplayed()
    }

    @Test
    fun moreOpensMenuWithoutNavigatingAndRenameKeepsItsCallback() {
        var opens = 0
        var renames = 0
        compose.setContent {
            MaterialTheme {
                ConversationItem(
                    conversation, selected = false, loading = false,
                    onClick = { opens++ }, onRename = { renames++ },
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.more_options))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(text(R.string.chat_page_rename_chat)).performClick()
        compose.runOnIdle {
            assertEquals(0, opens)
            assertEquals(1, renames)
        }
    }

    @Test
    fun longPressStillOpensTheSameManagementMenu() {
        compose.setContent {
            MaterialTheme {
                ConversationItem(conversation, selected = false, loading = false, onClick = {})
            }
        }
        compose.onNodeWithText(conversation.title).performTouchInput { longClick() }
        compose.onNodeWithText(text(R.string.chat_page_move_to_folder)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_page_move_to_assistant)).assertIsDisplayed()
    }

    @Test
    fun untitledConversationRetainsItsFallbackAndClickTarget() {
        var opens = 0
        compose.setContent {
            MaterialTheme {
                ConversationItem(
                    conversation.copy(title = ""), selected = false, loading = false,
                    onClick = { opens++ },
                )
            }
        }
        compose.onNodeWithText(text(R.string.chat_page_new_message)).performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test
    fun folderSelectionIsAccessibleAndLongNamesAreBounded() {
        val name = "A very long folder name that should never occupy the entire horizontal strip"
        var selections = 0
        compose.setContent {
            MaterialTheme {
                FolderChip(label = name, selected = true, onClick = { selections++ })
            }
        }
        compose.onNodeWithText(name)
            .assertIsSelected().assertHeightIsAtLeast(48.dp).assertWidthIsEqualTo(200.dp).performClick()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test
    fun loadingIsNotReportedAsAnEmptyFolder() {
        compose.setContent { MaterialTheme { ConversationListStatus(LoadState.Loading, onRetry = {}) } }
        compose.onNodeWithText(text(R.string.accessibility_loading)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_page_no_conversations)).assertDoesNotExist()
    }

    @Test
    fun failedLoadOffersRetryInsteadOfAnEmptyFolderMessage() {
        var retries = 0
        compose.setContent {
            MaterialTheme { ConversationListStatus(LoadState.Error(IllegalStateException())) { retries++ } }
        }
        compose.onNodeWithText(text(R.string.chat_drawer_load_failed)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_page_no_conversations)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.chat_drawer_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun successfulEmptyQueryShowsTheEmptyStateWithoutRetry() {
        compose.setContent {
            MaterialTheme { ConversationListStatus(LoadState.NotLoading(endOfPaginationReached = true), onRetry = {}) }
        }
        compose.onNodeWithText(text(R.string.chat_page_no_conversations)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.chat_drawer_retry)).assertDoesNotExist()
    }
}
