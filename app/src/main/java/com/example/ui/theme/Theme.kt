package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OledCarbon,
    primaryContainer = Color(0xFF003640),
    onPrimaryContainer = ElectricCyan,
    secondary = SignalMint,
    onSecondary = OledCarbon,
    secondaryContainer = Color(0xFF0A382E),
    onSecondaryContainer = SignalMint,
    tertiary = WarmAmber,
    onTertiary = OledCarbon,
    background = OledCarbon,
    onBackground = TextPrimary,
    surface = StudioSlate,
    onSurface = TextPrimary,
    surfaceVariant = StudioElevated,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    error = CrimsonDanger,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content
    )
}
