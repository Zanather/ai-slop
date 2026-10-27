package io.github.zanather.clicker.ui.remote

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.zanather.clicker.ui.model.AppTile
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.model.RemoteActions
import io.github.zanather.clicker.ui.model.RemoteUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme

/** Sample states for previews and screenshot tests. Icons are null: nothing to fetch them from. */
internal object RemoteSamples {
    val favorites = listOf(
        AppTile("12", "Netflix", null, isFavorite = true),
        AppTile("837", "YouTube", null, isFavorite = true),
        AppTile("291097", "Disney+", null, isFavorite = true),
        AppTile("2285", "Hulu", null, isFavorite = true),
        AppTile("13", "Prime Video", null, isFavorite = true),
        AppTile("61322", "Max", null, isFavorite = true),
    )

    val inputs = listOf(
        AppTile("tvinput.hdmi1", "PlayStation 5", null),
        AppTile("tvinput.hdmi2", "Soundbar", null),
        AppTile("tvinput.dtv", "Live TV", null),
    )

    val connectedTv = RemoteUiState(
        deviceName = "Living Room TV",
        status = ConnectionStatus.Connected,
        isTv = true,
        favorites = favorites,
        inputs = inputs,
        activeAppId = "12",
    )

    val streamingStick = RemoteUiState(
        deviceName = "Bedroom Streaming Stick",
        status = ConnectionStatus.Connected,
        isTv = false,
        favorites = favorites.take(4),
        activeAppId = "837",
    )

    fun tv(status: ConnectionStatus) = connectedTv.copy(status = status)
}

@Composable
private fun Sample(state: RemoteUiState) {
    ClickerTheme {
        RemoteScreen(state, RemoteActions.Noop, onOpenApps = {}, onOpenDevices = {}, onOpenSettings = {})
    }
}

@Preview(name = "Connected", widthDp = 411, heightDp = 891)
@Preview(name = "Connected, dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectedPreview() = Sample(RemoteSamples.connectedTv)

@Preview(name = "Touchpad", widthDp = 411, heightDp = 891)
@Composable
private fun TouchpadPreview() = Sample(RemoteSamples.connectedTv.copy(touchpadMode = true))

@Preview(name = "Live TV", widthDp = 411, heightDp = 891)
@Composable
private fun LiveTvPreview() = Sample(RemoteSamples.connectedTv.copy(activeAppId = "tvinput.dtv"))

@Preview(name = "Streaming stick", widthDp = 411, heightDp = 891)
@Composable
private fun StreamingStickPreview() = Sample(RemoteSamples.streamingStick)

@Preview(name = "Blocked (Limited)", widthDp = 411, heightDp = 891)
@Composable
private fun BlockedPreview() = Sample(RemoteSamples.tv(ConnectionStatus.Blocked(limitedMode = true)))

@Preview(name = "Can't reach", widthDp = 411, heightDp = 891)
@Composable
private fun UnreachablePreview() = Sample(RemoteSamples.tv(ConnectionStatus.Unreachable(canWake = true)))

@Preview(name = "TV off", widthDp = 411, heightDp = 891)
@Composable
private fun TvOffPreview() = Sample(RemoteSamples.tv(ConnectionStatus.TvOff))

@Preview(name = "No TV yet", widthDp = 411, heightDp = 891)
@Composable
private fun NoDevicePreview() = Sample(RemoteUiState())

@Preview(name = "Landscape", widthDp = 891, heightDp = 411)
@Composable
private fun LandscapePreview() = Sample(RemoteSamples.connectedTv)
