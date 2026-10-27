package io.github.zanather.clicker

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.data.SavedDevice
import io.github.zanather.clicker.session.FakeRoku
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** The phone's volume buttons, through the real activity and session to a fake TV. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ClickerApp::class)
class VolumeButtonsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val roku = FakeRoku()
    private val server = MockWebServer().apply {
        dispatcher = roku
        start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun volumeUps() = roku.keys.count { it == "VolumeUp" }

    @Test
    fun heldButtonStopsTheTvVolumeWhenLetGo() {
        compose.activityRule.scenario.onActivity { activity ->
            runBlocking {
                with(activity.container.store) {
                    upsert(SavedDevice(roku.serial, "TV", "127.0.0.1", server.port, isTv = true))
                    select(roku.serial)
                }
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("Living Room TV"), timeoutMillis = 10_000)

        val pressed = SystemClock.uptimeMillis()
        compose.activityRule.scenario.onActivity {
            it.dispatchKeyEvent(KeyEvent(pressed, pressed, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, 0))
        }
        compose.waitUntil(timeoutMillis = 5_000) { volumeUps() == 1 }

        // A second of the system's 50 ms key repeats, then the release. Each repeat used to queue a press of its own.
        compose.activityRule.scenario.onActivity {
            for (repeat in 1..20) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(50))
                it.dispatchKeyEvent(KeyEvent(pressed, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, repeat))
            }
            it.dispatchKeyEvent(KeyEvent(pressed, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, 0))
        }

        // Longer than the session waits before repeating a held key.
        Thread.sleep(700)
        assertThat(volumeUps()).isEqualTo(1)
    }
}
