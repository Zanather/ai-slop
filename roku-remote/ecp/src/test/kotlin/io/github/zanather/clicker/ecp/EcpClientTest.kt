package io.github.zanather.clicker.ecp

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import kotlin.time.Duration.Companion.seconds

class EcpClientTest {
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

    private fun respond(code: Int = 200, body: String = "", contentType: String = "text/xml; charset=\"utf-8\"") {
        server.enqueue(MockResponse(code = code, headers = headersOf("Content-Type", contentType), body = body))
    }

    private fun takeRequest(): RecordedRequest = server.takeRequest(5, TimeUnit.SECONDS) ?: error("No request arrived")

    private inline fun <reified T : Throwable> assertFailsWith(noinline block: suspend () -> Unit): T =
        assertThrows(T::class.java) { kotlinx.coroutines.runBlocking { block() } }

    @Test
    fun `keypress POSTs an empty body to the key's path`() = runTest {
        val keys = mapOf(
            RemoteKey.Home to "/keypress/Home",
            RemoteKey.Select to "/keypress/Select",
            RemoteKey.VolumeUp to "/keypress/VolumeUp",
            RemoteKey.InputHdmi1 to "/keypress/InputHDMI1",
            RemoteKey.InstantReplay to "/keypress/InstantReplay",
        )
        for ((key, path) in keys) {
            respond()
            client.keypress(address, key)
            val request = takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.target).isEqualTo(path)
            assertThat(request.bodySize).isEqualTo(0)
            assertThat(request.headers["Content-Length"]).isEqualTo("0")
            assertThat(request.headers["Origin"]).isNull()
            assertThat(request.headers["Host"]).isEqualTo("127.0.0.1:${server.port}")
        }
    }

    @Test
    fun `literal keys are percent-encoded exactly once`() = runTest {
        val expected = linkedMapOf(
            "a" to "Lit_a",
            "Z" to "Lit_Z",
            "7" to "Lit_7",
            "-" to "Lit_-",
            "." to "Lit_.",
            "_" to "Lit__",
            "~" to "Lit_~",
            " " to "Lit_%20",
            "@" to "Lit_%40",
            "$" to "Lit_%24",
            "&" to "Lit_%26",
            "=" to "Lit_%3D",
            ";" to "Lit_%3B",
            ":" to "Lit_%3A",
            "," to "Lit_%2C",
            "/" to "Lit_%2F",
            "?" to "Lit_%3F",
            "#" to "Lit_%23",
            "+" to "Lit_%2B",
            "%" to "Lit_%25",
            "é" to "Lit_%C3%A9",
            "€" to "Lit_%E2%82%AC",
            "😀" to "Lit_%F0%9F%98%80",
        )
        for ((char, segment) in expected) {
            respond()
            client.keypress(address, LiteralKey.of(char).single())
            val request = takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.target).isEqualTo("/keypress/$segment")
        }
    }

    @Test
    fun `percentEncode leaves only unreserved characters and uppercase escapes`() {
        assertThat(percentEncode("Lit_ä b")).isEqualTo("Lit_%C3%A4%20b")
        assertThat(percentEncode("%41")).isEqualTo("%2541")
        assertThat(percentEncode("tvinput.hdmi1")).isEqualTo("tvinput.hdmi1")
        assertThat(percentEncode("")).isEmpty()
    }

    @Test
    fun `any 2xx is success`() = runTest {
        for (code in listOf(200, 202, 204)) {
            respond(code = code)
            client.keypress(address, RemoteKey.Home)
            takeRequest()
        }
    }

    @Test
    fun `403 with Roku's Limited mode body is Forbidden in limited mode`() {
        respond(code = 403, body = LIMITED_MODE_BODY, contentType = "text/plain; charset=\"utf-8\"")
        val e = assertFailsWith<EcpException.Forbidden> { client.keypress(address, RemoteKey.Home) }
        assertThat(e.limitedMode).isTrue()
        assertThat(e.address).isEqualTo(address)
        assertThat(e.path).isEqualTo("keypress/Home")
    }

    @Test
    fun `403 on apps in Limited mode is Forbidden in limited mode`() {
        respond(code = 403, body = LIMITED_MODE_BODY, contentType = "text/plain; charset=\"utf-8\"")
        val e = assertFailsWith<EcpException.Forbidden> { client.apps(address) }
        assertThat(e.limitedMode).isTrue()
        assertThat(e.path).isEqualTo("query/apps")
    }

    @Test
    fun `403 with an empty or other body is Forbidden but not limited mode`() {
        respond(code = 403)
        val empty = assertFailsWith<EcpException.Forbidden> { client.keypress(address, RemoteKey.Home) }
        assertThat(empty.limitedMode).isFalse()

        respond(code = 403, body = "Forbidden")
        val other = assertFailsWith<EcpException.Forbidden> { client.launch(address, "12") }
        assertThat(other.limitedMode).isFalse()
        assertThat(other.path).isEqualTo("launch/12")
    }

    @Test
    fun `403 Limited mode detection only looks at the start of a long body`() {
        respond(code = 403, body = "x".repeat(4096) + LIMITED_MODE_BODY)
        val e = assertFailsWith<EcpException.Forbidden> { client.keypress(address, RemoteKey.Home) }
        assertThat(e.limitedMode).isFalse()
    }

    @Test
    fun `other non-2xx statuses are Http with the code`() {
        for (code in listOf(400, 404, 500, 503)) {
            respond(code = code)
            val e = assertFailsWith<EcpException.Http> { client.launch(address, "dev") }
            assertThat(e.code).isEqualTo(code)
            assertThat(e.path).isEqualTo("launch/dev")
            assertThat(e.address).isEqualTo(address)
        }
    }

    @Test
    fun `redirects are not followed`() {
        server.enqueue(MockResponse(code = 302, headers = headersOf("Location", "http://example.com/")))
        val e = assertFailsWith<EcpException.Http> { client.deviceInfo(address) }
        assertThat(e.code).isEqualTo(302)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a closed port is Unreachable`() {
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val closed = RokuAddress("127.0.0.1", port)
        val e = assertFailsWith<EcpException.Unreachable> { client.keypress(closed, RemoteKey.Home) }
        assertThat(e.address).isEqualTo(closed)
        assertThat(e.cause).isInstanceOf(java.io.IOException::class.java)
    }

    @Test
    fun `a host OkHttp can't address is Unreachable`() {
        val bogus = RokuAddress("not a host!")
        val e = assertFailsWith<EcpException.Unreachable> { client.deviceInfo(bogus) }
        assertThat(e.address).isEqualTo(bogus)
    }

    @Test
    fun `a server slower than the read timeout is Timeout`() {
        val impatient = EcpClient(http.newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build())
        server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).body("<apps/>").build())
        val e = assertFailsWith<EcpException.Timeout> { impatient.apps(address) }
        assertThat(e.address).isEqualTo(address)
    }

    @Test
    fun `a server slower than the call timeout is Timeout`() {
        val impatient = EcpClient(
            http.newBuilder().readTimeout(10, TimeUnit.SECONDS).callTimeout(300, TimeUnit.MILLISECONDS).build(),
        )
        server.enqueue(MockResponse.Builder().headersDelay(2, TimeUnit.SECONDS).build())
        val e = assertFailsWith<EcpException.Timeout> { impatient.keypress(address, RemoteKey.Home) }
        assertThat(e.cause).hasMessageThat().isEqualTo("timeout")
    }

    @Test
    fun `a connect timeout is Unreachable, since no TCP connection was made`() {
        // The JDK's and Android's wording for a SYN nobody answered (TV in deep sleep, wrong IP).
        val messages = listOf("Connect timed out", "failed to connect to /192.168.1.9 (port 8060) from /192.168.1.2 (port 40000) after 1500ms")
        for (message in messages) {
            val factory = object : SocketFactory() {
                override fun createSocket(): Socket = object : Socket() {
                    override fun connect(endpoint: SocketAddress?, timeout: Int) {
                        throw SocketTimeoutException(message)
                    }
                }

                override fun createSocket(host: String?, port: Int): Socket = throw UnsupportedOperationException()
                override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = throw UnsupportedOperationException()
                override fun createSocket(host: InetAddress?, port: Int): Socket = throw UnsupportedOperationException()
                override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = throw UnsupportedOperationException()
            }
            val sleepy = EcpClient(EcpClient.defaultHttpClient(factory))
            val e = assertFailsWith<EcpException.Unreachable> { sleepy.keypress(address, RemoteKey.PowerOn) }
            assertThat(e.cause).hasMessageThat().isEqualTo(message)
        }
    }

    @Test
    fun `cancelling the coroutine cancels the HTTP call and propagates cancellation`() = runTest {
        server.enqueue(MockResponse.Builder().headersDelay(3, TimeUnit.SECONDS).build())
        var thrown: Throwable? = null
        val job = launch {
            try {
                client.deviceInfo(address)
            } catch (e: Throwable) {
                thrown = e
                throw e
            }
        }
        withContext(Dispatchers.IO) { takeRequest() }
        job.cancel()
        job.join()
        assertThat(job.isCancelled).isTrue()
        assertThat(thrown).isInstanceOf(kotlinx.coroutines.CancellationException::class.java)
        withContext(Dispatchers.IO) {
            val deadline = System.nanoTime() + 2.seconds.inWholeNanoseconds
            while (http.dispatcher.runningCallsCount() > 0 && System.nanoTime() < deadline) Thread.sleep(10)
        }
        assertThat(http.dispatcher.runningCallsCount()).isEqualTo(0)
    }

    @Test
    fun `launch POSTs to the app's path with query-encoded params`() = runTest {
        respond()
        client.launch(address, "12", linkedMapOf("contentId" to "a b&c=d+e", "mediaType" to "movie"))
        val request = takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.bodySize).isEqualTo(0)
        assertThat(request.headers["Origin"]).isNull()
        assertThat(request.target).isEqualTo("/launch/12?contentId=a%20b%26c%3Dd%2Be&mediaType=movie")
        assertThat(request.url.queryParameter("contentId")).isEqualTo("a b&c=d+e")
    }

    @Test
    fun `launch switches TV inputs and encodes the app id`() = runTest {
        respond()
        client.launch(address, "tvinput.hdmi1")
        assertThat(takeRequest().target).isEqualTo("/launch/tvinput.hdmi1")

        respond()
        client.launch(address, "tvinput.dtv", mapOf("ch" to "1.1"))
        assertThat(takeRequest().target).isEqualTo("/launch/tvinput.dtv?ch=1.1")

        respond()
        client.launch(address, "a/b c")
        assertThat(takeRequest().target).isEqualTo("/launch/a%2Fb%20c")
    }

    @Test
    fun `queries are GETs`() = runTest {
        respond(body = HISENSE_DEVICE_INFO)
        client.deviceInfo(address)
        respond(body = "<apps/>")
        client.apps(address)
        respond(body = "<active-app><app>Roku</app></active-app>")
        client.activeApp(address)

        for (path in listOf("/query/device-info", "/query/apps", "/query/active-app")) {
            val request = takeRequest()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.target).isEqualTo(path)
            assertThat(request.headers["Origin"]).isNull()
        }
    }

    @Test
    fun `unparseable 2xx responses are BadResponse`() {
        val bodies = listOf("", "not xml", "<device-info><serial-number>X</device-info>", "<apps/>")
        for (body in bodies) {
            respond(body = body)
            val e = assertFailsWith<EcpException.BadResponse> { client.deviceInfo(address) }
            assertThat(e.path).isEqualTo("query/device-info")
            assertThat(e.address).isEqualTo(address)
        }

        respond(body = HISENSE_DEVICE_INFO)
        assertThat(assertFailsWith<EcpException.BadResponse> { client.apps(address) }.path).isEqualTo("query/apps")
        respond(body = "<apps/>")
        assertThat(assertFailsWith<EcpException.BadResponse> { client.activeApp(address) }.path).isEqualTo("query/active-app")
    }

    @Test
    fun `a huge response is BadResponse rather than read into memory`() {
        respond(body = "<apps>" + "<app id=\"1\">x</app>".repeat(60_000) + "</apps>")
        assertFailsWith<EcpException.BadResponse> { client.apps(address) }
    }

    @Test
    fun `iconUrl points at query icon with an encoded app id`() {
        val tv = RokuAddress("192.168.1.5")
        assertThat(client.iconUrl(tv, "12").toString()).isEqualTo("http://192.168.1.5:8060/query/icon/12")
        assertThat(client.iconUrl(tv, "tvinput.hdmi1").toString()).isEqualTo("http://192.168.1.5:8060/query/icon/tvinput.hdmi1")
        assertThat(client.iconUrl(tv, "a b/c&d").toString()).isEqualTo("http://192.168.1.5:8060/query/icon/a%20b%2Fc%26d")
        assertThat(client.iconUrl(RokuAddress("10.0.0.2", 9000), "12").toString()).isEqualTo("http://10.0.0.2:9000/query/icon/12")
    }

    @Test
    fun `defaultHttpClient is tuned for a TV on the LAN`() {
        val factory = SocketFactory.getDefault()
        val tuned: OkHttpClient = EcpClient.defaultHttpClient(factory)
        assertThat(tuned.connectTimeoutMillis).isEqualTo(1500)
        assertThat(tuned.readTimeoutMillis).isEqualTo(2500)
        assertThat(tuned.writeTimeoutMillis).isEqualTo(2500)
        assertThat(tuned.callTimeoutMillis).isEqualTo(4000)
        assertThat(tuned.followRedirects).isFalse()
        assertThat(tuned.retryOnConnectionFailure).isTrue()
        assertThat(tuned.socketFactory).isSameInstanceAs(factory)
        assertThat(tuned.proxy).isEqualTo(Proxy.NO_PROXY)
    }
}
