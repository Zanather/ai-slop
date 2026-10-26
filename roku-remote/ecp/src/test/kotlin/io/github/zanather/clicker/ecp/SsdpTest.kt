package io.github.zanather.clicker.ecp

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class SsdpTest {
    private val officialResponse = "HTTP/1.1 200 OK\r\n" +
        "Cache-Control: max-age=3600\r\n" +
        "ST: roku:ecp\r\n" +
        "Location: http://192.168.1.134:8060/\r\n" +
        "USN: uuid:roku:ecp:P0A070000007\r\n" +
        "\r\n"

    private fun response(serial: String, ip: String) = "HTTP/1.1 200 OK\r\n" +
        "Cache-Control: max-age=3600\r\n" +
        "ST: roku:ecp\r\n" +
        "Location: http://$ip:8060/\r\n" +
        "USN: uuid:roku:ecp:$serial\r\n" +
        "Server: Roku/15.3.4 UPnP/1.0 Roku/15.3.4\r\n" +
        "\r\n"

    @Test
    fun `searchRequest is the exact M-SEARCH datagram`() {
        assertThat(Ssdp.searchRequest(2)).isEqualTo(
            "M-SEARCH * HTTP/1.1\r\nHost: 239.255.255.250:1900\r\nMan: \"ssdp:discover\"\r\nST: roku:ecp\r\nMX: 2\r\n\r\n",
        )
        assertThat(Ssdp.searchRequest()).isEqualTo(Ssdp.searchRequest(2))
        assertThat(Ssdp.searchRequest(5)).contains("\r\nMX: 5\r\n\r\n")
    }

    @Test
    fun `parses Roku's documented response`() {
        assertThat(Ssdp.parseResponse(officialResponse, "10.9.9.9"))
            .isEqualTo(DiscoveredRoku(RokuAddress("192.168.1.134", 8060), "P0A070000007"))
    }

    @Test
    fun `header names are case-insensitive`() {
        val datagram = "HTTP/1.1 200 OK\r\ncache-control: max-age=3600\r\nst: roku:ecp\r\n" +
            "LOCATION: http://192.168.1.20:8060/\r\nusn: uuid:roku:ecp:YK00AB123456\r\n\r\n"
        assertThat(Ssdp.parseResponse(datagram, "10.9.9.9"))
            .isEqualTo(DiscoveredRoku(RokuAddress("192.168.1.20"), "YK00AB123456"))
    }

    @Test
    fun `tolerates LF-only line endings and extra whitespace`() {
        val datagram = "\nHTTP/1.1   200   OK\n" +
            "ST :   roku:ecp  \n" +
            "  Location:   http://192.168.1.21:8060/   \n" +
            "USN:uuid:roku:ecp:ABC123\n"
        assertThat(Ssdp.parseResponse(datagram, "10.9.9.9"))
            .isEqualTo(DiscoveredRoku(RokuAddress("192.168.1.21"), "ABC123"))
    }

    @Test
    fun `NOTIFY alive announces a Roku and byebye doesn't`() {
        val alive = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nCache-Control: max-age=3600\r\n" +
            "NT: roku:ecp\r\nNTS: ssdp:alive\r\nLocation: http://192.168.1.22:8060/\r\nUSN: uuid:roku:ecp:N0T1FY\r\n\r\n"
        assertThat(Ssdp.parseResponse(alive, "192.168.1.22"))
            .isEqualTo(DiscoveredRoku(RokuAddress("192.168.1.22"), "N0T1FY"))

        val byebye = alive.replace("ssdp:alive", "ssdp:byebye")
        assertThat(Ssdp.parseResponse(byebye, "192.168.1.22")).isNull()

        val otherNt = alive.replace("NT: roku:ecp", "NT: upnp:rootdevice")
        assertThat(Ssdp.parseResponse(otherNt, "192.168.1.22")).isNull()
    }

    @Test
    fun `other devices answering the search are ignored`() {
        val router = "HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
            "Location: http://192.168.1.1:5000/rootDesc.xml\r\nUSN: uuid:1234::urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"
        val chromecast = "HTTP/1.1 200 OK\r\nST: urn:dial-multiscreen-org:service:dial:1\r\n" +
            "LOCATION: http://192.168.1.30:8008/ssdp/device-desc.xml\r\n\r\n"
        val noSt = "HTTP/1.1 200 OK\r\nLocation: http://192.168.1.31:8060/\r\n\r\n"
        assertThat(Ssdp.parseResponse(router, "192.168.1.1")).isNull()
        assertThat(Ssdp.parseResponse(chromecast, "192.168.1.30")).isNull()
        assertThat(Ssdp.parseResponse(noSt, "192.168.1.31")).isNull()
    }

    @Test
    fun `non-responses are ignored`() {
        assertThat(Ssdp.parseResponse(Ssdp.searchRequest(), "192.168.1.2")).isNull() // Our own search, looped back.
        assertThat(Ssdp.parseResponse(officialResponse.replace("200 OK", "404 Not Found"), "192.168.1.2")).isNull()
        assertThat(Ssdp.parseResponse("", "192.168.1.2")).isNull()
        assertThat(Ssdp.parseResponse("\r\n\r\n", "192.168.1.2")).isNull()
        assertThat(Ssdp.parseResponse("garbage", "192.168.1.2")).isNull()
    }

    @Test
    fun `missing or unusable LOCATION falls back to the datagram's source`() {
        val noLocation = officialResponse.lines().filterNot { it.startsWith("Location") }.joinToString("\r\n")
        assertThat(Ssdp.parseResponse(noLocation, "192.168.1.40"))
            .isEqualTo(DiscoveredRoku(RokuAddress("192.168.1.40"), "P0A070000007"))

        val hostname = officialResponse.replace("192.168.1.134", "roku.local")
        assertThat(Ssdp.parseResponse(hostname, "192.168.1.41")?.address).isEqualTo(RokuAddress("192.168.1.41"))

        val garbage = officialResponse.replace("http://192.168.1.134:8060/", "::not a url::")
        assertThat(Ssdp.parseResponse(garbage, "192.168.1.42")?.address).isEqualTo(RokuAddress("192.168.1.42"))
    }

    @Test
    fun `LOCATION's port is used, defaulting to 8060`() {
        val custom = officialResponse.replace(":8060/", ":9090/")
        assertThat(Ssdp.parseResponse(custom, "10.9.9.9")?.address).isEqualTo(RokuAddress("192.168.1.134", 9090))

        val portless = officialResponse.replace(":8060/", "/")
        assertThat(Ssdp.parseResponse(portless, "10.9.9.9")?.address).isEqualTo(RokuAddress("192.168.1.134", 8060))
    }

    @Test
    fun `serial is null without a Roku USN`() {
        val noUsn = officialResponse.lines().filterNot { it.startsWith("USN") }.joinToString("\r\n")
        assertThat(Ssdp.parseResponse(noUsn, "10.9.9.9")?.serialNumber).isNull()

        val otherUsn = officialResponse.replace("uuid:roku:ecp:P0A070000007", "uuid:abcd")
        assertThat(Ssdp.parseResponse(otherUsn, "10.9.9.9")?.serialNumber).isNull()

        val suffixed = officialResponse.replace("P0A070000007", "P0A070000007::roku:ecp")
        assertThat(Ssdp.parseResponse(suffixed, "10.9.9.9")?.serialNumber).isEqualTo("P0A070000007")
    }

    @Test
    fun `discover sends the search three times, dedupes, completes after the timeout and closes the socket`() = runTest {
        val socket = FakeDatagramSocket()
        socket.onSend = { count ->
            if (count == 1) {
                socket.deliver("192.168.1.134", officialResponse)
                socket.deliver("192.168.1.50", "HTTP/1.1 200 OK\r\nST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n")
                socket.deliver("192.168.1.2", Ssdp.searchRequest())
                socket.deliver("192.168.1.60", response("SECOND", "192.168.1.60"))
            }
            // The same TVs answering the retransmits, plus one NOTIFY from the first.
            socket.deliver("192.168.1.134", officialResponse)
            socket.deliver("192.168.1.60", response("SECOND", "192.168.1.60"))
            if (count == 3) {
                socket.deliver(
                    "192.168.1.134",
                    "NOTIFY * HTTP/1.1\r\nNT: roku:ecp\r\nNTS: ssdp:alive\r\nLocation: http://192.168.1.134:8060/\r\nUSN: uuid:roku:ecp:P0A070000007\r\n\r\n",
                )
            }
        }
        val timeout = 1200.milliseconds
        val started = TimeSource.Monotonic.markNow()
        val found = SsdpDiscovery { socket }.discover(timeout).toList()
        val elapsed = started.elapsedNow()

        assertThat(found).containsExactly(
            DiscoveredRoku(RokuAddress("192.168.1.134"), "P0A070000007"),
            DiscoveredRoku(RokuAddress("192.168.1.60"), "SECOND"),
        ).inOrder()
        assertThat(elapsed).isAtLeast(timeout)
        assertThat(elapsed).isLessThan(timeout + 1.seconds)
        assertThat(socket.closed).isTrue()
        assertThat(socket.timeoutMillis).isIn(1..250)

        assertThat(socket.sent).hasSize(3)
        for (sent in socket.sent) {
            assertThat(sent.address).isEqualTo(InetAddress.getByName("239.255.255.250"))
            assertThat(sent.port).isEqualTo(1900)
            assertThat(sent.text).isEqualTo(Ssdp.searchRequest(1))
        }
        val offsets = socket.sent.map { (it.atNanos - socket.sent[0].atNanos) / 1_000_000 }
        assertThat(offsets[1]).isIn(250L..600L)
        assertThat(offsets[2]).isIn(850L..1150L)
    }

    @Test
    fun `discover dedupes by address when there is no serial`() = runTest {
        val socket = FakeDatagramSocket()
        val anonymous = "HTTP/1.1 200 OK\r\nST: roku:ecp\r\nLocation: http://192.168.1.70:8060/\r\n\r\n"
        socket.onSend = { socket.deliver("192.168.1.70", anonymous) }
        val found = SsdpDiscovery { socket }.discover(500.milliseconds).toList()
        assertThat(found).containsExactly(DiscoveredRoku(RokuAddress("192.168.1.70"), null))
    }

    @Test
    fun `discover stops promptly and closes the socket when the collector is done`() = runTest {
        val socket = FakeDatagramSocket()
        socket.onSend = { if (it == 1) socket.deliver("192.168.1.134", officialResponse) }
        val started = TimeSource.Monotonic.markNow()
        val first = SsdpDiscovery { socket }.discover(30.seconds).first()
        assertThat(first.serialNumber).isEqualTo("P0A070000007")
        assertThat(started.elapsedNow()).isLessThan(1.seconds)
        assertThat(socket.closed).isTrue()
    }

    @Test
    fun `discover with a large timeout still advertises a sane MX`() = runTest {
        val socket = FakeDatagramSocket()
        socket.onSend = { if (it == 1) socket.deliver("192.168.1.134", officialResponse) }
        SsdpDiscovery { socket }.discover(1.seconds * 60).first()
        assertThat(socket.sent.first().text).contains("\r\nMX: 5\r\n")
    }
}
