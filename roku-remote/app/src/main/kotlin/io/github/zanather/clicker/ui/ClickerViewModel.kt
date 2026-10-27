package io.github.zanather.clicker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.zanather.clicker.BuildConfig
import io.github.zanather.clicker.data.DeviceStore
import io.github.zanather.clicker.data.Settings
import io.github.zanather.clicker.data.StoreState
import io.github.zanather.clicker.ecp.DeviceInfo
import io.github.zanather.clicker.ecp.EcpClient
import io.github.zanather.clicker.ecp.EcpException
import io.github.zanather.clicker.ecp.EcpMode
import io.github.zanather.clicker.ecp.PowerMode
import io.github.zanather.clicker.ecp.RokuKey
import io.github.zanather.clicker.session.AppsError
import io.github.zanather.clicker.session.FoundRoku
import io.github.zanather.clicker.session.Link
import io.github.zanather.clicker.session.RokuSession
import io.github.zanather.clicker.session.SessionState
import io.github.zanather.clicker.session.address
import io.github.zanather.clicker.ui.model.AppTile
import io.github.zanather.clicker.ui.model.AppsUiState
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.model.DeviceInfoUiState
import io.github.zanather.clicker.ui.model.DeviceRow
import io.github.zanather.clicker.ui.model.DevicesUiState
import io.github.zanather.clicker.ui.model.RemoteActions
import io.github.zanather.clicker.ui.model.RemoteUiState
import io.github.zanather.clicker.ui.model.SettingsUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One ViewModel for the whole app: every screen is a view of the same TV session. */
class ClickerViewModel(
    private val session: RokuSession,
    private val store: DeviceStore,
    private val ecp: EcpClient,
) : ViewModel(), RemoteActions {

    val settings: StateFlow<Settings> = store.state
        .map { it.settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val remote: StateFlow<RemoteUiState> = combine(session.state, store.state) { s, stored -> s.toRemoteUiState(stored, ecp) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RemoteUiState(status = ConnectionStatus.Connecting))

    val apps: StateFlow<AppsUiState> = session.state
        .map { it.toAppsUiState(ecp) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState(loading = true))

    private val discovered = MutableStateFlow<List<FoundRoku>>(emptyList())
    private val scanning = MutableStateFlow(false)
    private val devicesMessage = MutableStateFlow<String?>(null)
    private var scanJob: Job? = null

    val devices: StateFlow<DevicesUiState> =
        combine(store.state, discovered, scanning, devicesMessage) { stored, found, isScanning, message ->
            val savedSerials = stored.devices.map { it.serialNumber }.toSet()
            DevicesUiState(
                saved = stored.devices.map {
                    DeviceRow(it.serialNumber, it.name, it.host, it.isTv, isSelected = it.serialNumber == stored.selectedSerial)
                },
                discovered = found
                    .filter { it.info.serialNumber !in savedSerials }
                    .map { DeviceRow(it.info.serialNumber, it.info.name, it.address.host, it.info.isTv) },
                scanning = isScanning,
                message = message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

    val settingsUi: StateFlow<SettingsUiState> = settings
        .map { SettingsUiState(it.hapticsEnabled, it.volumeKeysControlTv, it.keepScreenOn, it.dynamicColor, BuildConfig.VERSION_NAME) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private val _info = MutableStateFlow(DeviceInfoUiState(loading = true))
    val info: StateFlow<DeviceInfoUiState> = _info

    // region RemoteActions

    override fun press(key: RokuKey) = session.press(key)
    override fun holdStart(key: RokuKey) = session.holdStart(key)
    override fun holdEnd(key: RokuKey) = session.holdEnd(key)
    override fun launch(appId: String) = session.launch(appId)
    override fun sendKeys(keys: List<RokuKey>) = session.sendKeys(keys)
    override fun wake() = session.wake()
    override fun retry() = session.connect()

    override fun setTouchpadMode(enabled: Boolean) {
        viewModelScope.launch { store.updateSettings { it.copy(touchpadMode = enabled) } }
    }

    // endregion

    // region Apps

    fun refreshApps() {
        viewModelScope.launch { session.refreshApps() }
    }

    fun toggleFavorite(appId: String) {
        val state = session.state.value
        val device = state.device ?: return
        val favorites = if (appId in device.favorites) device.favorites - appId else device.favorites + appId
        val names = state.apps.filter { it.id == appId }.associate { it.id to it.name }
        viewModelScope.launch { store.setFavorites(device.serialNumber, favorites, names) }
    }

    // endregion

    // region Devices

    fun scan() {
        scanJob?.cancel()
        devicesMessage.value = null
        scanning.value = true
        discovered.value = emptyList()
        scanJob = viewModelScope.launch {
            try {
                session.discover().collect { found -> discovered.update { it + found } }
                if (discovered.value.isEmpty() && store.state.first().devices.isEmpty()) {
                    devicesMessage.value = "No Roku found. Make sure your phone is on the same Wi-Fi as the TV, or add it by IP address " +
                        "(on the TV: Settings > Network > About)."
                }
            } finally {
                scanning.value = false
            }
        }
    }

    fun select(row: DeviceRow) {
        val serial = row.serialNumber ?: return
        viewModelScope.launch {
            val found = discovered.value.firstOrNull { it.info.serialNumber == serial }
            if (found != null) session.choose(found) else store.select(serial)
        }
    }

    fun forget(row: DeviceRow) {
        val serial = row.serialNumber ?: return
        viewModelScope.launch { store.forget(serial) }
    }

    fun addByAddress(input: String) {
        val host = input.trim()
        if (!isIpv4Literal(host)) {
            devicesMessage.value = "Enter the TV's IP address, like 192.168.1.20. Find it on the TV under Settings > Network > About."
            return
        }
        devicesMessage.value = null
        viewModelScope.launch {
            scanning.value = true
            val found = session.probe(host)
            scanning.value = false
            if (found == null) {
                devicesMessage.value = "Nothing answered at $host. Check the address, and that the TV is on and on this Wi-Fi."
            } else {
                session.choose(found)
            }
        }
    }

    // endregion

    // region Info

    fun loadInfo(serialNumber: String?) {
        viewModelScope.launch {
            val current = session.state.value
            val device = store.state.first().devices.firstOrNull { it.serialNumber == serialNumber } ?: current.device ?: return@launch
            val cached = current.info?.takeIf { it.serialNumber == device.serialNumber }
            _info.value = cached?.toInfoUiState(device.host) ?: DeviceInfoUiState(name = device.name, loading = true)
            _info.value = try {
                ecp.deviceInfo(device.address).toInfoUiState(device.host)
            } catch (_: EcpException) {
                (cached?.toInfoUiState(device.host) ?: DeviceInfoUiState(name = device.name)).copy(
                    loading = false,
                    error = if (cached == null) "Couldn't reach ${device.name}." else null,
                )
            }
        }
    }

    // endregion

    fun updateSettings(new: SettingsUiState) {
        viewModelScope.launch {
            store.updateSettings {
                it.copy(
                    hapticsEnabled = new.hapticsEnabled,
                    volumeKeysControlTv = new.volumeKeysControlTv,
                    keepScreenOn = new.keepScreenOn,
                    dynamicColor = new.dynamicColor,
                )
            }
        }
    }

    companion object {
        fun factory(session: RokuSession, store: DeviceStore, ecp: EcpClient): ViewModelProvider.Factory = viewModelFactory {
            initializer { ClickerViewModel(session, store, ecp) }
        }
    }
}

internal fun SessionState.toRemoteUiState(stored: StoreState, ecp: EcpClient): RemoteUiState {
    val device = device ?: return RemoteUiState(status = ConnectionStatus.NoDevice, touchpadMode = stored.settings.touchpadMode)
    val byId = apps.associateBy { it.id }
    return RemoteUiState(
        deviceName = info?.name ?: device.name,
        status = connectionStatus(),
        isTv = info?.isTv ?: device.isTv,
        favorites = device.favorites.map { id ->
            val app = byId[id]
            AppTile(id, app?.name ?: device.favoriteNames[id] ?: id, ecp.iconUrl(device.address, id).toString(), isFavorite = true)
        },
        inputs = apps.filter { it.isTvInput }.map { AppTile(it.id, it.name, null) },
        activeAppId = activeApp?.app?.id,
        touchpadMode = stored.settings.touchpadMode,
    )
}

internal fun SessionState.connectionStatus(): ConnectionStatus = when (val l = link) {
    Link.NoDevice -> ConnectionStatus.NoDevice
    Link.Connecting -> ConnectionStatus.Connecting
    Link.Searching -> ConnectionStatus.Searching
    Link.Waking -> ConnectionStatus.Waking
    Link.Unreachable -> ConnectionStatus.Unreachable(canWake = device?.wakeMacs?.isNotEmpty() == true)
    is Link.Blocked -> ConnectionStatus.Blocked(l.limitedMode)
    Link.Connected -> if (info?.isTv != false && info?.powerMode?.isScreenOn == false) ConnectionStatus.TvOff else ConnectionStatus.Connected
}

internal fun SessionState.toAppsUiState(ecp: EcpClient): AppsUiState {
    val device = device ?: return AppsUiState(error = "Pick a TV first.")
    val favorites = device.favorites.toSet()
    return AppsUiState(
        apps = apps.filterNot { it.isTvInput }.map {
            AppTile(it.id, it.name, ecp.iconUrl(device.address, it.id).toString(), isFavorite = it.id in favorites)
        },
        activeAppId = activeApp?.app?.id,
        loading = appsLoading && apps.isEmpty(),
        error = when (appsError) {
            AppsError.Blocked -> "Your TV is hiding its channel list. On the TV, go to Settings > System > Advanced system settings > " +
                "Control by mobile apps > Network access and choose Enabled."
            AppsError.Failed -> if (apps.isEmpty()) "Couldn't load channels from ${device.name}." else null
            null -> null
        },
    )
}

internal fun DeviceInfo.toInfoUiState(host: String): DeviceInfoUiState {
    // Same order as the TV's own System Info screen, so things are where people expect them.
    val rows = buildList {
        add("Name" to name)
        modelNumber?.let { add("Model number" to it) }
        add("Software version" to listOfNotNull(softwareVersion, softwareBuild).joinToString(" build "))
        deviceId?.let { add("Device ID" to it) }
        wifiMac?.takeIf { it.isNotBlank() }?.let { add("Wi-Fi MAC" to it) }
        ethernetMac?.takeIf { it.isNotBlank() }?.let { add("Ethernet MAC" to it) }
        add("IP address" to host)
        add("Serial number" to serialNumber)
        uptimeSeconds?.let { add("Uptime" to formatUptime(it)) }
        modelName?.let { add("Model" to it) }
        vendorName?.let { add("Made by" to it) }
        networkType?.let { add("Connection" to if (it == "ethernet") "Ethernet" else "Wi-Fi") }
        add("Power" to powerMode.label())
        add("Control by mobile apps" to ecpMode.label())
        val shown = setOf(
            "user-device-name", "friendly-device-name", "default-device-name", "model-name", "model-number", "vendor-name",
            "software-version", "software-build", "serial-number", "device-id", "network-type", "wifi-mac", "ethernet-mac",
            "power-mode", "ecp-setting-mode", "uptime",
        )
        fields.filterKeys { it !in shown }.filterValues { it.isNotBlank() }.forEach { (k, v) -> add(k to v) }
    }
    return DeviceInfoUiState(name = name, rows = rows)
}

private fun PowerMode.label(): String = when (this) {
    PowerMode.On -> "On"
    PowerMode.DisplayOff -> "Screen off"
    PowerMode.Ready -> "Standby (Fast TV start)"
    PowerMode.Suspend -> "Suspended"
    PowerMode.PowerOff -> "Off"
    PowerMode.Headless -> "Streaming player"
    PowerMode.Unknown -> "Unknown"
}

private fun EcpMode.label(): String = when (this) {
    EcpMode.Limited -> "Limited (blocks this remote)"
    EcpMode.Enabled -> "Enabled"
    EcpMode.Permissive -> "Permissive"
    EcpMode.Disabled -> "Disabled (blocks this remote)"
    EcpMode.Unknown -> "Not reported"
}

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%d:%02d:%02d".format(h, m, s)
}

// Roku rejects requests whose Host header isn't its IP, so names like "tv.local" can't be used directly.
private fun isIpv4Literal(host: String): Boolean {
    val parts = host.split('.')
    return parts.size == 4 && parts.all { it.length in 1..3 && it.all(Char::isDigit) && it.toInt() <= 255 }
}
