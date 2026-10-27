package io.github.zanather.clicker.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.components.ClickerScaffold
import io.github.zanather.clicker.ui.components.InlineMessage
import io.github.zanather.clicker.ui.components.LoadingState
import io.github.zanather.clicker.ui.components.MessageState
import io.github.zanather.clicker.ui.model.AppTile
import io.github.zanather.clicker.ui.model.AppsUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme

/** Every installed channel as a tile; tap launches it, long-press pins it to the remote. */
@Composable
fun AppsScreen(
    state: AppsUiState,
    onLaunch: (appId: String) -> Unit,
    onToggleFavorite: (appId: String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickerScaffold(
        title = stringResource(R.string.apps_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.apps_refresh))
            }
        },
    ) { padding ->
        when {
            state.apps.isNotEmpty() -> AppGrid(state, onLaunch, onToggleFavorite, padding)
            state.loading -> LoadingState(stringResource(R.string.apps_loading), padding)
            state.error != null -> MessageState(
                icon = Icons.Rounded.SettingsRemote,
                title = stringResource(R.string.apps_error_title),
                body = state.error,
                contentPadding = padding,
                carded = true,
                action = { TryAgainButton(onRefresh) },
            )
            else -> MessageState(
                icon = Icons.Rounded.Apps,
                title = stringResource(R.string.apps_empty_title),
                body = stringResource(R.string.apps_empty_body),
                contentPadding = padding,
                action = {
                    FilledTonalButton(onClick = onRefresh, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.apps_refresh))
                    }
                },
            )
        }
    }
}

@Composable
private fun AppGrid(
    state: AppsUiState,
    onLaunch: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    padding: PaddingValues,
) {
    val direction = LocalLayoutDirection.current
    LazyVerticalGrid(
        // Wide enough for a "Now playing" pill on one line at the default font size.
        columns = GridCells.Adaptive(minSize = 112.dp),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction) + 10.dp,
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(direction) + 10.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hint", span = { GridItemSpan(maxLineSpan) }) { UsageHint() }
        state.error?.let { error ->
            item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                InlineMessage(Icons.Rounded.Info, error, Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
            }
        }
        items(state.apps, key = { it.id }) { app ->
            AppTileView(
                app = app,
                isActive = app.id == state.activeAppId,
                onLaunch = onLaunch,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

/** Long-press is invisible until someone tells you; this line does. */
@Composable
private fun UsageHint() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Icon(
            Icons.Rounded.TouchApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.apps_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TryAgainButton(onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.screens_try_again))
    }
}

internal val SampleApps = listOf(
    AppTile("12", "Netflix", null, isFavorite = true),
    AppTile("837", "YouTube", null, isFavorite = true),
    AppTile("13", "Prime Video", null),
    AppTile("291097", "Disney+", null, isFavorite = true),
    AppTile("2285", "Hulu", null),
    AppTile("61322", "Max", null),
    AppTile("151908", "The Roku Channel", null),
    AppTile("74519", "Pluto TV - It's Free TV", null),
    AppTile("2213", "Spotify Music", null),
    AppTile("593099", "Apple TV", null),
    AppTile("34376", "ESPN", null),
)

@PreviewLightDark
@Composable
private fun AppsScreenPreview() {
    ClickerTheme {
        AppsScreen(AppsUiState(apps = SampleApps, activeAppId = "837"), {}, {}, {}, {})
    }
}

@Preview
@Composable
private fun AppsScreenBlockedPreview() {
    ClickerTheme {
        AppsScreen(
            AppsUiState(
                error = "Your TV is hiding its channel list. On the TV, go to Settings > System > Advanced system settings > " +
                    "Control by mobile apps > Network access and choose Enabled.",
            ),
            {}, {}, {}, {},
        )
    }
}
