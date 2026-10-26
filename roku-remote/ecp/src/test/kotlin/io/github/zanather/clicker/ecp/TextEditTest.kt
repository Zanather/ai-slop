package io.github.zanather.clicker.ecp

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextEditTest {
    private val backspace = RemoteKey.Backspace

    private fun lit(text: String): List<RokuKey> = LiteralKey.of(text)

    @Test
    fun `appending sends only the new characters`() {
        assertThat(keysForEdit("", "roku")).isEqualTo(lit("roku"))
        assertThat(keysForEdit("bre", "breaking")).isEqualTo(lit("aking"))
    }

    @Test
    fun `deleting sends one backspace per removed character`() {
        assertThat(keysForEdit("roku", "ro")).containsExactly(backspace, backspace)
        assertThat(keysForEdit("roku", "")).containsExactly(backspace, backspace, backspace, backspace)
    }

    @Test
    fun `a mid-word correction rewinds to the first difference`() {
        assertThat(keysForEdit("teh office", "the office")).isEqualTo(List(9) { backspace } + lit("he office"))
        assertThat(keysForEdit("stranger thongs", "stranger things")).isEqualTo(List(4) { backspace } + lit("ings"))
    }

    @Test
    fun `a full replacement clears everything then types`() {
        assertThat(keysForEdit("abc", "xyz")).isEqualTo(listOf(backspace, backspace, backspace) + lit("xyz"))
    }

    @Test
    fun `emoji count as one character each`() {
        assertThat(keysForEdit("hi", "hi😀")).containsExactly(LiteralKey(0x1F600))
        assertThat(keysForEdit("hi😀", "hi")).containsExactly(backspace)
        assertThat(keysForEdit("a😀b", "a😃b")).isEqualTo(listOf(backspace, backspace) + lit("😃b"))
        assertThat(keysForEdit("👍🏽", "👍")).containsExactly(backspace) // Skin tone modifier is its own code point.
    }

    @Test
    fun `identical text and empty text need no keys`() {
        assertThat(keysForEdit("same", "same")).isEmpty()
        assertThat(keysForEdit("", "")).isEmpty()
    }

    @Test
    fun `literal keys carry the code point's ECP name`() {
        assertThat(keysForEdit("", "é😀").map { it.ecpName }).containsExactly("Lit_é", "Lit_😀").inOrder()
    }
}
