package io.github.zanather.clicker.ui.remote

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.model.RemoteActions

internal val NavKeySize = 64.dp
private val PlaybackKeySize = 64.dp
private val PlaybackKeyGap = 16.dp
private val RockerHeight = 60.dp

/** Play and pause in one glyph, matching the widget's; Material has no combined icon. */
private val PlayPauseIcon: ImageVector = ImageVector.Builder("PlayPause", 24.dp, 24.dp, 24f, 24f)
    .addPath(addPathNodes("M3,5v14l10,-7zM15,5h2.5v14h-2.5zM19.5,5h2.5v14h-2.5z"), fill = SolidColor(Color.Black))
    .build()

/** Media keys, rockers and the D-pad keep a physical remote's left-to-right order in every locale. */
@Composable
internal fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

@Composable
private fun IconKey(
    key: RemoteKey,
    @StringRes label: Int,
    icon: ImageVector,
    actions: RemoteActions,
    size: Dp,
    colors: KeyColors = KeyDefaults.tonal(),
) {
    KeyButton(key, stringResource(label), actions, Modifier.size(size), colors = colors) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
    }
}

/** Back, Home and Options, laid out by the caller's Row or Column. */
@Composable
private fun NavKeys(actions: RemoteActions) {
    IconKey(RemoteKey.Back, R.string.remote_key_back, Icons.AutoMirrored.Rounded.ArrowBack, actions, NavKeySize)
    IconKey(RemoteKey.Home, R.string.remote_key_home, Icons.Rounded.Home, actions, NavKeySize)
    // Roku's ✱ key.
    IconKey(RemoteKey.Info, R.string.remote_key_options, Icons.Rounded.Emergency, actions, NavKeySize)
}

@Composable
internal fun NavKeyRow(actions: RemoteActions, width: Dp, modifier: Modifier = Modifier) {
    Row(modifier.width(width), horizontalArrangement = Arrangement.SpaceBetween) { NavKeys(actions) }
}

@Composable
internal fun NavKeyColumn(actions: RemoteActions, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) { NavKeys(actions) }
}

@Composable
internal fun PlaybackRow(actions: RemoteActions, modifier: Modifier = Modifier) {
    Ltr {
        BoxWithConstraints(modifier) {
            // In a narrow column (landscape on a small phone) the keys shrink together instead of squeezing the last one.
            val size = ((maxWidth - 3 * PlaybackKeyGap) / 4).coerceIn(48.dp, PlaybackKeySize)
            Row(horizontalArrangement = Arrangement.spacedBy(PlaybackKeyGap), verticalAlignment = Alignment.CenterVertically) {
                IconKey(RemoteKey.InstantReplay, R.string.remote_key_replay, Icons.Rounded.Replay, actions, size)
                IconKey(RemoteKey.Rev, R.string.remote_key_rewind, Icons.Rounded.FastRewind, actions, size)
                IconKey(RemoteKey.Play, R.string.remote_key_play_pause, PlayPauseIcon, actions, size, KeyDefaults.accent())
                IconKey(RemoteKey.Fwd, R.string.remote_key_fast_forward, Icons.Rounded.FastForward, actions, size)
            }
        }
    }
}

/** Mute, the volume rocker and, while live TV is on screen, a channel rocker (wrapping on narrow phones). */
@Composable
internal fun VolumeRow(actions: RemoteActions, showChannels: Boolean, modifier: Modifier = Modifier) {
    Ltr {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconKey(RemoteKey.VolumeMute, R.string.remote_key_mute, Icons.AutoMirrored.Rounded.VolumeOff, actions, RockerHeight)
            Rocker(
                down = RockerKey(RemoteKey.VolumeDown, R.string.remote_key_volume_down, Icons.AutoMirrored.Rounded.VolumeDown),
                up = RockerKey(RemoteKey.VolumeUp, R.string.remote_key_volume_up, Icons.AutoMirrored.Rounded.VolumeUp),
                center = R.string.remote_volume_label,
                actions = actions,
            )
            if (showChannels) {
                Rocker(
                    down = RockerKey(RemoteKey.ChannelDown, R.string.remote_key_channel_down, Icons.Rounded.KeyboardArrowDown),
                    up = RockerKey(RemoteKey.ChannelUp, R.string.remote_key_channel_up, Icons.Rounded.KeyboardArrowUp),
                    center = R.string.remote_channel_label,
                    actions = actions,
                )
            }
        }
    }
}

private class RockerKey(val key: RemoteKey, @StringRes val label: Int, val icon: ImageVector)

/** Two keys sharing one pill, like a physical rocker. */
@Composable
private fun Rocker(down: RockerKey, up: RockerKey, @StringRes center: Int, actions: RemoteActions) {
    Row(
        modifier = Modifier
            .height(RockerHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RockerHalf(down, actions)
        Text(
            text = stringResource(center),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // The halves already say "Volume up" and "Volume down".
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .clearAndSetSemantics {},
        )
        RockerHalf(up, actions)
    }
}

@Composable
private fun RockerHalf(half: RockerKey, actions: RemoteActions) {
    KeyButton(half.key, stringResource(half.label), actions, Modifier.size(width = 58.dp, height = RockerHeight), colors = KeyDefaults.inRocker()) {
        Icon(half.icon, contentDescription = null, modifier = Modifier.size(26.dp))
    }
}

/** Switches between the D-pad and the swipe pad; the highlighted half is the one in use. */
@Composable
internal fun PadModeToggle(touchpad: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.remote_mode_touchpad)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(value = touchpad, role = Role.Switch, onValueChange = onToggle)
            .semantics { contentDescription = label }
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ModeSegment(Icons.Rounded.Gamepad, selected = !touchpad)
        ModeSegment(Icons.Rounded.Swipe, selected = touchpad)
    }
}

@Composable
private fun ModeSegment(icon: ImageVector, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.secondaryContainer else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
