package io.github.zanather.clicker

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.zanather.clicker.crash.CrashActivity
import io.github.zanather.clicker.crash.CrashLog
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.ClickerNavHost
import io.github.zanather.clicker.ui.ClickerViewModel
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.theme.ClickerTheme
import io.github.zanather.clicker.ui.theme.LocalHapticsEnabled
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ClickerViewModel by viewModels {
        with(container) { ClickerViewModel.factory(session, store, ecp) }
    }

    private var lastVolumeRepeat = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        if (CrashLog.read(this) != null) {
            // The last run crashed: show the report instead of walking into the same crash.
            super.onCreate(savedInstanceState)
            startActivity(Intent(this, CrashActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    if (settings.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            ClickerTheme(dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.hapticsEnabled) {
                    ClickerNavHost(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.session.onForeground()
    }

    override fun onStop() {
        container.session.onBackground()
        super.onStop()
    }

    /**
     * Phone volume buttons drive the TV while the app is open. These run when nothing on screen consumed the
     * key; returning true stops the phone's own volume from changing.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = volumeKey(keyCode) ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) {
            viewModel.press(key)
        } else if (key != RemoteKey.VolumeMute) {
            // Held button: the system repeats at ~20 Hz; the TV only keeps up with about half that.
            val now = SystemClock.uptimeMillis()
            if (now - lastVolumeRepeat >= VOLUME_REPEAT_MS) {
                lastVolumeRepeat = now
                viewModel.press(key)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        volumeKey(keyCode) != null || super.onKeyUp(keyCode, event)

    /** The TV key for a phone volume button, or null when the button should keep controlling the phone. */
    private fun volumeKey(keyCode: Int): RemoteKey? {
        if (!forwardsVolumeKeys()) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VolumeDown
            KeyEvent.KEYCODE_VOLUME_MUTE -> RemoteKey.VolumeMute
            else -> null
        }
    }

    private fun forwardsVolumeKeys(): Boolean {
        val remote = viewModel.remote.value
        return viewModel.settings.value.volumeKeysControlTv &&
            remote.isTv &&
            remote.status != ConnectionStatus.NoDevice
    }

    private companion object {
        const val VOLUME_REPEAT_MS = 100L
    }
}
