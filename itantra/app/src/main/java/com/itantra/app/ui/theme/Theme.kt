package com.itantra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ITantraColorScheme = darkColorScheme(
    primary = SignalGreen,
    onPrimary = BackgroundBlack,
    secondary = WarningAmber,
    onSecondary = BackgroundBlack,
    error = CriticalRed,
    onError = TextPrimary,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkElevated,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDim,
)

/**
 * Always-dark, high-contrast theme. This is intentionally NOT
 * light/dark-adaptive: iTantra is a field-communication tool, and a
 * consistent dark instrument-panel look is a deliberate product choice,
 * not an oversight. [isSystemInDarkTheme] is not consulted.
 */
@Composable
fun ITantraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ITantraColorScheme,
        typography = ITantraTypography,
        content = content,
    )
}
