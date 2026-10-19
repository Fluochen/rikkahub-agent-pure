package me.rerere.rikkahub.ui.pages.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * DeepSeek-style drawer state.
 * progress: 0f (closed) -> 1f (opened)
 */
@Stable
class DeepSeekDrawerState(
    initialProgress: Float = 0f,
    private val coroutineScope: CoroutineScope? = null,
) {
    val progressAnim = Animatable(initialProgress.coerceIn(0f, 1f))

    val progress: Float get() = progressAnim.value
    val isOpen: Boolean get() = progressAnim.value > 0.5f

    suspend fun snapTo(value: Float) {
        progressAnim.snapTo(value.coerceIn(0f, 1f))
    }

    suspend fun animateTo(
        value: Float,
        velocity: Float = 0f,
    ) {
        val clamped = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(velocity) > 800f) {
            progressAnim.animateTo(
                clamped,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = 0.001f
                ),
                initialVelocity = velocity / 1000f * 0.6f
            )
        } else {
            progressAnim.animateTo(
                clamped,
                animationSpec = tween(
                    durationMillis = 320,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
        }
    }

    suspend fun open(velocity: Float = 0f) = animateTo(1f, velocity)
    suspend fun close(velocity: Float = 0f) = animateTo(0f, velocity)
}

@Composable
fun rememberDeepSeekDrawerState(
    initialValue: Float = 0f,
): DeepSeekDrawerState {
    val scope = rememberCoroutineScope()
    return remember { DeepSeekDrawerState(initialValue, scope) }
}

/**
 * DeepSeek-style drawer layout.
 *
 * - Drawer sits underneath the main content (fixed)
 * - Main content translates right, scales down slightly (0.94), gains rounded corners + shadow
 * - Scrim overlay darkens main content for depth
 * - Edge swipe (28dp) to open when closed, drag on main to close when open
 * - Tap on overlay when open to close
 */
@Composable
fun DeepSeekDrawerLayout(
    drawerState: DeepSeekDrawerState,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
    drawerWidth: Dp = 300.dp,
    scrimColor: Color = Color.Black,
    enableGestures: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BackHandler(enabled = drawerState.progress > 0.01f) {
        scope.launch { drawerState.close() }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val drawerWidthPx = with(density) { drawerWidth.toPx() }
        val progress = drawerState.progressAnim.value

        // Drawer layer (underneath) - static, with subtle parallax (DeepSeek keeps drawer nearly static)
        Box(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val drawerParallaxOffset = (1f - progress) * drawerWidthPx * 0.08f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = -drawerParallaxOffset
                        alpha = 0.8f + 0.2f * progress
                    }
            ) {
                drawerContent()
            }
        }

        // Main content layer - DeepSeek: translate 100% width, scale 0.94, corner 20dp, soft shadow
        val translateX = progress * drawerWidthPx
        val scale = 1f - progress * 0.06f // 1 -> 0.94
        val cornerRadiusDp = (progress * 20).dp
        val scrimAlpha = progress * 0.08f // DeepSeek has almost no dim, just shadow - keep subtle to preserve original app's light feel

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(translateX.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(cornerRadiusDp)
                    clip = progress > 0.01f
                }
                .shadow(
                    elevation = (progress * 24).dp,
                    shape = RoundedCornerShape(cornerRadiusDp),
                    clip = false
                )
                .clip(RoundedCornerShape(cornerRadiusDp))
                .then(
                    if (enableGestures) {
                        Modifier.deepSeekGestures(
                            drawerState = drawerState,
                            drawerWidthPx = drawerWidthPx,
                            scope = scope,
                        )
                    } else Modifier
                )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }
            // Overlay scrim - tap to close + subtle dim
            if (progress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor.copy(alpha = scrimAlpha))
                )
            }
        }
    }
}

private fun Modifier.deepSeekGestures(
    drawerState: DeepSeekDrawerState,
    drawerWidthPx: Float,
    scope: CoroutineScope,
): Modifier = this.pointerInput(drawerState, drawerWidthPx) {
    val edgeWidthPx = 28.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val initialProgress = drawerState.progress
        val translateX = initialProgress * drawerWidthPx
        val inEdgeZone = down.position.x < edgeWidthPx
        val isOnMain = down.position.x >= translateX - 4f
        val shouldHandle = if (initialProgress < 0.02f) inEdgeZone else isOnMain
        if (!shouldHandle) return@awaitEachGesture

        val velocityTracker = VelocityTracker()
        var drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
            if (kotlin.math.abs(change.position.x - down.position.x) >
                kotlin.math.abs(change.position.y - down.position.y)
            ) {
                change.consume()
            }
        }
        if (drag == null) {
            // Consider tap to close when open
            if (initialProgress > 0.5f && down.position.x >= translateX) {
                scope.launch { drawerState.close() }
            }
            return@awaitEachGesture
        }
        velocityTracker.addPointerInputChange(drag)
        var totalDrag = drag.position.x - down.position.x
        val firstProgress = (initialProgress + totalDrag / drawerWidthPx).coerceIn(0f, 1f)
        scope.launch { drawerState.snapTo(firstProgress) }

        while (true) {
            val change = awaitHorizontalDragOrCancellation(drag.id)
            if (change == null) {
                val velocity = velocityTracker.calculateVelocity().x
                val cur = drawerState.progress
                val shouldOpen = when {
                    velocity > 700f -> true
                    velocity < -700f -> false
                    else -> cur > 0.4f
                }
                scope.launch { if (shouldOpen) drawerState.open(velocity) else drawerState.close(velocity) }
                break
            }
            velocityTracker.addPointerInputChange(change)
            totalDrag = change.position.x - down.position.x
            change.consume()
            val p = (initialProgress + totalDrag / drawerWidthPx).coerceIn(0f, 1f)
            scope.launch { drawerState.snapTo(p) }
            drag = change
        }
    }
}
