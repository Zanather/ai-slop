package io.github.zanather.clicker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A TV the user has picked. Keyed by serial number so it survives the router handing it a new IP. */
@Serializable
data class SavedDevice(
    val serialNumber: String,
    val name: String,
    val host: String,
    val port: Int = 8060,
    val isTv: Boolean = true,
    /** Captured while the TV is awake, because device-info can't be read once it's asleep. */
    val wakeMacs: List<String> = emptyList(),
    /** Pinned channel ids, in the order shown on the remote. */
    val favorites: List<String> = emptyList(),
    /** Last seen name of each favorite, to label its tile while the TV can't list its channels. */
    val favoriteNames: Map<String, String> = emptyMap(),
)

@Serializable
data class Settings(
    val hapticsEnabled: Boolean = true,
    val volumeKeysControlTv: Boolean = true,
    val keepScreenOn: Boolean = false,
    val dynamicColor: Boolean = false,
    val touchpadMode: Boolean = false,
)

data class StoreState(
    val devices: List<SavedDevice> = emptyList(),
    val selectedSerial: String? = null,
    val settings: Settings = Settings(),
) {
    val selected: SavedDevice? get() = devices.firstOrNull { it.serialNumber == selectedSerial }
}

/** Remembered TVs, the current choice, and settings. Everything lives in one small preferences file. */
class DeviceStore(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<StoreState> = dataStore.data
        .map { prefs ->
            StoreState(
                devices = prefs[DEVICES]?.let { decodeOrNull<List<SavedDevice>>(it) }.orEmpty(),
                selectedSerial = prefs[SELECTED],
                settings = prefs[SETTINGS]?.let { decodeOrNull<Settings>(it) } ?: Settings(),
            )
        }
        .distinctUntilChanged()

    /** Adds [device] or refreshes its name, address and MACs, keeping the user's favorites. */
    suspend fun upsert(device: SavedDevice) = editDevices { devices ->
        val existing = devices.firstOrNull { it.serialNumber == device.serialNumber }
        val merged = existing?.let {
            device.copy(
                favorites = it.favorites,
                favoriteNames = it.favoriteNames,
                wakeMacs = device.wakeMacs.ifEmpty { it.wakeMacs },
            )
        } ?: device
        if (existing == null) devices + merged else devices.map { if (it.serialNumber == device.serialNumber) merged else it }
    }

    suspend fun select(serialNumber: String?) {
        dataStore.edit { prefs ->
            if (serialNumber == null) prefs.remove(SELECTED) else prefs[SELECTED] = serialNumber
        }
    }

    /** Removes the device; if it was selected, selects whichever remembered device is left first. */
    suspend fun forget(serialNumber: String) {
        dataStore.edit { prefs ->
            val remaining = prefs.devices().filterNot { it.serialNumber == serialNumber }
            prefs[DEVICES] = json.encodeToString(remaining)
            if (prefs[SELECTED] == serialNumber) {
                val next = remaining.firstOrNull()?.serialNumber
                if (next == null) prefs.remove(SELECTED) else prefs[SELECTED] = next
            }
        }
    }

    /** The TV moved (new DHCP lease); keep everything else. */
    suspend fun updateAddress(serialNumber: String, host: String, port: Int) = editDevices { devices ->
        devices.map { if (it.serialNumber == serialNumber) it.copy(host = host, port = port) else it }
    }

    /** Pins, unpins or reorders channels. [names] are channel names to remember along with them. */
    suspend fun setFavorites(serialNumber: String, favorites: List<String>, names: Map<String, String> = emptyMap()) =
        editDevices { devices ->
            devices.map {
                if (it.serialNumber == serialNumber) {
                    it.copy(favorites = favorites, favoriteNames = (it.favoriteNames + names).filterKeys { id -> id in favorites })
                } else {
                    it
                }
            }
        }

    /** Updates the remembered names of the device's favorites from [names] (channel id to name). */
    suspend fun rememberFavoriteNames(serialNumber: String, names: Map<String, String>) = editDevices { devices ->
        devices.map {
            if (it.serialNumber == serialNumber) {
                it.copy(favoriteNames = it.favoriteNames + names.filterKeys { id -> id in it.favorites })
            } else {
                it
            }
        }
    }

    suspend fun updateSettings(transform: (Settings) -> Settings) {
        dataStore.edit { prefs ->
            val current = prefs[SETTINGS]?.let { decodeOrNull<Settings>(it) } ?: Settings()
            prefs[SETTINGS] = json.encodeToString(transform(current))
        }
    }

    private suspend fun editDevices(transform: (List<SavedDevice>) -> List<SavedDevice>) {
        dataStore.edit { prefs -> prefs[DEVICES] = json.encodeToString(transform(prefs.devices())) }
    }

    private fun Preferences.devices(): List<SavedDevice> =
        this[DEVICES]?.let { decodeOrNull<List<SavedDevice>>(it) }.orEmpty()

    // A corrupt or older-format value shouldn't brick the app; it just resets that piece.
    private inline fun <reified T> decodeOrNull(value: String): T? =
        runCatching { json.decodeFromString<T>(value) }.getOrNull()

    private companion object {
        val DEVICES = stringPreferencesKey("devices")
        val SELECTED = stringPreferencesKey("selected_serial")
        val SETTINGS = stringPreferencesKey("settings")
    }
}
