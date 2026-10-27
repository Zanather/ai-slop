package io.github.zanather.clicker.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory

/** The phone's own address on the Wi-Fi network, for scanning the subnet and aiming Wake-on-LAN. */
data class LanAddress(val address: Inet4Address, val prefixLength: Int) {
    private val mask: Int get() = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)

    /** Directed broadcast for this subnet, e.g. 192.168.8.255 for a /24. */
    val broadcast: InetAddress
        get() {
            val bcast = address.address.toInt() or mask.inv()
            return InetAddress.getByAddress(ByteArray(4) { i -> (bcast ushr (24 - 8 * i)).toByte() })
        }

    /** Whether [host] is an IPv4 address on this subnet. Hostnames never match: resolving one here could block. */
    operator fun contains(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false }
        val ip = octets.fold(0) { acc, octet -> (acc shl 8) or octet }
        return (ip and mask) == (address.address.toInt() and mask)
    }

    private fun ByteArray.toInt() = fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
}

/**
 * Keeps track of the Wi-Fi and Ethernet networks and hands out sockets that prefer them.
 *
 * With a VPN up, Android may route 192.168.x.x into the tunnel, where the TV can't be reached. Binding
 * each socket to the LAN [Network] skips the tunnel. A non-bypassable VPN refuses the bind (EPERM); then
 * the socket stays on the default route, which works when that VPN excludes the LAN.
 */
class LanNetwork(context: Context) : Lan {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    /** Every matching network that is up: a phone on a USB-C dock can have Wi-Fi and Ethernet at once. */
    private val networks = ConcurrentHashMap<Network, LanLink>()

    /** The selected TV's address, so that with two LANs up the sockets go to the one the TV is on. */
    @Volatile
    var tvHost: String? = null

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivity.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    networks.putIfAbsent(network, LanLink())
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    networks.compute(network) { _, link -> (link ?: LanLink()).copy(isWifi = wifi) }
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    networks.compute(network) { _, link -> (link ?: LanLink()).copy(properties = linkProperties) }
                }

                override fun onLost(network: Network) {
                    networks.remove(network)
                }
            },
        )
    }

    /** For OkHttp: plain sockets, bound to the LAN when the platform allows it. */
    val socketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = Socket().also(::bindToLan)

        // OkHttp only calls createSocket(); these exist to satisfy the abstract class.
        override fun createSocket(host: String, port: Int): Socket = unsupported()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = unsupported()
        override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Only unconnected sockets are supported")
    }

    /** A UDP socket for SSDP and Wake-on-LAN, bound to the LAN when the platform allows it. */
    override fun openDatagramSocket(): DatagramSocket = DatagramSocket().also(::bindToLan)

    /** The phone's IPv4 address on the network sockets are bound to, or null when not on a LAN. */
    override fun lanAddress(): LanAddress? {
        val (network, link) = chosen() ?: return null
        return link.address ?: connectivity.getLinkProperties(network)?.lanAddress()
    }

    /** Where to send Wake-on-LAN: the subnet's directed broadcast first, then the limited broadcast. */
    override fun broadcastAddresses(): List<InetAddress> =
        listOfNotNull(lanAddress()?.broadcast, InetAddress.getByName("255.255.255.255")).distinct()

    /**
     * Holds a multicast lock while [block] runs. Most Wi-Fi drivers filter multicast to save power; SSDP
     * replies are unicast, but some routers answer in ways only the lock lets through.
     */
    override suspend fun <T> withMulticastLock(block: suspend () -> T): T {
        val lock = wifiManager?.createMulticastLock("clicker-ssdp")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        try {
            return block()
        } finally {
            runCatching { lock?.release() }
        }
    }

    /**
     * The network to use, picked the same way every time rather than by whichever callback came last: the one
     * whose subnet holds the TV, then the system's default, then Wi-Fi, then any.
     */
    private fun chosen(): Pair<Network, LanLink>? {
        val up = networks.entries.map { it.key to it.value }.ifEmpty { return null }
        val tv = tvHost
        val default = connectivity.activeNetwork
        return up.minWith(
            compareByDescending<Pair<Network, LanLink>> { (_, link) -> tv != null && link.address?.contains(tv) == true }
                .thenByDescending { (network, _) -> network == default }
                .thenByDescending { (_, link) -> link.isWifi }
                .thenBy { (network, _) -> network.networkHandle },
        )
    }

    private fun bindToLan(socket: Socket) {
        val network = chosen()?.first ?: return
        try {
            network.bindSocket(socket)
        } catch (_: IOException) {
            // Non-bypassable VPN: stay on the default route.
        }
    }

    private fun bindToLan(socket: DatagramSocket) {
        val network = chosen()?.first ?: return
        try {
            network.bindSocket(socket)
        } catch (_: IOException) {
            // Non-bypassable VPN: stay on the default route.
        }
    }

    private data class LanLink(val isWifi: Boolean = false, val properties: LinkProperties? = null) {
        val address: LanAddress? get() = properties?.lanAddress()
    }
}

private fun LinkProperties.lanAddress(): LanAddress? =
    linkAddresses
        .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
        ?.let { LanAddress(it.address as Inet4Address, it.prefixLength) }
