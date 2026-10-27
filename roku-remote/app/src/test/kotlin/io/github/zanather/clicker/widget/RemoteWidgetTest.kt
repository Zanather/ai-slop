package io.github.zanather.clicker.widget

import android.content.Context
import android.util.Xml
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.runComposition
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.zanather.clicker.R
import io.github.zanather.clicker.data.DeviceStore
import io.github.zanather.clicker.data.SavedDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class RemoteWidgetTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val density = context.resources.displayMetrics.density
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = DeviceStore(PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "widget.preferences_pb") })
    private val tv = SavedDevice("SERIAL1", "Living Room", "192.168.8.198")

    private val labels = listOf(R.string.widget_power, R.string.widget_volume_down, R.string.widget_mute, R.string.widget_volume_up, R.string.widget_play_pause)
        .map(context::getString)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @OptIn(ExperimentalGlanceApi::class)
    @Test
    fun aRunningWidgetFollowsATvBeingPickedAndForgotten() = runBlocking {
        val frames = Channel<RemoteViews>(Channel.UNLIMITED)
        val session = launch(Dispatchers.Default) {
            RemoteWidget { store }.runComposition(context, sizes = listOf(DpSize(250.dp, 60.dp))).collect { frames.send(it) }
        }

        suspend fun awaitButtons(shown: Boolean) = withTimeout(10.seconds) {
            while (frames.receive().render(250).showsButtons() != shown) continue
        }

        awaitButtons(false)
        store.upsert(tv)
        store.select(tv.serialNumber)
        awaitButtons(true)
        store.forget(tv.serialNumber)
        awaitButtons(false)
        session.cancel()
    }

    @Test
    fun keepInSyncRefreshesOnlyWhenThereStartsOrStopsBeingATv() = runBlocking {
        val updates = Channel<Unit>(Channel.UNLIMITED)
        RemoteWidget.keepInSync(scope, context, store) { updates.send(Unit) }

        store.upsert(tv)
        store.select(tv.serialNumber)
        withTimeout(5.seconds) { updates.receive() }

        // Changes that keep a TV selected leave the widget's layout alone.
        store.setFavorites(tv.serialNumber, listOf("12"))
        store.updateSettings { it.copy(hapticsEnabled = false) }
        store.forget(tv.serialNumber)
        withTimeout(5.seconds) { updates.receive() }

        delay(300)
        assertThat(updates.tryReceive().isSuccess).isFalse()
    }

    @Test
    fun keepInSyncSurvivesAFailedUpdate() = runBlocking {
        val attempts = Channel<Int>(Channel.UNLIMITED)
        var calls = 0
        RemoteWidget.keepInSync(scope, context, store) {
            val attempt = calls++
            attempts.send(attempt)
            if (attempt == 0) error("launcher gone")
        }

        store.upsert(tv)
        store.select(tv.serialNumber)
        assertThat(withTimeout(5.seconds) { attempts.receive() }).isEqualTo(0)

        store.forget(tv.serialNumber)
        assertThat(withTimeout(5.seconds) { attempts.receive() }).isEqualTo(1)
    }

    @Test
    fun allFiveButtonsFitFromTheNarrowestAllowedWidthUp() = runBlocking {
        store.upsert(tv)
        store.select(tv.serialNumber)

        for (width in listOf(minResizeWidthDp(), 250, 276, 400)) {
            val root = RemoteWidget { store }.compose(context, size = DpSize(width.dp, 60.dp)).render(width)
            val icons = labels.map { label -> root.descendants().single { it.contentDescription == label } }
            // The row clips to its 8dp padding, so anything outside it is cut off on the home screen.
            val left = 8 * density
            val right = (width - 8) * density
            for (icon in icons) {
                val x = icon.xIn(root)
                assertWithMessage("${icon.contentDescription} at ${width}dp: [$x, ${x + icon.width}] within [$left, $right]")
                    .that(x >= left - 1 && x + icon.width <= right + 1)
                    .isTrue()
            }
            assertWithMessage("icons in order at ${width}dp").that(icons.map { it.xIn(root) }).isInStrictOrder()
        }
    }

    private fun RemoteViews.render(widthDp: Int, heightDp: Int = 60): ViewGroup {
        val parent = FrameLayout(context)
        parent.addView(apply(context, parent))
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, width, height)
        return parent
    }

    private fun ViewGroup.showsButtons(): Boolean {
        val views = descendants()
        val buttons = views.any { it.contentDescription == labels.first() }
        val noTv = views.any { it is TextView && it.text.toString() == context.getString(R.string.widget_no_tv) }
        check(buttons != noTv) { "expected exactly one of the two layouts" }
        return buttons
    }

    private fun View.descendants(): List<View> =
        listOf(this) + if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()

    private fun View.xIn(root: View): Int {
        var x = 0
        var view: View = this
        while (view !== root) {
            x += view.left - view.scrollX
            view = view.parent as View
        }
        return x
    }

    /** The narrowest width the launcher may resize the widget to, read from its provider info. */
    private fun minResizeWidthDp(): Int {
        val parser = context.resources.getXml(R.xml.remote_widget_info)
        while (parser.next() != XmlPullParser.START_TAG) continue
        val attrs = context.obtainStyledAttributes(Xml.asAttributeSet(parser), intArrayOf(android.R.attr.minResizeWidth))
        try {
            return (attrs.getDimension(0, 0f) / density).roundToInt().also { check(it > 0) }
        } finally {
            attrs.recycle()
        }
    }
}
