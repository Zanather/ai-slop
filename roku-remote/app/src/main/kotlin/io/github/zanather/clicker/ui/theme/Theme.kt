package io.github.zanather.clicker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Whether remote buttons buzz on press. Provided from settings at the root. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Status colors that read the same in light and dark; not part of Material's scheme. */
@Immutable
data class StatusColors(
    val online: Color,
    val busy: Color,
    val offline: Color,
    val idle: Color,
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(online = Color(0xFF2E7D32), busy = Color(0xFFF9A825), offline = Color(0xFFC62828), idle = Color(0xFF8D8A86))
}

// Amber from the launcher icon on warm charcoal. Deliberately nothing like Roku purple.
private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA6),
    onPrimaryContainer = Color(0xFF261900),
    secondary = Color(0xFF6B5D3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF4E0BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF4A6547),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCCEBC4),
    onTertiaryContainer = Color(0xFF072109),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFEDE1CF),
    onSurfaceVariant = Color(0xFF4D4639),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF2E8),
    surfaceContainer = Color(0xFFF5ECE2),
    surfaceContainerHigh = Color(0xFFEFE6DC),
    surfaceContainerHighest = Color(0xFFE9E1D7),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD0C5B4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFBA2C),
    onPrimary = Color(0xFF412D00),
    primaryContainer = Color(0xFF5D4200),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondary = Color(0xFFD7C4A0),
    onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF52452A),
    onSecondaryContainer = Color(0xFFF4E0BB),
    tertiary = Color(0xFFB1CFA9),
    onTertiary = Color(0xFF1D361C),
    tertiaryContainer = Color(0xFF334D31),
    onTertiaryContainer = Color(0xFFCCEBC4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF151311),
    onBackground = Color(0xFFEAE1D9),
    surface = Color(0xFF151311),
    onSurface = Color(0xFFEAE1D9),
    surfaceVariant = Color(0xFF4D4639),
    onSurfaceVariant = Color(0xFFD0C5B4),
    surfaceContainerLowest = Color(0xFF100E0C),
    surfaceContainerLow = Color(0xFF1E1B18),
    surfaceContainer = Color(0xFF221F1C),
    surfaceContainerHigh = Color(0xFF2D2926),
    surfaceContainerHighest = Color(0xFF383431),
    outline = Color(0xFF999080),
    outlineVariant = Color(0xFF4D4639),
)

@Composable
fun ClickerTheme(
    dynamicColor: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
