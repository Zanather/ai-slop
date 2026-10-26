package io.github.zanather.clicker.ui.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.zanather.clicker.ui.model.DeviceRow
import io.github.zanather.clicker.ui.model.DevicesUiState

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
    TODO("UI agent")
}
