package io.github.zanather.clicker.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.theme.LocalStatusColors

@Composable
private fun statusColor(status: ConnectionStatus): Color {
    val colors = LocalStatusColors.current
    return when (status) {
        ConnectionStatus.Connected -> colors.online
        ConnectionStatus.Connecting, ConnectionStatus.Searching, ConnectionStatus.Waking -> colors.busy
        ConnectionStatus.TvOff, ConnectionStatus.NoDevice -> colors.idle
        is ConnectionStatus.Unreachable, is ConnectionStatus.Blocked -> colors.offline
    }
}

@Composable
private fun statusText(status: ConnectionStatus): String = stringResource(
    when (status) {
        ConnectionStatus.NoDevice -> R.string.remote_status_no_device
        ConnectionStatus.Connecting -> R.string.remote_status_connecting
        ConnectionStatus.Connected -> R.string.remote_status_connected
        ConnectionStatus.Searching -> R.string.remote_status_searching
        ConnectionStatus.TvOff -> R.string.remote_status_tv_off
        ConnectionStatus.Waking -> R.string.remote_status_waking
        is ConnectionStatus.Unreachable -> R.string.remote_status_unreachable
        is ConnectionStatus.Blocked -> R.string.remote_status_blocked
    },
)

/**
 * Always names the TV being controlled (users of the official app complain it turns on the wrong one),
 * with its connection state as a dot and a word, never color alone.
 */
@Composable
internal fun RemoteTopBar(
    deviceName: String?,
    status: ConnectionStatus,
    isTv: Boolean,
    onOpenDevices: () -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onPower: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDevice = status != ConnectionStatus.NoDevice
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            DeviceChip(deviceName ?: stringResource(R.string.remote_device_none), status, onOpenDevices)
        }
        if (hasDevice) {
            BarAction(Icons.Rounded.Keyboard, stringResource(R.string.remote_action_keyboard), onOpenKeyboard)
            BarAction(Icons.Rounded.Apps, stringResource(R.string.remote_action_channels), onOpenApps)
        }
        BarAction(Icons.Rounded.Settings, stringResource(R.string.remote_action_settings), onOpenSettings)
        if (hasDevice && isTv) {
            Spacer(Modifier.width(4.dp))
            PowerButton(onPower)
        }
    }
}

@Composable
private fun DeviceChip(name: String, status: ConnectionStatus, onClick: () -> Unit) {
    val state = statusText(status)
    val chooseTv = stringResource(R.string.remote_choose_tv)
    Row(
        modifier = Modifier
            .heightIn(min = 52.dp)
            // A pill at one line; stays a rounded rectangle when a long name or large text wraps.
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = chooseTv, role = Role.Button, onClick = onClick)
            .semantics { stateDescription = state }
            .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(statusColor(status)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                // Two lines before truncating: the TV's name is the one thing this chip must show.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Fires on release, not touch, so brushing past it can't switch the TV off mid-show. */
@Composable
private fun PowerButton(onPower: () -> Unit) {
    val haptics = rememberKeyHaptics()
    FilledIconButton(
        onClick = {
            haptics.keyDown()
            onPower()
        },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.size(48.dp),
    ) {
        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = stringResource(R.string.remote_key_power))
    }
}
