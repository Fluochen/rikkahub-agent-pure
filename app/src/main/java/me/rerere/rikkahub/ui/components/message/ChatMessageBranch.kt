package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MessageNode

@Composable
fun ChatMessageBranchSelector(
    node: MessageNode,
    modifier: Modifier = Modifier,
    onUpdate: (MessageNode) -> Unit,
) {
    if (node.messages.size <= 1) return

    // Match ChatMessage's fallback when a removed branch leaves a stale selection.
    val selectedIndex = node.selectIndex.takeIf { it in node.messages.indices } ?: node.messages.lastIndex
    val actionColor = MaterialTheme.colorScheme.onSurfaceVariant
    val buttonColors = IconButtonDefaults.iconButtonColors(contentColor = actionColor)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(
            onClick = { onUpdate(node.copy(selectIndex = selectedIndex - 1)) },
            enabled = selectedIndex > 0,
            modifier = Modifier.size(48.dp),
            colors = buttonColors,
        ) {
            Icon(
                imageVector = HugeIcons.ArrowLeft01,
                contentDescription = stringResource(R.string.accessibility_previous_branch),
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "${selectedIndex + 1}/${node.messages.size}",
            style = MaterialTheme.typography.labelMedium,
            color = actionColor,
            maxLines = 1,
        )
        IconButton(
            onClick = { onUpdate(node.copy(selectIndex = selectedIndex + 1)) },
            enabled = selectedIndex < node.messages.lastIndex,
            modifier = Modifier.size(48.dp),
            colors = buttonColors,
        ) {
            Icon(
                imageVector = HugeIcons.ArrowRight01,
                contentDescription = stringResource(R.string.accessibility_next_branch),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
