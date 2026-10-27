package io.github.zanather.clicker.ui.remote

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.model.RemoteActions
import kotlin.math.abs

/** OK's diameter as a share of the pad's. */
private const val OK_FRACTION = 0.4f

/** Groove between OK and the arms. */
private val OkGap = 6.dp

private enum class DPadPart(val key: RemoteKey, @StringRes val label: Int, val angle: Float) {
    Up(RemoteKey.Up, R.string.remote_key_up, 270f),
    Right(RemoteKey.Right, R.string.remote_key_right, 0f),
    Down(RemoteKey.Down, R.string.remote_key_down, 90f),
    Left(RemoteKey.Left, R.string.remote_key_left, 180f),
    Ok(RemoteKey.Select, R.string.remote_key_ok, 0f),
}

private val Arms = DPadPart.entries - DPadPart.Ok

/**
 * Picks the part under [position] the way a physical D-pad does: the center is OK, the ring is split
 * into four 90° wedges, and the corners outside the circle belong to nothing (so they can scroll).
 */
private fun partAt(position: Offset, size: IntSize, okRadius: Float): DPadPart? {
    val radius = size.width / 2f
    val offset = position - Offset(radius, size.height / 2f)
    val distance = offset.getDistance()
    return when {
        distance > radius -> null
        distance <= okRadius -> DPadPart.Ok
        abs(offset.x) > abs(offset.y) -> if (offset.x > 0) DPadPart.Right else DPadPart.Left
        else -> if (offset.y > 0) DPadPart.Down else DPadPart.Up
    }
}

/**
 * A round D-pad: four arms and OK. One pointer handler covers the whole disc so the wedges split
 * exactly on the diagonals, while each part is its own labelled button for accessibility.
 */
@Composable
internal fun DPad(actions: RemoteActions, size: Dp, modifier: Modifier = Modifier) {
    // A parent that squeezes one side gets a smaller disc, centered, so what's drawn is always what's hit.
    BoxWithConstraints(modifier.size(size), contentAlignment = Alignment.Center) {
        DPadDisc(actions, min(maxWidth, maxHeight))
    }
}

@Composable
private fun DPadDisc(actions: RemoteActions, size: Dp) {
    val haptics by rememberUpdatedState(rememberKeyHaptics())
    val currentActions by rememberUpdatedState(actions)
    var pressed by remember { mutableStateOf<DPadPart?>(null) }
    val scheme = MaterialTheme.colorScheme

    val okSize = size * OK_FRACTION
    val ringWidth = (size - okSize) / 2 - OkGap
    val armHighlight = scheme.primary.copy(alpha = 0.26f).compositeOver(scheme.surfaceContainerHigh)
    val glows = Arms.associateWith { part ->
        animateFloatAsState(
            targetValue = if (pressed == part) 1f else 0f,
            animationSpec = if (pressed == part) snap() else tween(200),
            label = "armGlow",
        )
    }

    Box(
        modifier = Modifier
            .size(size)
            .semantics { isTraversalGroup = true }
            .pointerInput(size) {
                val okRadius = (okSize / 2 + OkGap / 2).toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val part = partAt(down.position, this.size, okRadius) ?: return@awaitEachGesture
                    trackKeyTouch(down, part.key, currentActions, haptics) { isDown ->
                        pressed = if (isDown) part else null
                    }
                }
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(scheme.surfaceContainerHigh)
            val ringPx = ringWidth.toPx()
            val arcRadius = this.size.minDimension / 2 - ringPx / 2
            for (part in Arms) {
                val glow = glows.getValue(part).value
                if (glow == 0f) continue
                drawArc(
                    color = armHighlight.copy(alpha = glow),
                    startAngle = part.angle - 45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = center - Offset(arcRadius, arcRadius),
                    size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(width = ringPx),
                )
            }
        }

        for (part in Arms) {
            val alignment = when (part) {
                DPadPart.Up -> Alignment.TopCenter
                DPadPart.Down -> Alignment.BottomCenter
                // The TV's left is left in every locale.
                DPadPart.Left -> AbsoluteAlignment.CenterLeft
                else -> AbsoluteAlignment.CenterRight
            }
            Arm(part, pressed == part, ringWidth, actions, Modifier.align(alignment))
        }

        OkButton(pressed == DPadPart.Ok, okSize, actions, Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Arm(part: DPadPart, isPressed: Boolean, size: Dp, actions: RemoteActions, modifier: Modifier) {
    val label = stringResource(part.label)
    val scale = pressedScale(isPressed)
    Box(
        modifier = modifier
            .size(size)
            .keySemantics(label) { actions.press(part.key) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowUp,
            contentDescription = null,
            tint = if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .rotate(part.angle - DPadPart.Up.angle),
        )
    }
}

@Composable
private fun OkButton(isPressed: Boolean, size: Dp, actions: RemoteActions, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = KeyColors(
        container = scheme.primary,
        content = scheme.onPrimary,
        pressedContainer = scheme.onPrimary.copy(alpha = 0.2f).compositeOver(scheme.primary),
    )
    val scale = pressedScale(isPressed)
    val label = stringResource(R.string.remote_key_ok)
    Box(
        modifier = modifier
            .size(size)
            .keySemantics(label) { actions.press(RemoteKey.Select) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(pressedColor(isPressed, colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.content)
    }
}

/** Button semantics for a part whose touches are handled elsewhere; TalkBack's double tap sends one press. */
private fun Modifier.keySemantics(label: String, onPress: () -> Unit): Modifier = clearAndSetSemantics {
    role = Role.Button
    contentDescription = label
    onClick {
        onPress()
        true
    }
}
