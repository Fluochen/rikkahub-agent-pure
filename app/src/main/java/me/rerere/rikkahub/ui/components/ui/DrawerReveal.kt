package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * DeepSeek-style conversation drawer: the chat page recedes into a rounded, slightly
 * perspective-tilted card while the drawer slides in from the left. Numbers are kept in one
 * place so the swipe progress, the card transform and the drawer width stay in lockstep.
 */
object DrawerReveal {
    /** Phone drawer width as a fraction of the window; the leftover strip is the chat-card peek. */
    const val WidthFraction = 0.86f

    val MaxWidth = 420.dp

    /** How far the chat card shrinks at a fully-open drawer. */
    const val MinScale = 0.94f

    /** Subtle Y-axis tilt so the card reads as sitting behind the drawer. */
    const val MaxRotationY = 8f

    val MaxCornerRadius = 28.dp

    val MaxElevation = 22.dp

    /** Camera distance in dp; larger = shallower perspective. */
    const val CameraDistanceDp = 28f

    /**
     * Material drawer anchors: closed at `-drawerWidth`, open at `0`.
     * [offsetPx] may be NaN before the first layout pass.
     */
    fun openFraction(offsetPx: Float, drawerWidthPx: Float, fallbackOpen: Boolean): Float {
        if (offsetPx.isNaN() || drawerWidthPx <= 0f) {
            return if (fallbackOpen) 1f else 0f
        }
        return ((offsetPx + drawerWidthPx) / drawerWidthPx).coerceIn(0f, 1f)
    }

    fun scale(progress: Float): Float = 1f - (1f - MinScale) * progress.coerceIn(0f, 1f)

    /**
     * After a center-origin scale, the card's left edge sits inset by `(width * (1 - scale)) / 2`.
     * Shift it so that edge stays glued to the drawer's trailing edge across the whole swipe.
     */
    fun translationX(progress: Float, drawerWidthPx: Float, contentWidthPx: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val inset = contentWidthPx * (1f - scale(p)) / 2f
        return p * drawerWidthPx - inset
    }
}

@Composable
fun drawerOpenProgress(drawerState: DrawerState, drawerWidthPx: Float): Float {
    val offset = runCatching { drawerState.currentOffset }.getOrElse { Float.NaN }
    return DrawerReveal.openFraction(offset, drawerWidthPx, drawerState.isOpen)
}

/**
 * Apply the receding-card transform. Layout bounds stay full-size (so the modal scrim and
 * drawer still receive gestures); only the drawn pixels move, scale and clip.
 */
fun Modifier.drawerRevealContent(
    progress: Float,
    drawerWidthPx: Float,
    density: Float,
): Modifier = graphicsLayer {
    val p = progress.coerceIn(0f, 1f)
    val scale = DrawerReveal.scale(p)
    scaleX = scale
    scaleY = scale
    translationX = DrawerReveal.translationX(p, drawerWidthPx, size.width)
    rotationY = DrawerReveal.MaxRotationY * p
    cameraDistance = DrawerReveal.CameraDistanceDp * density
    transformOrigin = TransformOrigin.Center
    shadowElevation = DrawerReveal.MaxElevation.toPx() * p
    shape = RoundedCornerShape(DrawerReveal.MaxCornerRadius * p)
    clip = true
    ambientShadowColor = Color.Black.copy(alpha = 0.28f * p)
    spotShadowColor = Color.Black.copy(alpha = 0.36f * p)
}
