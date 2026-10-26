package io.github.zanather.clicker.ui.model

import io.github.zanather.clicker.ecp.RokuKey

/** Connection state as the UI presents it. */
sealed interface ConnectionStatus {
    /** No TV chosen yet. */
    data object NoDevice : ConnectionStatus

    /** First contact with the saved TV since the app came to the foreground. */
    data object Connecting : ConnectionStatus

    data object Connected : ConnectionStatus

    /** The TV stopped answering at its last address; looking for it elsewhere on the network. */
    data object Searching : ConnectionStatus

    /** Reachable, screen off. Power turns it on. */
    data object TvOff : ConnectionStatus

    /** Not answering anywhere. [canWake] when a MAC is known for Wake-on-LAN. */
    data class Unreachable(val canWake: Boolean) : ConnectionStatus

    /**
     * The TV refuses remote keys and needs a one-time fix on the TV itself:
     * Settings > System > Advanced system settings > Control by mobile apps > Network access > Enabled.
     * [limitedMode] is the common case (Roku's default since OS 14.1); false means access is Disabled
     * or the TV rejected this phone's address.
     */
    data class Blocked(val limitedMode: Boolean) : ConnectionStatus
}

/** A launchable channel or TV input. */
data class AppTile(
    val id: String,
    val name: String,
    /** Null when there's no device to fetch from (previews, tests). */
    val iconUrl: String?,
    val isFavorite: Boolean = false,
)

data class RemoteUiState(
    /** Null until a TV has been chosen. */
    val deviceName: String? = null,
    val status: ConnectionStatus = ConnectionStatus.NoDevice,
    /** Roku TVs get volume, power, channel and input controls; sticks and boxes don't. */
    val isTv: Boolean = true,
    /** Pinned channels shown on the remote for one-tap launch. */
    val favorites: List<AppTile> = emptyList(),
    /** TV inputs (HDMI, antenna, AV) with the names the user gave them on the TV. */
    val inputs: List<AppTile> = emptyList(),
    val activeAppId: String? = null,
    /** Swipe pad instead of a D-pad. */
    val touchpadMode: Boolean = false,
)

/** Everything the remote screen can ask for. Keeps [io.github.zanather.clicker.ui.remote.RemoteScreen]'s signature short. */
interface RemoteActions {
    /** A single tap. */
    fun press(key: RokuKey)

    /** Finger down on a key that repeats while held ([RokuKey.repeatsWhenHeld]); [press] is not also called. */
    fun holdStart(key: RokuKey)

    /** Finger up after [holdStart]. */
    fun holdEnd(key: RokuKey)

    fun launch(appId: String)

    /** Characters the user added in the keyboard sheet, sent in order. */
    fun typeText(text: String)

    fun wake()

    fun retry()

    fun setTouchpadMode(enabled: Boolean)

    /** For previews and tests. */
    object Noop : RemoteActions {
        override fun press(key: RokuKey) = Unit
        override fun holdStart(key: RokuKey) = Unit
        override fun holdEnd(key: RokuKey) = Unit
        override fun launch(appId: String) = Unit
        override fun typeText(text: String) = Unit
        override fun wake() = Unit
        override fun retry() = Unit
        override fun setTouchpadMode(enabled: Boolean) = Unit
    }
}

data class AppsUiState(
    val apps: List<AppTile> = emptyList(),
    val activeAppId: String? = null,
    val loading: Boolean = false,
    /** Human-readable reason the list couldn't load, if it couldn't. */
    val error: String? = null,
)

data class DeviceRow(
    /** Null for a manually typed address that hasn't answered yet. */
    val serialNumber: String?,
    val name: String,
    val host: String,
    val isTv: Boolean,
    val isSelected: Boolean = false,
)

data class DevicesUiState(
    /** Remembered TVs. */
    val saved: List<DeviceRow> = emptyList(),
    /** Found on the network now and not already in [saved]. */
    val discovered: List<DeviceRow> = emptyList(),
    val scanning: Boolean = false,
    /** Shown when a manual address didn't answer or a scan hit a wall. */
    val message: String? = null,
)

data class DeviceInfoUiState(
    val name: String = "",
    /** Label to value, in display order. */
    val rows: List<Pair<String, String>> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class SettingsUiState(
    val hapticsEnabled: Boolean = true,
    /** Phone volume buttons change TV volume while the app is open. */
    val volumeKeysControlTv: Boolean = true,
    val keepScreenOn: Boolean = false,
    /** Material You colors from the wallpaper instead of Clicker's own palette. */
    val dynamicColor: Boolean = false,
)
