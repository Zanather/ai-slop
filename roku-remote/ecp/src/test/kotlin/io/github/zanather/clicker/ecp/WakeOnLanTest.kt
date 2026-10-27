package io.github.zanather.clicker.ecp

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetAddress

class WakeOnLanTest {
    private val mac = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0x0D, 0xEE.toByte(), 0x0F)

    @Test
    fun `magic packet is six 0xFF then the MAC sixteen times`() {
        val packet = WakeOnLan.magicPacket("aa:bb:cc:0d:ee:0f")
        assertThat(packet).hasLength(102)
        assertThat(packet.copyOfRange(0, 6)).isEqualTo(ByteArray(6) { 0xFF.toByte() })
        for (i in 0 until 16) {
            assertThat(packet.copyOfRange(6 + i * 6, 12 + i * 6)).isEqualTo(mac)
        }
    }

    @Test
    fun `colon, dash and bare MAC formats are equivalent`() {
        val expected = WakeOnLan.magicPacket("aa:bb:cc:0d:ee:0f")
        assertThat(WakeOnLan.magicPacket("AA-BB-CC-0D-EE-0F")).isEqualTo(expected)
        assertThat(WakeOnLan.magicPacket("AA:BB:CC:0D:EE:0F")).isEqualTo(expected)
        assertThat(WakeOnLan.magicPacket("aabbcc0dee0f")).isEqualTo(expected)
        assertThat(WakeOnLan.magicPacket("AaBbCc0DeE0f")).isEqualTo(expected)
    }

    @Test
    fun `anything else is rejected`() {
        val invalid = listOf(
            "",
            "aa:bb:cc:dd:ee",
            "aa:bb:cc:dd:ee:ff:00",
            "aa:bb:cc:dd:ee:gg",
            "aa:bb-cc:dd:ee:ff",
            "aa.bb.cc.dd.ee.ff",
            "aabbccddeef",
            "aabbccddeeff00",
            "+abbccddeeff",
            " aa:bb:cc:dd:ee:ff",
            "aa:bb:cc:dd:ee:ff ",
            "a:bb:cc:dd:ee:fff",
        )
        for (mac in invalid) {
            assertThrows("for \"$mac\"", IllegalArgumentException::class.java) { WakeOnLan.magicPacket(mac) }
        }
    }

    @Test
    fun `send broadcasts to ports 9 and 7`() {
        val socket = FakeDatagramSocket()
        val broadcast = InetAddress.getByName("192.168.8.255")
        WakeOnLan.send("aa:bb:cc:0d:ee:0f", broadcast, socket)

        assertThat(socket.broadcastEnabled).isTrue()
        assertThat(socket.sent.map { it.port }).containsExactly(9, 7).inOrder()
        for (sent in socket.sent) {
            assertThat(sent.address).isEqualTo(broadcast)
            assertThat(sent.data).isEqualTo(WakeOnLan.magicPacket("aa:bb:cc:0d:ee:0f"))
        }
        socket.close()
    }

    @Test
    fun `send rejects a bad MAC before touching the socket`() {
        val socket = FakeDatagramSocket()
        assertThrows(IllegalArgumentException::class.java) {
            WakeOnLan.send("nope", InetAddress.getByName("255.255.255.255"), socket)
        }
        assertThat(socket.sent).isEmpty()
        socket.close()
    }
}
