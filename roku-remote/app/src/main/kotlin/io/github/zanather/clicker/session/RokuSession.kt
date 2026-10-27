package io.github.zanather.clicker.session

import io.github.zanather.clicker.data.DeviceStore
import io.github.zanather.clicker.data.SavedDevice
import io.github.zanather.clicker.ecp.ActiveApp
import io.github.zanather.clicker.ecp.CommandQueue
import io.github.zanather.clicker.ecp.DeviceInfo
import io.github.zanather.clicker.ecp.EcpClient
import io.github.zanather.clicker.ecp.EcpException
import io.github.zanather.clicker.ecp.EcpMode
import io.github.zanather.clicker.ecp.LiteralKey
import io.github.zanather.clicker.ecp.PowerMode
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ecp.RokuAddress
import io.github.zanather.clicker.ecp.RokuApp
import io.github.zanather.clicker.ecp.RokuKey
import io.github.zanather.clicker.ecp.SsdpDiscovery
import io.github.zanather.clicker.ecp.SubnetScanner
import io.github.zanather.clicker.ecp.WakeOnLan
import io.github.zanather.clicker.net.Lan
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The session's view of the link to the selected TV. */
sealed interface Link {
    data object NoDevice : Link
    data object Connecting : Link
    data object Connected : Link

    /** Lost the TV at its saved address; scanning for its serial number. */
    data object Searching : Link

    /** Sent Wake-on-LAN and PowerOn; waiting for the TV to answer. */
    data object Waking : Link

    data object Unreachable : Link

    /** The TV refuses keys until its "Control by mobile apps" setting changes. */
    data class Blocked(val limitedMode: Boolean) : Link
}

/** Why the channel list is empty. */
enum class AppsError {
    /** Limited mode blocks `/query/apps`. */
    Blocked,
    Failed,
}

data class SessionState(
    val device: SavedDevice? = null,
    val link: Link = Link.NoDevice,
    val info: DeviceInfo? = null,
    val apps: List<RokuApp> = emptyList(),
    val appsLoading: Boolean = false,
    val appsError: AppsError? = null,
    val activeApp: ActiveApp? = null,
)

/** Delays the session uses; shortened in tests. */
data class SessionTiming(
    /** Under Roku's ~5 s idle-socket cutoff, so the pooled connection stays warm for the next press. */
    val pollInterval: Duration = 4.seconds,
    /** Device-info refresh cadence, in poll ticks. Active-app is polled on the other ticks. */
    val infoEveryTicks: Int = 3,
    val holdDelay: Duration = 400.milliseconds,
    val holdInterval: Duration = 90.milliseconds,
    val volumeHoldInterval: Duration = 60.milliseconds,
    /** On-screen keyboards drop characters that arrive faster than they redraw. */
    val literalGap: Duration = 30.milliseconds,
    val ssdpTimeout: Duration = 3.seconds,
    val wakeTimeout: Duration = 20.seconds,
    val settleDelay: Duration = 1500.milliseconds,
)

/** A Roku found while scanning. */
data class FoundRoku(val address: RokuAddress, val info: DeviceInfo)

/**
 * Owns the connection to the selected TV: connects, re-finds it when its IP changes, wakes it, polls it
 * while the app is visible, and sends keys in order.
 *
 * Thread-safe: UI calls arrive on the main thread; work runs on [scope].
 */
class RokuSession(
    private val scope: CoroutineScope,
    private val ecp: EcpClient,
    private val store: DeviceStore,
    private val lan: Lan,
    private val timing: SessionTiming = SessionTiming(),
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val queue = CommandQueue(scope, onFailure = ::onCommandFailure)
    private val typing = Channel<List<RokuKey>>(Channel.UNLIMITED)

    private val lock = Any()

    @Volatile
    private var foreground = false

    /** Device-info at the saved address came back with another serial: that's someone else's TV now. */
    @Volatile
    private var foreignAtAddress = false
    private var connectJob: Job? = null
    private var pollJob: Job? = null

    // One for volume too, so the phone's volume buttons and a finger on the D-pad can't end each other's hold.
    private val hold = HoldSlot(timing.holdInterval)
    private val volumeHold = HoldSlot(timing.volumeHoldInterval)

    // Separate, so a Home right after Power can't cancel the power-state refresh.
    private val infoFollowUp = FollowUp { refreshInfo() }
    private val activeAppFollowUp = FollowUp { refreshActiveApp() }

    init {
        scope.launch {
            var last: SavedDevice? = null
            store.state.collect { stored ->
                val device = stored.selected
                val moved = device?.serialNumber != last?.serialNumber || device?.address != last?.address
                last = device
                if (moved) onDeviceChanged(device) else _state.update { it.copy(device = device) }
            }
        }
        scope.launch {
            for (keys in typing) {
                for (key in keys) {
                    try {
                        queue.run { send(key) }
                    } catch (_: EcpException) {
                        break
                    }
                    if (key is LiteralKey) delay(timing.literalGap)
                }
            }
        }
    }

    // region Lifecycle

    /** The app is visible: reconnect if needed and keep the connection warm. */
    fun onForeground() {
        synchronized(lock) { foreground = true }
        when (_state.value.link) {
            Link.Connected, is Link.Blocked -> {
                startPolling()
                scope.launch { refreshInfo() }
            }
            Link.NoDevice, Link.Waking -> Unit
            else -> connect()
        }
    }

    /** The app is hidden: stop polling and any held key so nothing runs in the background. */
    fun onBackground() {
        synchronized(lock) {
            foreground = false
            pollJob?.cancel()
            hold.cancel()
            volumeHold.cancel()
            if (_state.value.link == Link.Searching || _state.value.link == Link.Connecting) connectJob?.cancel()
        }
    }

    // endregion

    // region Keys

    /** One tap. [RemoteKey.Power] becomes PowerOn/PowerOff from the TV's power state, or a wake-up if it's gone. */
    fun press(key: RokuKey) {
        if (key == RemoteKey.Power && _state.value.link.tvMissing) {
            wake()
            return
        }
        val resolved = resolvePower(key)
        queue.offer {
            try {
                send(resolved)
            } catch (e: EcpException.Unreachable) {
                // Power the session didn't know was for a sleeping TV (cold start, or state from before): wake it.
                // Not on a Timeout: that TV got the request, so it's awake and may have taken the key.
                val device = _state.value.device
                // A failure at an old address goes on to onCommandFailure, which ignores it.
                if (key != RemoteKey.Power || device == null || e.address != device.address) throw e
                queue.discardOffered()
                // A connect still running may be about to find the TV elsewhere; wake() would cancel it.
                if (synchronized(lock) { connectJob?.isActive == true }) scope.launch { sendWakePackets(device) } else wake()
            }
        }
    }

    /** Finger down on a repeating key: press now, then repeat after [SessionTiming.holdDelay] until [holdEnd]. */
    fun holdStart(key: RokuKey) = holdSlot(key).start(key)

    fun holdEnd(key: RokuKey) = holdSlot(key).end(key)

    private fun holdSlot(key: RokuKey) = if (key == RemoteKey.VolumeUp || key == RemoteKey.VolumeDown) volumeHold else hold

    /** Keyboard keys: never dropped, always in order, paced for on-screen keyboards. */
    fun sendKeys(keys: List<RokuKey>) {
        if (keys.isNotEmpty()) typing.trySend(keys)
    }

    fun launch(appId: String) {
        queue.offer {
            val device = _state.value.device ?: return@offer
            if (foreignAtAddress) return@offer
            ecp.launch(device.address, appId)
            // Show it as playing now; the next poll confirms what the TV actually opened.
            _state.update { s ->
                val app = s.apps.firstOrNull { it.id == appId } ?: RokuApp(appId, appId, null, null)
                s.copy(activeApp = ActiveApp(app, null))
            }
            activeAppFollowUp.schedule()
        }
    }

    /**
     * For the widget and Quick Settings tile, which can run before any UI exists. Reads the device straight
     * from storage and waits for the press to land, so it finishes inside the caller's short lifecycle.
     */
    suspend fun pressFromOutside(key: RemoteKey): Boolean {
        val device = store.state.first().selected ?: return false
        // Also checks it's still our TV: after a DHCP shuffle another Roku can answer at the saved address.
        val info = try {
            ecp.deviceInfo(device.address)
        } catch (_: EcpException) {
            null
        }
        if (info != null && info.serialNumber != device.serialNumber) {
            // Never press keys on someone else's TV. Ours may be asleep elsewhere; the app re-finds it when opened.
            if (key == RemoteKey.Power) sendWakePackets(device)
            return false
        }
        val resolved = if (key == RemoteKey.Power) {
            if (info == null) {
                sendWakePackets(device)
                RemoteKey.PowerOn
            } else if (info.powerMode.isScreenOn) {
                RemoteKey.PowerOff
            } else {
                RemoteKey.PowerOn
            }
        } else {
            key
        }
        return withTimeoutOrNull(6.seconds) {
            try {
                queue.run { ecp.keypress(device.address, resolved) }
                true
            } catch (_: EcpException) {
                false
            }
        } ?: false
    }

    private suspend fun send(key: RokuKey) {
        val device = _state.value.device ?: return
        if (foreignAtAddress) return
        ecp.keypress(device.address, key)
        onKeyAccepted(key)
    }

    private fun resolvePower(key: RokuKey): RokuKey {
        if (key != RemoteKey.Power) return key
        val mode = _state.value.info?.powerMode ?: return key
        return when {
            mode == PowerMode.Unknown -> key
            mode.isScreenOn -> RemoteKey.PowerOff
            else -> RemoteKey.PowerOn
        }
    }

    private fun onKeyAccepted(key: RokuKey) {
        // A key got through, so whatever blocked us is fixed.
        if (_state.value.link is Link.Blocked) {
            _state.update { it.copy(link = Link.Connected) }
            scope.launch { refreshApps() }
        }
        when (key) {
            RemoteKey.PowerOn, RemoteKey.PowerOff, RemoteKey.Power -> infoFollowUp.schedule()
            RemoteKey.Home, RemoteKey.Back, RemoteKey.Select -> activeAppFollowUp.schedule()
            else -> Unit
        }
    }

    private fun onCommandFailure(e: EcpException) {
        when (e) {
            is EcpException.Forbidden -> _state.update { it.copy(link = Link.Blocked(e.limitedMode)) }
            is EcpException.Unreachable -> onLinkFailure(e.address)
            is EcpException.Timeout -> onLinkFailure(e.address)
            is EcpException.Http, is EcpException.BadResponse -> Unit
        }
    }

    private fun onLinkFailure(address: RokuAddress) {
        // The TV has moved since; onDeviceChanged already dropped the presses meant for the old address.
        if (address != _state.value.device?.address) return
        // Presses queued behind this one would each wait out a timeout and land seconds late, if at all.
        queue.discardOffered()
        connect()
    }

    // endregion

    // region Connection

    /** Probes the saved address; if the TV isn't there, looks for it on the network. */
    fun connect() {
        synchronized(lock) {
            if (connectJob?.isActive == true) return
            val device = _state.value.device ?: return
            connectJob = scope.launch {
                // Blocked stays until onInfo shows it's fixed: old firmware doesn't report the mode.
                val link = _state.value.link
                if (link != Link.Connected && link !is Link.Blocked) _state.update { it.copy(link = Link.Connecting) }
                try {
                    onInfo(device, ecp.deviceInfo(device.address))
                } catch (e: EcpException.Forbidden) {
                    _state.update { it.copy(link = Link.Blocked(e.limitedMode)) }
                } catch (_: EcpException) {
                    relocate(device)
                }
            }
        }
    }

    /** Wake-on-LAN, then PowerOn until the TV answers or [SessionTiming.wakeTimeout] passes. */
    fun wake() {
        val device = _state.value.device ?: return
        synchronized(lock) {
            connectJob?.cancel()
            connectJob = scope.launch {
                _state.update { it.copy(link = Link.Waking) }
                sendWakePackets(device)
                val start = TimeSource.Monotonic.markNow()
                while (start.elapsedNow() < timing.wakeTimeout) {
                    // Another TV answers at the saved address: don't turn it on. Rediscovery below looks for ours.
                    if (!foreignAtAddress) {
                        try {
                            ecp.keypress(device.address, RemoteKey.PowerOn)
                        } catch (_: EcpException) {
                            // Expected while it's still asleep.
                        }
                        val info = try {
                            ecp.deviceInfo(device.address)
                        } catch (_: EcpException) {
                            null
                        }
                        if (info != null) {
                            onInfo(device, info)
                            // Blocked TVs ignore PowerOn; the banner explains the fix, so stop retrying.
                            if (!foreignAtAddress && (info.powerMode.isScreenOn || _state.value.link is Link.Blocked)) return@launch
                        }
                    }
                    delay(1.seconds)
                }
                if (_state.value.info == null || _state.value.link == Link.Waking) relocate(device)
            }
        }
    }

    private suspend fun sendWakePackets(device: SavedDevice) {
        if (device.wakeMacs.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                lan.openDatagramSocket().use { socket ->
                    repeat(3) {
                        for (mac in device.wakeMacs) {
                            for (target in lan.broadcastAddresses()) {
                                try {
                                    WakeOnLan.send(mac, target, socket)
                                } catch (_: IOException) {
                                    // One unreachable broadcast address shouldn't stop the others.
                                } catch (_: IllegalArgumentException) {
                                    // A malformed saved MAC; the other one may still work.
                                }
                            }
                        }
                        delay(250.milliseconds)
                    }
                }
            } catch (_: IOException) {
                // No UDP socket (e.g. Wi-Fi off); PowerOn below may still reach a TV in standby.
            }
        }
    }

    /** The TV stopped answering at its saved address. Find it by serial and save the new address. */
    private suspend fun relocate(device: SavedDevice) {
        // Blocked stays, as in connect(): old firmware only reports it through a refused key.
        _state.update { if (it.link is Link.Blocked) it else it.copy(link = Link.Searching) }
        val found = discover(exhaustive = true).firstOrNull { it.info.serialNumber == device.serialNumber }
        when {
            found == null -> _state.update { it.copy(link = Link.Unreachable) }
            // Saving the new address re-triggers connect() through the store collector.
            found.address != device.address -> store.updateAddress(device.serialNumber, found.address.host, found.address.port)
            else -> onInfo(device, found.info)
        }
    }

    private suspend fun onInfo(device: SavedDevice, info: DeviceInfo) {
        if (info.serialNumber != device.serialNumber) {
            // Something else now owns this IP; the real TV moved. Until it's found, keys go nowhere.
            foreignAtAddress = true
            relocate(device)
            return
        }
        foreignAtAddress = false
        val link = when (info.ecpMode) {
            EcpMode.Limited -> Link.Blocked(limitedMode = true)
            EcpMode.Disabled -> Link.Blocked(limitedMode = false)
            EcpMode.Enabled, EcpMode.Permissive -> Link.Connected
            // Older firmware doesn't report the mode; only a refused or accepted key tells us. Once one was
            // refused, ask /query/apps whether that still holds, so "Check again" can't clear it by accident.
            EcpMode.Unknown -> when (val current = _state.value.link) {
                is Link.Blocked -> if (current.limitedMode && appsAllowed(device)) Link.Connected else current
                else -> Link.Connected
            }
        }
        val wasConnected = _state.value.info != null && _state.value.link == Link.Connected
        _state.update { it.copy(info = info, link = link) }

        val refreshed = device.copy(
            name = info.name,
            isTv = info.isTv,
            wakeMacs = info.wakeMacs.ifEmpty { device.wakeMacs },
        )
        if (refreshed != device) store.upsert(refreshed)

        if (!wasConnected) {
            scope.launch { refreshApps() }
            scope.launch { refreshActiveApp() }
        }
        if (foreground) startPolling()
    }

    private fun startPolling() {
        synchronized(lock) {
            if (pollJob?.isActive == true || !foreground) return
            pollJob = scope.launch {
                var tick = 0
                while (isActive) {
                    delay(timing.pollInterval)
                    tick++
                    val device = _state.value.device ?: break
                    try {
                        // Blocked: poll device-info often, so the banner clears the moment the setting changes.
                        if (_state.value.link is Link.Blocked || tick % timing.infoEveryTicks == 0) {
                            onInfo(device, ecp.deviceInfo(device.address))
                        } else {
                            _state.update { it.copy(activeApp = ecp.activeApp(device.address)) }
                        }
                    } catch (e: EcpException.Forbidden) {
                        _state.update { it.copy(link = Link.Blocked(e.limitedMode)) }
                    } catch (_: EcpException) {
                        synchronized(lock) { pollJob = null }
                        connect()
                        break
                    }
                }
            }
        }
    }

    private fun onDeviceChanged(device: SavedDevice?) {
        synchronized(lock) {
            connectJob?.cancel()
            pollJob?.cancel()
            hold.cancel()
            volumeHold.cancel()
        }
        // Presses meant for the old TV or address mustn't land on the new one.
        queue.discardOffered()
        // A new address or TV isn't known to be wrong, so keys go out right away.
        foreignAtAddress = false
        _state.value = SessionState(device = device, link = if (device == null) Link.NoDevice else Link.Connecting)
        // In the background (widget or tile woke the process) stay quiet; onForeground() connects.
        if (device != null && foreground) connect()
    }

    // endregion

    // region Queries

    suspend fun refreshApps() {
        val device = _state.value.device ?: return
        _state.update { it.copy(appsLoading = true) }
        try {
            val apps = ecp.apps(device.address)
            _state.update { it.copy(apps = apps, appsLoading = false, appsError = null) }
            // Keeps favorite tiles labelled while the TV can't list its channels (asleep, Limited mode).
            store.rememberFavoriteNames(device.serialNumber, apps.associate { it.id to it.name })
        } catch (_: EcpException.Forbidden) {
            _state.update { it.copy(appsLoading = false, appsError = AppsError.Blocked) }
        } catch (_: EcpException) {
            _state.update { it.copy(appsLoading = false, appsError = AppsError.Failed) }
        }
    }

    private suspend fun refreshActiveApp() {
        val device = _state.value.device ?: return
        try {
            val active = ecp.activeApp(device.address)
            _state.update { it.copy(activeApp = active) }
        } catch (_: EcpException) {
            // Polling will notice a dead link.
        }
    }

    private suspend fun refreshInfo() {
        val device = _state.value.device ?: return
        try {
            onInfo(device, ecp.deviceInfo(device.address))
        } catch (e: EcpException.Forbidden) {
            _state.update { it.copy(link = Link.Blocked(e.limitedMode)) }
        } catch (_: EcpException) {
            connect()
        }
    }

    /** Limited mode refuses `/query/apps` as well as keys, so this asks whether keys work without pressing one. */
    private suspend fun appsAllowed(device: SavedDevice): Boolean = try {
        ecp.apps(device.address)
        true
    } catch (_: EcpException) {
        false
    }

    /** One repeating key at a time: a new hold replaces the one before it. */
    private inner class HoldSlot(private val interval: Duration) {
        private var held: RokuKey? = null
        private var job: Job? = null

        fun start(key: RokuKey) {
            synchronized(lock) {
                job?.cancel()
                // The tap itself is queued now, like press(): a quick release can't cancel it or reorder it.
                queue.offer { send(key) }
                held = key
                job = scope.launch {
                    try {
                        delay(timing.holdDelay)
                        while (isActive) {
                            queue.run { send(key) }
                            delay(interval)
                        }
                    } catch (_: EcpException) {
                        // Already reported through onCommandFailure; stop repeating into a dead link.
                    }
                }
            }
        }

        /** Finger up on [key]. A hold another key has since taken over keeps going. */
        fun end(key: RokuKey) {
            synchronized(lock) {
                if (key == held) cancel()
            }
        }

        fun cancel() {
            synchronized(lock) {
                job?.cancel()
                job = null
                held = null
            }
        }
    }

    /** A query run once the TV has had a moment to react. Scheduling again replaces the pending run. */
    private inner class FollowUp(private val query: suspend () -> Unit) {
        private var job: Job? = null

        fun schedule() {
            synchronized(lock) {
                job?.cancel()
                job = scope.launch {
                    delay(timing.settleDelay)
                    query()
                }
            }
        }
    }

    // endregion

    // region Discovery

    /**
     * Every Roku on the network, each once, with its device-info. SSDP first; then, when [exhaustive] or when
     * SSDP found nothing (multicast blocked by the router or a VPN), probes every address in the subnet.
     * Cancel collection (e.g. `firstOrNull`) to stop early.
     */
    fun discover(exhaustive: Boolean = false): Flow<FoundRoku> = channelFlow {
        val seen = mutableSetOf<String>()
        suspend fun offer(address: RokuAddress, info: DeviceInfo) {
            if (seen.add(info.serialNumber)) send(FoundRoku(address, info))
        }

        try {
            lan.withMulticastLock {
                SsdpDiscovery(lan::openDatagramSocket).discover(timing.ssdpTimeout).collect { roku ->
                    probeInfo(roku.address)?.let { offer(roku.address, it) }
                }
            }
        } catch (_: IOException) {
            // No multicast on this network; the subnet scan below still works.
        }
        if (seen.isNotEmpty() && !exhaustive) return@channelFlow

        val self = lan.lanAddress() ?: return@channelFlow
        SubnetScanner(::probeInfo)
            .scan(SubnetScanner.hostsInSubnet(self.address, self.prefixLength))
            .collect { (address, info) -> offer(address, info) }
    }

    private suspend fun probeInfo(address: RokuAddress): DeviceInfo? = try {
        ecp.deviceInfo(address)
    } catch (_: EcpException) {
        null
    }

    /** Probes [host] directly, for "add by IP". */
    suspend fun probe(host: String): FoundRoku? {
        val address = RokuAddress(host)
        return try {
            FoundRoku(address, ecp.deviceInfo(address))
        } catch (_: EcpException) {
            null
        }
    }

    /** Remembers [found] and makes it the current TV. */
    suspend fun choose(found: FoundRoku) {
        store.upsert(
            SavedDevice(
                serialNumber = found.info.serialNumber,
                name = found.info.name,
                host = found.address.host,
                port = found.address.port,
                isTv = found.info.isTv,
                wakeMacs = found.info.wakeMacs,
            ),
        )
        store.select(found.info.serialNumber)
    }

    // endregion
}

val SavedDevice.address: RokuAddress get() = RokuAddress(host, port)

/** The TV isn't answering at all, so Power should try to wake it rather than send a key. */
private val Link.tvMissing: Boolean
    get() = this == Link.Unreachable || this == Link.Searching
