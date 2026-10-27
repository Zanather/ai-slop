package io.github.zanather.clicker.ui.remote

import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.model.RemoteUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RemoteScreenInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = RecordingActions()
    private val opened = mutableListOf<String>()
    private lateinit var view: View

    private fun show(state: RemoteUiState = RemoteSamples.connectedTv) {
        compose.setContent {
            view = LocalView.current
            ClickerTheme {
                RemoteScreen(
                    state = state,
                    actions = actions,
                    onOpenApps = { opened += "apps" },
                    onOpenDevices = { opened += "devices" },
                    onOpenSettings = { opened += "settings" },
                )
            }
        }
    }

    private fun bounds(description: String): DpRect = compose.onNodeWithContentDescription(description).getBoundsInRoot()

    private fun tap(description: String) {
        compose.onNodeWithContentDescription(description).performClick()
        compose.waitForIdle()
    }

    @Test
    fun homePressesOnce() {
        show()
        tap("Home")
        assertThat(actions.calls).containsExactly("press Home")
    }

    @Test
    fun okPressesSelectOnce() {
        show()
        tap("OK")
        assertThat(actions.calls).containsExactly("press Select")
    }

    @Test
    fun backPressesOnce() {
        show()
        tap("Back")
        assertThat(actions.calls).containsExactly("press Back")
    }

    @Test
    fun playPausePressesPlayOnce() {
        show()
        tap("Play or pause")
        assertThat(actions.calls).containsExactly("press Play")
    }

    @Test
    fun singleKeysFireOnFingerDown() {
        show()
        compose.onNodeWithContentDescription("Home").performTouchInput { down(center) }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("press Home")

        compose.onNodeWithContentDescription("Home").performTouchInput { up() }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("press Home")
    }

    @Test
    fun upHoldsWhileTouchedAndNeverPresses() {
        show()
        compose.onNodeWithContentDescription("Up").performTouchInput { down(center) }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("holdStart Up")

        compose.onNodeWithContentDescription("Up").performTouchInput { up() }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("holdStart Up", "holdEnd Up").inOrder()
    }

    @Test
    fun dPadArmsSplitOnTheDiagonals() {
        show()
        for (arm in listOf("Down", "Left", "Right")) {
            compose.onNodeWithContentDescription(arm).performTouchInput {
                down(center)
                up()
            }
        }
        compose.waitForIdle()
        assertThat(actions.calls)
            .containsExactly("holdStart Down", "holdEnd Down", "holdStart Left", "holdEnd Left", "holdStart Right", "holdEnd Right")
            .inOrder()
    }

    @Test
    fun volumeUpHoldsWhileTouched() {
        show()
        compose.onNodeWithContentDescription("Volume up").performTouchInput {
            down(center)
            advanceEventTime(150)
            up()
        }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("holdStart VolumeUp", "holdEnd VolumeUp").inOrder()
    }

    @Test
    fun powerFiresOnReleaseNotTouch() {
        show()
        compose.onNodeWithContentDescription("Power").performTouchInput { down(center) }
        compose.waitForIdle()
        assertThat(actions.calls).isEmpty()

        compose.onNodeWithContentDescription("Power").performTouchInput { up() }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("press Power")
    }

    @Test
    fun everyDPadPartIsItsOwnLabelledButton() {
        show()
        for (part in listOf("Up", "Down", "Left", "Right", "OK")) {
            compose.onNodeWithContentDescription(part).assertHasClickAction()
        }
    }

    @Test
    fun accessibilityClickOnARepeatingKeySendsOnePress() {
        show()
        compose.onNodeWithContentDescription("Up").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithContentDescription("Volume down").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(actions.calls).containsExactly("press Up", "press VolumeDown").inOrder()
    }

    @Test
    fun checkAgainRetriesWhenLimited() {
        show(RemoteSamples.tv(ConnectionStatus.Blocked(limitedMode = true)))
        compose.onNodeWithText("Check again").performClick()
        assertThat(actions.calls).containsExactly("retry")
    }

    @Test
    fun checkAgainRetriesWhenRefused() {
        show(RemoteSamples.tv(ConnectionStatus.Blocked(limitedMode = false)))
        compose.onNodeWithText("Check again").performClick()
        assertThat(actions.calls).containsExactly("retry")
    }

    @Test
    fun unreachableCardOffersWakeRetryAndChoose() {
        show(RemoteSamples.tv(ConnectionStatus.Unreachable(canWake = true)))
        compose.onNodeWithText("Wake TV").performClick()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Choose TV").performClick()
        assertThat(actions.calls).containsExactly("wake", "retry").inOrder()
        assertThat(opened).containsExactly("devices")
    }

    @Test
    fun unreachableWithoutMacHasNoWake() {
        show(RemoteSamples.tv(ConnectionStatus.Unreachable(canWake = false)))
        compose.onNodeWithText("Wake TV").assertDoesNotExist()
        compose.onNodeWithText("Try again").performClick()
        assertThat(actions.calls).containsExactly("retry")
    }

    @Test
    fun turnOnPressesPowerWhenTvIsOff() {
        show(RemoteSamples.tv(ConnectionStatus.TvOff))
        compose.onNodeWithText("Turn on").performClick()
        assertThat(actions.calls).containsExactly("press Power")
    }

    @Test
    fun findMyTvOpensDevices() {
        show(RemoteUiState())
        compose.onNodeWithText("Find my TV").performClick()
        assertThat(opened).containsExactly("devices")
        compose.onNodeWithContentDescription("OK").assertDoesNotExist()
    }

    @Test
    fun deviceChipOpensDevices() {
        show()
        compose.onNodeWithText("Living Room TV").performClick()
        assertThat(opened).containsExactly("devices")
    }

    @Test
    fun topBarActionsNavigate() {
        show()
        tap("Channels")
        tap("Settings")
        assertThat(opened).containsExactly("apps", "settings").inOrder()
    }

    @Test
    fun favoriteTileLaunchesItsChannel() {
        show()
        compose.onNodeWithText("YouTube").performClick()
        assertThat(actions.calls).containsExactly("launch 837")
    }

    @Test
    fun inputMenuLaunchesTheChosenInput() {
        show()
        compose.onNodeWithText("Input").performClick()
        compose.onNodeWithText("PlayStation 5").performClick()
        assertThat(actions.calls).containsExactly("launch tvinput.hdmi1")
    }

    @Test
    fun inputMenuFallsBackToInputKeys() {
        show(RemoteSamples.connectedTv.copy(inputs = emptyList()))
        compose.onNodeWithText("Input").performClick()
        compose.onNodeWithText("HDMI 2").performClick()
        assertThat(actions.calls).containsExactly("press InputHDMI2")
    }

    @Test
    fun streamingStickHasNoTvOnlyControls() {
        show(RemoteSamples.streamingStick)
        for (tvOnly in listOf("Power", "Volume up", "Volume down", "Mute")) {
            compose.onNodeWithContentDescription(tvOnly).assertDoesNotExist()
        }
        compose.onNodeWithText("Input").assertDoesNotExist()
    }

    @Test
    fun channelRockerOnlyOnLiveTv() {
        show()
        compose.onNodeWithContentDescription("Channel up").assertDoesNotExist()
    }

    @Test
    fun channelUpHoldsOnLiveTv() {
        show(RemoteSamples.connectedTv.copy(activeAppId = "tvinput.dtv"))
        compose.onNodeWithContentDescription("Channel up").performClick()
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("holdStart ChannelUp", "holdEnd ChannelUp").inOrder()
    }

    @Test
    fun modeToggleSwitchesToTouchpad() {
        show()
        tap("Touchpad mode")
        assertThat(actions.calls).containsExactly("setTouchpadMode true")
    }

    @Test
    fun touchpadSwipeRightPressesRight() {
        show(RemoteSamples.connectedTv.copy(touchpadMode = true))
        val distance = with(compose.density) { 60.dp.toPx() }
        compose.onNodeWithContentDescription("Touchpad. Swipe to move, tap for OK.").performTouchInput {
            swipe(start = center - Offset(distance / 2, 0f), end = center + Offset(distance / 2, 0f), durationMillis = 200)
        }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("press Right")
    }

    @Test
    fun touchpadLongSwipeUpPressesOncePerStep() {
        show(RemoteSamples.connectedTv.copy(touchpadMode = true))
        val distance = with(compose.density) { 140.dp.toPx() }
        compose.onNodeWithContentDescription("Touchpad. Swipe to move, tap for OK.").performTouchInput {
            swipe(start = center + Offset(0f, distance / 2), end = center - Offset(0f, distance / 2), durationMillis = 300)
        }
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("press Up", "press Up", "press Up")
    }

    @Test
    fun touchpadTapPressesSelect() {
        show(RemoteSamples.connectedTv.copy(touchpadMode = true))
        compose.onNodeWithContentDescription("Touchpad. Swipe to move, tap for OK.").performClick()
        compose.waitForIdle()
        assertThat(actions.calls).containsExactly("press Select")
    }

    @Test
    fun touchpadModeHidesTheDPad() {
        show(RemoteSamples.connectedTv.copy(touchpadMode = true))
        compose.onNodeWithContentDescription("Up").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun touchpadStaysOutOfTheBackGestureEdges() {
        show(RemoteSamples.connectedTv.copy(touchpadMode = true))
        val edge = with(compose.density) { 30.dp.roundToPx() }
        compose.runOnIdle {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemGestures(), Insets.of(edge, 0, edge, 0))
                .build()
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        compose.waitForIdle()

        val pad = bounds("Touchpad. Swipe to move, tap for OK.")
        val screen = compose.onRoot().getBoundsInRoot()
        assertThat(pad.left.value).isAtLeast(30f)
        assertThat((screen.right - pad.right).value).isAtLeast(30f)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xxhdpi")
    fun shortLandscapePhoneKeepsTheDPadRound() {
        show()
        val up = bounds("Up")
        val down = bounds("Down")
        val left = bounds("Left")
        val right = bounds("Right")
        assertThat((down.bottom - up.top).value).isWithin(0.5f).of((right.right - left.left).value)

        for (arm in listOf("Up", "Down", "Left", "Right")) {
            compose.onNodeWithContentDescription(arm).performTouchInput {
                down(center)
                up()
            }
        }
        compose.waitForIdle()
        assertThat(actions.calls.filter { it.startsWith("holdStart") })
            .containsExactly("holdStart Up", "holdStart Down", "holdStart Left", "holdStart Right")
            .inOrder()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xxhdpi")
    fun shortLandscapePhoneKeepsPlaybackKeysRound() {
        show()
        for (key in listOf("Replay", "Rewind", "Play or pause", "Fast forward")) {
            val bounds = bounds(key)
            assertThat(bounds.width.value).isWithin(0.5f).of(bounds.height.value)
        }
    }
}
