package com.example.ui.theme

import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppThemePreset(
    val storageKey: String,
    val displayName: String,
    val badge: String,
    val description: String,
    val isLight: Boolean
) {
    PASTEL_DUSK(
        storageKey = "pastel_dusk",
        displayName = "Pastel Dusk",
        badge = "EYE COMFORT",
        description = "Soft matte charcoal with gentle pastel lavender & sage accents — zero neon glare",
        isLight = false
    ),
    WARM_ESPRESSO(
        storageKey = "warm_espresso",
        displayName = "Warm Espresso",
        badge = "NIGHT WARM",
        description = "Low-blue-light cocoa surfaces with warm honey & peach tones for late-night listening",
        isLight = false
    ),
    SOFT_LINEN(
        storageKey = "soft_linen",
        displayName = "Soft Linen",
        badge = "GENTLE LIGHT",
        description = "Warm cream paper palette with dusty teal & sage for calm daytime readability",
        isLight = true
    ),
    MIDNIGHT_SLATE(
        storageKey = "midnight_slate",
        displayName = "Midnight Slate",
        badge = "OLED DARK",
        description = "Deep dark studio surfaces with toned-down ice-blue & muted sage accents",
        isLight = false
    );

    companion object {
        fun fromStorageKey(key: String?): AppThemePreset {
            return entries.find { it.storageKey == key } ?: PASTEL_DUSK
        }
    }
}

@Immutable
data class StudioPalette(
    val isLight: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val primary: Color,
    val primaryContainer: Color,
    val primaryDim: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val mutedSage: Color,
    val mutedSlate: Color,
    val tertiary: Color,
    val danger: Color,
    val onDanger: Color,
    val purple: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val switchCheckedTrack: Color,
    val switchCheckedBorder: Color,
    val switchCheckedThumb: Color,
    val switchUncheckedTrack: Color,
    val switchUncheckedThumb: Color
)

val PastelDuskPalette = StudioPalette(
    isLight = false,
    background = Color(0xFF16181F),
    surface = Color(0xFF1F222C),
    surfaceElevated = Color(0xFF282C38),
    border = Color(0xFF353A4A),
    primary = Color(0xFFA8C5E2),
    primaryContainer = Color(0xFF2C3B4B),
    primaryDim = Color(0xFF7B9AB8),
    secondary = Color(0xFFA3C9B8),
    secondaryContainer = Color(0xFF283D35),
    mutedSage = Color(0xFF9AC2B0),
    mutedSlate = Color(0xFF93A8BE),
    tertiary = Color(0xFFE4C09B),
    danger = Color(0xFFE29498),
    onDanger = Color(0xFF1E1214),
    purple = Color(0xFFC2B2DF),
    textPrimary = Color(0xFFECEFF4),
    textSecondary = Color(0xFFAEB6C7),
    textMuted = Color(0xFF788096),
    switchCheckedTrack = Color(0xFF42576B),
    switchCheckedBorder = Color(0xFF5D7891),
    switchCheckedThumb = Color(0xFFECEFF4),
    switchUncheckedTrack = Color(0xFF262A36),
    switchUncheckedThumb = Color(0xFF8C94A8)
)

val WarmEspressoPalette = StudioPalette(
    isLight = false,
    background = Color(0xFF191513),
    surface = Color(0xFF231D1A),
    surfaceElevated = Color(0xFF2E2622),
    border = Color(0xFF3F352F),
    primary = Color(0xFFDFBE99),
    primaryContainer = Color(0xFF423426),
    primaryDim = Color(0xFFB3916E),
    secondary = Color(0xFFA7BFA3),
    secondaryContainer = Color(0xFF2D3A2B),
    mutedSage = Color(0xFFA7BFA3),
    mutedSlate = Color(0xFFB3A294),
    tertiary = Color(0xFFE2A97E),
    danger = Color(0xFFDF8882),
    onDanger = Color(0xFF211211),
    purple = Color(0xFFC7A8C9),
    textPrimary = Color(0xFFF3ECE4),
    textSecondary = Color(0xFFBFB3A8),
    textMuted = Color(0xFF85796F),
    switchCheckedTrack = Color(0xFF5A4839),
    switchCheckedBorder = Color(0xFF7A6350),
    switchCheckedThumb = Color(0xFFF3ECE4),
    switchUncheckedTrack = Color(0xFF2B2420),
    switchUncheckedThumb = Color(0xFF978A80)
)

val SoftLinenPalette = StudioPalette(
    isLight = true,
    background = Color(0xFFF5F2EB),
    surface = Color(0xFFECE8DF),
    surfaceElevated = Color(0xFFE2DDD2),
    border = Color(0xFFCEC7B8),
    primary = Color(0xFF3E6B7B),
    primaryContainer = Color(0xFFD1E2E8),
    primaryDim = Color(0xFF2F5360),
    secondary = Color(0xFF4A7564),
    secondaryContainer = Color(0xFFD2E4DC),
    mutedSage = Color(0xFF4A7564),
    mutedSlate = Color(0xFF556D82),
    tertiary = Color(0xFF9E6930),
    danger = Color(0xFFB54B4B),
    onDanger = Color(0xFFFFFBF8),
    purple = Color(0xFF6F5A8E),
    textPrimary = Color(0xFF212529),
    textSecondary = Color(0xFF525860),
    textMuted = Color(0xFF7A818A),
    switchCheckedTrack = Color(0xFF5A808E),
    switchCheckedBorder = Color(0xFF3E6B7B),
    switchCheckedThumb = Color(0xFFFAF8F3),
    switchUncheckedTrack = Color(0xFFD5CFC2),
    switchUncheckedThumb = Color(0xFF6E757E)
)

val MidnightSlatePalette = StudioPalette(
    isLight = false,
    background = Color(0xFF0D0F14),
    surface = Color(0xFF141822),
    surfaceElevated = Color(0xFF1D2330),
    border = Color(0xFF2A3244),
    primary = Color(0xFF7CC6D9),
    primaryContainer = Color(0xFF1A3A44),
    primaryDim = Color(0xFF4F8E9E),
    secondary = Color(0xFF7FAEA3),
    secondaryContainer = Color(0xFF1B342F),
    mutedSage = Color(0xFF7FAEA3),
    mutedSlate = Color(0xFF7E9BB0),
    tertiary = Color(0xFFE0B063),
    danger = Color(0xFFE06C75),
    onDanger = Color(0xFFFFFFFF),
    purple = Color(0xFFB09AE0),
    textPrimary = Color(0xFFF1F4F8),
    textSecondary = Color(0xFF9BA3B8),
    textMuted = Color(0xFF687085),
    switchCheckedTrack = Color(0xFF2B4B5A),
    switchCheckedBorder = Color(0xFF3E6A7E),
    switchCheckedThumb = Color(0xFFF1F4F8),
    switchUncheckedTrack = Color(0xFF181C26),
    switchUncheckedThumb = Color(0xFF7B8396)
)

fun paletteFor(preset: AppThemePreset): StudioPalette = when (preset) {
    AppThemePreset.PASTEL_DUSK -> PastelDuskPalette
    AppThemePreset.WARM_ESPRESSO -> WarmEspressoPalette
    AppThemePreset.SOFT_LINEN -> SoftLinenPalette
    AppThemePreset.MIDNIGHT_SLATE -> MidnightSlatePalette
}

val LocalStudioPalette = compositionLocalOf { PastelDuskPalette }

val OledCarbon: Color @Composable get() = LocalStudioPalette.current.background
val StudioSlate: Color @Composable get() = LocalStudioPalette.current.surface
val StudioElevated: Color @Composable get() = LocalStudioPalette.current.surfaceElevated
val StudioBorder: Color @Composable get() = LocalStudioPalette.current.border

val ElectricCyan: Color @Composable get() = LocalStudioPalette.current.primary
val ElectricCyanDim: Color @Composable get() = LocalStudioPalette.current.primaryDim
val SignalMint: Color @Composable get() = LocalStudioPalette.current.secondary
val MutedSage: Color @Composable get() = LocalStudioPalette.current.mutedSage
val MutedSlateBlue: Color @Composable get() = LocalStudioPalette.current.mutedSlate
val WarmAmber: Color @Composable get() = LocalStudioPalette.current.tertiary
val CrimsonDanger: Color @Composable get() = LocalStudioPalette.current.danger
val SoftPurple: Color @Composable get() = LocalStudioPalette.current.purple

val TextPrimary: Color @Composable get() = LocalStudioPalette.current.textPrimary
val TextSecondary: Color @Composable get() = LocalStudioPalette.current.textSecondary
val TextMuted: Color @Composable get() = LocalStudioPalette.current.textMuted

@Composable
fun studioSwitchColors(): SwitchColors {
    val p = LocalStudioPalette.current
    return SwitchDefaults.colors(
        checkedThumbColor = p.switchCheckedThumb,
        checkedTrackColor = p.switchCheckedTrack,
        checkedBorderColor = p.switchCheckedBorder,
        uncheckedThumbColor = p.switchUncheckedThumb,
        uncheckedTrackColor = p.switchUncheckedTrack,
        uncheckedBorderColor = p.border
    )
}
