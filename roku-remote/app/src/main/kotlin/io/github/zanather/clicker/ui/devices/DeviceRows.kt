package io.github.zanather.clicker.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.components.groupedItem
import io.github.zanather.clicker.ui.model.DeviceRow

/** A remembered TV: tap to control it, info for its details, overflow to forget it. */
@Composable
internal fun SavedDeviceRow(
    row: DeviceRow,
    index: Int,
    count: Int,
    onSelect: (DeviceRow) -> Unit,
    onForget: (DeviceRow) -> Unit,
    onShowInfo: (DeviceRow) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val controlling = stringResource(R.string.devices_controlling)
    DeviceRowFrame(
        row = row,
        index = index,
        count = count,
        color = if (row.isSelected) scheme.primaryContainer else scheme.surfaceContainer,
        onSelect = onSelect,
        stateModifier = if (row.isSelected) {
            Modifier.semantics {
                selected = true
                stateDescription = controlling
            }
        } else {
            Modifier
        },
        highlighted = row.isSelected,
        status = if (row.isSelected) {
            {
                // Announced through the row's state description instead.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clearAndSetSemantics {},
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                    Text(controlling, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                }
            }
        } else {
            null
        },
    ) {
        IconButton(onClick = { onShowInfo(row) }) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.devices_details))
        }
        ForgetMenu(onForget = { onForget(row) })
    }
}

/** A TV that answered the scan and isn't saved yet. The whole row adds it. */
@Composable
internal fun FoundDeviceRow(row: DeviceRow, index: Int, count: Int, onSelect: (DeviceRow) -> Unit) {
    DeviceRowFrame(
        row = row,
        index = index,
        count = count,
        color = MaterialTheme.colorScheme.surfaceContainer,
        onSelect = onSelect,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(end = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.devices_add), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DeviceRowFrame(
    row: DeviceRow,
    index: Int,
    count: Int,
    color: Color,
    onSelect: (DeviceRow) -> Unit,
    modifier: Modifier = Modifier,
    stateModifier: Modifier = Modifier,
    highlighted: Boolean = false,
    status: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val kind = stringResource(if (row.isTv) R.string.devices_kind_tv else R.string.devices_kind_player)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .groupedItem(index, count, color)
            .clickable(onClickLabel = stringResource(R.string.devices_control), role = Role.Button) { onSelect(row) }
            .then(stateModifier)
            .heightIn(min = 76.dp)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        DeviceIcon(isTv = row.isTv, highlighted = highlighted, description = kind)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            status?.invoke()
            Text(
                text = row.host,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun DeviceIcon(isTv: Boolean, highlighted: Boolean, description: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .background(if (highlighted) scheme.primary else scheme.surfaceContainerHighest, RoundedCornerShape(16.dp)),
    ) {
        Icon(
            imageVector = if (isTv) Icons.Rounded.Tv else Icons.Rounded.SettingsInputHdmi,
            // Read with the row, so TalkBack hears "Roku TV" or "Streaming player" the icon shows.
            contentDescription = description,
            tint = if (highlighted) scheme.onPrimary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ForgetMenu(onForget: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.devices_more))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.devices_forget)) },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                onClick = {
                    open = false
                    onForget()
                },
            )
        }
    }
}

/** First row of the found group while a scan runs. */
@Composable
internal fun SearchingRow(index: Int, count: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .groupedItem(index, count, MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.devices_searching),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
internal fun NoneFoundRow() {
    Text(
        text = stringResource(R.string.devices_none_found),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .groupedItem(0, 1, MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    )
}
