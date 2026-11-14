package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun MyApplicationTheme(
    themePreset: AppThemePreset = AppThemePreset.PASTEL_DUSK,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themePreset)
    val colorScheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.background,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.primary,
            secondary = palette.secondary,
            onSecondary = palette.background,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.secondary,
            tertiary = palette.tertiary,
            onTertiary = palette.background,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.danger,
            onError = palette.onDanger
        )
    } else {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.background,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.primary,
            secondary = palette.secondary,
            onSecondary = palette.background,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.secondary,
            tertiary = palette.tertiary,
            onTertiary = palette.background,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.danger,
            onError = palette.onDanger
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = palette.isLight
            insetsController.isAppearanceLightNavigationBars = palette.isLight
        }
    }

    CompositionLocalProvider(LocalStudioPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
