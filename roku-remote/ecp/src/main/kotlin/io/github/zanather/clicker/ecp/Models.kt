package io.github.zanather.clicker.ecp

/** Where a Roku listens for ECP. [host] is always an IP literal: Roku rejects requests whose Host isn't its IP. */
data class RokuAddress(val host: String, val port: Int = DEFAULT_PORT) {
    override fun toString(): String = if (port == DEFAULT_PORT) host else "$host:$port"

    companion object {
        const val DEFAULT_PORT = 8060
    }
}

/** The subset of `/query/device-info` a remote cares about, plus every raw field for display. */
data class DeviceInfo(
    /** Stable identity across IP changes. Matches the serial in the SSDP `USN` header. */
    val serialNumber: String,
    val deviceId: String?,
    /**
     * Display name: `user-device-name`, else `friendly-device-name`, else `default-device-name`,
     * else `model-name`, skipping blank values (Roku sends `<user-device-name />` when unset).
     */
    val name: String,
    val modelName: String?,
    val modelNumber: String?,
    val vendorName: String?,
    val isTv: Boolean,
    val powerMode: PowerMode,
    /** The TV's "Control by mobile apps > Network access" setting, when the firmware reports it (OS 15+). */
    val ecpMode: EcpMode,
    val softwareVersion: String?,
    val softwareBuild: String?,
    val wifiMac: String?,
    val ethernetMac: String?,
    /** `wifi` or `ethernet`. */
    val networkType: String?,
    val uptimeSeconds: Long?,
    val supportsFindRemote: Boolean,
    val supportsWakeOnWlan: Boolean,
    val developerEnabled: Boolean,
    /** All top-level fields in document order, for an "about this TV" screen. */
    val fields: Map<String, String>,
) {
    /** MACs to try for Wake-on-LAN, the active interface first. */
    val wakeMacs: List<String>
        get() {
            val active = if (networkType == "ethernet") ethernetMac else wifiMac
            return listOfNotNull(active, wifiMac, ethernetMac).filter { it.isNotBlank() }.distinct()
        }
}

/** `power-mode` from device-info. */
enum class PowerMode(val ecpValue: String?) {
    /** Screen on. */
    On("PowerOn"),

    /** Screen off but still running (audio may play). `PowerOn` restores the screen. */
    DisplayOff("DisplayOff"),

    /** Networked standby ("Fast TV start"). ECP still answers; `PowerOn` wakes it. */
    Ready("Ready"),

    Suspend("Suspend"),

    PowerOff("PowerOff"),

    /** Streaming stick/box without its own screen. */
    Headless("Headless"),

    Unknown(null),
    ;

    /** Whether the user would call this "on". */
    val isScreenOn: Boolean get() = this == On || this == Headless || this == Unknown

    companion object {
        fun parse(value: String?): PowerMode = entries.firstOrNull { it.ecpValue != null && it.ecpValue.equals(value, ignoreCase = true) } ?: Unknown
    }
}

/** `ecp-setting-mode`: the TV's "Control by mobile apps > Network access" setting. */
enum class EcpMode(val ecpValue: String?) {
    /** Default since Roku OS 14.1. Blocks keypresses and `/query/apps` with HTTP 403. */
    Limited("limited"),
    Enabled("enabled"),
    Permissive("permissive"),
    Disabled("disabled"),

    /** Older firmware doesn't report the field. */
    Unknown(null),
    ;

    /** Whether remote keys will be accepted. Unknown is optimistic; a 403 will correct it. */
    val allowsKeys: Boolean get() = this != Limited && this != Disabled

    companion object {
        fun parse(value: String?): EcpMode = entries.firstOrNull { it.ecpValue != null && it.ecpValue.equals(value, ignoreCase = true) } ?: Unknown
    }
}

/** An installed channel (app) or TV input from `/query/apps`. */
data class RokuApp(
    val id: String,
    /** With non-breaking spaces normalized to plain spaces (Roku sends `HDMI 1`). */
    val name: String,
    /** `appl` for channels, `tvin` for TV inputs (`tvinput.hdmi1`, `tvinput.dtv`, …), `menu`, `ndka`… */
    val type: String?,
    val version: String?,
) {
    val isTvInput: Boolean get() = type == "tvin" || id.startsWith("tvinput.")
}

/** `/query/active-app`. A null [app] means the Roku home screen is showing. */
data class ActiveApp(
    /**
     * Null on the home screen. Roku reports home three ways depending on firmware: a bare `<app>Roku</app>`
     * with no id, `type="home"`, or `ui-location="home"`; all three map to null.
     */
    val app: RokuApp?,
    val screensaver: RokuApp?,
) {
    val isHome: Boolean get() = app == null
}

/** A Roku that answered discovery. */
data class DiscoveredRoku(
    val address: RokuAddress,
    /** From the SSDP `USN` (`uuid:roku:ecp:<serial>`), when known. */
    val serialNumber: String?,
)
