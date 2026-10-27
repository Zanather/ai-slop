package io.github.zanather.clicker.ui.remote

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The sheet's own keys must queue behind typed text (the session only orders [RecordingActions.sendKeys]
 * calls against each other), and a held Backspace must delete as much on the TV as in the field.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class KeyboardSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()

    private val field get() = compose.onNode(hasSetTextAction())

    private fun openSheet() {
        compose.setContent {
            ClickerTheme {
                RemoteScreen(RemoteSamples.connectedTv, actions, onOpenApps = {}, onOpenDevices = {}, onOpenSettings = {})
            }
        }
        compose.onNodeWithContentDescription("Keyboard").performClick()
        compose.waitForIdle()
    }

    private fun type(text: String) {
        field.performTextInput(text)
        compose.waitForIdle()
        actions.calls.clear()
    }

    private fun fieldText(): String =
        field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @Test
    fun enterAndSendQueueBehindTheTyping() {
        openSheet()
        field.performTextInput("hi")
        compose.onNodeWithContentDescription("Enter").performTouchInput {
            down(center)
            up()
        }
        field.performImeAction()
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("sendKeys Lit_h,Lit_i", "sendKeys Enter", "sendKeys Enter").inOrder()
    }

    @Test
    fun backspaceTapQueuesBehindTheTyping() {
        openSheet()
        type("abc")
        compose.onNodeWithContentDescription("Backspace").performTouchInput {
            down(center)
            up()
        }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("sendKeys Backspace")
        assertThat(fieldText()).isEqualTo("ab")
    }

    @Test
    fun heldBackspaceDeletesExactlyWhatTheFieldLoses() {
        openSheet()
        type("hello world")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Backspace").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Backspace").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        val deleted = "hello world".length - fieldText().length
        assertThat(deleted).isGreaterThan(3)
        assertThat(actions.calls).containsExactlyElementsIn(List(deleted) { "sendKeys Backspace" })
    }

    @Test
    fun heldBackspaceStopsAtAnEmptyField() {
        openSheet()
        type("ab")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Backspace").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Backspace").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        assertThat(fieldText()).isEmpty()
        assertThat(actions.calls).containsExactly("sendKeys Backspace", "sendKeys Backspace")
    }

    @Test
    fun accessibilityBackspaceSendsOneAndTrimsOne() {
        openSheet()
        type("ab")
        compose.onNodeWithContentDescription("Backspace").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("sendKeys Backspace")
        assertThat(fieldText()).isEqualTo("a")
    }
}
