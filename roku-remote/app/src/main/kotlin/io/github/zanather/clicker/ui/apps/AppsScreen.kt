package io.github.zanather.clicker.ui.apps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.zanather.clicker.ui.model.AppsUiState

@Composable
fun AppsScreen(
    state: AppsUiState,
    onLaunch: (appId: String) -> Unit,
    onToggleFavorite: (appId: String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TODO("UI agent")
}
