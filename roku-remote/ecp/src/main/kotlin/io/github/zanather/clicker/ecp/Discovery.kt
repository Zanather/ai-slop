package io.github.zanather.clicker.ecp

import kotlinx.coroutines.flow.Flow
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** SSDP wire format for Roku discovery. Pure functions so they're testable without multicast. */
object Ssdp {
    const val MULTICAST_HOST = "239.255.255.250"
    const val PORT = 1900
    const val SEARCH_TARGET = "roku:ecp"

    /** The `M-SEARCH` datagram payload. */
    fun searchRequest(mxSeconds: Int = 2): String = TODO()

    /**
     * Parses one SSDP response datagram. Returns null for anything that isn't a Roku ECP response.
     * Takes the address from `LOCATION` (falling back to [sourceHost]) and the serial from `USN`.
     */
    fun parseResponse(datagram: String, sourceHost: String): DiscoveredRoku? = TODO()
}

/**
 * Finds Rokus with SSDP `M-SEARCH`. Sends the search a few times (UDP is lossy) and emits each
 * device once, completing after [discover]'s timeout.
 *
 * @param openSocket creates the UDP socket; Android binds it to the Wi-Fi network first.
 */
class SsdpDiscovery(private val openSocket: () -> DatagramSocket = { DatagramSocket() }) {
    fun discover(timeout: Duration = 3.seconds): Flow<DiscoveredRoku> = TODO()
}

/**
 * Fallback discovery for networks that drop multicast (common with VPNs, guest Wi-Fi, mesh routers):
 * probes `:8060/query/device-info` on every host in the subnet.
 *
 * @param probe returns the device's info, or null when nothing ECP answers there.
 */
class SubnetScanner(private val probe: suspend (RokuAddress) -> DeviceInfo?) {
    /** Emits each Roku found, completing when every host has been probed. */
    fun scan(hosts: List<String>, parallelism: Int = 48): Flow<Pair<RokuAddress, DeviceInfo>> = TODO()

    companion object {
        /**
         * Host addresses in [address]'s subnet, excluding network, broadcast and [address] itself,
         * nearest-first. Capped at [limit] so a /16 doesn't mean 65k probes.
         */
        fun hostsInSubnet(address: InetAddress, prefixLength: Int, limit: Int = 1022): List<String> = TODO()
    }
}

/** Wake-on-LAN, for Roku TVs whose network sleeps with the screen. */
object WakeOnLan {
    /** 6 × `0xFF` then the MAC 16 times. Accepts `aa:bb:cc:dd:ee:ff` or `aa-bb-…`. */
    fun magicPacket(mac: String): ByteArray = TODO()

    /** Broadcasts the magic packet for [mac] to UDP ports 7 and 9 on [broadcast]. */
    fun send(mac: String, broadcast: InetAddress, socket: DatagramSocket): Unit = TODO()
}
