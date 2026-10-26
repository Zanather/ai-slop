package io.github.zanather.clicker.ecp

import org.w3c.dom.Element
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Thrown by the parsers below for a well-formed document that isn't what ECP should have sent. */
internal class EcpXmlException(message: String) : Exception(message)

/**
 * Parses [xml] and returns its root element.
 *
 * ECP responses never carry a DTD, so any `<!DOCTYPE` or `<!ENTITY` is refused before the parser sees it.
 * That keeps XXE safety independent of which hardening features the platform's parser happens to support.
 */
internal fun parseXml(xml: String): Element {
    if (xml.contains("<!DOCTYPE", ignoreCase = true) || xml.contains("<!ENTITY", ignoreCase = true)) {
        throw EcpXmlException("Refusing a document with a DTD")
    }
    return parseHardened(xml)
}

/** The parser alone, without [parseXml]'s DTD pre-check. */
internal fun parseHardened(xml: String): Element {
    val builder = hardenedFactory().newDocumentBuilder()
    builder.setEntityResolver { _, _ -> throw EcpXmlException("Refusing an external entity") }
    builder.setErrorHandler(ThrowingErrorHandler)
    // A reader, not bytes: what the parser sees is exactly the text the DTD pre-check saw.
    val text = xml.removePrefix("﻿").trimStart()
    return builder.parse(InputSource(StringReader(text))).documentElement
}

internal fun parseDeviceInfo(xml: String): DeviceInfo {
    val root = parseXml(xml).requireName("device-info")
    val fields = LinkedHashMap<String, String>()
    for (child in root.childElements()) {
        fields.putIfAbsent(child.nodeName, child.textContent.trim())
    }

    fun text(name: String): String? = fields[name]?.takeIf { it.isNotBlank() }
    fun flag(name: String): Boolean = fields[name].equals("true", ignoreCase = true)

    val serialNumber = text("serial-number") ?: throw EcpXmlException("device-info has no serial-number")
    return DeviceInfo(
        serialNumber = serialNumber,
        deviceId = text("device-id"),
        // With no name at all, the serial still tells two TVs apart.
        name = NAME_FIELDS.firstNotNullOfOrNull(::text) ?: "Roku $serialNumber",
        modelName = text("model-name"),
        modelNumber = text("model-number"),
        vendorName = text("vendor-name"),
        isTv = flag("is-tv"),
        powerMode = PowerMode.parse(text("power-mode")),
        ecpMode = EcpMode.parse(text("ecp-setting-mode")),
        softwareVersion = text("software-version"),
        softwareBuild = text("software-build"),
        wifiMac = text("wifi-mac"),
        ethernetMac = text("ethernet-mac"),
        networkType = text("network-type"),
        uptimeSeconds = text("uptime")?.toLongOrNull(),
        supportsFindRemote = flag("supports-find-remote"),
        supportsWakeOnWlan = flag("supports-wake-on-wlan"),
        developerEnabled = flag("developer-enabled"),
        fields = fields,
    )
}

internal fun parseApps(xml: String): List<RokuApp> =
    parseXml(xml).requireName("apps").childElements()
        .filter { it.nodeName == "app" }
        .mapNotNull { it.toRokuApp() }

internal fun parseActiveApp(xml: String): ActiveApp {
    val children = parseXml(xml).requireName("active-app").childElements()
    val app = children.firstOrNull { it.nodeName == "app" }
    return ActiveApp(
        app = app?.takeUnless { it.isHomeScreen() }?.toRokuApp(),
        screensaver = children.firstOrNull { it.nodeName == "screensaver" }?.toRokuApp(),
    )
}

private val NAME_FIELDS = listOf("user-device-name", "friendly-device-name", "default-device-name", "model-name")

private fun hardenedFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = false
    isValidating = false
    // Android's parser rejects some of these; each is best effort and the DTD pre-check backs them all up.
    bestEffort { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    bestEffort { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    bestEffort { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    bestEffort { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    bestEffort { isXIncludeAware = false }
    bestEffort { isExpandEntityReferences = false }
}

private inline fun bestEffort(block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        // Unsupported on this platform's parser.
    }
}

/** The JDK's default handler prints fatal errors to stderr before throwing; throw quietly instead. */
private object ThrowingErrorHandler : ErrorHandler {
    override fun warning(exception: SAXParseException) = Unit

    override fun error(exception: SAXParseException) {
        throw exception
    }

    override fun fatalError(exception: SAXParseException) {
        throw exception
    }
}

private fun Element.requireName(name: String): Element =
    if (nodeName == name) this else throw EcpXmlException("Expected <$name>, got <$nodeName>")

private fun Element.childElements(): List<Element> {
    val nodes = childNodes
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

private fun Element.attribute(name: String): String? = getAttribute(name).trim().ifEmpty { null }

private fun Element.isHomeScreen(): Boolean =
    attribute("id") == null ||
        attribute("type").equals("home", ignoreCase = true) ||
        attribute("ui-location").equals("home", ignoreCase = true)

/** Null without an id: there's nothing to launch or fetch an icon for. */
private fun Element.toRokuApp(): RokuApp? {
    val id = attribute("id") ?: return null
    return RokuApp(
        id = id,
        name = textContent.replace(' ', ' ').trim(),
        type = attribute("type"),
        version = attribute("version"),
    )
}
