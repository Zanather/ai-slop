package io.github.zanather.clicker.ui.remote

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.times
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ecp.RemoteKey
import io.github.zanather.clicker.ui.model.ConnectionStatus
import io.github.zanather.clicker.ui.model.RemoteActions
import io.github.zanather.clicker.ui.model.RemoteUiState

private val ScreenPadding = 16.dp
private val MaxPadSize = 260.dp
private val MinPadSize = 196.dp

/** Floor for a landscape pad squeezed by a narrow half; below it the pad overflows rather than shrink further. */
private val MinLandscapePadSize = 140.dp

/** Between the nav keys and the pad in landscape. */
private val LandscapeGap = 28.dp

/** Height of everything in portrait other than the pad, so the pad shrinks before anything scrolls. */
private val PortraitReserved = 480.dp

/** Live TV from the antenna; the only "app" with channels to flip. */
private const val TUNER_APP_PREFIX = "tvinput.dtv"

/**
 * The remote. It's the app's start screen, so it draws from [state] alone and never waits: every key
 * works the moment the screen appears, whatever the connection status says.
 */
@Composable
fun RemoteScreen(
    state: RemoteUiState,
    actions: RemoteActions,
    onOpenApps: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    val hasDevice = state.status != ConnectionStatus.NoDevice
    // TalkBack's own gestures clash with a swipe pad, so its users always get the D-pad's labelled keys.
    val touchExploration = rememberTouchExplorationEnabled()

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
            RemoteTopBar(
                deviceName = state.deviceName,
                status = state.status,
                isTv = state.isTv,
                onOpenDevices = onOpenDevices,
                onOpenKeyboard = { keyboardOpen = true },
                onOpenApps = onOpenApps,
                onOpenSettings = onOpenSettings,
                onPower = { actions.press(RemoteKey.Power) },
            )
            BoxWithConstraints(Modifier.weight(1f)) {
                when {
                    !hasDevice -> FindTvHero(
                        onOpenDevices = onOpenDevices,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(vertical = 24.dp),
                    )
                    maxWidth > maxHeight -> LandscapeRemote(state, actions, touchExploration, onOpenApps, onOpenDevices, maxWidth, maxHeight)
                    else -> PortraitRemote(state, actions, touchExploration, onOpenApps, onOpenDevices, maxWidth, maxHeight)
                }
            }
        }
    }

    if (keyboardOpen && hasDevice) {
        KeyboardSheet(actions, onDismiss = { keyboardOpen = false })
    }
}

@Composable
private fun PortraitRemote(
    state: RemoteUiState,
    actions: RemoteActions,
    touchExploration: Boolean,
    onOpenApps: () -> Unit,
    onOpenDevices: () -> Unit,
    width: Dp,
    height: Dp,
) {
    val padSize = (height - PortraitReserved).coerceIn(MinPadSize, max(MinPadSize, min(MaxPadSize, width - 2 * ScreenPadding)))
    // The swipe pad stays out of the edge-swipe back zones, which would take its sideways swipes.
    val gestures = WindowInsets.systemGestures
    val direction = LocalLayoutDirection.current
    val gestureInset = with(LocalDensity.current) { maxOf(gestures.getLeft(this, direction), gestures.getRight(this, direction)).toDp() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = height)
            .padding(bottom = 24.dp),
        // Top and bottom groups hug the edges; the pad sits in the middle, where thumbs land best.
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Banner(state, actions, onOpenDevices, Modifier.padding(horizontal = ScreenPadding))
            if (state.favorites.isNotEmpty()) {
                FavoritesRow(
                    favorites = state.favorites,
                    activeAppId = state.activeAppId,
                    onLaunch = actions::launch,
                    onOpenApps = onOpenApps,
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            ToolRow(state, actions, canSwitchPad = !touchExploration, onOpenApps, Modifier.padding(horizontal = ScreenPadding))
        }
        Column(
            modifier = Modifier.padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            NavKeyRow(actions, width = max(padSize, 240.dp))
            Pad(state.touchpadMode && !touchExploration, actions, padSize, width - 2 * max(ScreenPadding, gestureInset))
        }
        BottomKeys(state, actions)
    }
}

@Composable
private fun LandscapeRemote(
    state: RemoteUiState,
    actions: RemoteActions,
    touchExploration: Boolean,
    onOpenApps: () -> Unit,
    onOpenDevices: () -> Unit,
    width: Dp,
    height: Dp,
) {
    // On short phones the half's width, not the height, is what limits the pad.
    val padRoom = width / 2 - 2 * ScreenPadding - NavKeySize - LandscapeGap
    val padSize = min((height - 32.dp).coerceIn(MinPadSize, MaxPadSize), padRoom).coerceAtLeast(MinLandscapePadSize)
    Row(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .heightIn(min = height)
                .padding(ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(LandscapeGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavKeyColumn(actions)
            Pad(state.touchpadMode && !touchExploration, actions, padSize, max(padSize, min(padSize * 1.3f, padRoom)))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .heightIn(min = height)
                .padding(vertical = ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Banner(state, actions, onOpenDevices, Modifier.padding(horizontal = ScreenPadding))
            if (state.favorites.isNotEmpty()) {
                FavoritesRow(
                    favorites = state.favorites,
                    activeAppId = state.activeAppId,
                    onLaunch = actions::launch,
                    onOpenApps = onOpenApps,
                    contentPadding = PaddingValues(horizontal = ScreenPadding),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ToolRow(state, actions, canSwitchPad = !touchExploration, onOpenApps, Modifier.padding(horizontal = ScreenPadding))
            BottomKeys(state, actions)
        }
    }
}

@Composable
private fun Banner(state: RemoteUiState, actions: RemoteActions, onOpenDevices: () -> Unit, modifier: Modifier) {
    StatusBanner(
        status = state.status,
        deviceName = state.deviceName ?: stringResource(R.string.remote_device_fallback),
        actions = actions,
        onOpenDevices = onOpenDevices,
        modifier = modifier,
    )
}

/** Inputs, the channel list when there are no favorites to show it, and the pad mode switch. */
@Composable
private fun ToolRow(state: RemoteUiState, actions: RemoteActions, canSwitchPad: Boolean, onOpenApps: () -> Unit, modifier: Modifier) {
    if (!state.isTv && state.favorites.isNotEmpty() && !canSwitchPad) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isTv) InputButton(state.inputs, state.activeAppId, actions)
        if (state.favorites.isEmpty()) {
            FilledTonalButton(onClick = onOpenApps, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.remote_action_channels))
            }
        }
        Spacer(Modifier.weight(1f))
        if (canSwitchPad) PadModeToggle(state.touchpadMode, onToggle = actions::setTouchpadMode)
    }
}

@Composable
private fun Pad(touchpad: Boolean, actions: RemoteActions, size: Dp, touchpadWidth: Dp) {
    if (touchpad) {
        Touchpad(actions, Modifier.size(width = min(touchpadWidth, 360.dp), height = size))
    } else {
        Ltr { DPad(actions, size) }
    }
}

@Composable
private fun BottomKeys(state: RemoteUiState, actions: RemoteActions) {
    Column(
        modifier = Modifier.padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PlaybackRow(actions)
        if (state.isTv) {
            VolumeRow(actions, showChannels = state.activeAppId?.startsWith(TUNER_APP_PREFIX) == true)
        }
    }
}

/** Whether TalkBack (or another touch-exploration service) is on; follows changes while shown. */
@Composable
private fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
