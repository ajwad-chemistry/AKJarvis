package com.akjarvis.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.akjarvis.app.domain.model.ThemeMode

// Futuristic palette: deep space navy with cyan/violet accents (dark),
// clean daylight with teal accents (light).
private val DarkColors = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF04252C),
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color(0xFF17062E),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF0A0E1A),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF121A2E),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1B2440),
    onSurfaceVariant = Color(0xFFA7B4CC),
    error = Color(0xFFF87171),
    outline = Color(0xFF2E3A5C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    tertiary = Color(0xFF059669),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFE4EAF4),
    onSurfaceVariant = Color(0xFF475467),
    error = Color(0xFFB3261E),
    outline = Color(0xFFC4CEDC),
)

private val AppTypography = Typography()

@Composable
fun AKJarvisTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
