package io.github.zanather.clicker.ui.devices

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.ui.model.DeviceRow
import io.github.zanather.clicker.ui.model.DevicesUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val selected = mutableListOf<DeviceRow>()
    private val forgotten = mutableListOf<DeviceRow>()
    private val detailed = mutableListOf<DeviceRow>()
    private val added = mutableListOf<String>()
    private var scans = 0
    private var backs = 0

    private fun show(state: DevicesUiState) {
        compose.setContent {
            ClickerTheme {
                DevicesScreen(
                    state = state,
                    onSelect = { selected += it },
                    onForget = { forgotten += it },
                    onShowInfo = { detailed += it },
                    onScan = { scans++ },
                    onAddByAddress = { added += it },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun tappingASavedTvSelectsIt() {
        show(DevicesUiState(saved = SampleSaved))
        compose.onNodeWithText("Bedroom Streaming Stick").performClick()
        assertThat(selected).containsExactly(SampleSaved[1])
    }

    @Test
    fun tappingAFoundTvSelectsIt() {
        show(DevicesUiState(saved = SampleSaved, discovered = SampleDiscovered))
        compose.onNodeWithText("Basement TV").performClick()
        assertThat(selected).containsExactly(SampleDiscovered[0])
    }

    @Test
    fun overflowMenuForgetsThatRow() {
        show(DevicesUiState(saved = SampleSaved))
        compose.onAllNodesWithContentDescription("More options")[1].performClick()
        compose.onNodeWithText("Forget").performClick()
        assertThat(forgotten).containsExactly(SampleSaved[1])
        assertThat(selected).isEmpty()
    }

    @Test
    fun infoButtonOpensThatRowsDetails() {
        show(DevicesUiState(saved = SampleSaved))
        compose.onAllNodesWithContentDescription("TV details")[0].performClick()
        assertThat(detailed).containsExactly(SampleSaved[0])
        assertThat(selected).isEmpty()
    }

    @Test
    fun addByAddressSendsTheTypedText() {
        show(DevicesUiState(saved = SampleSaved))
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("192.168.8.198")
        compose.onNode(hasText("Add") and hasClickAction()).performClick()
        assertThat(added).containsExactly("192.168.8.198")
    }

    @Test
    fun addByAddressKeepsOnlyDigitsAndDotsAndSubmitsFromTheKeyboard() {
        show(DevicesUiState())
        compose.onNode(hasSetTextAction()).performTextInput(" 10.0.0.7 ")
        compose.onNode(hasSetTextAction()).performImeAction()
        assertThat(added).containsExactly("10.0.0.7")
    }

    @Test
    fun addIsDisabledUntilSomethingIsTyped() {
        show(DevicesUiState())
        compose.onNode(hasText("Add") and hasClickAction()).assertIsNotEnabled()
    }

    @Test
    fun scanAgainScans() {
        show(DevicesUiState(saved = SampleSaved))
        compose.onNodeWithText("Scan again").performClick()
        assertThat(scans).isEqualTo(1)
    }

    @Test
    fun scanningShowsProgressInsteadOfScanAgain() {
        show(DevicesUiState(saved = SampleSaved, scanning = true))
        compose.onNodeWithText("Searching your Wi-Fi…").assertExists()
        compose.onNodeWithText("Scan again").assertDoesNotExist()
    }

    @Test
    fun emptyStateCanScan() {
        show(DevicesUiState())
        compose.onNodeWithText("No TVs yet").assertExists()
        compose.onNodeWithText("Scan again").performClick()
        assertThat(scans).isEqualTo(1)
    }

    @Test
    fun messageIsShown() {
        val message = "Nothing answered at 192.168.8.20. Check the address, and that the TV is on and on this Wi-Fi."
        show(DevicesUiState(saved = SampleSaved, message = message))
        compose.onNodeWithText(message).assertExists()
    }

    @Test
    fun backArrowGoesBack() {
        show(DevicesUiState(saved = SampleSaved))
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }
}
