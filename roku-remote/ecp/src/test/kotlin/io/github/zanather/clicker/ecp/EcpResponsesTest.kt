package io.github.zanather.clicker.ecp

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress

/** Parsing of real-world ECP responses, end to end through [EcpClient]. */
class EcpResponsesTest {
    private val server = MockWebServer()
    private val http = EcpClient.defaultHttpClient()
    private val client = EcpClient(http)
    private lateinit var address: RokuAddress

    @Before
    fun setUp() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        address = RokuAddress("127.0.0.1", server.port)
    }

    @After
    fun tearDown() {
        server.close()
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    private fun respond(body: String) {
        // Roku quotes the charset, which some parsers trip over.
        server.enqueue(MockResponse(code = 200, headers = headersOf("Content-Type", "text/xml; charset=\"utf-8\""), body = body))
    }

    private suspend fun deviceInfo(xml: String): DeviceInfo {
        respond(xml)
        return client.deviceInfo(address)
    }

    private fun badDeviceInfo(xml: String): EcpException.BadResponse {
        respond(xml)
        return assertThrows(EcpException.BadResponse::class.java) { runBlocking { client.deviceInfo(address) } }
    }

    @Test
    fun `device-info from a Hisense Roku TV`() = runTest {
        val info = deviceInfo(HISENSE_DEVICE_INFO)
        assertThat(info.serialNumber).isEqualTo("X01900SGN8UY")
        assertThat(info.deviceId).isEqualTo("S0A3619GN8UY")
        // user-device-name is the empty <user-device-name />, so the friendly name wins.
        assertThat(info.name).isEqualTo("Hisense•Roku TV - X01900SGN8UY")
        assertThat(info.vendorName).isEqualTo("Hisense")
        assertThat(info.modelName).isEqualTo("6Series-50")
        assertThat(info.modelNumber).isEqualTo("G218X")
        assertThat(info.isTv).isTrue()
        assertThat(info.powerMode).isEqualTo(PowerMode.On)
        assertThat(info.ecpMode).isEqualTo(EcpMode.Unknown)
        assertThat(info.softwareVersion).isEqualTo("12.5.5")
        assertThat(info.softwareBuild).isEqualTo("4174")
        assertThat(info.wifiMac).isEqualTo("80:cb:bc:98:79:0a")
        assertThat(info.ethernetMac).isEqualTo("a0:62:fb:78:29:ee")
        assertThat(info.networkType).isEqualTo("wifi")
        assertThat(info.uptimeSeconds).isNull()
        assertThat(info.supportsFindRemote).isFalse()
        assertThat(info.supportsWakeOnWlan).isTrue()
        assertThat(info.developerEnabled).isFalse()
        assertThat(info.wakeMacs).containsExactly("80:cb:bc:98:79:0a", "a0:62:fb:78:29:ee").inOrder()

        assertThat(info.fields).hasSize(29)
        assertThat(info.fields.keys.take(4)).containsExactly("udn", "serial-number", "device-id", "vendor-name").inOrder()
        assertThat(info.fields.keys.last()).isEqualTo("supports-airplay")
        assertThat(info.fields).containsEntry("user-device-name", "")
        assertThat(info.fields).containsEntry("screen-size", "50")
        assertThat(info.fields).containsEntry("supports-ecs-textedit", "true")
    }

    @Test
    fun `device-info from Roku's documentation`() = runTest {
        val info = deviceInfo(ROKU3_DEVICE_INFO)
        assertThat(info.serialNumber).isEqualTo("1GU48T017973")
        assertThat(info.name).isEqualTo("My Roku 3")
        assertThat(info.isTv).isFalse()
        assertThat(info.developerEnabled).isTrue()
        assertThat(info.networkType).isEqualTo("ethernet")
        // The active interface comes first.
        assertThat(info.wakeMacs).containsExactly("b0:a7:37:96:4d:fa", "b0:a7:37:96:4d:fb").inOrder()
        assertThat(info.fields).containsEntry("locale", "en_US")
        assertThat(info.fields).containsEntry("time-zone-offset", "-480")
        assertThat(info.fields.keys.first()).isEqualTo("udn")
        assertThat(info.fields.keys.last()).isEqualTo("headphones-connected")
    }

    @Test
    fun `name falls back through user, friendly, default and model names, skipping blanks`() = runTest {
        val serial = "serial-number" to "S1"
        assertThat(
            deviceInfo(deviceInfoXml(serial, "user-device-name" to "Living room", "friendly-device-name" to "F", "model-name" to "M")).name,
        ).isEqualTo("Living room")
        assertThat(deviceInfo(deviceInfoXml(serial, "user-device-name" to null, "friendly-device-name" to "F")).name).isEqualTo("F")
        assertThat(
            deviceInfo(deviceInfoXml(serial, "user-device-name" to "   ", "friendly-device-name" to "", "default-device-name" to "D", "model-name" to "M")).name,
        ).isEqualTo("D")
        assertThat(deviceInfo(deviceInfoXml(serial, "user-device-name" to null, "model-name" to "Roku Ultra")).name).isEqualTo("Roku Ultra")
        assertThat(deviceInfo(deviceInfoXml(serial)).name).isEqualTo("Roku S1")
    }

    @Test
    fun `ecp-setting-mode maps to EcpMode`() = runTest {
        val serial = "serial-number" to "S1"
        assertThat(deviceInfo(deviceInfoXml(serial, "ecp-setting-mode" to "limited")).ecpMode).isEqualTo(EcpMode.Limited)
        assertThat(deviceInfo(deviceInfoXml(serial, "ecp-setting-mode" to "enabled")).ecpMode).isEqualTo(EcpMode.Enabled)
        assertThat(deviceInfo(deviceInfoXml(serial, "ecp-setting-mode" to "permissive")).ecpMode).isEqualTo(EcpMode.Permissive)
        assertThat(deviceInfo(deviceInfoXml(serial, "ecp-setting-mode" to "disabled")).ecpMode).isEqualTo(EcpMode.Disabled)
        assertThat(deviceInfo(deviceInfoXml(serial, "ecp-setting-mode" to "Limited")).ecpMode).isEqualTo(EcpMode.Limited)
        assertThat(deviceInfo(deviceInfoXml(serial, "ecp-setting-mode" to "something-new")).ecpMode).isEqualTo(EcpMode.Unknown)
        assertThat(deviceInfo(deviceInfoXml(serial)).ecpMode).isEqualTo(EcpMode.Unknown)
    }

    @Test
    fun `power-mode maps to PowerMode, unknown values included`() = runTest {
        val expected = mapOf(
            "PowerOn" to PowerMode.On,
            "DisplayOff" to PowerMode.DisplayOff,
            "Ready" to PowerMode.Ready,
            "Suspend" to PowerMode.Suspend,
            "PowerOff" to PowerMode.PowerOff,
            "Headless" to PowerMode.Headless,
            "poweron" to PowerMode.On,
            "Hibernating" to PowerMode.Unknown,
            "" to PowerMode.Unknown,
        )
        for ((value, mode) in expected) {
            assertThat(deviceInfo(deviceInfoXml("serial-number" to "S1", "power-mode" to value)).powerMode).isEqualTo(mode)
        }
        assertThat(deviceInfo(deviceInfoXml("serial-number" to "S1")).powerMode).isEqualTo(PowerMode.Unknown)
    }

    @Test
    fun `uptime, booleans and wake MACs`() = runTest {
        val info = deviceInfo(
            deviceInfoXml(
                "serial-number" to " S1 ",
                "uptime" to "  86400 ",
                "is-tv" to "TRUE",
                "supports-find-remote" to "True",
                "supports-wake-on-wlan" to "yes",
                "developer-enabled" to "false",
                "wifi-mac" to "aa:aa:aa:aa:aa:aa",
                "ethernet-mac" to "bb:bb:bb:bb:bb:bb",
                "network-type" to "ethernet",
            ),
        )
        assertThat(info.serialNumber).isEqualTo("S1")
        assertThat(info.uptimeSeconds).isEqualTo(86400L)
        assertThat(info.isTv).isTrue()
        assertThat(info.supportsFindRemote).isTrue()
        assertThat(info.supportsWakeOnWlan).isFalse()
        assertThat(info.developerEnabled).isFalse()
        assertThat(info.wakeMacs).containsExactly("bb:bb:bb:bb:bb:bb", "aa:aa:aa:aa:aa:aa").inOrder()

        assertThat(deviceInfo(deviceInfoXml("serial-number" to "S1", "uptime" to "soon")).uptimeSeconds).isNull()
        val wifiOnly = deviceInfo(deviceInfoXml("serial-number" to "S1", "wifi-mac" to "aa:aa:aa:aa:aa:aa", "ethernet-mac" to null, "network-type" to "ethernet"))
        assertThat(wifiOnly.ethernetMac).isNull()
        assertThat(wifiOnly.wakeMacs).containsExactly("aa:aa:aa:aa:aa:aa")
    }

    @Test
    fun `device-info without a serial number is BadResponse`() {
        badDeviceInfo(deviceInfoXml("model-name" to "Roku 3"))
        badDeviceInfo(deviceInfoXml("serial-number" to null))
    }

    @Test
    fun `device-info with an external entity is refused without reading the file`() {
        val secret = File.createTempFile("ecp-xxe", ".txt").apply {
            writeText("TOP-SECRET-${System.nanoTime()}")
            deleteOnExit()
        }
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE device-info [ <!ENTITY xxe SYSTEM "${secret.toURI()}"> ]>
            |<device-info><serial-number>&xxe;</serial-number><user-device-name>&xxe;</user-device-name></device-info>
        """.trimMargin()

        val e = badDeviceInfo(xml)
        val messages = generateSequence<Throwable>(e) { it.cause }.joinToString("\n") { "$it" }
        assertThat(messages).doesNotContain(secret.readText())
    }

    @Test
    fun `the parser itself rejects DTDs even without the pre-check`() {
        val secret = File.createTempFile("ecp-xxe", ".txt").apply {
            writeText("TOP-SECRET-${System.nanoTime()}")
            deleteOnExit()
        }
        val xml = """<!DOCTYPE device-info [ <!ENTITY xxe SYSTEM "${secret.toURI()}"> ]><device-info><serial-number>&xxe;</serial-number></device-info>"""
        val e = assertThrows(Exception::class.java) { parseHardened(xml) }
        assertThat("$e").doesNotContain(secret.readText())
    }

    @Test
    fun `internal entity expansion is refused`() {
        val laughs = """<?xml version="1.0"?>
            |<!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">]>
            |<device-info><serial-number>&lol2;</serial-number></device-info>
        """.trimMargin()
        badDeviceInfo(laughs)
    }

    @Test
    fun `a deeply nested document is BadResponse rather than a stack overflow`() {
        val depth = 100_000 // Well under the size cap.
        val nested = "<a>".repeat(depth) + "</a>".repeat(depth)
        badDeviceInfo("<device-info><serial-number>X</serial-number><x>$nested</x></device-info>")
    }

    @Test
    fun `markup inside comments, CDATA, instructions and attributes can't hide nesting`() {
        val hiders = listOf("<!-- </a> -->", "<![CDATA[</a>]]>", "<?pi </a>?>")
        for (hider in hiders) {
            badDeviceInfo("<device-info><serial-number>X</serial-number>" + "<a>$hider".repeat(40) + "</a>".repeat(40) + "</device-info>")
        }
        badDeviceInfo("<device-info><serial-number>X</serial-number>" + "<a b=\"/\">".repeat(40) + "</a>".repeat(40) + "</device-info>")
    }

    @Test
    fun `wide documents with self-closing tags and markup in attributes aren't deep`() {
        val xml = "<apps>" + "<app id=\"1\" note=\"a>b\"/>".repeat(100) + "<!-- <a><a> -->".repeat(50) +
            "<app id=\"2\">Two</app>".repeat(100) + "</apps>"
        assertThat(parseApps(xml)).hasSize(200)
    }

    @Test
    fun `a byte order mark and leading whitespace are tolerated`() = runTest {
        assertThat(deviceInfo("﻿\n  " + deviceInfoXml("serial-number" to "S1")).serialNumber).isEqualTo("S1")
    }

    @Test
    fun `apps keeps Roku's order, normalizes non-breaking spaces and keeps types`() = runTest {
        respond(
            """<?xml version="1.0" encoding="UTF-8" ?>
            |<apps>
            |	<app id="tvinput.hdmi1" type="tvin" version="1.0.0">HDMI${' '}1</app>
            |	<app id="tvinput.dtv" type="tvin" version="1.0.0">Live${' '}TV</app>
            |	<app id="12" type="appl" version="5.1.2">Netflix</app>
            |	<app id="2285" type="appl" version="6.71.0">  Hulu  </app>
            |	<app id="552944" type="appl" version="1.2.45">AT&amp;T&#160;TV</app>
            |	<app type="appl">No id, can't launch</app>
            |	<app id="tvinput.av1" type="tvin" version="1.0.0">AV</app>
            |</apps>
            """.trimMargin(),
        )
        val apps = client.apps(address)
        assertThat(apps).containsExactly(
            RokuApp("tvinput.hdmi1", "HDMI 1", "tvin", "1.0.0"),
            RokuApp("tvinput.dtv", "Live TV", "tvin", "1.0.0"),
            RokuApp("12", "Netflix", "appl", "5.1.2"),
            RokuApp("2285", "Hulu", "appl", "6.71.0"),
            RokuApp("552944", "AT&T TV", "appl", "1.2.45"),
            RokuApp("tvinput.av1", "AV", "tvin", "1.0.0"),
        ).inOrder()
        assertThat(apps.filter { it.isTvInput }.map { it.id }).containsExactly("tvinput.hdmi1", "tvinput.dtv", "tvinput.av1").inOrder()
    }

    @Test
    fun `apps with no attributes beyond id`() = runTest {
        respond("<apps><app id=\"dev\">My dev channel</app></apps>")
        assertThat(client.apps(address)).containsExactly(RokuApp("dev", "My dev channel", null, null))
        respond("<apps/>")
        assertThat(client.apps(address)).isEmpty()
    }

    @Test
    fun `active-app home screen variants all map to a null app`() = runTest {
        val homes = listOf(
            "<active-app><app>Roku</app></active-app>",
            """<active-app><app id="562859" type="appl" version="1.0.80000286" ui-location="home">Roku Dynamic Menu</app></active-app>""",
            """<active-app><app id="562859" type="home" version="1.0">Home</app></active-app>""",
            "<active-app/>",
        )
        for (xml in homes) {
            respond("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n$xml")
            val active = client.activeApp(address)
            assertThat(active.app).isNull()
            assertThat(active.isHome).isTrue()
            assertThat(active.screensaver).isNull()
        }
    }

    @Test
    fun `active-app with a channel running`() = runTest {
        respond("""<?xml version="1.0" encoding="UTF-8" ?><active-app><app id="12" type="appl" version="5.1.2">Netflix</app></active-app>""")
        val active = client.activeApp(address)
        assertThat(active.app).isEqualTo(RokuApp("12", "Netflix", "appl", "5.1.2"))
        assertThat(active.isHome).isFalse()
        assertThat(active.screensaver).isNull()
    }

    @Test
    fun `active-app with a screensaver over an app or over home`() = runTest {
        respond(
            """<active-app>
            |  <app id="12" type="appl" version="5.1.2">Netflix</app>
            |  <screensaver id="55545" type="ssvr" version="2.0.1">Default screensaver</screensaver>
            |</active-app>
            """.trimMargin(),
        )
        val overApp = client.activeApp(address)
        assertThat(overApp.app).isEqualTo(RokuApp("12", "Netflix", "appl", "5.1.2"))
        assertThat(overApp.screensaver).isEqualTo(RokuApp("55545", "Default screensaver", "ssvr", "2.0.1"))

        respond("""<active-app><app>Roku</app><screensaver id="55545" type="ssvr" version="2.0.1">Default screensaver</screensaver></active-app>""")
        val overHome = client.activeApp(address)
        assertThat(overHome.isHome).isTrue()
        assertThat(overHome.screensaver?.id).isEqualTo("55545")
    }

    @Test
    fun `TV input apps report isTvInput`() = runTest {
        respond("""<active-app><app id="tvinput.hdmi2" type="tvin" version="1.0.0">HDMI${' '}2</app></active-app>""")
        val app = client.activeApp(address).app
        assertThat(app?.name).isEqualTo("HDMI 2")
        assertThat(app?.isTvInput).isTrue()
    }
}
