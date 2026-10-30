package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PhotoCoachColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = CharcoalBlack,
    primaryContainer = CyanAccentSubtle,
    onPrimaryContainer = CyanAccent,
    secondary = CyanAccentMuted,
    onSecondary = CharcoalBlack,
    secondaryContainer = CharcoalSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanAccent,
    onTertiary = CharcoalBlack,
    background = CharcoalBlack,
    onBackground = TextPrimary,
    surface = CharcoalDark,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalSurface,
    onSurfaceVariant = TextSecondary,
    outline = CharcoalBorder,
    outlineVariant = CharcoalSurfaceVariant,
    error = ErrorRed,
    onError = PureWhite
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PhotoCoachColorScheme,
        typography = Typography,
        content = content
    )
}
