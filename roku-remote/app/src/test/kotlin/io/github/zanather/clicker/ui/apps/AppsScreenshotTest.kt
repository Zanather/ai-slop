package io.github.zanather.clicker.ui.apps

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.zanather.clicker.ui.model.AppsUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val grid = AppsUiState(apps = SampleApps, activeAppId = "837")

    @Test
    fun grid() = capture("apps_grid", grid)

    @Test
    @Config(qualifiers = "+night")
    fun gridDark() = capture("apps_grid_dark", grid)

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun gridLandscape() = capture("apps_grid_landscape", grid)

    @Test
    @Config(fontScale = 1.5f)
    fun gridLargeFont() = capture("apps_grid_font_scale_150", grid)

    @Test
    fun blocked() = capture("apps_error_blocked", AppsUiState(error = BLOCKED_MESSAGE))

    @Test
    @Config(qualifiers = "+night")
    fun blockedDark() = capture("apps_error_blocked_dark", AppsUiState(error = BLOCKED_MESSAGE))

    @Test
    fun loading() = capture("apps_loading", AppsUiState(loading = true))

    @Test
    fun empty() = capture("apps_empty", AppsUiState())

    private fun capture(name: String, state: AppsUiState) {
        // Frozen clock: the loading spinner never settles, and a fixed frame keeps the golden stable.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ClickerTheme {
                AppsScreen(state = state, onLaunch = {}, onToggleFavorite = {}, onRefresh = {}, onBack = {})
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        // Exactly what ClickerViewModel shows when the TV is in Limited mode.
        const val BLOCKED_MESSAGE = "Your TV is hiding its channel list. On the TV, go to Settings > System > Advanced system settings > " +
            "Control by mobile apps > Network access and choose Enabled."
    }
}
