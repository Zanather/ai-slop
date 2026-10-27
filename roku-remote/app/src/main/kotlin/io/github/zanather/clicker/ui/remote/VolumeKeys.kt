package io.github.zanather.clicker.ui.remote

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.model.RemoteActions

/**
 * Turns the phone's volume buttons into the TV's. A held button holds the TV key, paced like the on-screen
 * rocker, so the volume stops when the button is let go; the system's own key repeats are ignored.
 *
 * [enabled] is checked on every press, so the setting and the selected TV apply at once.
 */
internal class VolumeKeyForwarder(private val actions: RemoteActions, private val enabled: () -> Boolean) {
    private var held: RemoteKey? = null

    /** Returns true when [event] went to the TV, which keeps the phone's own volume where it is. */
    fun handle(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VolumeDown
            KeyEvent.KEYCODE_VOLUME_MUTE -> RemoteKey.VolumeMute
            else -> return false
        }
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!enabled()) return false
                if (event.repeatCount == 0) {
                    if (key == RemoteKey.VolumeMute) {
                        actions.press(key)
                    } else {
                        held = key
                        actions.holdStart(key)
                    }
                }
                true
            }
            // Also the canceled up that arrives when focus moves to another window mid-press.
            KeyEvent.ACTION_UP -> if (key == held) {
                release()
                true
            } else {
                enabled()
            }
            else -> false
        }
    }

    /** Ends a held button whose key up won't arrive, for example because its window has closed. */
    fun release() {
        held?.let(actions::holdEnd)
        held = null
    }
}

/** The app's [VolumeKeyForwarder]; null in previews and screen tests. */
internal val LocalVolumeKeyForwarder = staticCompositionLocalOf<VolumeKeyForwarder?> { null }

/**
 * Forwards the volume buttons from the window this is composed in. Sheets and menus are windows of their
 * own: they get the keys instead of the activity, and would otherwise hand them to the phone's volume.
 */
@Composable
internal fun ForwardVolumeKeysFromThisWindow() {
    val forwarder = LocalVolumeKeyForwarder.current ?: return
    val view = LocalView.current
    DisposableEffect(view, forwarder) {
        // Runs once nothing in the window took the key, before the window's fallback changes the phone's volume.
        val listener = ViewCompat.OnUnhandledKeyEventListenerCompat { _, event -> forwarder.handle(event) }
        ViewCompat.addOnUnhandledKeyEventListener(view, listener)
        onDispose {
            ViewCompat.removeOnUnhandledKeyEventListener(view, listener)
            // A button held in this window gets no key up once the window is gone.
            forwarder.release()
        }
    }
}
