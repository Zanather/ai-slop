package io.github.zanather.clicker.ecp

/**
 * A key the Roku understands, named exactly as ECP expects in `/keypress/<name>`.
 *
 * Names are case-sensitive on the wire. [ecpName] is the raw, unencoded path segment;
 * [EcpClient] is responsible for percent-encoding it.
 */
sealed interface RokuKey {
    val ecpName: String

    /** Keys that repeat when held, like a physical remote's arrows and volume rocker. */
    val repeatsWhenHeld: Boolean get() = false
}

/** The fixed set of named remote keys. */
enum class RemoteKey(
    override val ecpName: String,
    override val repeatsWhenHeld: Boolean = false,
) : RokuKey {
    Home("Home"),
    Back("Back"),
    Select("Select"),
    Up("Up", repeatsWhenHeld = true),
    Down("Down", repeatsWhenHeld = true),
    Left("Left", repeatsWhenHeld = true),
    Right("Right", repeatsWhenHeld = true),
    InstantReplay("InstantReplay"),
    Info("Info"),
    Rev("Rev"),
    Play("Play"),
    Fwd("Fwd"),
    Backspace("Backspace", repeatsWhenHeld = true),
    Enter("Enter"),
    Search("Search"),
    FindRemote("FindRemote"),

    // Roku TV only.
    VolumeUp("VolumeUp", repeatsWhenHeld = true),
    VolumeDown("VolumeDown", repeatsWhenHeld = true),
    VolumeMute("VolumeMute"),
    PowerOff("PowerOff"),
    PowerOn("PowerOn"),
    Power("Power"),
    ChannelUp("ChannelUp", repeatsWhenHeld = true),
    ChannelDown("ChannelDown", repeatsWhenHeld = true),
    InputTuner("InputTuner"),
    InputHdmi1("InputHDMI1"),
    InputHdmi2("InputHDMI2"),
    InputHdmi3("InputHDMI3"),
    InputHdmi4("InputHDMI4"),
    InputAv1("InputAV1"),
}

/**
 * A single typed character, sent as `Lit_<char>`.
 *
 * Holds a code point rather than a [Char] so characters outside the BMP (emoji) survive.
 */
data class LiteralKey(val codePoint: Int) : RokuKey {
    override val ecpName: String get() = "Lit_" + String(Character.toChars(codePoint))

    companion object {
        /** Splits [text] into one [LiteralKey] per Unicode code point. */
        fun of(text: String): List<LiteralKey> = text.codePoints().toArray().map(::LiteralKey)
    }
}
