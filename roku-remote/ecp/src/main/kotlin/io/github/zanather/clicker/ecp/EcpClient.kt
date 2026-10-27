package io.github.zanather.clicker.ecp

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * Talks plain REST ECP (`http://<tv>:8060/...`) to a Roku.
 *
 * Every call is a suspend function that throws [EcpException] subtypes and nothing else
 * (except [kotlinx.coroutines.CancellationException]). Calls never block the caller's thread.
 * Any 2xx counts as success (Roku answers 200, 202 or 204 depending on firmware).
 */
class EcpClient(private val http: OkHttpClient) {

    /** `POST /keypress/<key>`: a full press and release. */
    suspend fun keypress(address: RokuAddress, key: RokuKey) {
        val name = key.ecpName
        execute(address, "keypress/$name", post = true, url = {
            addPathSegment("keypress").addEncodedPathSegment(percentEncode(name))
        }) { }
    }

    /** `POST /launch/<appId>?<params>`: opens a channel or switches to a TV input (`tvinput.hdmi1`). */
    suspend fun launch(address: RokuAddress, appId: String, params: Map<String, String> = emptyMap()) {
        execute(address, "launch/$appId", post = true, url = {
            addPathSegment("launch").addEncodedPathSegment(percentEncode(appId))
            for ((name, value) in params) addQueryParameter(name, value)
        }) { }
    }

    /** `GET /query/device-info`. Cheap, allowed in every access mode; doubles as a reachability probe. */
    suspend fun deviceInfo(address: RokuAddress): DeviceInfo = query(address, "device-info", ::parseDeviceInfo)

    /** `GET /query/apps`, in the order the Roku returns them. 403 in Limited mode. */
    suspend fun apps(address: RokuAddress): List<RokuApp> = query(address, "apps", ::parseApps)

    /** `GET /query/active-app`. Allowed in Limited mode. */
    suspend fun activeApp(address: RokuAddress): ActiveApp = query(address, "active-app", ::parseActiveApp)

    /** URL of `GET /query/icon/<appId>`, for an image loader to fetch directly. */
    fun iconUrl(address: RokuAddress, appId: String): HttpUrl =
        address.urlBuilder()
            .addPathSegment("query")
            .addPathSegment("icon")
            .addEncodedPathSegment(percentEncode(appId))
            .build()

    private suspend fun <T> query(address: RokuAddress, name: String, parse: (String) -> T): T {
        val path = "query/$name"
        val url: HttpUrl.Builder.() -> Unit = { addPathSegment("query").addPathSegment(name) }
        return execute(address, path, post = false, url = url) { response ->
            val source = response.body.source()
            if (source.request(MAX_XML_BYTES + 1)) {
                throw EcpException.BadResponse(address, path, EcpXmlException("Response over $MAX_XML_BYTES bytes"))
            }
            // UTF-8 regardless of Content-Type: Roku quotes the charset (`charset="utf-8"`).
            val xml = source.buffer.readUtf8()
            try {
                parse(xml)
            } catch (e: Exception) {
                throw EcpException.BadResponse(address, path, e)
            } catch (e: StackOverflowError) {
                // Backstop for a pathological document the depth pre-check missed; callers only expect EcpException.
                throw EcpException.BadResponse(address, path, e)
            }
        }
    }

    /**
     * Sends one request and hands a 2xx [Response] to [onSuccess], which runs on OkHttp's thread and may
     * read the body. [path] is the unencoded path, used only in exceptions.
     */
    private suspend fun <T> execute(
        address: RokuAddress,
        path: String,
        post: Boolean,
        url: HttpUrl.Builder.() -> Unit,
        onSuccess: (Response) -> T,
    ): T {
        val request = try {
            Request.Builder()
                .url(address.urlBuilder().apply(url).build())
                .apply { if (post) post(EMPTY_BODY) else get() }
                .build()
        } catch (e: IllegalArgumentException) {
            // Not a host OkHttp can address, e.g. a mistyped manual IP.
            throw EcpException.Unreachable(address, e)
        }
        try {
            return http.newCall(request).await { response ->
                if (!response.isSuccessful) throw statusException(address, path, response)
                onSuccess(response)
            }
        } catch (e: IOException) {
            throw e.toEcpException(address)
        }
    }

    companion object {
        /**
         * An [OkHttpClient] tuned for a TV on the LAN: short timeouts so a dead TV fails fast, and pooled
         * connections that expire before Roku's ~5 s idle cutoff so a press never lands on a dead socket.
         *
         * @param socketFactory lets Android pin sockets to the Wi-Fi network so a VPN can't capture LAN traffic.
         */
        fun defaultHttpClient(socketFactory: SocketFactory = SocketFactory.getDefault()): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(1500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .writeTimeout(2500, TimeUnit.MILLISECONDS)
                .callTimeout(4, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(maxIdleConnections = 2, keepAliveDuration = 4, TimeUnit.SECONDS))
                .followRedirects(false)
                // A Wi-Fi HTTP proxy can't reach the TV, and would put its own name in the Host header.
                .proxy(Proxy.NO_PROXY)
                .socketFactory(socketFactory)
                .build()
    }
}

/** Responses are a few KB; this only stops a misbehaving device from filling memory. */
private const val MAX_XML_BYTES = 1024L * 1024

/** Enough to recognize Roku's `ECP command not allowed in Limited mode.` */
private const val MAX_ERROR_BODY_BYTES = 1024L

private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

private fun RokuAddress.urlBuilder(): HttpUrl.Builder = HttpUrl.Builder().scheme("http").host(host).port(port)

/**
 * Percent-encodes every UTF-8 byte of anything outside RFC 3986's unreserved set, so the result can go
 * through [HttpUrl.Builder.addEncodedPathSegment] untouched. OkHttp's own `addPathSegment` leaves
 * `@ $ & = ; : ,` raw, and Roku rejects those in `Lit_` keys. Encoding the whole string's UTF-8 at once
 * handles surrogate pairs (emoji) as single code points; continuation bytes are never unreserved ASCII.
 */
internal fun percentEncode(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    val out = StringBuilder(bytes.size * 3)
    for (byte in bytes) {
        val b = byte.toInt() and 0xFF
        val c = b.toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~') {
            out.append(c)
        } else {
            out.append('%').append(HEX_DIGITS[b shr 4]).append(HEX_DIGITS[b and 0xF])
        }
    }
    return out.toString()
}

private const val HEX_DIGITS = "0123456789ABCDEF"

private fun statusException(address: RokuAddress, path: String, response: Response): EcpException {
    if (response.code != 403) return EcpException.Http(address, path, response.code)
    val body = try {
        val source = response.body.source()
        source.request(MAX_ERROR_BODY_BYTES)
        source.buffer.readUtf8(minOf(source.buffer.size, MAX_ERROR_BODY_BYTES))
    } catch (e: IOException) {
        "" // The status alone still says "forbidden".
    }
    return EcpException.Forbidden(address, path, limitedMode = body.contains("Limited mode", ignoreCase = true))
}

/**
 * Timeouts after the TCP connection is up become [EcpException.Timeout]; everything else, including a
 * connect timeout (no TCP connection: deep sleep, wrong IP), becomes [EcpException.Unreachable].
 */
private fun IOException.toEcpException(address: RokuAddress): EcpException {
    val timeout = generateSequence<Throwable>(this) { it.cause }
        .take(8)
        .firstOrNull { it is SocketTimeoutException || (it is InterruptedIOException && it.message == "timeout") }
    // The JDK says "Connect timed out", Android "failed to connect to /<ip> (port <n>) … after <n>ms".
    val connectTimeout = timeout is SocketTimeoutException &&
        timeout.message.orEmpty().contains("connect", ignoreCase = true)
    return if (timeout != null && !connectTimeout) {
        EcpException.Timeout(address, this)
    } else {
        EcpException.Unreachable(address, this)
    }
}

/**
 * Enqueues the call and suspends until [onResponse] has consumed the response on OkHttp's thread.
 * The response is always closed; cancelling the coroutine cancels the call.
 */
private suspend fun <T> Call.await(onResponse: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resumeWith(runCatching { response.use(onResponse) })
            }
        },
    )
}
