package io.github.zanather.clicker

import android.content.Intent
import android.os.Bundle
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
import io.github.zanather.clicker.ui.ClickerNavHost
import io.github.zanather.clicker.ui.ClickerViewModel
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.remote.LocalVolumeKeyForwarder
import io.github.zanather.clicker.ui.remote.VolumeKeyForwarder
import io.github.zanather.clicker.ui.theme.ClickerTheme
import io.github.zanather.clicker.ui.theme.LocalHapticsEnabled
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ClickerViewModel by viewModels {
        with(container) { ClickerViewModel.factory(session, store, ecp) }
    }

    /** Phone volume buttons drive the TV while the app is open; the app's sheets and menus forward them too. */
    private val volumeKeys by lazy { VolumeKeyForwarder(viewModel, ::forwardsVolumeKeys) }

    override fun onCreate(savedInstanceState: Bundle?) {
        CrashLog.breadcrumb("main onCreate restored=${savedInstanceState != null} action=${intent?.action}")
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
                CompositionLocalProvider(
                    LocalHapticsEnabled provides settings.hapticsEnabled,
                    LocalVolumeKeyForwarder provides volumeKeys,
                ) {
                    ClickerNavHost(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CrashLog.breadcrumb("main onStart")
        container.session.onForeground()
    }

    override fun onResume() {
        super.onResume()
        CrashLog.breadcrumb("main onResume")
    }

    override fun onPause() {
        CrashLog.breadcrumb("main onPause finishing=$isFinishing")
        super.onPause()
    }

    override fun onStop() {
        CrashLog.breadcrumb("main onStop finishing=$isFinishing config=$isChangingConfigurations")
        container.session.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        CrashLog.breadcrumb("main onDestroy finishing=$isFinishing config=$isChangingConfigurations")
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        CrashLog.breadcrumb("main focus=$hasFocus")
        if (!hasFocus) volumeKeys.release()
    }

    // These run when nothing on screen consumed the key; returning true stops the phone's own volume from changing.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = volumeKeys.handle(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = volumeKeys.handle(event) || super.onKeyUp(keyCode, event)

    private fun forwardsVolumeKeys(): Boolean {
        val remote = viewModel.remote.value
        return viewModel.settings.value.volumeKeysControlTv &&
            remote.isTv &&
            remote.status != ConnectionStatus.NoDevice
    }
}
