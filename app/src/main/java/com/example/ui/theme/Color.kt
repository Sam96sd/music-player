package com.example.ui.theme

import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OledCarbon = Color(0xFF090A0F)
val StudioSlate = Color(0xFF12151E)
val StudioElevated = Color(0xFF1B202E)
val StudioBorder = Color(0xFF262D40)

val ElectricCyan = Color(0xFF00E5FF)
val ElectricCyanDim = Color(0xFF0088A3)
val SignalMint = Color(0xFF1DE9B6)
val MutedSage = Color(0xFF7FAEA3)
val MutedSlateBlue = Color(0xFF7E9BB0)
val WarmAmber = Color(0xFFFFB300)
val CrimsonDanger = Color(0xFFFF5252)
val SoftPurple = Color(0xFFB388FF)

val TextPrimary = Color(0xFFF3F6FA)
val TextSecondary = Color(0xFF9BA3B8)
val TextMuted = Color(0xFF636B80)

@Composable
fun studioSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = TextPrimary,
    checkedTrackColor = Color(0xFF2B4B5A),
    checkedBorderColor = Color(0xFF3E6A7E),
    uncheckedThumbColor = Color(0xFF7B8396),
    uncheckedTrackColor = Color(0xFF181C26),
    uncheckedBorderColor = StudioBorder
)
