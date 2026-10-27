package io.github.zanather.clicker.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.components.ClickerScaffold
import io.github.zanather.clicker.ui.components.Grouped
import io.github.zanather.clicker.ui.components.InlineMessage
import io.github.zanather.clicker.ui.components.MessagePanel
import io.github.zanather.clicker.ui.components.ReadableColumnPadding
import io.github.zanather.clicker.ui.components.SectionHeader
import io.github.zanather.clicker.ui.model.DeviceRow
import io.github.zanather.clicker.ui.model.DevicesUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme

/** Saved TVs, TVs found on the Wi-Fi right now, and a manual IP entry for networks where discovery is blocked. */
@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onSelect: (DeviceRow) -> Unit,
    onForget: (DeviceRow) -> Unit,
    onShowInfo: (DeviceRow) -> Unit,
    onScan: () -> Unit,
    onAddByAddress: (host: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEmpty = state.saved.isEmpty() && state.discovered.isEmpty() && !state.scanning
    ClickerScaffold(title = stringResource(R.string.devices_title), onBack = onBack, modifier = modifier) { padding ->
        ReadableColumnPadding(padding) { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(Grouped.Gap),
                // Shrinks above the keyboard so the address field can scroll into view while typing.
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                if (isEmpty) {
                    item(key = "empty") { EmptyDevices(state.message, onScan) }
                } else {
                    state.message?.let { message ->
                        item(key = "message") { InlineMessage(Icons.Rounded.Info, message, Modifier.padding(top = 4.dp, bottom = 8.dp)) }
                    }
                    savedSection(state.saved, onSelect, onForget, onShowInfo)
                    foundSection(state.discovered, state.scanning, onSelect, onScan)
                }
                item(key = "address-header") { SectionHeader(stringResource(R.string.devices_add_by_ip)) }
                item(key = "address") { AddByAddressCard(onAddByAddress) }
            }
        }
    }
}

private fun LazyListScope.savedSection(
    saved: List<DeviceRow>,
    onSelect: (DeviceRow) -> Unit,
    onForget: (DeviceRow) -> Unit,
    onShowInfo: (DeviceRow) -> Unit,
) {
    if (saved.isEmpty()) return
    item(key = "saved-header") { SectionHeader(stringResource(R.string.devices_saved)) }
    itemsIndexed(saved, key = { _, row -> "saved:${row.serialNumber ?: row.host}" }) { index, row ->
        SavedDeviceRow(row, index, saved.size, onSelect, onForget, onShowInfo)
    }
}

private fun LazyListScope.foundSection(
    discovered: List<DeviceRow>,
    scanning: Boolean,
    onSelect: (DeviceRow) -> Unit,
    onScan: () -> Unit,
) {
    item(key = "found-header") {
        SectionHeader(
            text = stringResource(R.string.devices_found),
            action = if (scanning) null else ({ ScanAgainButton(onScan) }),
        )
    }
    // The searching row is part of the group, so results appear right under it as they arrive.
    val count = discovered.size + if (scanning || discovered.isEmpty()) 1 else 0
    if (scanning) {
        item(key = "searching") { SearchingRow(index = 0, count = count) }
    } else if (discovered.isEmpty()) {
        item(key = "none-found") { NoneFoundRow() }
    }
    val offset = count - discovered.size
    itemsIndexed(discovered, key = { _, row -> "found:${row.serialNumber ?: row.host}" }) { index, row ->
        FoundDeviceRow(row, index + offset, count, onSelect)
    }
}

@Composable
private fun ScanAgainButton(onScan: () -> Unit) {
    TextButton(onClick = onScan, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.devices_scan_again))
    }
}

@Composable
private fun EmptyDevices(message: String?, onScan: () -> Unit) {
    MessagePanel(
        icon = Icons.Rounded.Tv,
        title = stringResource(R.string.devices_empty_title),
        // The scan's own explanation, when there is one, says the same thing more precisely.
        body = message ?: stringResource(R.string.devices_empty_body),
        modifier = Modifier.fillMaxWidth(),
        action = {
            Button(onClick = onScan, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.devices_scan_again))
            }
        },
    )
}

internal val SampleSaved = listOf(
    DeviceRow("X01900ABCDEF", "Living Room TV", "192.168.8.198", isTv = true, isSelected = true),
    DeviceRow("YH00AB123456", "Bedroom Streaming Stick", "192.168.8.143", isTv = false),
)

internal val SampleDiscovered = listOf(
    DeviceRow("X02100FEDCBA", "Basement TV", "192.168.8.211", isTv = true),
)

@PreviewLightDark
@Composable
private fun DevicesScreenPreview() {
    ClickerTheme {
        DevicesScreen(
            state = DevicesUiState(saved = SampleSaved, discovered = SampleDiscovered),
            onSelect = {}, onForget = {}, onShowInfo = {}, onScan = {}, onAddByAddress = {}, onBack = {},
        )
    }
}

@Preview
@Composable
private fun DevicesScreenScanningPreview() {
    ClickerTheme {
        DevicesScreen(
            state = DevicesUiState(saved = SampleSaved, scanning = true),
            onSelect = {}, onForget = {}, onShowInfo = {}, onScan = {}, onAddByAddress = {}, onBack = {},
        )
    }
}

@Preview
@Composable
private fun DevicesScreenEmptyPreview() {
    ClickerTheme {
        DevicesScreen(
            state = DevicesUiState(),
            onSelect = {}, onForget = {}, onShowInfo = {}, onScan = {}, onAddByAddress = {}, onBack = {},
        )
    }
}
