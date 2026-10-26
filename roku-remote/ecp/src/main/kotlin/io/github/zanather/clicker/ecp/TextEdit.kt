package io.github.zanather.clicker.ecp

/**
 * Keys that turn the text already on the TV ([sent]) into what the user now has ([current]):
 * one [RemoteKey.Backspace] per code point after the common prefix, then a [LiteralKey] per added code point.
 *
 * Lets the keyboard sheet mirror a phone text field live, including mid-word corrections and
 * autocorrect replacements. Code-point aware, so an emoji is one Backspace, not two.
 */
fun keysForEdit(sent: String, current: String): List<RokuKey> = TODO()
