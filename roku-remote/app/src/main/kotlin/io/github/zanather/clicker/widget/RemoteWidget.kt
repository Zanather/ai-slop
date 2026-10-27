package io.github.zanather.clicker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.zanather.clicker.MainActivity
import io.github.zanather.clicker.R
import io.github.zanather.clicker.container
import io.github.zanather.clicker.crash.CrashLog
import io.github.zanather.clicker.data.DeviceStore
import io.github.zanather.clicker.ecp.RemoteKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A one-row home screen remote: the buttons you reach for without wanting to open an app. */
class RemoteWidget(
    // Tests pass their own store; the app's lives in the container.
    private val store: (Context) -> DeviceStore = { it.container.store },
) : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hasTv = store(context).hasTv()
        val initial = hasTv.first()
        provideContent {
            // Observed, not read once: a session that is still running must follow a TV being picked or forgotten.
            val showButtons by hasTv.collectAsState(initial)
            GlanceTheme {
                if (showButtons) Buttons(context) else NoTv(context)
            }
        }
    }

    @Composable
    private fun Buttons(context: Context) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            KeyButton(RemoteKey.Power, R.drawable.ic_power, context.getString(R.string.widget_power))
            KeyButton(RemoteKey.VolumeDown, R.drawable.ic_volume_down, context.getString(R.string.widget_volume_down))
            KeyButton(RemoteKey.VolumeMute, R.drawable.ic_volume_mute, context.getString(R.string.widget_mute))
            KeyButton(RemoteKey.VolumeUp, R.drawable.ic_volume_up, context.getString(R.string.widget_volume_up))
            KeyButton(RemoteKey.Play, R.drawable.ic_play_pause, context.getString(R.string.widget_play_pause))
        }
    }

    // The buttons share the width rather than each taking a fixed 52dp, so none is cut off when the
    // widget is resized down to minResizeWidth.
    @Composable
    private fun RowScope.KeyButton(key: RemoteKey, icon: Int, label: String) {
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(52.dp)
                .padding(4.dp)
                .cornerRadius(22.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionRunCallback<PressKeyAction>(actionParametersOf(PressKeyAction.Key to key.name))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }

    @Composable
    private fun NoTv(context: Context) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = context.getString(R.string.widget_no_tv),
                style = TextStyle(color = GlanceTheme.colors.onSurface),
            )
        }
    }

    companion object {
        /**
         * Re-renders placed widgets whenever there starts or stops being a TV to control. Glance only re-runs
         * [provideGlance] on system events (added, resized, reboot), so without this a widget placed before
         * the first TV was picked would say "No TV" until the next reboot.
         */
        fun keepInSync(
            scope: CoroutineScope,
            context: Context,
            store: DeviceStore,
            update: suspend () -> Unit = { RemoteWidget().updateAll(context) },
        ): Job = scope.launch {
            // The first value is what any placed widget already shows.
            store.hasTv().drop(1).collect {
                try {
                    update()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A widget that fails to refresh mustn't take the app down; it catches up on the next change.
                    CrashLog.breadcrumb("widget update failed: $e")
                }
            }
        }
    }
}

private fun DeviceStore.hasTv(): Flow<Boolean> = state.map { it.selected != null }.distinctUntilChanged()

/** Sends one key from the widget. Runs inside the widget's broadcast, so it waits for the press to land. */
class PressKeyAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[Key]?.let { name -> RemoteKey.entries.firstOrNull { it.name == name } } ?: return
        context.container.session.pressFromOutside(key)
    }

    companion object {
        val Key = ActionParameters.Key<String>("key")
    }
}

class RemoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemoteWidget()
}
