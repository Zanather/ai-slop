package io.github.zanather.clicker.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether remote buttons buzz on press. Provided from settings at the root. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun ClickerTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    TODO("UI agent")
}
