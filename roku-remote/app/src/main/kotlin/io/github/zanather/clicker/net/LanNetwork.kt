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
import javax.net.SocketFactory

/** The phone's own address on the Wi-Fi network, for scanning the subnet and aiming Wake-on-LAN. */
data class LanAddress(val address: Inet4Address, val prefixLength: Int) {
    /** Directed broadcast for this subnet, e.g. 192.168.8.255 for a /24. */
    val broadcast: InetAddress
        get() {
            val ip = address.address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
            val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
            val bcast = ip or mask.inv()
            return InetAddress.getByAddress(ByteArray(4) { i -> (bcast ushr (24 - 8 * i)).toByte() })
        }
}

/**
 * Keeps track of the Wi-Fi network and hands out sockets that prefer it.
 *
 * With a VPN up, Android may route 192.168.x.x into the tunnel, where the TV can't be reached. Binding
 * each socket to the Wi-Fi [Network] skips the tunnel. A non-bypassable VPN refuses the bind (EPERM); then
 * the socket stays on the default route, which works when that VPN excludes the LAN.
 */
class LanNetwork(context: Context) : Lan {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    @Volatile
    private var wifi: Network? = null

    @Volatile
    private var wifiLink: LinkProperties? = null

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        connectivity.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    wifi = network
                }

                override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                    wifi = network
                    wifiLink = linkProperties
                }

                override fun onLost(network: Network) {
                    if (wifi == network) {
                        wifi = null
                        wifiLink = null
                    }
                }
            },
        )
    }

    /** For OkHttp: plain sockets, bound to Wi-Fi when the platform allows it. */
    val socketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = Socket().also(::preferWifi)

        // OkHttp only calls createSocket(); these exist to satisfy the abstract class.
        override fun createSocket(host: String, port: Int): Socket = unsupported()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket = unsupported()
        override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("Only unconnected sockets are supported")
    }

    /** A UDP socket for SSDP and Wake-on-LAN, bound to Wi-Fi when the platform allows it. */
    override fun openDatagramSocket(): DatagramSocket = DatagramSocket().also(::preferWifi)

    /** The phone's IPv4 address on Wi-Fi/Ethernet, or null when not on a LAN. */
    override fun lanAddress(): LanAddress? {
        val link = wifiLink ?: wifi?.let(connectivity::getLinkProperties) ?: return null
        return link.linkAddresses
            .firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.let { LanAddress(it.address as Inet4Address, it.prefixLength) }
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

    private fun preferWifi(socket: Socket) {
        val network = wifi ?: return
        try {
            network.bindSocket(socket)
        } catch (_: IOException) {
            // Non-bypassable VPN: stay on the default route.
        }
    }

    private fun preferWifi(socket: DatagramSocket) {
        val network = wifi ?: return
        try {
            network.bindSocket(socket)
        } catch (_: IOException) {
            // Non-bypassable VPN: stay on the default route.
        }
    }
}
