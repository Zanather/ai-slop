package io.github.zanather.clicker.ecp

/**
 * Keys that turn the text already on the TV ([sent]) into what the user now has ([current]):
 * one [RemoteKey.Backspace] per code point after the common prefix, then a [LiteralKey] per added code point.
 *
 * Lets the keyboard sheet mirror a phone text field live, including mid-word corrections and
 * autocorrect replacements. Code-point aware, so an emoji is one Backspace, not two.
 */
fun keysForEdit(sent: String, current: String): List<RokuKey> {
    val old = sent.codePoints().toArray()
    val new = current.codePoints().toArray()
    var common = 0
    while (common < old.size && common < new.size && old[common] == new[common]) common++
    return buildList {
        repeat(old.size - common) { add(RemoteKey.Backspace) }
        for (i in common until new.size) add(LiteralKey(new[i]))
    }
}
