package io.github.zanather.clicker.ecp

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A real Hisense Roku TV on OS 12.5 (from the Roam write-up), plus the XML declaration Roku sends. */
const val HISENSE_DEVICE_INFO = """<?xml version="1.0" encoding="UTF-8" ?>
<device-info><udn>28001240-0000-1000-8000-80cbbc98790a</udn><serial-number>X01900SGN8UY</serial-number>
<device-id>S0A3619GN8UY</device-id><vendor-name>Hisense</vendor-name><model-name>6Series-50</model-name>
<model-number>G218X</model-number><is-tv>true</is-tv><is-stick>false</is-stick><screen-size>50</screen-size>
<ui-resolution>1080p</ui-resolution><supports-ethernet>true</supports-ethernet><wifi-mac>80:cb:bc:98:79:0a</wifi-mac>
<ethernet-mac>a0:62:fb:78:29:ee</ethernet-mac><network-type>wifi</network-type>
<friendly-device-name>Hisense•Roku TV - X01900SGN8UY</friendly-device-name><user-device-name />
<software-version>12.5.5</software-version><software-build>4174</software-build><power-mode>PowerOn</power-mode>
<supports-suspend>true</supports-suspend><supports-find-remote>false</supports-find-remote>
<developer-enabled>false</developer-enabled><supports-private-listening>true</supports-private-listening>
<supports-warm-standby>true</supports-warm-standby><headphones-connected>false</headphones-connected>
<supports-ecs-textedit>true</supports-ecs-textedit><supports-ecs-microphone>true</supports-ecs-microphone>
<supports-wake-on-wlan>true</supports-wake-on-wlan><supports-airplay>true</supports-airplay></device-info>
"""

/** The example from Roku's ECP documentation. */
const val ROKU3_DEVICE_INFO = """<?xml version="1.0" encoding="UTF-8" ?>
<device-info>
  <udn>015e5108-9000-1046-8035-b0a737964dfb</udn>
  <serial-number>1GU48T017973</serial-number>
  <device-id>1GU48T017973</device-id>
  <vendor-name>Roku</vendor-name>
  <model-number>4200X</model-number>
  <model-name>Roku 3</model-name>
  <model-region>US</model-region>
  <ui-resolution>1080p</ui-resolution>
  <supports-ethernet>true</supports-ethernet>
  <wifi-mac>b0:a7:37:96:4d:fb</wifi-mac>
  <ethernet-mac>b0:a7:37:96:4d:fa</ethernet-mac>
  <network-type>ethernet</network-type>
  <user-device-name>My Roku 3</user-device-name>
  <software-version>9.3.0</software-version>
  <software-build>09021</software-build>
  <secure-device>true</secure-device>
  <language>en</language>
  <country>US</country>
  <locale>en_US</locale>
  <time-zone>US/Pacific</time-zone>
  <time-zone-offset>-480</time-zone-offset>
  <power-mode>PowerOn</power-mode>
  <supports-suspend>false</supports-suspend>
  <supports-find-remote>false</supports-find-remote>
  <supports-audio-guide>false</supports-audio-guide>
  <developer-enabled>true</developer-enabled>
  <keyed-developer-id>70f6ed9c90cf60718a26f3a7c3e5af1c3ec29558</keyed-developer-id>
  <search-enabled>true</search-enabled>
  <voice-search-enabled>true</voice-search-enabled>
  <notifications-enabled>true</notifications-enabled>
  <notifications-first-use>false</notifications-first-use>
  <supports-private-listening>false</supports-private-listening>
  <headphones-connected>false</headphones-connected>
</device-info>
"""

const val LIMITED_MODE_BODY = "ECP command not allowed in Limited mode."

/** A minimal device-info document with [fields] as its children, in order. */
fun deviceInfoXml(vararg fields: Pair<String, String?>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<device-info>\n")
    for ((name, value) in fields) {
        if (value == null) append("  <$name />\n") else append("  <$name>$value</$name>\n")
    }
    append("</device-info>\n")
}

fun fakeDeviceInfo(serial: String) = DeviceInfo(
    serialNumber = serial,
    deviceId = null,
    name = "TV $serial",
    modelName = null,
    modelNumber = null,
    vendorName = null,
    isTv = true,
    powerMode = PowerMode.On,
    ecpMode = EcpMode.Unknown,
    softwareVersion = null,
    softwareBuild = null,
    wifiMac = null,
    ethernetMac = null,
    networkType = null,
    uptimeSeconds = null,
    supportsFindRemote = false,
    supportsWakeOnWlan = false,
    developerEnabled = false,
    fields = mapOf("serial-number" to serial),
)

/**
 * An unbound [DatagramSocket] that records sends and replays queued datagrams, honoring soTimeout
 * the way a real socket does (0 blocks forever).
 */
class FakeDatagramSocket : DatagramSocket(null as SocketAddress?) {
    class Sent(val data: ByteArray, val address: InetAddress, val port: Int, val atNanos: Long) {
        val text: String get() = String(data, Charsets.UTF_8)
    }

    val sent = CopyOnWriteArrayList<Sent>()

    /** Called after each send with the number of sends so far. */
    @Volatile
    var onSend: (Int) -> Unit = {}

    @Volatile
    var timeoutMillis = 0
        private set

    @Volatile
    var broadcastEnabled = false
        private set

    @Volatile
    var closed = false
        private set

    private val incoming = LinkedBlockingQueue<Pair<String, ByteArray>>()

    fun deliver(sourceIp: String, datagram: String) {
        incoming += sourceIp to datagram.toByteArray(Charsets.UTF_8)
    }

    override fun send(p: DatagramPacket) {
        if (closed) throw SocketException("Socket is closed")
        sent += Sent(p.data.copyOfRange(p.offset, p.offset + p.length), p.address, p.port, System.nanoTime())
        onSend(sent.size)
    }

    override fun receive(p: DatagramPacket) {
        if (closed) throw SocketException("Socket is closed")
        val (sourceIp, bytes) = if (timeoutMillis == 0) {
            incoming.take()
        } else {
            incoming.poll(timeoutMillis.toLong(), TimeUnit.MILLISECONDS) ?: throw SocketTimeoutException("Receive timed out")
        }
        val length = minOf(bytes.size, p.data.size - p.offset)
        bytes.copyInto(p.data, destinationOffset = p.offset, endIndex = length)
        p.length = length
        p.address = InetAddress.getByName(sourceIp)
        p.port = Ssdp.PORT
    }

    override fun setSoTimeout(timeout: Int) {
        timeoutMillis = timeout
    }

    override fun getSoTimeout(): Int = timeoutMillis

    override fun setBroadcast(on: Boolean) {
        broadcastEnabled = on
    }

    override fun getBroadcast(): Boolean = broadcastEnabled

    override fun close() {
        closed = true
        super.close()
    }

    override fun isClosed(): Boolean = closed
}
