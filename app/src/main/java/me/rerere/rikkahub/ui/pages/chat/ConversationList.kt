package me.rerere.rikkahub.ui.pages.chat

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.PinOff
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PencilEdit01
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.runtime.util.toLocalString
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.uuid.Uuid

sealed class ConversationListItem {
    data class DateHeader(val date: LocalDate, val label: String) : ConversationListItem()
    data object PinnedHeader : ConversationListItem()
    data class Item(val conversation: Conversation) : ConversationListItem()
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
    onMoveToFolder: (Conversation) -> Unit = {}
) {
    var hasScrolledToCurrent by remember(current.id) { mutableStateOf(false) }

    LaunchedEffect(current.id, conversations.itemCount, hasScrolledToCurrent) {
        if (hasScrolledToCurrent) return@LaunchedEffect
        val currentIndex = conversations.itemSnapshotList.items.indexOfFirst {
            (it as? ConversationListItem.Item)?.conversation?.id == current.id
        }
        if (currentIndex >= 0) {
            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }
            if (!isVisible) {
                listState.scrollToItem(currentIndex)
            }
            hasScrolledToCurrent = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (conversations.itemCount == 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "\uD83D\uDCAD",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(id = R.string.chat_page_no_conversations),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.chat_page_new_chat) + " →",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
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
                    DateHeaderItem(label = item.label, modifier = Modifier.animateItem())
                }
                is ConversationListItem.PinnedHeader -> {
                    PinnedHeader(modifier = Modifier.animateItem())
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
                null -> {}
            }
        }
    }
}

@Composable
private fun DateHeaderItem(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Box(
            modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun PinnedHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = HugeIcons.Pin,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = stringResource(R.string.pinned_chats),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        )
        Spacer(Modifier.size(8.dp))
        Box(
            modifier = Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun ConversationItem(
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
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val itemShape = RoundedCornerShape(12.dp)
    // DeepSeek selected style: subtle primaryContainer fill with soft outline
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
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
    var showDropdownMenu by remember { mutableStateOf(false) }

    // Outer container with DeepSeek card feel
    Box(
        modifier = modifier
            .clip(itemShape)
            .background(backgroundColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onClick(conversation) },
                onLongClick = {
                    focusManager.clearFocus(force = true)
                    showDropdownMenu = true
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main text column
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = conversation.title.ifBlank { stringResource(id = R.string.chat_page_new_message) },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Subtle preview: could show folder or model hint; we show time + pin inline for compactness
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                    if (conversation.isPinned) {
                        Icon(
                            imageVector = HugeIcons.Pin,
                            contentDescription = stringResource(R.string.accessibility_pinned),
                            modifier = Modifier.size(11.dp),
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    if (loading) {
                        Box(
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.extendColors.green6).size(6.dp)
                                .semantics { contentDescription = stringResource(R.string.accessibility_loading) }
                        )
                    }
                }
            }

            // Chevron/indicator for selected - DeepSeek shows subtle arrow on selected
            if (selected) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                )
            }

            DropdownMenu(expanded = showDropdownMenu, onDismissRequest = { showDropdownMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (conversation.isPinned) stringResource(R.string.unpin_chat) else stringResource(R.string.pin_chat)) },
                    onClick = { onPin(conversation); showDropdownMenu = false },
                    leadingIcon = { Icon(if (conversation.isPinned) HugeIcons.PinOff else HugeIcons.Pin, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.chat_page_rename_chat)) },
                    onClick = { onRename(conversation); showDropdownMenu = false },
                    leadingIcon = { Icon(HugeIcons.PencilEdit01, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_page_move_to_assistant)) },
                    onClick = { onMoveToAssistant(conversation); showDropdownMenu = false },
                    leadingIcon = { Icon(HugeIcons.Forward02, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_page_move_to_folder)) },
                    onClick = { onMoveToFolder(conversation); showDropdownMenu = false },
                    leadingIcon = { Icon(HugeIcons.Folder01, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(id = R.string.chat_page_delete)) },
                    onClick = { onDelete(conversation); showDropdownMenu = false },
                    leadingIcon = { Icon(HugeIcons.Delete01, null) }
                )
            }
        }
    }
}
