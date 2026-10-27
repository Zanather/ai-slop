package io.github.zanather.clicker.ui.info

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.zanather.clicker.ui.model.DeviceInfoUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DeviceInfoScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rows() {
        show(SampleInfo)
        capture("info_rows")
    }

    @Test
    @Config(qualifiers = "+night")
    fun rowsDark() {
        show(SampleInfo)
        capture("info_rows_dark")
    }

    @Test
    fun allFieldsExpanded() {
        show(SampleInfo)
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("All fields"))
        compose.onNodeWithText("All fields").performClick()
        list.performScrollToNode(hasText("has-wifi-5G-support"))
        capture("info_all_fields")
    }

    @Test
    fun loading() {
        show(DeviceInfoUiState(name = "Living Room TV", loading = true), freezeClock = true)
        capture("info_loading")
    }

    @Test
    fun error() {
        show(DeviceInfoUiState(name = "Living Room TV", error = "Couldn't reach Living Room TV."))
        capture("info_error")
    }

    /** [freezeClock] for the spinner, which never settles; a fixed frame keeps its golden stable. */
    private fun show(state: DeviceInfoUiState, freezeClock: Boolean = false) {
        compose.mainClock.autoAdvance = !freezeClock
        compose.setContent {
            ClickerTheme {
                DeviceInfoScreen(state = state, onBack = {})
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
