package io.github.zanather.clicker.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import java.net.Inet4Address
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class LanNetworkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val lan = LanNetwork(context)
    private val callback = shadowOf(connectivity).networkCallbacks.single()

    // Robolectric's own default network is mobile; these ids stay clear of it.
    private val wifi = ShadowNetwork.newInstance(100)
    private val ethernet = ShadowNetwork.newInstance(101)

    @Test
    fun withNoLanThereIsNoAddressAndSocketsStayOnTheDefaultRoute() {
        assertThat(lan.lanAddress()).isNull()
        lan.openDatagramSocket().use { socket ->
            assertThat(shadowOf(wifi).isSocketBound(socket)).isFalse()
        }
    }

    @Test
    fun theLastNetworkToChangeDoesNotTakeOver() {
        connect(wifi, NetworkCapabilities.TRANSPORT_WIFI, "192.168.1.23", 24)
        connect(ethernet, NetworkCapabilities.TRANSPORT_ETHERNET, "10.0.0.5", 24)
        // A routine refresh (e.g. an IPv6 address renewing) on the dock's Ethernet.
        callback.onLinkPropertiesChanged(ethernet, link("10.0.0.5", 24))

        assertUsing(wifi, "192.168.1.23", 24)
    }

    @Test
    fun losingOneNetworkKeepsTheOneStillUp() {
        connect(wifi, NetworkCapabilities.TRANSPORT_WIFI, "192.168.1.23", 24)
        connect(ethernet, NetworkCapabilities.TRANSPORT_ETHERNET, "10.0.0.5", 24)
        lan.tvHost = "10.0.0.40"
        assertUsing(ethernet, "10.0.0.5", 24)

        callback.onLost(ethernet)

        assertUsing(wifi, "192.168.1.23", 24)
    }

    @Test
    fun theNetworkWhoseSubnetHoldsTheTvWins() {
        connect(wifi, NetworkCapabilities.TRANSPORT_WIFI, "192.168.1.23", 24)
        connect(ethernet, NetworkCapabilities.TRANSPORT_ETHERNET, "10.0.0.5", 24)

        lan.tvHost = "10.0.0.40"
        assertUsing(ethernet, "10.0.0.5", 24)

        lan.tvHost = "192.168.1.50"
        assertUsing(wifi, "192.168.1.23", 24)

        // A TV on neither subnet (or saved by name) falls back to the usual order.
        lan.tvHost = "172.16.0.9"
        assertUsing(wifi, "192.168.1.23", 24)
    }

    @Test
    fun theSystemDefaultBeatsWifiWhenTheTvGivesNoHint() {
        // Robolectric reports the active NetworkInfo's type as the netId of the default network.
        val defaultEthernet = ShadowNetwork.newInstance(ConnectivityManager.TYPE_ETHERNET)
        @Suppress("DEPRECATION")
        val info = ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_ETHERNET,
            0,
            true,
            NetworkInfo.State.CONNECTED,
        )
        shadowOf(connectivity).addNetwork(defaultEthernet, info)
        shadowOf(connectivity).setActiveNetworkInfo(info)
        connect(defaultEthernet, NetworkCapabilities.TRANSPORT_ETHERNET, "10.0.0.5", 24)
        connect(wifi, NetworkCapabilities.TRANSPORT_WIFI, "192.168.1.23", 24)

        assertUsing(defaultEthernet, "10.0.0.5", 24)
    }

    @Test
    fun subnetMembershipOnlyMatchesIpv4LiteralsOnThatSubnet() {
        val home = LanAddress(ipv4("192.168.1.23"), 24)

        assertThat("192.168.1.200" in home).isTrue()
        assertThat("192.168.1.0" in home).isTrue()
        assertThat("192.168.2.1" in home).isFalse()
        assertThat("10.0.0.1" in home).isFalse()
        assertThat("living-room.local" in home).isFalse()
        assertThat("192.168.1" in home).isFalse()
        assertThat("192.168.1.256" in home).isFalse()
        assertThat("" in home).isFalse()
        assertThat("10.200.3.4" in LanAddress(ipv4("10.0.0.5"), 8)).isTrue()
        assertThat("8.8.8.8" in LanAddress(ipv4("10.0.0.5"), 0)).isTrue()
    }

    private fun connect(network: Network, transport: Int, address: String, prefixLength: Int) {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addTransportType(transport)
        callback.onAvailable(network)
        callback.onCapabilitiesChanged(network, capabilities)
        callback.onLinkPropertiesChanged(network, link(address, prefixLength))
    }

    /** The address that scanning and Wake-on-LAN use, and the network both kinds of socket are bound to, agree. */
    private fun assertUsing(network: Network, address: String, prefixLength: Int) {
        assertThat(lan.lanAddress()).isEqualTo(LanAddress(ipv4(address), prefixLength))
        lan.openDatagramSocket().use { socket -> assertThat(shadowOf(network).isSocketBound(socket)).isTrue() }
        lan.socketFactory.createSocket().use { socket -> assertThat(shadowOf(network).isSocketBound(socket)).isTrue() }
    }

    private fun link(address: String, prefixLength: Int) = LinkProperties().apply {
        // LinkAddress's constructors are system API; Robolectric runs the real class, so reflection reaches them.
        val linkAddress = LinkAddress::class.java
            .getConstructor(InetAddress::class.java, Int::class.javaPrimitiveType)
            .newInstance(ipv4(address), prefixLength)
        setLinkAddresses(listOf(linkAddress))
    }

    private fun ipv4(address: String) = InetAddress.getByName(address) as Inet4Address
}
