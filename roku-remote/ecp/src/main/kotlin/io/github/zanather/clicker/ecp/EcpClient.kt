package io.github.zanather.clicker.ecp

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
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
    suspend fun keypress(address: RokuAddress, key: RokuKey): Unit = TODO()

    /** `POST /launch/<appId>?<params>`: opens a channel or switches to a TV input (`tvinput.hdmi1`). */
    suspend fun launch(address: RokuAddress, appId: String, params: Map<String, String> = emptyMap()): Unit = TODO()

    /** `GET /query/device-info`. Cheap, allowed in every access mode; doubles as a reachability probe. */
    suspend fun deviceInfo(address: RokuAddress): DeviceInfo = TODO()

    /** `GET /query/apps`, in the order the Roku returns them. 403 in Limited mode. */
    suspend fun apps(address: RokuAddress): List<RokuApp> = TODO()

    /** `GET /query/active-app`. Allowed in Limited mode. */
    suspend fun activeApp(address: RokuAddress): ActiveApp = TODO()

    /** URL of `GET /query/icon/<appId>`, for an image loader to fetch directly. */
    fun iconUrl(address: RokuAddress, appId: String): HttpUrl = TODO()

    companion object {
        /**
         * An [OkHttpClient] tuned for a TV on the LAN: short timeouts so a dead TV fails fast, and pooled
         * connections that expire before Roku's ~5 s idle cutoff so a press never lands on a dead socket.
         *
         * @param socketFactory lets Android pin sockets to the Wi-Fi network so a VPN can't capture LAN traffic.
         */
        fun defaultHttpClient(socketFactory: SocketFactory = SocketFactory.getDefault()): OkHttpClient = TODO()
    }
}
