package io.github.zanather.clicker.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.zanather.clicker.MainActivity
import io.github.zanather.clicker.R
import io.github.zanather.clicker.container
import io.github.zanather.clicker.ecp.RemoteKey
import kotlinx.coroutines.flow.first

/** A one-row home screen remote: the buttons you reach for without wanting to open an app. */
class RemoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hasTv = context.container.store.state.first().selected != null
        provideContent {
            GlanceTheme {
                if (hasTv) Buttons(context) else NoTv(context)
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

    @Composable
    private fun KeyButton(key: RemoteKey, icon: Int, label: String) {
        Box(
            modifier = GlanceModifier
                .size(52.dp)
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
}

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
