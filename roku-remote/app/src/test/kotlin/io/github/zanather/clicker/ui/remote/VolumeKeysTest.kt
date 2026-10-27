package io.github.zanather.clicker.ui.remote

import android.os.Looper
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.activity.ComponentDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

private fun down(code: Int, repeat: Int = 0) = KeyEvent(0L, repeat * 50L, KeyEvent.ACTION_DOWN, code, repeat)

private fun up(code: Int, flags: Int = 0) =
    KeyEvent(0L, 1_000L, KeyEvent.ACTION_UP, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags)

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class VolumeKeysTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private var enabled = true
    private val forwarder = VolumeKeyForwarder(actions) { enabled }

    @Test
    fun heldButtonHoldsTheTvKeyAndIgnoresSystemRepeats() {
        assertThat(forwarder.handle(down(KeyEvent.KEYCODE_VOLUME_UP))).isTrue()
        for (repeat in 1..20) assertThat(forwarder.handle(down(KeyEvent.KEYCODE_VOLUME_UP, repeat))).isTrue()
        assertThat(forwarder.handle(up(KeyEvent.KEYCODE_VOLUME_UP))).isTrue()
        assertThat(actions.calls).containsExactly("holdStart VolumeUp", "holdEnd VolumeUp").inOrder()
    }

    @Test
    fun canceledKeyUpEndsTheHold() {
        forwarder.handle(down(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertThat(forwarder.handle(up(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.FLAG_CANCELED))).isTrue()
        assertThat(actions.calls).containsExactly("holdStart VolumeDown", "holdEnd VolumeDown").inOrder()
    }

    @Test
    fun muteSendsOnePress() {
        forwarder.handle(down(KeyEvent.KEYCODE_VOLUME_MUTE))
        forwarder.handle(down(KeyEvent.KEYCODE_VOLUME_MUTE, repeat = 1))
        assertThat(forwarder.handle(up(KeyEvent.KEYCODE_VOLUME_MUTE))).isTrue()
        assertThat(actions.calls).containsExactly("press VolumeMute")
    }

    @Test
    fun switchedOffLeavesThePhoneVolumeAlone() {
        enabled = false
        assertThat(forwarder.handle(down(KeyEvent.KEYCODE_VOLUME_UP))).isFalse()
        assertThat(forwarder.handle(up(KeyEvent.KEYCODE_VOLUME_UP))).isFalse()
        assertThat(forwarder.handle(down(KeyEvent.KEYCODE_BACK))).isFalse()
        assertThat(actions.calls).isEmpty()
    }

    @Test
    fun releaseEndsAHoldWhoseKeyUpNeverCame() {
        forwarder.handle(down(KeyEvent.KEYCODE_VOLUME_UP))
        forwarder.release()
        forwarder.release()
        assertThat(actions.calls).containsExactly("holdStart VolumeUp", "holdEnd VolumeUp").inOrder()
    }

    private fun show() {
        compose.setContent {
            ClickerTheme {
                CompositionLocalProvider(LocalVolumeKeyForwarder provides forwarder) {
                    RemoteScreen(RemoteSamples.connectedTv, actions, onOpenApps = {}, onOpenDevices = {}, onOpenSettings = {})
                }
            }
        }
    }

    @Test
    fun keyboardSheetSendsVolumeButtonsToTheTv() {
        show()
        compose.onNodeWithContentDescription("Keyboard").performClick()
        compose.waitForIdle()

        // The sheet is its own window: the keys reach it, not the activity.
        deliverToNewestWindow(down(KeyEvent.KEYCODE_VOLUME_UP))
        deliverToNewestWindow(up(KeyEvent.KEYCODE_VOLUME_UP))
        assertThat(actions.calls).containsExactly("holdStart VolumeUp", "holdEnd VolumeUp").inOrder()
    }

    @Test
    fun closingTheSheetMidPressEndsTheHold() {
        show()
        compose.onNodeWithContentDescription("Keyboard").performClick()
        compose.waitForIdle()
        val sheet = ShadowDialog.getLatestDialog() as ComponentDialog
        deliverToNewestWindow(down(KeyEvent.KEYCODE_VOLUME_DOWN))

        compose.runOnIdle { sheet.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("holdStart VolumeDown", "holdEnd VolumeDown").inOrder()
    }

    @Test
    fun inputMenuSendsVolumeButtonsToTheTv() {
        show()
        compose.onNodeWithText("Input").performClick()
        compose.waitForIdle()

        deliverToNewestWindow(down(KeyEvent.KEYCODE_VOLUME_UP))
        deliverToNewestWindow(up(KeyEvent.KEYCODE_VOLUME_UP))
        assertThat(actions.calls).containsExactly("holdStart VolumeUp", "holdEnd VolumeUp").inOrder()
    }
}

/**
 * Sends [event] down the newest window's real input pipeline, as the system does for the focused window.
 * Robolectric only hands keys straight to a root view, which skips the unhandled-key pass, and never
 * focuses dialog or popup windows, whose pipeline drops keys until they are.
 */
private fun deliverToNewestWindow(event: KeyEvent) {
    val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
    val roots = global.javaClass.getDeclaredField("mRoots").apply { isAccessible = true }.get(global) as List<*>
    val root = checkNotNull(roots.last())
    root.javaClass.getMethod("windowFocusChanged", Boolean::class.javaPrimitiveType).invoke(root, true)
    root.javaClass.getMethod("dispatchInputEvent", InputEvent::class.java).invoke(root, event)
    shadowOf(Looper.getMainLooper()).idle()
}
