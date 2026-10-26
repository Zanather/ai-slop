package io.github.zanather.clicker.ui

import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.data.SavedDevice
import io.github.zanather.clicker.data.Settings
import io.github.zanather.clicker.data.StoreState
import io.github.zanather.clicker.ecp.ActiveApp
import io.github.zanather.clicker.ecp.DeviceInfo
import io.github.zanather.clicker.ecp.EcpClient
import io.github.zanather.clicker.ecp.EcpMode
import io.github.zanather.clicker.ecp.PowerMode
import io.github.zanather.clicker.ecp.RokuApp
import io.github.zanather.clicker.session.AppsError
import io.github.zanather.clicker.session.Link
import io.github.zanather.clicker.session.SessionState
import io.github.zanather.clicker.ui.model.ConnectionStatus
import okhttp3.OkHttpClient
import org.junit.Test

class UiMappingTest {
    private val ecp = EcpClient(OkHttpClient())
    private val device = SavedDevice(
        serialNumber = "X00000ND22GG",
        name = "Living Room TV",
        host = "192.168.8.198",
        isTv = true,
        wakeMacs = listOf("64:e0:03:d4:56:41"),
        favorites = listOf("837", "12"),
    )
    private val apps = listOf(
        RokuApp("tvinput.hdmi1", "PlayStation", "tvin", "1"),
        RokuApp("12", "Netflix", "appl", "5"),
        RokuApp("837", "YouTube", "appl", "2"),
    )

    @Test
    fun noDevice() {
        val ui = SessionState().toRemoteUiState(StoreState(settings = Settings(touchpadMode = true)), ecp)

        assertThat(ui.status).isEqualTo(ConnectionStatus.NoDevice)
        assertThat(ui.deviceName).isNull()
        assertThat(ui.touchpadMode).isTrue()
    }

    @Test
    fun connectedTvMapsFavoritesInPinnedOrderAndSplitsOutInputs() {
        val state = SessionState(device, Link.Connected, info(PowerMode.On), apps, activeApp = ActiveApp(apps[1], null))

        val ui = state.toRemoteUiState(StoreState(listOf(device), device.serialNumber), ecp)

        assertThat(ui.status).isEqualTo(ConnectionStatus.Connected)
        assertThat(ui.favorites.map { it.name }).containsExactly("YouTube", "Netflix").inOrder()
        assertThat(ui.favorites.first().iconUrl).isEqualTo("http://192.168.8.198:8060/query/icon/837")
        assertThat(ui.inputs.map { it.name }).containsExactly("PlayStation")
        assertThat(ui.activeAppId).isEqualTo("12")
    }

    @Test
    fun aTvInStandbyShowsAsOff() {
        assertThat(SessionState(device, Link.Connected, info(PowerMode.Ready)).connectionStatus()).isEqualTo(ConnectionStatus.TvOff)
        assertThat(SessionState(device, Link.Connected, info(PowerMode.DisplayOff)).connectionStatus()).isEqualTo(ConnectionStatus.TvOff)
        assertThat(SessionState(device, Link.Connected, info(PowerMode.On)).connectionStatus()).isEqualTo(ConnectionStatus.Connected)
    }

    @Test
    fun unreachableOffersWakeOnlyWithAKnownMac() {
        assertThat(SessionState(device, Link.Unreachable).connectionStatus()).isEqualTo(ConnectionStatus.Unreachable(canWake = true))
        assertThat(SessionState(device.copy(wakeMacs = emptyList()), Link.Unreachable).connectionStatus())
            .isEqualTo(ConnectionStatus.Unreachable(canWake = false))
    }

    @Test
    fun blockedCarriesTheReason() {
        assertThat(SessionState(device, Link.Blocked(true)).connectionStatus()).isEqualTo(ConnectionStatus.Blocked(limitedMode = true))
    }

    @Test
    fun appsHideInputsMarkFavoritesAndExplainLimitedMode() {
        val ui = SessionState(device, Link.Blocked(true), apps = apps, appsError = AppsError.Blocked).toAppsUiState(ecp)

        assertThat(ui.apps.map { it.id }).containsExactly("12", "837").inOrder()
        assertThat(ui.apps.all { it.isFavorite }).isTrue()
        assertThat(ui.error).contains("Control by mobile apps")
    }

    @Test
    fun deviceInfoRowsLeadWithWhatTheOfficialScreenShows() {
        val ui = info(PowerMode.On).toInfoUiState("192.168.8.198")
        val labels = ui.rows.map { it.first }

        assertThat(labels).containsAtLeast("Model number", "Software version", "Device ID", "Wi-Fi MAC", "IP address", "Serial number", "Uptime").inOrder()
        assertThat(ui.rows.toMap()["Software version"]).isEqualTo("15.3.4 build 2402")
        assertThat(ui.rows.toMap()["Uptime"]).isEqualTo("21:42:03")
        assertThat(ui.rows.toMap()["Control by mobile apps"]).isEqualTo("Enabled")
    }

    private fun info(power: PowerMode) = DeviceInfo(
        serialNumber = "X00000ND22GG",
        deviceId = "S076216D22GG",
        name = "Living Room TV",
        modelName = "Roku TV",
        modelNumber = "C148X",
        vendorName = "Roku",
        isTv = true,
        powerMode = power,
        ecpMode = EcpMode.Enabled,
        softwareVersion = "15.3.4",
        softwareBuild = "2402",
        wifiMac = "64:e0:03:d4:56:41",
        ethernetMac = null,
        networkType = "wifi",
        uptimeSeconds = 78123,
        supportsFindRemote = true,
        supportsWakeOnWlan = true,
        developerEnabled = false,
        fields = mapOf("model-number" to "C148X", "screen-size" to "55"),
    )
}
