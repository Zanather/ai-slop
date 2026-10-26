package io.github.zanather.clicker.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.zanather.clicker.MainActivity
import io.github.zanather.clicker.R
import io.github.zanather.clicker.container
import io.github.zanather.clicker.ecp.RemoteKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Quick Settings tile: pause the TV from the notification shade, no app needed. */
class PlayPauseTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val tv = container.store.state.first().selected
            qsTile?.apply {
                label = getString(R.string.tile_play_pause)
                state = if (tv == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = tv?.name
                updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            if (container.store.state.first().selected == null) {
                openApp()
            } else {
                container.session.pressFromOutside(RemoteKey.Play)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
