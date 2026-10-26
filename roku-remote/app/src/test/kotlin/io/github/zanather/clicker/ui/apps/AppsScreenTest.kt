package io.github.zanather.clicker.ui.apps

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.ui.model.AppTile
import io.github.zanather.clicker.ui.model.AppsUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val launched = mutableListOf<String>()
    private val toggled = mutableListOf<String>()
    private var refreshes = 0
    private var backs = 0

    private val apps = listOf(
        AppTile("12", "Netflix", null, isFavorite = true),
        AppTile("837", "YouTube", null),
        AppTile("2285", "Hulu", null),
    )

    private fun show(state: AppsUiState = AppsUiState(apps = apps, activeAppId = "837")) {
        compose.setContent {
            ClickerTheme {
                AppsScreen(
                    state = state,
                    onLaunch = { launched += it },
                    onToggleFavorite = { toggled += it },
                    onRefresh = { refreshes++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun tapLaunchesTheChannel() {
        show()
        compose.onNodeWithText("Hulu").performClick()
        assertThat(launched).containsExactly("2285")
        assertThat(toggled).isEmpty()
    }

    @Test
    fun longPressTogglesThePin() {
        show()
        compose.onNodeWithText("YouTube").performTouchInput { longClick() }
        assertThat(toggled).containsExactly("837")
        assertThat(launched).isEmpty()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun screenReaderCanPinAndUnpin() {
        show()
        compose.onNodeWithText("Hulu").performCustomAccessibilityActionWithLabel("Pin to remote")
        compose.onNodeWithText("Netflix").performCustomAccessibilityActionWithLabel("Unpin from remote")
        assertThat(toggled).containsExactly("2285", "12").inOrder()
    }

    @Test
    fun activeAndPinnedStatesAreAnnounced() {
        show()
        compose.onNodeWithText("YouTube").assert(hasStateDescription("Now playing"))
        compose.onNodeWithText("Netflix").assert(hasStateDescription("Pinned to remote"))
    }

    @Test
    fun refreshAndBackButtons() {
        show()
        compose.onNodeWithContentDescription("Refresh channels").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertThat(refreshes).isEqualTo(1)
        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun errorOffersTryAgain() {
        show(AppsUiState(error = "Couldn't load channels from Living Room TV."))
        compose.onNodeWithText("Couldn't load channels from Living Room TV.").assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertThat(refreshes).isEqualTo(1)
    }

    @Test
    fun loadingShowsNoTiles() {
        show(AppsUiState(loading = true))
        compose.onNodeWithText("Getting your channels…").assertExists()
        compose.onNodeWithText("Tap to open. Long-press to pin to the remote.").assertDoesNotExist()
    }

    private fun hasStateDescription(text: String) = SemanticsMatcher("stateDescription contains '$text'") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.contains(text) == true
    }
}
