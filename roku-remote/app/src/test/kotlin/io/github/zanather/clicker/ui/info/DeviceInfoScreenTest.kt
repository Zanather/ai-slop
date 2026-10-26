package io.github.zanather.clicker.ui.info

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.ui.model.DeviceInfoUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceInfoScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0

    private fun show(state: DeviceInfoUiState) {
        compose.setContent {
            ClickerTheme {
                DeviceInfoScreen(state = state, onBack = { backs++ })
            }
        }
    }

    @Test
    fun rawFieldsStartCollapsedAndExpandOnTap() {
        show(SampleInfo)
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("All fields"))
        compose.onNodeWithText("udn").assertDoesNotExist()

        compose.onNodeWithText("All fields").performClick()
        list.performScrollToNode(hasText("udn"))
        compose.onNodeWithText("29a40003-6c08-1070-80c3-a8b57c123456").assertExists()
    }

    @Test
    fun titleFallsBackWhenTheNameIsUnknown() {
        show(DeviceInfoUiState(loading = true))
        compose.onNodeWithText("TV details").assertExists()
    }

    @Test
    fun errorIsShown() {
        show(DeviceInfoUiState(name = "Living Room TV", error = "Couldn't reach Living Room TV."))
        compose.onNodeWithText("Couldn't reach Living Room TV.").assertExists()
    }

    @Test
    fun backArrowGoesBack() {
        show(SampleInfo)
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun splitsFriendlyRowsFromRawFields() {
        val (friendly, raw) = splitRows(SampleInfo.rows)
        assertThat(friendly.map { it.first }).containsExactly(
            "Name", "Model", "Model number", "Made by", "Software version", "Serial number", "Device ID",
            "IP address", "Connection", "Wi-Fi MAC", "Power", "Control by mobile apps", "Uptime",
        ).inOrder()
        assertThat(raw.first().first).isEqualTo("udn")
        assertThat(raw.map { it.first }).contains("has-wifi-5G-support")
    }

    @Test
    fun allFriendlyRowsMeanNothingToExpand() {
        val (friendly, raw) = splitRows(listOf("Name" to "TV", "Wi-Fi MAC" to "aa:bb"))
        assertThat(friendly).hasSize(2)
        assertThat(raw).isEmpty()
    }
}
