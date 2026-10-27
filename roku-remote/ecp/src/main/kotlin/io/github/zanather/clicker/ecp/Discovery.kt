package io.github.zanather.clicker.ecp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** SSDP wire format for Roku discovery. Pure functions so they're testable without multicast. */
object Ssdp {
    const val MULTICAST_HOST = "239.255.255.250"
    const val PORT = 1900
    const val SEARCH_TARGET = "roku:ecp"

    /** The `M-SEARCH` datagram payload. */
    fun searchRequest(mxSeconds: Int = 2): String =
        "M-SEARCH * HTTP/1.1\r\n" +
            "Host: $MULTICAST_HOST:$PORT\r\n" +
            "Man: \"ssdp:discover\"\r\n" +
            "ST: $SEARCH_TARGET\r\n" +
            "MX: $mxSeconds\r\n" +
            "\r\n"

    /**
     * Parses one SSDP response datagram. Returns null for anything that isn't a Roku ECP response.
     * Takes the address from `LOCATION` (falling back to [sourceHost]) and the serial from `USN`.
     */
    fun parseResponse(datagram: String, sourceHost: String): DiscoveredRoku? {
        val lines = datagram.lines().map { it.trim() }.dropWhile { it.isEmpty() }
        val startLine = lines.firstOrNull()?.split(WHITESPACE) ?: return null
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            headers.putIfAbsent(line.substring(0, colon).trim().lowercase(), line.substring(colon + 1).trim())
        }

        val isRoku = when {
            startLine.size >= 2 && startLine[0].startsWith("HTTP/", ignoreCase = true) && startLine[1] == "200" ->
                headers["st"].orEmpty().contains(SEARCH_TARGET, ignoreCase = true)

            startLine.size >= 2 && startLine[0].equals("NOTIFY", ignoreCase = true) && startLine[1] == "*" ->
                headers["nt"].orEmpty().contains(SEARCH_TARGET, ignoreCase = true) &&
                    headers["nts"].equals("ssdp:alive", ignoreCase = true)

            else -> false
        }
        if (!isRoku) return null

        return DiscoveredRoku(addressFrom(headers["location"], sourceHost), serialFrom(headers["usn"]))
    }

    private val WHITESPACE = Regex("\\s+")
    private const val USN_PREFIX = "uuid:roku:ecp:"

    private fun addressFrom(location: String?, sourceHost: String): RokuAddress {
        val uri = location?.let { runCatching { URI(it) }.getOrNull() }
        // RokuAddress needs an IP literal; a hostname here would also make Roku reject our Host header.
        val host = uri?.host?.removeSurrounding("[", "]")?.takeIf(::isIpLiteral) ?: sourceHost
        val port = uri?.port?.takeIf { it in 1..65535 } ?: RokuAddress.DEFAULT_PORT
        return RokuAddress(host, port)
    }

    private fun serialFrom(usn: String?): String? {
        if (usn == null || !usn.startsWith(USN_PREFIX, ignoreCase = true)) return null
        // UPnP USNs may append "::<type>"; Roku's don't, but tolerate it.
        return usn.substring(USN_PREFIX.length).substringBefore("::").trim().ifEmpty { null }
    }

    private fun isIpLiteral(host: String): Boolean {
        if (':' in host) return true
        val parts = host.split('.')
        return parts.size == 4 &&
            parts.all { part -> part.length in 1..3 && part.all { it in '0'..'9' } && part.toInt() <= 255 }
    }
}

/**
 * Finds Rokus with SSDP `M-SEARCH`. Sends the search a few times (UDP is lossy) and emits each
 * device once, completing after [discover]'s timeout.
 *
 * @param openSocket creates the UDP socket; Android binds it to the Wi-Fi network first.
 */
class SsdpDiscovery(private val openSocket: () -> DatagramSocket = { DatagramSocket() }) {
    /**
     * A cold flow; each collection opens its own socket and closes it when done or cancelled.
     * Fails with the [java.io.IOException] if the socket can't be opened or the search can't be sent.
     */
    fun discover(timeout: Duration = 3.seconds): Flow<DiscoveredRoku> = flow {
        val socket = openSocket()
        try {
            // Tells devices how long they may wait before answering; keep it inside our window.
            val mx = (timeout.inWholeSeconds - 1).coerceIn(1, 5).toInt()
            val request = Ssdp.searchRequest(mx).toByteArray(Charsets.US_ASCII)
            val target = InetSocketAddress(InetAddress.getByName(Ssdp.MULTICAST_HOST), Ssdp.PORT)
            val started = TimeSource.Monotonic.markNow()
            var sends = 0
            val seenAddresses = HashSet<RokuAddress>()
            val seenSerials = HashSet<String>()
            val buffer = ByteArray(RECEIVE_BUFFER_BYTES)

            while (true) {
                currentCoroutineContext().ensureActive()
                val elapsed = started.elapsedNow()
                if (elapsed >= timeout) break
                while (sends < SEND_TIMES.size && elapsed >= SEND_TIMES[sends]) {
                    socket.send(DatagramPacket(request, request.size, target))
                    sends++
                }

                // Wake up for the next resend, the deadline, or a cancellation check, whichever is first.
                var wait = minOf(RECEIVE_POLL, timeout - elapsed)
                if (sends < SEND_TIMES.size) wait = minOf(wait, SEND_TIMES[sends] - elapsed)
                socket.soTimeout = wait.inWholeMilliseconds.toInt().coerceAtLeast(1) // 0 would mean "forever"

                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val source = packet.address?.hostAddress ?: continue
                val roku = Ssdp.parseResponse(String(packet.data, packet.offset, packet.length, Charsets.UTF_8), source)
                    ?: continue
                val serial = roku.serialNumber
                if (roku.address in seenAddresses || (serial != null && serial in seenSerials)) continue
                seenAddresses += roku.address
                if (serial != null) seenSerials += serial
                emit(roku)
            }
        } finally {
            socket.close()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        val SEND_TIMES = listOf(Duration.ZERO, 300.milliseconds, 900.milliseconds)
        val RECEIVE_POLL = 250.milliseconds
        const val RECEIVE_BUFFER_BYTES = 2048
    }
}

/**
 * Fallback discovery for networks that drop multicast (common with VPNs, guest Wi-Fi, mesh routers):
 * probes `:8060/query/device-info` on every host in the subnet.
 *
 * @param probe returns the device's info, or null when nothing ECP answers there.
 */
class SubnetScanner(private val probe: suspend (RokuAddress) -> DeviceInfo?) {
    /** Emits each Roku found, completing when every host has been probed. */
    fun scan(hosts: List<String>, parallelism: Int = 48): Flow<Pair<RokuAddress, DeviceInfo>> {
        require(parallelism > 0) { "parallelism must be positive: $parallelism" }
        return channelFlow {
            val permits = Semaphore(parallelism)
            for (host in hosts) {
                // Acquire before launching so a /22 doesn't park a thousand coroutines at once.
                permits.acquire()
                launch {
                    try {
                        val address = RokuAddress(host)
                        val info = try {
                            probe(address)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                        if (info != null) send(address to info)
                    } finally {
                        permits.release()
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    companion object {
        /**
         * Host addresses in [address]'s subnet, excluding network, broadcast and [address] itself,
         * nearest-first. Capped at [limit] so a /16 doesn't mean 65k probes.
         */
        fun hostsInSubnet(address: InetAddress, prefixLength: Int, limit: Int = 1022): List<String> {
            if (address !is Inet4Address) return emptyList()
            require(prefixLength in 0..32) { "IPv4 prefix length out of range: $prefixLength" }
            val self = address.address.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }
            val mask = if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
            val network = self and mask
            val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
            // A /31 is a point-to-point link with no network or broadcast address (RFC 3021).
            val first = if (prefixLength >= 31) network else network + 1
            val last = if (prefixLength >= 31) broadcast else broadcast - 1

            val hosts = ArrayList<String>()
            var distance = 1L
            while (hosts.size < limit) {
                val below = self - distance
                val above = self + distance
                if (below < first && above > last) break
                if (below >= first) hosts += ipv4ToString(below)
                if (above <= last && hosts.size < limit) hosts += ipv4ToString(above)
                distance++
            }
            return hosts
        }

        private fun ipv4ToString(value: Long): String =
            "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
    }
}

/** Wake-on-LAN, for Roku TVs whose network sleeps with the screen. */
object WakeOnLan {
    /** 6 × `0xFF` then the MAC 16 times. Accepts `aa:bb:cc:dd:ee:ff` or `aa-bb-…`. */
    fun magicPacket(mac: String): ByteArray {
        val macBytes = parseMac(mac)
        val packet = ByteArray(6 + 16 * macBytes.size)
        packet.fill(0xFF.toByte(), 0, 6)
        for (i in 0 until 16) macBytes.copyInto(packet, destinationOffset = 6 + i * macBytes.size)
        return packet
    }

    /** Broadcasts the magic packet for [mac] to UDP ports 7 and 9 on [broadcast]. */
    fun send(mac: String, broadcast: InetAddress, socket: DatagramSocket) {
        val packet = magicPacket(mac)
        socket.broadcast = true
        for (port in WOL_PORTS) socket.send(DatagramPacket(packet, packet.size, broadcast, port))
    }

    // 9 (discard) is what most NICs listen on; 7 (echo) is the traditional alternative.
    private val WOL_PORTS = intArrayOf(9, 7)

    /** `aa:bb:cc:dd:ee:ff`, `AA-BB-CC-DD-EE-FF` or `aabbccddeeff`; mixed separators are rejected. */
    private fun parseMac(mac: String): ByteArray {
        val hex = when (mac.length) {
            12 -> mac
            17 -> {
                val separator = mac[2]
                require(separator == ':' || separator == '-') { "Not a MAC address: $mac" }
                require((2 until 17 step 3).all { mac[it] == separator }) { "Not a MAC address: $mac" }
                mac.filterIndexed { index, _ -> index % 3 != 2 }
            }
            else -> throw IllegalArgumentException("Not a MAC address: $mac")
        }
        require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Not a MAC address: $mac" }
        return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
