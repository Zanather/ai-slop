package io.github.zanather.clicker.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.data.DeviceStore
import io.github.zanather.clicker.data.SavedDevice
import io.github.zanather.clicker.ecp.EcpClient
import io.github.zanather.clicker.ecp.LiteralKey
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ecp.RokuAddress
import io.github.zanather.clicker.net.Lan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RokuSessionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val roku = FakeRoku()
    private val server = MockWebServer()
    private lateinit var scope: CoroutineScope
    private lateinit var store: DeviceStore

    private val timing = SessionTiming(
        pollInterval = 150.milliseconds,
        infoEveryTicks = 2,
        holdDelay = 150.milliseconds,
        holdInterval = 40.milliseconds,
        volumeHoldInterval = 40.milliseconds,
        literalGap = 1.milliseconds,
        ssdpTimeout = 500.milliseconds,
        wakeTimeout = 1.seconds,
        settleDelay = 50.milliseconds,
    )

    @Before
    fun setUp() {
        server.dispatcher = roku
        server.start()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store = DeviceStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "test.preferences_pb") })
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    @Test
    fun connectsOnForegroundAndLoadsTheTv() = runBlocking<Unit> {
        saveTv()
        val session = session()
        session.onForeground()

        val state = session.awaitState { it.link == Link.Connected && it.apps.isNotEmpty() && it.activeApp != null }

        assertThat(state.info?.name).isEqualTo("Living Room TV")
        assertThat(state.apps.map { it.id }).containsExactly("tvinput.hdmi1", "tvinput.dtv", "12", "837").inOrder()
        assertThat(state.activeApp?.app?.name).isEqualTo("Netflix")
    }

    @Test
    fun staysQuietInTheBackground() = runBlocking<Unit> {
        saveTv()
        val session = session()
        session.awaitState { it.device != null }
        delay(300)

        assertThat(session.state.value.link).isEqualTo(Link.Connecting)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun limitedModeBlocksThenClearsItselfWhenTheSettingChanges() = runBlocking<Unit> {
        roku.ecpMode = "limited"
        saveTv()
        val session = session()
        session.onForeground()

        session.awaitState { it.link == Link.Blocked(limitedMode = true) }
        assertThat(session.awaitState { it.appsError != null }.appsError).isEqualTo(AppsError.Blocked)

        roku.ecpMode = "enabled"
        session.awaitState { it.link == Link.Connected }
    }

    @Test
    fun aRejectedKeyOnOlderFirmwareBlocksAndAnAcceptedKeyUnblocks() = runBlocking<Unit> {
        roku.ecpMode = null
        roku.rejectKeys = true
        val session = connected()

        session.press(RemoteKey.Home)
        session.awaitState { it.link == Link.Blocked(limitedMode = true) }

        roku.rejectKeys = false
        session.press(RemoteKey.Home)
        session.awaitState { it.link == Link.Connected }
        assertThat(roku.keys).containsExactly("Home")
    }

    @Test
    fun checkAgainOnOlderFirmwareKeepsBlockedUntilTheTvAllowsKeys() = runBlocking<Unit> {
        roku.ecpMode = null
        roku.rejectKeys = true
        val session = connected()
        session.press(RemoteKey.Home)
        session.awaitState { it.link == Link.Blocked(limitedMode = true) }

        session.connect() // "Check again" with nothing changed on the TV.
        delay(600) // Several polls.
        assertThat(session.state.value.link).isEqualTo(Link.Blocked(limitedMode = true))

        roku.rejectKeys = false // The user switched the setting to Enabled.
        session.connect()
        session.awaitState { it.link == Link.Connected }
        assertThat(roku.keys).isEmpty()
    }

    @Test
    fun searchingWhileBlockedOnOlderFirmwareKeepsTheBlockedBanner() = runBlocking<Unit> {
        roku.ecpMode = null
        roku.rejectKeys = true
        val lan = FakeLan(ssdpReplies = listOf(ssdpReply(server.port, roku.serial)))
        val session = connected(lan = lan)
        session.press(RemoteKey.Home)
        session.awaitState { it.link == Link.Blocked(limitedMode = true) }

        val before = roku.deviceInfoRequests.get()
        roku.deviceInfoFailures.set(2) // A Wi-Fi blip: a poll and connect()'s retry both fail, so it searches.
        // SSDP finds the TV at the same address and reads its device-info. Polling resumes once that's handled.
        withTimeout(5.seconds) { while (roku.deviceInfoRequests.get() < before + 5) delay(10) }

        assertThat(lan.sent.any { String(it.data, 0, it.length).startsWith("M-SEARCH") }).isTrue()
        assertThat(session.state.value.link).isEqualTo(Link.Blocked(limitedMode = true))
    }

    @Test
    fun keysGoOutBeforeTheFirstConnectFinishes() = runBlocking<Unit> {
        saveTv()
        val session = session() // In the background, so nothing has probed the TV.
        session.awaitState { it.device != null }

        session.press(RemoteKey.Home)

        awaitKeys(1)
        assertThat(roku.keys).containsExactly("Home")
    }

    @Test
    fun pressesArriveInOrder() = runBlocking<Unit> {
        val session = connected()

        listOf(RemoteKey.Down, RemoteKey.Down, RemoteKey.Right, RemoteKey.Select).forEach(session::press)

        awaitKeys(4)
        assertThat(roku.keys).containsExactly("Down", "Down", "Right", "Select").inOrder()
    }

    @Test
    fun holdingRepeatsUntilReleasedAndStopsAfter() = runBlocking<Unit> {
        val session = connected()

        session.holdStart(RemoteKey.VolumeUp)
        delay(400)
        session.holdEnd(RemoteKey.VolumeUp)
        delay(100)
        val afterRelease = roku.keys.size
        delay(300)

        assertThat(afterRelease).isAtLeast(3)
        assertThat(roku.keys.toSet()).containsExactly("VolumeUp")
        assertThat(roku.keys.size).isEqualTo(afterRelease)
    }

    @Test
    fun aQuickTapOnARepeatingKeySendsExactlyOnePress() = runBlocking<Unit> {
        val session = connected()

        session.holdStart(RemoteKey.Up)
        delay(30)
        session.holdEnd(RemoteKey.Up)
        delay(400)

        assertThat(roku.keys).containsExactly("Up")
    }

    @Test
    fun aVolumeHoldAndADPadHoldDontEndEachOther() = runBlocking<Unit> {
        val session = connected()

        session.holdStart(RemoteKey.Down) // A finger on the D-pad.
        session.holdStart(RemoteKey.VolumeUp) // The phone's volume button, held meanwhile.
        delay(400)
        session.holdEnd(RemoteKey.VolumeUp)
        delay(100)
        val volumeUps = roku.keys.count { it == "VolumeUp" }
        val downs = roku.keys.count { it == "Down" }
        delay(300)
        session.holdEnd(RemoteKey.Down)

        assertThat(volumeUps).isAtLeast(3)
        assertThat(roku.keys.count { it == "VolumeUp" }).isEqualTo(volumeUps)
        assertThat(roku.keys.count { it == "Down" }).isGreaterThan(downs)
    }

    @Test
    fun releasingAKeyWhoseHoldWasTakenOverDoesntEndTheNewHold() = runBlocking<Unit> {
        val session = connected()

        session.holdStart(RemoteKey.Up)
        session.holdStart(RemoteKey.Down) // A second finger takes over.
        session.holdEnd(RemoteKey.Up) // The first one lifts.
        delay(400)
        session.holdEnd(RemoteKey.Down)

        assertThat(roku.keys.count { it == "Down" }).isAtLeast(3)
    }

    @Test
    fun quickTapsOnARepeatingKeyAreNeverDroppedBehindASlowPress() = runBlocking<Unit> {
        val session = connected()
        roku.keyDelayMs = 200

        session.press(RemoteKey.Home)
        delay(20)
        session.holdStart(RemoteKey.Down)
        delay(60)
        session.holdEnd(RemoteKey.Down) // Home is still in flight.
        session.holdStart(RemoteKey.Down)
        session.holdEnd(RemoteKey.Down) // Released before the hold even starts.
        session.press(RemoteKey.Select)

        awaitKeys(4)
        delay(300)
        assertThat(roku.keys).containsExactly("Home", "Down", "Down", "Select").inOrder()
    }

    @Test
    fun pressesQueuedBehindAStalledOneAreDroppedNotReplayedLate() = runBlocking<Unit> {
        val session = connected()
        roku.keyDelayMs = 3_000 // Past the read timeout: the TV hangs.

        repeat(4) { session.press(RemoteKey.Down) }
        awaitKeys(1)
        roku.keyDelayMs = 0
        delay(3_500)

        assertThat(roku.keys).containsExactly("Down")
    }

    @Test
    fun aFailureAtTheOldAddressDoesntDropAPressMadeAtTheNewOne() = runBlocking<Unit> {
        // The old address accepts connections but never answers, so requests there time out.
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { old ->
            saveTv(port = old.localPort)
            val session = session(FakeLan(ssdpReplies = listOf(ssdpReply(server.port, roku.serial))))
            session.onForeground() // Its device-info times out at 2.5 s; then SSDP finds the TV at the new address.
            delay(1_000)
            session.press(RemoteKey.Down) // Times out at the old address at about 3.5 s.

            session.awaitState { it.link == Link.Connected && it.device?.port == server.port }
            session.press(RemoteKey.Select) // Queued behind Down.

            withTimeout(4.seconds) { while ("Select" !in roku.keys) delay(10) }
        }
    }

    @Test
    fun powerFollowsTheTvsPowerState() = runBlocking<Unit> {
        val session = connected()

        session.press(RemoteKey.Power)
        awaitKeys(1)
        assertThat(roku.keys.last()).isEqualTo("PowerOff")

        session.awaitState { it.info?.powerMode?.isScreenOn == false }
        session.press(RemoteKey.Power)
        awaitKeys(2)
        assertThat(roku.keys.last()).isEqualTo("PowerOn")
    }

    @Test
    fun aKeyRightAfterPowerDoesntCancelThePowerStateRefresh() = runBlocking<Unit> {
        roku.powerMode = "Ready"
        // Polls too rarely to refresh the power state on their own.
        val session = connected(timing.copy(pollInterval = 30.seconds, settleDelay = 300.milliseconds))

        session.press(RemoteKey.Power)
        session.press(RemoteKey.Home)

        awaitKeys(2)
        assertThat(roku.keys).containsExactly("PowerOn", "Home").inOrder()
        session.awaitState(2.seconds) { it.info?.powerMode?.isScreenOn == true }
    }

    @Test
    fun typedTextArrivesInOrderAndEncoded() = runBlocking<Unit> {
        val session = connected()

        session.sendKeys(LiteralKey.of("hi you"))
        session.sendKeys(listOf(RemoteKey.Backspace) + LiteralKey.of("!"))

        awaitKeys(8)
        assertThat(roku.keys).containsExactly(
            "Lit_h", "Lit_i", "Lit_%20", "Lit_y", "Lit_o", "Lit_u", "Backspace", "Lit_%21",
        ).inOrder()
    }

    @Test
    fun launchOptimisticallyUpdatesTheActiveApp() = runBlocking<Unit> {
        val session = connected()

        session.launch("837")

        session.awaitState { it.activeApp?.app?.id == "837" }
        withTimeout(2.seconds) { while (roku.launches.isEmpty()) delay(10) }
        assertThat(roku.launches).containsExactly("837")
    }

    @Test
    fun findsTheTvAgainAfterItsAddressChanges() = runBlocking<Unit> {
        val deadPort = deadPort()
        saveTv(port = deadPort)
        val session = session(FakeLan(ssdpReplies = listOf(ssdpReply(server.port, roku.serial))))
        session.onForeground()

        session.awaitState { it.link == Link.Connected && it.device?.port == server.port }
        assertThat(store.state.first().selected?.port).isEqualTo(server.port)
    }

    @Test
    fun ignoresAnotherRokuThatAnswersDiscovery() = runBlocking<Unit> {
        saveTv(port = deadPort(), serial = "SOMEONE-ELSE")
        val session = session(FakeLan(ssdpReplies = listOf(ssdpReply(server.port, roku.serial))))
        session.onForeground()

        session.awaitState { it.link == Link.Unreachable }
        assertThat(store.state.first().selected?.port).isNotEqualTo(server.port)
    }

    @Test
    fun keysNeverReachAnotherTvThatTookTheSavedAddress() = runBlocking<Unit> {
        roku.powerMode = "Ready"
        saveTv(serial = "MY-OTHER-TV") // The FakeRoku at the saved address is a different TV.
        val lan = FakeLan()
        val session = session(lan)
        session.onForeground()
        session.awaitState { it.link == Link.Unreachable }

        session.press(RemoteKey.VolumeUp)
        session.launch("837")
        session.sendKeys(LiteralKey.of("a"))
        session.press(RemoteKey.Power)
        session.awaitState { it.link == Link.Waking }
        session.awaitState(5.seconds) { it.link == Link.Unreachable }

        assertThat(roku.keys).isEmpty()
        assertThat(roku.launches).isEmpty()
        assertThat(roku.powerMode).isEqualTo("Ready")
        assertThat(lan.sent.filter { it.length == 102 }).isNotEmpty() // Ours still gets Wake-on-LAN.
    }

    @Test
    fun wakeSendsMagicPacketsThenGivesUp() = runBlocking<Unit> {
        saveTv(port = deadPort())
        val lan = FakeLan()
        val session = session(lan)
        session.onForeground()
        session.awaitState { it.link == Link.Unreachable }

        session.wake()
        session.awaitState { it.link == Link.Waking }
        session.awaitState(5.seconds) { it.link == Link.Unreachable }

        val magic = lan.sent.filter { it.length == 102 }
        assertThat(magic).isNotEmpty()
        assertThat(magic.first().data.copyOfRange(0, 6).all { it == 0xFF.toByte() }).isTrue()
        assertThat(magic.first().data.copyOfRange(6, 12).map { it.toInt() and 0xFF })
            .containsExactly(0x64, 0xE0, 0x03, 0xD4, 0x56, 0x41).inOrder()
    }

    @Test
    fun powerWhileTheTvIsMissingWakesInsteadOfSendingAKey() = runBlocking<Unit> {
        saveTv(port = deadPort())
        val lan = FakeLan()
        val session = session(lan)
        session.onForeground()
        session.awaitState { it.link == Link.Unreachable }

        session.press(RemoteKey.Power)

        session.awaitState { it.link == Link.Waking }
    }

    @Test
    fun powerThatCantReachTheTvWakesIt() = runBlocking<Unit> {
        saveTv(port = deadPort())
        val lan = FakeLan()
        val session = session(lan) // In the background, so the session doesn't know yet that the TV is gone.
        session.awaitState { it.device != null }

        session.press(RemoteKey.Power)

        session.awaitState { it.link == Link.Waking }
        withTimeout(3.seconds) { while (lan.sent.none { it.length == 102 }) delay(10) }
    }

    @Test
    fun aPowerOffTheTvTookButAnsweredLateIsntUndoneByAWakeUp() = runBlocking<Unit> {
        val session = connected()
        roku.keyDelayMs = 3_000 // Takes the key, then answers after the read timeout.

        session.press(RemoteKey.Power)
        awaitKeys(1)
        roku.keyDelayMs = 0
        delay(4_000)

        assertThat(roku.keys).containsExactly("PowerOff")
    }

    @Test
    fun powerDuringTheFirstConnectDoesntStallFindingTheTvAtItsNewAddress() = runBlocking<Unit> {
        saveTv(port = deadPort()) // The router gave the TV a new IP.
        // The SSDP reply comes after the Power tap has failed at the old address.
        val lan = FakeLan(ssdpReplies = listOf(ssdpReply(server.port, roku.serial)), ssdpDelayMs = 400)
        val session = session(lan, timing.copy(wakeTimeout = 20.seconds))
        session.awaitState { it.device != null }

        session.onForeground()
        session.press(RemoteKey.Power)

        session.awaitState { it.link == Link.Connected && it.device?.port == server.port }
        assertThat(lan.sent.filter { it.length == 102 }).isNotEmpty() // In case it's asleep there.
    }

    @Test
    fun widgetPressWorksWithoutTheAppInTheForeground() = runBlocking<Unit> {
        saveTv()
        val session = session()

        assertThat(session.pressFromOutside(RemoteKey.Play)).isTrue()
        assertThat(roku.keys).containsExactly("Play")
    }

    @Test
    fun widgetPowerTogglesFromTheLiveState() = runBlocking<Unit> {
        saveTv()
        val session = session()

        assertThat(session.pressFromOutside(RemoteKey.Power)).isTrue()
        assertThat(roku.keys).containsExactly("PowerOff")
    }

    @Test
    fun widgetPressesNeverReachAnotherTvAtTheSavedAddress() = runBlocking<Unit> {
        saveTv(serial = "MY-OTHER-TV")
        val lan = FakeLan()
        val session = session(lan)

        assertThat(session.pressFromOutside(RemoteKey.Power)).isFalse()
        assertThat(session.pressFromOutside(RemoteKey.Play)).isFalse()

        assertThat(roku.keys).isEmpty()
        assertThat(lan.sent.filter { it.length == 102 }).isNotEmpty() // Ours may just be asleep.
    }

    @Test
    fun remembersFavoriteNamesWhenTheChannelListLoads() = runBlocking<Unit> {
        saveTv()
        store.setFavorites(roku.serial, listOf("837", "12"))
        session().onForeground()

        val saved = withTimeout(5.seconds) { store.state.first { it.selected?.favoriteNames?.size == 2 } }.selected
        assertThat(saved?.favoriteNames).containsExactly("837", "YouTube", "12", "Netflix")
    }

    @Test
    fun choosingADiscoveredTvSavesAndSelectsIt() = runBlocking<Unit> {
        val session = session()
        val found = session.probe("127.0.0.1") // nothing on :8060 here
        assertThat(found).isNull()

        val address = RokuAddress("127.0.0.1", server.port)
        session.choose(FoundRoku(address, EcpClient(EcpClient.defaultHttpClient()).deviceInfo(address)))

        val saved = store.state.first().selected
        assertThat(saved?.serialNumber).isEqualTo(roku.serial)
        assertThat(saved?.name).isEqualTo("Living Room TV")
        assertThat(saved?.wakeMacs).containsExactly("64:e0:03:d4:56:41")
    }

    // region helpers

    private fun session(lan: Lan = FakeLan(), timing: SessionTiming = this.timing) =
        RokuSession(scope, EcpClient(EcpClient.defaultHttpClient()), store, lan, timing)

    private suspend fun connected(timing: SessionTiming = this.timing, lan: Lan = FakeLan()): RokuSession {
        saveTv()
        return session(lan, timing).also {
            it.onForeground()
            it.awaitState { s -> s.info != null && s.link != Link.Connecting }
        }
    }

    private suspend fun saveTv(port: Int = server.port, serial: String = roku.serial) {
        store.upsert(SavedDevice(serial, "TV", "127.0.0.1", port, isTv = true, wakeMacs = listOf("64:e0:03:d4:56:41")))
        store.select(serial)
    }

    private suspend fun RokuSession.awaitState(timeout: Duration = 5.seconds, predicate: (SessionState) -> Boolean): SessionState =
        withTimeout(timeout) { state.first(predicate) }

    private suspend fun awaitKeys(count: Int) = withTimeout(3.seconds) {
        while (roku.keys.size < count) delay(10)
    }

    private fun deadPort(): Int = MockWebServer().run {
        start()
        val port = this.port
        close()
        port
    }

    // endregion
}
