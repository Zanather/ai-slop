package io.github.zanather.clicker.ui.remote

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.model.RemoteActions
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Finger travel per arrow press: long enough to be deliberate, short enough to scroll a row quickly. */
private val StepDistance = 44.dp

private val PadShape = RoundedCornerShape(40.dp)

private val Directions = listOf(RemoteKey.Up, RemoteKey.Right, RemoteKey.Down, RemoteKey.Left)

/**
 * A swipe pad: every [StepDistance] dragged along the dominant axis sends one arrow press, and a tap
 * sends OK. Not offered under TalkBack, whose own gestures would clash with it.
 */
@Composable
internal fun Touchpad(actions: RemoteActions, modifier: Modifier = Modifier) {
    val haptics by rememberUpdatedState(rememberKeyHaptics())
    val currentActions by rememberUpdatedState(actions)
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { StepDistance.toPx() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val flashes = remember { Directions.associateWith { Animatable(0f) } }
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.remote_touchpad_description)

    fun step(key: RemoteKey) {
        currentActions.press(key)
        haptics.tick()
        scope.launch {
            val flash = flashes.getValue(key)
            flash.snapTo(1f)
            flash.animateTo(0f, tween(320))
        }
    }

    Box(
        modifier = modifier
            .clip(PadShape)
            .background(scheme.surfaceContainerHigh)
            .clearAndSetSemantics {
                contentDescription = description
                onClick {
                    currentActions.press(RemoteKey.Select)
                    true
                }
            }
            .pointerInput(stepPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    finger = down.position
                    var pending = Offset.Zero
                    var travelled = 0f
                    var stepped = false
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                change.consume()
                                if (!stepped && travelled < viewConfiguration.touchSlop) {
                                    haptics.keyDown()
                                    currentActions.press(RemoteKey.Select)
                                }
                                break
                            }
                            val delta = change.positionChange()
                            change.consume()
                            finger = change.position
                            travelled += delta.getDistance()
                            pending += delta
                            while (true) {
                                val horizontal = abs(pending.x) >= abs(pending.y)
                                val along = if (horizontal) pending.x else pending.y
                                if (abs(along) < stepPx) break
                                step(
                                    when {
                                        horizontal && along > 0 -> RemoteKey.Right
                                        horizontal -> RemoteKey.Left
                                        along > 0 -> RemoteKey.Down
                                        else -> RemoteKey.Up
                                    },
                                )
                                stepped = true
                                // Drop the other axis so a slightly diagonal swipe doesn't sneak in a sideways press.
                                val left = along - sign(along) * stepPx
                                pending = if (horizontal) Offset(left, 0f) else Offset(0f, left)
                            }
                        }
                    } finally {
                        finger = null
                    }
                }
            },
    ) {
        val glowRadius = with(LocalDensity.current) { 44.dp.toPx() }
        Canvas(Modifier.matchParentSize()) {
            finger?.let { drawCircle(scheme.primary.copy(alpha = 0.16f), glowRadius, it) }
        }

        for (key in Directions) {
            val alignment = when (key) {
                RemoteKey.Up -> Alignment.TopCenter
                RemoteKey.Down -> Alignment.BottomCenter
                RemoteKey.Left -> AbsoluteAlignment.CenterLeft
                else -> AbsoluteAlignment.CenterRight
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = null,
                tint = lerp(scheme.outline, scheme.primary, flashes.getValue(key).value),
                modifier = Modifier
                    .align(alignment)
                    .padding(12.dp)
                    .size(28.dp)
                    .rotate(Directions.indexOf(key) * 90f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.remote_touchpad_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
