package io.github.zanather.clicker.net

import java.net.DatagramSocket
import java.net.InetAddress

/** The bits of the local network the session needs. [LanNetwork] on Android; fakes in tests. */
interface Lan {
    /** A UDP socket for SSDP and Wake-on-LAN. */
    fun openDatagramSocket(): DatagramSocket

    /** The phone's own address on the LAN, or null when it isn't on one. */
    fun lanAddress(): LanAddress?

    /** Where to send Wake-on-LAN packets. */
    fun broadcastAddresses(): List<InetAddress>

    /** Runs [block] with multicast reception enabled. */
    suspend fun <T> withMulticastLock(block: suspend () -> T): T
}
