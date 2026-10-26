package io.github.zanather.clicker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeviceStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.newStore() = PreferenceDataStoreFactory.create(scope = backgroundScope) {
        File(tmp.root, "store.preferences_pb")
    }.let { it to DeviceStore(it) }

    private val tv = SavedDevice("SERIAL1", "Living Room", "192.168.8.198", isTv = true, wakeMacs = listOf("64:e0:03:d4:56:41"))
    private val stick = SavedDevice("SERIAL2", "Bedroom", "192.168.8.40", isTv = false)

    @Test
    fun startsEmptyWithDefaults() = runTest {
        val (_, store) = newStore()

        val state = store.state.first()

        assertThat(state.devices).isEmpty()
        assertThat(state.selected).isNull()
        assertThat(state.settings).isEqualTo(Settings())
    }

    @Test
    fun upsertRefreshesTheDeviceButKeepsFavoritesAndKnownMacs() = runTest {
        val (_, store) = newStore()
        store.upsert(tv)
        store.setFavorites("SERIAL1", listOf("12", "837"))

        store.upsert(tv.copy(name = "Den", host = "192.168.8.199", wakeMacs = emptyList()))

        val saved = store.state.first().devices.single()
        assertThat(saved.name).isEqualTo("Den")
        assertThat(saved.host).isEqualTo("192.168.8.199")
        assertThat(saved.favorites).containsExactly("12", "837").inOrder()
        assertThat(saved.wakeMacs).containsExactly("64:e0:03:d4:56:41")
    }

    @Test
    fun forgettingTheSelectedTvSelectsTheNextOne() = runTest {
        val (_, store) = newStore()
        store.upsert(tv)
        store.upsert(stick)
        store.select("SERIAL1")

        store.forget("SERIAL1")

        val state = store.state.first()
        assertThat(state.devices.map { it.serialNumber }).containsExactly("SERIAL2")
        assertThat(state.selected?.serialNumber).isEqualTo("SERIAL2")
    }

    @Test
    fun forgettingTheLastTvClearsTheSelection() = runTest {
        val (_, store) = newStore()
        store.upsert(tv)
        store.select("SERIAL1")

        store.forget("SERIAL1")

        assertThat(store.state.first().selectedSerial).isNull()
    }

    @Test
    fun updateAddressOnlyTouchesTheAddress() = runTest {
        val (_, store) = newStore()
        store.upsert(tv)
        store.setFavorites("SERIAL1", listOf("12"))

        store.updateAddress("SERIAL1", "192.168.8.77", 8061)

        assertThat(store.state.first().devices.single())
            .isEqualTo(tv.copy(host = "192.168.8.77", port = 8061, favorites = listOf("12")))
    }

    @Test
    fun settingsRoundTrip() = runTest {
        val (_, store) = newStore()

        store.updateSettings { it.copy(hapticsEnabled = false, touchpadMode = true) }

        assertThat(store.state.first().settings).isEqualTo(Settings(hapticsEnabled = false, touchpadMode = true))
    }

    @Test
    fun corruptDataResetsInsteadOfCrashing() = runTest {
        val (dataStore, store) = newStore()
        store.upsert(tv)
        store.select("SERIAL1")

        dataStore.edit {
            it[stringPreferencesKey("devices")] = "{not json"
            it[stringPreferencesKey("settings")] = "[]"
        }

        val state = store.state.first()
        assertThat(state.devices).isEmpty()
        assertThat(state.selected).isNull()
        assertThat(state.settings).isEqualTo(Settings())
    }
}
