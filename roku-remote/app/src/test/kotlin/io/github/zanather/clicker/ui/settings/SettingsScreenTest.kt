package io.github.zanather.clicker.ui.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.ui.model.SettingsUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = SettingsUiState(hapticsEnabled = true, volumeKeysControlTv = false, keepScreenOn = true, dynamicColor = false)
    private val changes = mutableListOf<SettingsUiState>()
    private var backs = 0

    private fun show() {
        compose.setContent {
            ClickerTheme {
                SettingsScreen(state = state, onChange = { changes += it }, onBack = { backs++ })
            }
        }
    }

    @Test
    fun hapticsRowTogglesOnlyHaptics() = assertRowToggles("Haptic feedback", state.copy(hapticsEnabled = false))

    @Test
    fun volumeKeysRowTogglesOnlyVolumeKeys() = assertRowToggles("Phone volume buttons", state.copy(volumeKeysControlTv = true))

    @Test
    fun keepScreenOnRowTogglesOnlyKeepScreenOn() = assertRowToggles("Keep screen on", state.copy(keepScreenOn = false))

    @Test
    fun dynamicColorRowTogglesOnlyDynamicColor() = assertRowToggles("Match wallpaper colors", state.copy(dynamicColor = true))

    @Test
    fun rowsAreSwitchesShowingTheirState() {
        show()
        compose.onNodeWithText("Haptic feedback").assert(hasRole(Role.Switch)).assertIsOn()
        compose.onNodeWithText("Phone volume buttons").assert(hasRole(Role.Switch)).assertIsOff()
    }

    @Test
    fun helpExpandsToShowTheFixes() {
        show()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Remote not working?"))
        compose.onNodeWithText("Let Clicker control the TV").assertDoesNotExist()

        compose.onNodeWithText("Remote not working?").performClick()
        list.performScrollToNode(hasText("Let Clicker turn the TV on"))
        compose.onNodeWithText("Control by mobile apps > Network access > Enabled", substring = true).assertExists()
        compose.onNodeWithText("Fast TV start", substring = true).assertExists()
        assertThat(changes).isEmpty()
    }

    @Test
    fun backArrowGoesBack() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }

    private fun assertRowToggles(title: String, expected: SettingsUiState) {
        show()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
        compose.onNodeWithText(title).performClick()
        assertThat(changes).containsExactly(expected)
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
}
