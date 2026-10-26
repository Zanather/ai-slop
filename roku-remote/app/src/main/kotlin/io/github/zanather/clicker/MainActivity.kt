package io.github.zanather.clicker

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
     * Phone volume buttons drive the TV while the app is open. Handled here rather than in Compose because
     * Compose key handlers only see events along the focused node's path.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VolumeDown
            KeyEvent.KEYCODE_VOLUME_MUTE -> RemoteKey.VolumeMute
            else -> null
        }
        if (key == null || !forwardsVolumeKeys()) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
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
        }
        return true
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
