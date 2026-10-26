package io.github.zanather.clicker.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import io.github.zanather.clicker.R
import io.github.zanather.clicker.ui.components.ClickerScaffold
import io.github.zanather.clicker.ui.components.Grouped
import io.github.zanather.clicker.ui.components.ReadableColumnPadding
import io.github.zanather.clicker.ui.components.SectionHeader
import io.github.zanather.clicker.ui.model.SettingsUiState
import io.github.zanather.clicker.ui.theme.ClickerTheme

/** Four switches, the two TV-side fixes that solve most "remote not working" reports, and the version. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onChange: (SettingsUiState) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    ClickerScaffold(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) { padding ->
        ReadableColumnPadding(padding) { contentPadding ->
            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(Grouped.Gap),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "remote") { SectionHeader(stringResource(R.string.settings_section_remote)) }
                item(key = "haptics") {
                    SwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = stringResource(R.string.settings_haptics),
                        description = stringResource(R.string.settings_haptics_body),
                        checked = state.hapticsEnabled,
                        onToggle = { onChange(state.copy(hapticsEnabled = !state.hapticsEnabled)) },
                        index = 0,
                        count = 3,
                    )
                }
                item(key = "volume-keys") {
                    SwitchRow(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        title = stringResource(R.string.settings_volume_keys),
                        description = stringResource(R.string.settings_volume_keys_body),
                        checked = state.volumeKeysControlTv,
                        onToggle = { onChange(state.copy(volumeKeysControlTv = !state.volumeKeysControlTv)) },
                        index = 1,
                        count = 3,
                    )
                }
                item(key = "keep-screen-on") {
                    SwitchRow(
                        icon = Icons.Rounded.LightMode,
                        title = stringResource(R.string.settings_keep_screen_on),
                        description = stringResource(R.string.settings_keep_screen_on_body),
                        checked = state.keepScreenOn,
                        onToggle = { onChange(state.copy(keepScreenOn = !state.keepScreenOn)) },
                        index = 2,
                        count = 3,
                    )
                }
                // Wallpaper colors arrived with Material You in Android 12.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item(key = "appearance") { SectionHeader(stringResource(R.string.settings_section_appearance)) }
                    item(key = "dynamic-color") {
                        SwitchRow(
                            icon = Icons.Rounded.Palette,
                            title = stringResource(R.string.settings_dynamic_color),
                            description = stringResource(R.string.settings_dynamic_color_body),
                            checked = state.dynamicColor,
                            onToggle = { onChange(state.copy(dynamicColor = !state.dynamicColor)) },
                            index = 0,
                            count = 1,
                        )
                    }
                }
                item(key = "help") { SectionHeader(stringResource(R.string.settings_section_help)) }
                item(key = "help-card") { HelpCard(expanded = helpOpen, onToggle = { helpOpen = !helpOpen }) }
                item(key = "about") { SectionHeader(stringResource(R.string.settings_section_about)) }
                item(key = "about-row") { AboutRow(state.appVersion) }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    ClickerTheme {
        SettingsScreen(SettingsUiState(keepScreenOn = true, appVersion = "1.0.0"), onChange = {}, onBack = {})
    }
}
