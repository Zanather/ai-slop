package io.github.zanather.clicker.ui.remote

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.model.RemoteActions
import io.github.zanather.clicker.ui.theme.LocalHapticsEnabled

/** Matches the session's hold delay, so the "now repeating" buzz lands with the first repeat. */
private const val HOLD_FEEDBACK_MS = 400L

/** Remote-key haptics that honor the in-app switch; the system haptics setting still applies on top. */
@Stable
internal class KeyHaptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    fun keyDown() = perform(HapticFeedbackType.VirtualKey)

    fun holding() = perform(HapticFeedbackType.LongPress)

    fun tick() = perform(HapticFeedbackType.SegmentTick)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

@Composable
internal fun rememberKeyHaptics(): KeyHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalHapticsEnabled.current
    return remember(feedback, enabled) { KeyHaptics(feedback, enabled) }
}

/**
 * Runs one touch on a remote key whose finger just went [down], like a physical remote: other keys
 * press immediately, repeating keys hold until the finger lifts, slides off, or the gesture is
 * cancelled. Consumes the pointer so a surrounding scroll can't take over a held key.
 */
internal suspend fun AwaitPointerEventScope.trackKeyTouch(
    down: PointerInputChange,
    key: RemoteKey,
    actions: RemoteActions,
    haptics: KeyHaptics,
    onPressedChange: (Boolean) -> Unit,
) {
    down.consume()
    onPressedChange(true)
    haptics.keyDown()
    val holds = key.repeatsWhenHeld
    if (holds) actions.holdStart(key) else actions.press(key)
    try {
        val releasedEarly = withTimeoutOrNull(HOLD_FEEDBACK_MS) { awaitKeyRelease(down.id) } != null
        if (!releasedEarly) {
            if (holds) haptics.holding()
            awaitKeyRelease(down.id)
        }
    } finally {
        // Also runs if the key leaves the screen mid-hold, so the TV never keeps repeating on its own.
        if (holds) actions.holdEnd(key)
        onPressedChange(false)
    }
}

private suspend fun AwaitPointerEventScope.awaitKeyRelease(pointer: PointerId) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer } ?: return
        val done = !change.pressed || change.isConsumed || change.isOutOfBounds(size, extendedTouchPadding)
        change.consume()
        if (done) return
    }
}

/** Resting and pressed fills for a key; pressed shows instantly, before the TV hears anything. */
@Immutable
internal data class KeyColors(val container: Color, val content: Color, val pressedContainer: Color)

internal object KeyDefaults {
    @Composable
    fun tonal(): KeyColors {
        val scheme = MaterialTheme.colorScheme
        return KeyColors(
            container = scheme.surfaceContainerHigh,
            content = scheme.onSurface,
            pressedContainer = scheme.primary.copy(alpha = 0.24f).compositeOver(scheme.surfaceContainerHigh),
        )
    }

    /** For keys inside a rocker, which already draws the shared background. */
    @Composable
    fun inRocker(): KeyColors {
        val scheme = MaterialTheme.colorScheme
        return KeyColors(
            container = Color.Transparent,
            content = scheme.onSurface,
            pressedContainer = scheme.primary.copy(alpha = 0.24f),
        )
    }

    /** Matches a filled tonal button, for keys that sit among them. */
    @Composable
    fun secondary(): KeyColors {
        val scheme = MaterialTheme.colorScheme
        return KeyColors(
            container = scheme.secondaryContainer,
            content = scheme.onSecondaryContainer,
            pressedContainer = scheme.onSecondaryContainer.copy(alpha = 0.16f).compositeOver(scheme.secondaryContainer),
        )
    }

    @Composable
    fun accent(): KeyColors {
        val scheme = MaterialTheme.colorScheme
        return KeyColors(
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer,
            pressedContainer = scheme.onPrimaryContainer.copy(alpha = 0.18f).compositeOver(scheme.primaryContainer),
        )
    }
}

/** Scale and fill for a pressed key: snaps in on touch, eases back out on release. */
@Composable
internal fun pressedScale(pressed: Boolean): Float =
    animateFloatAsState(if (pressed) 0.92f else 1f, if (pressed) snap() else spring(), label = "keyScale").value

@Composable
internal fun pressedColor(pressed: Boolean, colors: KeyColors): Color =
    animateColorAsState(
        targetValue = if (pressed) colors.pressedContainer else colors.container,
        animationSpec = if (pressed) snap() else tween(180),
        label = "keyFill",
    ).value

/**
 * A remote key that fires on finger down (see [trackKeyTouch]) and is a plain button to TalkBack,
 * where a double tap sends one press.
 *
 * [onActivate] runs on every activation, touch or accessibility; [onPressedChange] tracks the finger.
 */
@Composable
internal fun KeyButton(
    key: RemoteKey,
    label: String,
    actions: RemoteActions,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    colors: KeyColors = KeyDefaults.tonal(),
    onActivate: () -> Unit = {},
    onPressedChange: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val haptics by rememberUpdatedState(rememberKeyHaptics())
    val currentActions by rememberUpdatedState(actions)
    val currentOnActivate by rememberUpdatedState(onActivate)
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    var pressed by remember { mutableStateOf(false) }
    val scale = pressedScale(pressed)
    val fill = pressedColor(pressed, colors)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pointerInput(key) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    currentOnActivate()
                    trackKeyTouch(down, key, currentActions, haptics) {
                        pressed = it
                        currentOnPressedChange(it)
                    }
                }
            }
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                onClick {
                    currentActions.press(key)
                    currentOnActivate()
                    true
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) { content() }
    }
}
