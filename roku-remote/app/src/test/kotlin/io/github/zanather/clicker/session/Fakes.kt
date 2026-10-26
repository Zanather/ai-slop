package io.github.zanather.clicker.session

import io.github.zanather.clicker.net.Lan
import io.github.zanather.clicker.net.LanAddress
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Headers
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A Roku TV good enough to drive the session: device-info, apps, active-app, keys and launch. */
class FakeRoku(
    val serial: String = "X00000ND22GG",
    @Volatile var ecpMode: String? = "enabled",
    @Volatile var powerMode: String = "PowerOn",
) : Dispatcher() {
    /** Raw (still percent-encoded) key names received, in order. */
    val keys = CopyOnWriteArrayList<String>()
    val launches = CopyOnWriteArrayList<String>()

    /** Reject keys with Roku's Limited-mode 403 even when [ecpMode] doesn't say so (older firmware). */
    @Volatile var rejectKeys = false

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val limited = ecpMode == "limited" || rejectKeys
        return when {
            path == "/query/device-info" -> xml(deviceInfo())
            path == "/query/apps" -> if (limited) limitedMode() else xml(APPS)
            path == "/query/active-app" -> xml("""<active-app><app id="12" type="appl" version="5.1">Netflix</app></active-app>""")
            path.startsWith("/keypress/") && request.method == "POST" -> {
                if (limited) {
                    limitedMode()
                } else {
                    val key = path.removePrefix("/keypress/")
                    keys += key
                    when (key) {
                        "PowerOff" -> powerMode = "Ready"
                        "PowerOn" -> powerMode = "PowerOn"
                    }
                    MockResponse(code = 200)
                }
            }
            path.startsWith("/launch/") && request.method == "POST" -> {
                launches += path.removePrefix("/launch/")
                MockResponse(code = 204)
            }
            else -> MockResponse(code = 404)
        }
    }

    private fun deviceInfo() = buildString {
        append("<device-info>")
        append("<udn>28001240-0000-1000-8000-64e003d45641</udn>")
        append("<serial-number>$serial</serial-number>")
        append("<device-id>S076216D22GG</device-id>")
        append("<vendor-name>Roku</vendor-name>")
        append("<model-name>Roku TV</model-name>")
        append("<model-number>C148X</model-number>")
        append("<is-tv>true</is-tv>")
        append("<wifi-mac>64:e0:03:d4:56:41</wifi-mac>")
        append("<network-type>wifi</network-type>")
        append("<user-device-name>Living Room TV</user-device-name>")
        append("<software-version>15.3.4</software-version>")
        append("<software-build>2402</software-build>")
        append("<power-mode>$powerMode</power-mode>")
        ecpMode?.let { append("<ecp-setting-mode>$it</ecp-setting-mode>") }
        append("<uptime>78123</uptime>")
        append("</device-info>")
    }

    private fun xml(body: String) = MockResponse(code = 200, headers = Headers.headersOf("Content-Type", "text/xml; charset=\"utf-8\""), body = body)

    private fun limitedMode() = MockResponse(code = 403, body = "ECP command not allowed in Limited mode.")

    companion object {
        const val APPS = """<apps>
            <app id="tvinput.hdmi1" type="tvin" version="1.0.0">HDMI 1</app>
            <app id="tvinput.dtv" type="tvin" version="1.0.0">Live TV</app>
            <app id="12" type="appl" version="5.1">Netflix</app>
            <app id="837" type="appl" version="2.1">YouTube</app>
        </apps>"""
    }
}

/**
 * The LAN as the session sees it: an SSDP socket that answers M-SEARCH with [ssdpReplies], and records every
 * datagram sent (for Wake-on-LAN assertions).
 */
class FakeLan(
    private val ssdpReplies: List<String> = emptyList(),
    private val lanAddress: LanAddress? = null,
) : Lan {
    val sent = CopyOnWriteArrayList<DatagramPacket>()

    override fun openDatagramSocket(): DatagramSocket = FakeDatagramSocket()

    override fun lanAddress(): LanAddress? = lanAddress

    override fun broadcastAddresses(): List<InetAddress> = listOf(InetAddress.getByName("192.0.2.255"))

    override suspend fun <T> withMulticastLock(block: suspend () -> T): T = block()

    private inner class FakeDatagramSocket : DatagramSocket(null as SocketAddress?) {
        private val inbox = LinkedBlockingQueue<String>()
        private var timeoutMs = 0

        override fun send(p: DatagramPacket) {
            sent += DatagramPacket(p.data.copyOfRange(p.offset, p.offset + p.length), p.length, p.socketAddress)
            if (String(p.data, p.offset, p.length).startsWith("M-SEARCH")) inbox.addAll(ssdpReplies)
        }

        override fun receive(p: DatagramPacket) {
            val reply = (if (timeoutMs > 0) inbox.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) else inbox.take())
                ?: throw SocketTimeoutException("no reply")
            val bytes = reply.toByteArray()
            System.arraycopy(bytes, 0, p.data, p.offset, bytes.size)
            p.length = bytes.size
            p.address = InetAddress.getLoopbackAddress()
            p.port = 1900
        }

        override fun setSoTimeout(timeout: Int) {
            timeoutMs = timeout
        }

        override fun getSoTimeout(): Int = timeoutMs
    }
}

fun ssdpReply(port: Int, serial: String) =
    "HTTP/1.1 200 OK\r\nCache-Control: max-age=3600\r\nST: roku:ecp\r\nLocation: http://127.0.0.1:$port/\r\nUSN: uuid:roku:ecp:$serial\r\n\r\n"
