package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.uuid.Uuid
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.PinOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation

/**
 * Represents different types of items in the conversation list
 */
sealed class ConversationListItem {
    data class DateHeader(
        val date: LocalDate,
        val label: String
    ) : ConversationListItem()
    data object PinnedHeader : ConversationListItem()
    data class Item(
        val conversation: Conversation
    ) : ConversationListItem()
}

@Composable
fun ConversationList(
    current: Conversation,
    conversations: LazyPagingItems<ConversationListItem>,
    conversationJobs: Collection<Uuid>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (Conversation) -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
    onRename: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMoveToAssistant: (Conversation) -> Unit = {},
    onMoveToFolder: (Conversation) -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    var hasScrolledToCurrent by remember(current.id) { mutableStateOf(false) }

    val headerCount = if (header != null) 1 else 0
    LaunchedEffect(current.id, conversations.itemCount, hasScrolledToCurrent, headerCount) {
        if (hasScrolledToCurrent) return@LaunchedEffect
        val snapshotIndex = conversations.itemSnapshotList.indexOfFirst {
            (it as? ConversationListItem.Item)?.conversation?.id == current.id
        }
        if (snapshotIndex >= 0) {
            val currentIndex = snapshotIndex + headerCount
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }
            if (!isVisible) {
                listState.scrollToItem(currentIndex)
            }
            hasScrolledToCurrent = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 8.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        header?.let { content ->
            item(key = "drawer_header", contentType = "controls") { content() }
        }
        if (conversations.itemCount == 0) {
            item(key = "list_status") {
                ConversationListStatus(
                    loadState = conversations.loadState.refresh,
                    onRetry = { conversations.retry() },
                )
            }
        }

        items(
            count = conversations.itemCount,
            key = conversations.itemKey { item ->
                when (item) {
                    is ConversationListItem.DateHeader -> "date_${item.date}"
                    is ConversationListItem.PinnedHeader -> "pinned_header"
                    is ConversationListItem.Item -> item.conversation.id.toString()
                }
            }
        ) { index ->
            when (val item = conversations[index]) {
                is ConversationListItem.DateHeader -> {
                    DateHeaderItem(
                        label = item.label,
                        modifier = Modifier.animateItem()
                    )
                }

                is ConversationListItem.PinnedHeader -> {
                    PinnedHeader(
                        modifier = Modifier.animateItem()
                    )
                }

                is ConversationListItem.Item -> {
                    ConversationItem(
                        conversation = item.conversation,
                        selected = item.conversation.id == current.id,
                        loading = item.conversation.id in conversationJobs,
                        onClick = onClick,
                        onDelete = onDelete,
                        onRename = onRename,
                        onPin = onPin,
                        onMoveToAssistant = onMoveToAssistant,
                        onMoveToFolder = onMoveToFolder,
                        modifier = Modifier.animateItem()
                    )
                }

                null -> {
                    // Placeholder for loading state
                }
            }
        }
        if (conversations.itemCount > 0) {
            // A refresh failure must not turn cached conversations into an empty list.
            if (conversations.loadState.refresh is LoadState.Error) {
                item(key = "refresh_error") {
                    ConversationListStatus(conversations.loadState.refresh) { conversations.retry() }
                }
            } else if (conversations.loadState.append is LoadState.Error ||
                conversations.loadState.append is LoadState.Loading
            ) {
                item(key = "append_status") {
                    ConversationListStatus(conversations.loadState.append) { conversations.retry() }
                }
            }
        }

        footer?.let { content ->
            item(key = "drawer_footer", contentType = "controls") { content() }
        }
    }
}

/** Never present loading or a failed query as an empty folder. */
@Composable
internal fun ConversationListStatus(loadState: LoadState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (loadState) {
            is LoadState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.accessibility_loading),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is LoadState.Error -> {
                Text(
                    stringResource(R.string.chat_drawer_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_drawer_retry)) }
            }
            is LoadState.NotLoading -> Text(
                stringResource(R.string.chat_page_no_conversations),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateHeaderItem(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PinnedHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = HugeIcons.Pin,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.pinned_chats),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
internal fun ConversationItem(
    conversation: Conversation,
    selected: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onDelete: (Conversation) -> Unit = {},
    onRename: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMoveToAssistant: (Conversation) -> Unit = {},
    onMoveToFolder: (Conversation) -> Unit = {},
    onClick: (Conversation) -> Unit
) {
    val loadingDescription = stringResource(R.string.accessibility_loading)
    val moreDescription = stringResource(R.string.more_options)
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val itemShape = RoundedCornerShape(12.dp)
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val timeText = remember(conversation.updateAt) {
        val zoneId = ZoneId.systemDefault()
        val dt = conversation.updateAt.atZone(zoneId).toLocalDateTime()
        val today = LocalDate.now(zoneId)
        if (dt.toLocalDate() == today) {
            String.format(Locale.getDefault(), "%02d:%02d", dt.hour, dt.minute)
        } else if (dt.year == today.year) {
            String.format(Locale.getDefault(), "%d/%d", dt.monthValue, dt.dayOfMonth)
        } else {
            String.format(Locale.getDefault(), "%02d/%d/%d", dt.year % 100, dt.monthValue, dt.dayOfMonth)
        }
    }
    var showDropdownMenu by remember {
        mutableStateOf(false)
    }
    Box(
        modifier = modifier
            .clip(itemShape)
            .semantics {
                this.selected = selected
                if (loading) stateDescription = loadingDescription
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onClick(conversation) },
                onLongClickLabel = moreDescription,
                onLongClick = {
                    // Also clear chat input focus when the drawer is permanently visible.
                    focusManager.clearFocus(force = true)
                    showDropdownMenu = true
                }
            )
            .background(backgroundColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 选中指示竖条（未选中时透明占位，保证各条目文字起始基线严格对齐）
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 16.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = conversation.title.ifBlank { stringResource(R.string.chat_page_new_message) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (conversation.isPinned) {
                        Icon(
                            imageVector = HugeIcons.Pin,
                            contentDescription = stringResource(R.string.accessibility_pinned),
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (loading) {
                        // Text, not just a green dot: distinguish running work without relying on colour.
                        Text(
                            text = loadingDescription,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            // Keep long-press as a shortcut, but make management actions discoverable.
            Box {
                IconButton(
                    onClick = {
                        focusManager.clearFocus(force = true)
                        showDropdownMenu = true
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        HugeIcons.MoreVertical,
                        contentDescription = moreDescription,
                        modifier = Modifier.size(18.dp),
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = showDropdownMenu,
                    onDismissRequest = { showDropdownMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (conversation.isPinned) stringResource(R.string.unpin_chat) else stringResource(R.string.pin_chat)
                            )
                        },
                        onClick = {
                            onPin(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                if (conversation.isPinned) HugeIcons.PinOff else HugeIcons.Pin,
                                null
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(stringResource(id = R.string.chat_page_rename_chat))
                        },
                        onClick = {
                            onRename(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = {
                            Icon(HugeIcons.PencilEdit01, null)
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.chat_page_move_to_assistant))
                        },
                        onClick = {
                            onMoveToAssistant(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = {
                            Icon(HugeIcons.Forward02, null)
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.chat_page_move_to_folder))
                        },
                        onClick = {
                            onMoveToFolder(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = {
                            Icon(HugeIcons.Folder01, null)
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(stringResource(id = R.string.chat_page_delete))
                        },
                        onClick = {
                            onDelete(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = {
                            Icon(HugeIcons.Delete01, null)
                        }
                    )
                }
            }
        }
    }
}
