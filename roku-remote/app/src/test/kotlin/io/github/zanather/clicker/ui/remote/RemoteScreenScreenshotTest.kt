package io.github.zanather.clicker.ui.remote

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.model.RemoteActions
import io.github.zanather.clicker.ui.model.RemoteUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RemoteScreenScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: RemoteUiState) {
        compose.setContent {
            ClickerTheme {
                RemoteScreen(state, RemoteActions.Noop, onOpenApps = {}, onOpenDevices = {}, onOpenSettings = {})
            }
        }
    }

    private fun capture(name: String, state: RemoteUiState) {
        show(state)
        compose.onRoot().captureRoboImage("src/test/screenshots/remote_$name.png")
    }

    @Test
    fun connectedTv() = capture("connected_tv", RemoteSamples.connectedTv)

    @Test
    @Config(qualifiers = "+night")
    fun connectedTvNight() = capture("connected_tv_night", RemoteSamples.connectedTv)

    @Test
    fun liveTvShowsChannelRocker() = capture("live_tv", RemoteSamples.connectedTv.copy(activeAppId = "tvinput.dtv"))

    @Test
    fun touchpad() = capture("touchpad", RemoteSamples.connectedTv.copy(touchpadMode = true))

    @Test
    fun blockedLimited() = capture("blocked_limited", RemoteSamples.tv(ConnectionStatus.Blocked(limitedMode = true)))

    @Test
    @Config(qualifiers = "+night")
    fun blockedLimitedNight() = capture("blocked_limited_night", RemoteSamples.tv(ConnectionStatus.Blocked(limitedMode = true)))

    @Test
    fun blockedRefused() = capture("blocked_refused", RemoteSamples.tv(ConnectionStatus.Blocked(limitedMode = false)))

    @Test
    fun unreachable() = capture("unreachable", RemoteSamples.tv(ConnectionStatus.Unreachable(canWake = true)))

    @Test
    fun searching() = capture("searching", RemoteSamples.tv(ConnectionStatus.Searching))

    @Test
    fun waking() = capture("waking", RemoteSamples.tv(ConnectionStatus.Waking))

    @Test
    fun tvOff() = capture("tv_off", RemoteSamples.tv(ConnectionStatus.TvOff))

    @Test
    fun noDevice() = capture("no_device", RemoteUiState())

    @Test
    fun streamingStick() = capture("streaming_stick", RemoteSamples.streamingStick)

    @Test
    fun noFavorites() = capture("no_favorites", RemoteSamples.connectedTv.copy(favorites = emptyList(), status = ConnectionStatus.Connecting))

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscape() = capture("landscape", RemoteSamples.connectedTv)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun smallPhoneLiveTv() = capture("small_phone_live_tv", RemoteSamples.connectedTv.copy(activeAppId = "tvinput.dtv"))

    @Test
    @Config(fontScale = 1.5f)
    fun largeFont() = capture("font_scale_150", RemoteSamples.connectedTv)

    // The sheet and the menu are separate windows, so these capture the whole screen.
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun keyboardSheet() {
        show(RemoteSamples.connectedTv)
        compose.onNodeWithContentDescription("Keyboard").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/remote_keyboard_sheet.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun inputMenu() {
        show(RemoteSamples.connectedTv.copy(activeAppId = "tvinput.hdmi1"))
        compose.onNodeWithText("Input").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/remote_input_menu.png")
    }
}
