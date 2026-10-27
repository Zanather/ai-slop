package io.github.zanather.clicker.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.data.DeviceStore
import io.github.zanather.clicker.data.SavedDevice
import io.github.zanather.clicker.ecp.EcpClient
import io.github.zanather.clicker.session.FakeLan
import io.github.zanather.clicker.session.FakeRoku
import io.github.zanather.clicker.session.RokuSession
import io.github.zanather.clicker.session.SessionTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ClickerViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val roku = FakeRoku()
    private val server = MockWebServer()
    private val viewModels = ViewModelStore()
    private lateinit var scope: CoroutineScope
    private lateinit var store: DeviceStore

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
        server.dispatcher = roku
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store = DeviceStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
    }

    @After
    fun tearDown() {
        viewModels.clear()
        scope.cancel()
        server.close()
        Dispatchers.resetMain()
    }

    @Test
    fun pinningAChannelRemembersItsName() = runBlocking<Unit> {
        store.upsert(SavedDevice(roku.serial, "TV", "127.0.0.1", server.port))
        store.select(roku.serial)
        val ecp = EcpClient(EcpClient.defaultHttpClient())
        val session = RokuSession(scope, ecp, store, FakeLan(), SessionTiming(pollInterval = 30.seconds))
        session.onForeground()
        withTimeout(5.seconds) { session.state.first { it.apps.isNotEmpty() } }
        val viewModel = ViewModelProvider(viewModels, ClickerViewModel.factory(session, store, ecp))[ClickerViewModel::class.java]

        viewModel.toggleFavorite("837")

        val saved = withTimeout(5.seconds) { store.state.first { it.selected?.favorites == listOf("837") } }.selected
        assertThat(saved?.favoriteNames).containsExactly("837", "YouTube")
    }
}
