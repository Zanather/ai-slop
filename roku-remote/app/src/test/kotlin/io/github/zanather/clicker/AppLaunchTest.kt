package io.github.zanather.clicker

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.zanather.clicker.data.SavedDevice
import io.github.zanather.clicker.session.FakeRoku
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Boots the real app (ClickerApp, its container, MainActivity, the nav graph) rather than screens in isolation,
 * and walks every destination. The screen tests can't catch wiring bugs like an unreachable route serializer.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ClickerApp::class)
class AppLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val roku = FakeRoku()
    private val server = MockWebServer().apply {
        dispatcher = roku
        start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun forgetAllTvs() {
        // DataStore instances are process-wide, so another test's TV may still be saved.
        compose.activityRule.scenario.onActivity { activity ->
            runBlocking {
                with(activity.container.store) { state.first().devices.forEach { forget(it.serialNumber) } }
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("Find my TV"), timeoutMillis = 10_000)
    }

    @Test
    fun firstLaunchOffersToFindTheTvAndEveryScreenOpens() {
        forgetAllTvs()
        compose.onNodeWithText("Find my TV").assertIsDisplayed().performClick()
        compose.onNodeWithText("Your TVs").assertIsDisplayed()
        back()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Keep screen on").assertIsDisplayed()
        back()
    }

    private fun selectTheFakeTv() {
        compose.activityRule.scenario.onActivity { activity ->
            runBlocking {
                with(activity.container.store) {
                    upsert(SavedDevice(roku.serial, "TV", "127.0.0.1", server.port, isTv = true))
                    select(roku.serial)
                }
            }
        }
        compose.waitUntilAtLeastOneExists(hasText("Living Room TV"), timeoutMillis = 10_000)
    }

    @Test
    fun withATvTheRemoteConnectsAndChannelsAndDetailsOpen() {
        selectTheFakeTv()
        compose.onNodeWithContentDescription("Channels").performClick()
        compose.waitUntilAtLeastOneExists(hasText("YouTube"), timeoutMillis = 10_000)
        back()

        compose.onNodeWithText("Living Room TV").performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("TV details"), timeoutMillis = 10_000)
        compose.onAllNodesWithContentDescription("TV details").onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasText("C148X"), timeoutMillis = 10_000)
    }

    @Test
    fun doubleTappingBackLandsOnTheRemote() {
        forgetAllTvs()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Keep screen on").assertIsDisplayed()

        // The second tap lands on Settings while it's still fading out.
        compose.onNodeWithContentDescription("Back").performTouchInput {
            click()
            advanceEventTime(150)
            click()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Find my TV").assertIsDisplayed()
        compose.onNodeWithText("Keep screen on").assertDoesNotExist()
    }

    @Test
    fun doubleTappingAChannelLaunchesItOnceAndLandsOnTheRemote() {
        selectTheFakeTv()
        compose.onNodeWithContentDescription("Channels").performClick()
        compose.waitUntilAtLeastOneExists(hasText("YouTube"), timeoutMillis = 10_000)

        compose.onAllNodesWithText("YouTube").onFirst().performTouchInput {
            click()
            advanceEventTime(150)
            click()
        }
        compose.waitUntil(timeoutMillis = 5_000) { roku.launches.isNotEmpty() }
        // Both launches would go down the same queue, so a second one lands right behind the first.
        Thread.sleep(300)
        compose.waitForIdle()
        assertThat(roku.launches).hasSize(1)
        compose.onNodeWithContentDescription("Channels").assertIsDisplayed()
    }

    private fun back() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
}
