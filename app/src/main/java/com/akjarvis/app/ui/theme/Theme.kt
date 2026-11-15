package com.akjarvis.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.akjarvis.app.domain.model.ThemeMode

// "Liquid Glass" design system (inspired by the AbuZar Jarvis app):
// deep slate surfaces with indigo accents, glassy cards and gradients.

// Slate palette
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)

// Brand accents
val Indigo = Color(0xFF6366F1)
val IndigoLight = Color(0xFF818CF8)
val IndigoDeep = Color(0xFF4F46E5)
val Success = Color(0xFF22C55E)
val Danger = Color(0xFFEF4444)
val Warning = Color(0xFFF59E0B)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = Slate950,
    secondary = Indigo,
    onSecondary = Slate950,
    tertiary = Success,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    error = Danger,
    outline = Slate700,
)

private val LightColors = lightColorScheme(
    primary = IndigoDeep,
    onPrimary = Color.White,
    secondary = Indigo,
    onSecondary = Color.White,
    tertiary = Color(0xFF059669),
    background = Slate50,
    onBackground = Slate900,
    surface = Slate50,
    onSurface = Slate900,
    surfaceVariant = Color.White,
    onSurfaceVariant = Slate600,
    error = Color(0xFFB3261E),
    outline = Slate300,
)

// Iron Man edition: hot-rod red and gold on dark carbon (kept from v1.2).
private val IronColors = darkColorScheme(
    primary = Color(0xFFFBBF24),
    onPrimary = Color(0xFF2B1902),
    secondary = Color(0xFFEF4444),
    onSecondary = Color(0xFF2B0505),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF120B08),
    onBackground = Color(0xFFF7EFE3),
    surface = Color(0xFF1E120C),
    onSurface = Color(0xFFF7EFE3),
    surfaceVariant = Color(0xFF2C1B11),
    onSurfaceVariant = Color(0xFFCBB79B),
    error = Color(0xFFF87171),
    outline = Color(0xFF4A2E1C),
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
        ThemeMode.IRON -> true
    }
    val colorScheme = when (themeMode) {
        ThemeMode.IRON -> IronColors
        else -> if (darkTheme) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
