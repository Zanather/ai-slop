package io.github.zanather.clicker.ui.settings

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.zanather.clicker.ui.model.SettingsUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = SettingsUiState(hapticsEnabled = true, volumeKeysControlTv = true, keepScreenOn = false, dynamicColor = false, appVersion = "1.0.0")

    @Test
    fun settings() {
        show()
        capture("settings")
    }

    @Test
    @Config(qualifiers = "+night")
    fun settingsDark() {
        show()
        capture("settings_dark")
    }

    @Test
    fun helpExpanded() {
        show()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Remote not working?"))
        compose.onNodeWithText("Remote not working?").performClick()
        list.performScrollToNode(hasText("Version", substring = true))
        capture("settings_help_expanded")
    }

    @Test
    @Config(fontScale = 1.5f)
    fun largeFont() {
        show()
        capture("settings_font_scale_150")
    }

    private fun show() {
        compose.setContent {
            ClickerTheme {
                SettingsScreen(state = state, onChange = {}, onBack = {})
            }
        }
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
