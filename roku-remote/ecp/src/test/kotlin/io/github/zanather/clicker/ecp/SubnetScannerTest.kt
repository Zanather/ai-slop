package io.github.zanather.clicker.ecp

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class SubnetScannerTest {
    private fun hosts(address: String, prefix: Int, limit: Int = 1022) =
        SubnetScanner.hostsInSubnet(InetAddress.getByName(address), prefix, limit)

    @Test
    fun `a 24 excludes network, broadcast and self, nearest first`() {
        val hosts = hosts("192.168.1.10", 24)
        assertThat(hosts).hasSize(253)
        assertThat(hosts).containsNoDuplicates()
        assertThat(hosts).containsNoneOf("192.168.1.0", "192.168.1.255", "192.168.1.10")
        assertThat(hosts).containsAtLeast("192.168.1.1", "192.168.1.254")
        assertThat(hosts.take(6)).containsExactly(
            "192.168.1.9", "192.168.1.11", "192.168.1.8", "192.168.1.12", "192.168.1.7", "192.168.1.13",
        ).inOrder()
        // Past .1 only the upper side remains.
        assertThat(hosts.subList(16, 20)).containsExactly("192.168.1.1", "192.168.1.19", "192.168.1.20", "192.168.1.21").inOrder()
        assertThat(hosts.last()).isEqualTo("192.168.1.254")
    }

    @Test
    fun `a host at the edge of its subnet`() {
        val hosts = hosts("192.168.1.1", 24)
        assertThat(hosts).hasSize(253)
        assertThat(hosts.take(3)).containsExactly("192.168.1.2", "192.168.1.3", "192.168.1.4").inOrder()
        assertThat(hosts).doesNotContain("192.168.1.0")
    }

    @Test
    fun `a 30 has two hosts`() {
        assertThat(hosts("10.0.0.1", 30)).containsExactly("10.0.0.2")
        assertThat(hosts("10.0.0.6", 30)).containsExactly("10.0.0.5")
    }

    @Test
    fun `a 31 is a point-to-point link with no network or broadcast address`() {
        assertThat(hosts("10.0.0.0", 31)).containsExactly("10.0.0.1")
        assertThat(hosts("10.0.0.1", 31)).containsExactly("10.0.0.0")
    }

    @Test
    fun `a 32 has nobody else`() {
        assertThat(hosts("10.0.0.1", 32)).isEmpty()
    }

    @Test
    fun `a 16 is capped by the limit`() {
        val hosts = hosts("172.16.5.5", 16)
        assertThat(hosts).hasSize(1022)
        assertThat(hosts).containsNoDuplicates()
        assertThat(hosts.take(2)).containsExactly("172.16.5.4", "172.16.5.6").inOrder()
        assertThat(hosts).containsAtLeast("172.16.4.255", "172.16.6.0") // Mid-subnet .0 and .255 are real hosts.
        assertThat(hosts).containsNoneOf("172.16.0.0", "172.16.255.255", "172.16.5.5")

        assertThat(hosts("172.16.5.5", 16, limit = 10)).hasSize(10)
        assertThat(hosts("172.16.5.5", 16, limit = 0)).isEmpty()
    }

    @Test
    fun `addresses past the top of the address space work`() {
        val hosts = hosts("255.255.255.253", 24)
        assertThat(hosts.take(3)).containsExactly("255.255.255.252", "255.255.255.254", "255.255.255.251").inOrder()
        assertThat(hosts).doesNotContain("255.255.255.255")
    }

    @Test
    fun `IPv6 yields nothing`() {
        assertThat(hosts("fe80::1", 64)).isEmpty()
        assertThat(hosts("2001:db8::1", 120)).isEmpty()
    }

    @Test
    fun `an invalid prefix length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { hosts("10.0.0.1", 33) }
        assertThrows(IllegalArgumentException::class.java) { hosts("10.0.0.1", -1) }
    }

    @Test
    fun `scan emits what the probe finds and treats a failing probe as nothing`() = runTest {
        val probed = ConcurrentHashMap.newKeySet<RokuAddress>()
        val scanner = SubnetScanner { address ->
            probed += address
            when (address.host) {
                "10.0.0.5" -> fakeDeviceInfo("FIVE")
                "10.0.0.9" -> fakeDeviceInfo("NINE")
                "10.0.0.7" -> throw EcpException.Unreachable(address, java.io.IOException("refused"))
                "10.0.0.8" -> error("probe bug")
                else -> null
            }
        }
        val hosts = (1..20).map { "10.0.0.$it" }
        val found = scanner.scan(hosts, parallelism = 4).toList()
        assertThat(found.map { it.first to it.second.serialNumber }).containsExactly(
            RokuAddress("10.0.0.5") to "FIVE",
            RokuAddress("10.0.0.9") to "NINE",
        )
        assertThat(probed.map { it.host }).containsExactlyElementsIn(hosts)
        assertThat(probed.map { it.port }.toSet()).containsExactly(8060)
    }

    @Test
    fun `a probe cancelling itself doesn't stop the scan`() = runTest {
        val scanner = SubnetScanner { address ->
            if (address.host == "10.0.0.1") throw CancellationException("probe gave up")
            fakeDeviceInfo(address.host)
        }
        val found = scanner.scan(listOf("10.0.0.1", "10.0.0.2", "10.0.0.3"), parallelism = 2).toList()
        assertThat(found.map { it.first.host }).containsExactly("10.0.0.2", "10.0.0.3")
    }

    @Test
    fun `scan never runs more than parallelism probes at once`() = runTest {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val scanner = SubnetScanner { address ->
            val now = inFlight.incrementAndGet()
            maxInFlight.accumulateAndGet(now, ::maxOf)
            delay(15)
            inFlight.decrementAndGet()
            if (address.host.endsWith(".3")) fakeDeviceInfo("THREE") else null
        }
        val found = scanner.scan((1..60).map { "10.0.0.$it" }, parallelism = 5).toList()
        assertThat(found.map { it.first.host }).containsExactly("10.0.0.3")
        assertThat(maxInFlight.get()).isAtMost(5)
        assertThat(maxInFlight.get()).isAtLeast(2)
        assertThat(inFlight.get()).isEqualTo(0)
    }

    @Test
    fun `scan of no hosts completes empty`() = runTest {
        assertThat(SubnetScanner { fakeDeviceInfo("X") }.scan(emptyList()).toList()).isEmpty()
    }

    @Test
    fun `scan rejects non-positive parallelism`() {
        assertThrows(IllegalArgumentException::class.java) { SubnetScanner { null }.scan(listOf("10.0.0.1"), parallelism = 0) }
    }
}
