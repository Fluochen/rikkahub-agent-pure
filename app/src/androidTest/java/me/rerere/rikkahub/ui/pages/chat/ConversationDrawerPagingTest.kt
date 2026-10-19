package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationDrawerPagingTest {
    @get:Rule val compose = createComposeRule()

    private val entries = (0 until 30).map {
        ConversationListItem.Item(Conversation.ofId(Uuid.random()).copy(title = "Conversation $it"))
    }

    @Test
    fun scrollToCurrentAccountsForTheScrollableHeader() {
        val target = entries[20].conversation
        val flow = MutableStateFlow(PagingData.from<ConversationListItem>(entries))
        var state: LazyListState? = null
        compose.setContent {
            MaterialTheme {
                val listState = rememberLazyListState()
                state = listState
                ConversationList(
                    current = target,
                    conversations = flow.collectAsLazyPagingItems(),
                    conversationJobs = emptyList(),
                    listState = listState,
                    modifier = Modifier.width(280.dp).height(240.dp),
                    header = { Text("Drawer header") },
                    footer = { Text("Drawer footer") },
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            state?.layoutInfo?.visibleItemsInfo?.any { it.key == target.id.toString() } == true
        }
        compose.onNodeWithText(target.title).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun shortViewportCanScrollToFooterControls() {
        val flow = MutableStateFlow(PagingData.from<ConversationListItem>(entries))
        val current = Conversation.ofId(Uuid.random())
        compose.setContent {
            MaterialTheme {
                ConversationList(
                    current = current,
                    conversations = flow.collectAsLazyPagingItems(),
                    conversationJobs = emptyList(),
                    listState = rememberLazyListState(),
                    modifier = Modifier.width(280.dp).height(240.dp).testTag("conversations"),
                    header = { Text("Drawer header") },
                    footer = { Text("Drawer footer") },
                )
            }
        }
        compose.onNodeWithTag("conversations").performScrollToNode(hasText("Drawer footer"))
        compose.onNodeWithText("Drawer footer").assertIsDisplayed()
    }
}
