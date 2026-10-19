package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.TextSelection
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.hugeicons.stroke.WebDesign01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.runtime.util.toLocalString
import me.rerere.rikkahub.runtime.util.toMessageTimeString
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent

/** Keep the reading surface quiet; secondary actions live in the labelled, scrollable sheet. */
@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var showRegenerateConfirm by remember(message.id) { mutableStateOf(false) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        MessageActionIconButton(
            icon = HugeIcons.Copy01,
            label = stringResource(R.string.copy),
            onClick = { context.copyMessageToClipboard(message) },
        )
        MessageActionIconButton(
            icon = HugeIcons.Refresh03,
            label = stringResource(R.string.regenerate),
            onClick = {
                if (message.role == MessageRole.USER) {
                    showRegenerateConfirm = true
                } else {
                    onRegenerate()
                }
            },
        )
        MessageActionIconButton(
            icon = HugeIcons.MoreVertical,
            label = stringResource(R.string.more_options),
            onClick = onOpenActionSheet,
        )
        ChatMessageBranchSelector(node = node, onUpdate = onUpdate)
        if (settings.displaySetting.showDateTimeInMessage) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(),
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
        text = { Text(stringResource(R.string.regenerate_confirm_message)) }
    )
}

/** Explicit 48dp layout bounds avoid overlapping expanded touch targets in a dense toolbar. */
@Composable
private fun MessageActionIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Tooltip(tooltip = { Text(label) }) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onSelectAndCopy: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onWebViewPreview: () -> Unit,
    onTranslate: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val settings = LocalSettings.current
    val tts = LocalTTSState.current
    val isSpeaking by tts.isSpeaking.collectAsStateWithLifecycle()
    val isAvailable by tts.isAvailable.collectAsStateWithLifecycle()
    val hasTextContent = message.parts.filterIsInstance<UIMessagePart.Text>()
        .any { it.text.isNotBlank() }

    // Dismiss before invoking actions that open another sheet/dialog or navigate away.
    fun perform(action: () -> Unit) {
        onDismissRequest()
        action()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.more_options), style = MaterialTheme.typography.titleLarge)
                model?.let {
                    Text(
                        text = it.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = message.createdAt.toJavaLocalDateTime().toLocalString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    MessageActionRow(HugeIcons.TextSelection, stringResource(R.string.select_and_copy)) {
                        perform(onSelectAndCopy)
                    }
                    if (message.role == MessageRole.ASSISTANT) {
                        MessageActionRow(
                            icon = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                            label = stringResource(if (isSpeaking) R.string.stop else R.string.tts),
                            enabled = isAvailable || isSpeaking,
                        ) {
                            perform {
                                if (isSpeaking) {
                                    tts.stop()
                                } else {
                                    var text = message.toText()
                                    if (settings.displaySetting.ttsOnlyReadQuoted) {
                                        text = text.extractQuotedContentAsText() ?: text
                                    }
                                    if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
                                        text = text.removeBracketedContent() ?: text
                                    }
                                    tts.speak(text)
                                }
                            }
                        }
                        if (onTranslate != null) {
                            MessageActionRow(HugeIcons.Translate, stringResource(R.string.translate)) {
                                perform(onTranslate)
                            }
                        }
                    }
                    if (hasTextContent) {
                        MessageActionRow(HugeIcons.WebDesign01, stringResource(R.string.render_with_webview)) {
                            perform(onWebViewPreview)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MessageActionRow(HugeIcons.Edit01, stringResource(R.string.edit)) { perform(onEdit) }
                    MessageActionRow(HugeIcons.Share04, stringResource(R.string.share)) { perform(onShare) }
                    MessageActionRow(HugeIcons.GitFork, stringResource(R.string.create_fork)) { perform(onFork) }
                    if (onToggleFavorite != null) {
                        MessageActionRow(
                            icon = HugeIcons.FavouriteCircle,
                            label = stringResource(
                                if (isFavorite) R.string.chat_message_remove_favorite
                                else R.string.chat_message_add_favorite
                            ),
                        ) { perform(onToggleFavorite) }
                    }
                }
            }

            // Destructive action is visually separated, without overwhelming the whole sheet.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                MessageActionRow(
                    icon = HugeIcons.Delete01,
                    label = stringResource(R.string.delete),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) { perform(onDelete) }
            }
        }
    }
}

@Composable
private fun MessageActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val color = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        ListItem(
            modifier = Modifier.heightIn(min = 56.dp),
            headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
            leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = color,
                leadingIconColor = color,
            ),
        )
    }
}
