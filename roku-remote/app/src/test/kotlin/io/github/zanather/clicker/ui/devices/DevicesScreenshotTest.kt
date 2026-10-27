package io.github.zanather.clicker.ui.devices

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.zanather.clicker.ui.model.DevicesUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DevicesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val list = DevicesUiState(
        saved = SampleSaved,
        discovered = SampleDiscovered,
        message = "Nothing answered at 192.168.8.20. Check the address, and that the TV is on and on this Wi-Fi.",
    )

    @Test
    fun list() = capture("devices_list", list)

    @Test
    @Config(qualifiers = "+night")
    fun listDark() = capture("devices_list_dark", list)

    @Test
    fun scanning() = capture("devices_scanning", DevicesUiState(saved = SampleSaved, scanning = true))

    @Test
    fun empty() = capture("devices_empty", DevicesUiState())

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun landscape() = capture("devices_list_landscape", list)

    private fun capture(name: String, state: DevicesUiState) {
        // Frozen clock: the scan progress bar never settles, and a fixed frame keeps the golden stable.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ClickerTheme {
                DevicesScreen(
                    state = state,
                    onSelect = {},
                    onForget = {},
                    onShowInfo = {},
                    onScan = {},
                    onAddByAddress = {},
                    onBack = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
