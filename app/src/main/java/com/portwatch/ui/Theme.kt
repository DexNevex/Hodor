package com.portwatch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.portwatch.R

/**
 * Light grey screen, white cards, one lime accent and dark pills. Names describe roles:
 * "Card" is the default surface and "Ink" the text on it.
 */
object Hodor {
    val Bg = Color(0xFFE9EAEC)
    val BgDeep = Color(0xFFE2E3E6)
    val Glow = Color(0xFFE9EAEC)          // (no glow in the light design)
    val Card = Color(0xFFFFFFFF)
    val CardHi = Color(0xFFF5F6F7)
    val Dark = Color(0xFF1D1E20)          // dark pills: status bar, dock
    val DarkRow = Color(0xFF2E2F33)
    val Border = Color(0xFFE4E5E8)
    val Pill = Color(0xFFEFF0F2)
    val Accent = Color(0xFFC6D96E)        // lime, for fills
    val AccentInk = Color(0xFF6A7B1A)     // olive, for lime-coloured text on white
    val AccentDim = Color(0xFFEEF3D3)     // soft lime tint
    val Violet = Color(0xFF8C7CF7)
    val Ink = Color(0xFF1B1C1E)
    val Muted = Color(0xFF8A8D93)
    val OnBg = Color(0xFF1B1C1E)
    val OnBgMuted = Color(0xFF6F7278)
    val OnDark = Color(0xFFF4F4F2)
    val OnDarkMuted = Color(0xFF9C9EA3)
    val OnAccent = Color(0xFF1B1C1E)
    val Danger = Color(0xFFE5484D)
    val DangerTint = Color(0xFFFCE6E6)
    val OkTint = Color(0xFFEEF3D3)
    val OkInk = Color(0xFF5E6E16)
    val Sea = Color(0xFFF5F6F7)
    val Land = Color(0xFFDADCE0)
    val LandHot = Color(0xFFC6D96E)
}

val Outfit = FontFamily(
    Font(R.font.outfit_300, FontWeight.Light),
    Font(R.font.outfit_400, FontWeight.Normal),
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
)

/** Squared "tech" face for numbers, labels and headings. */
val Oxanium = FontFamily(
    Font(R.font.oxanium_400, FontWeight.Normal),
    Font(R.font.oxanium_500, FontWeight.Medium),
    Font(R.font.oxanium_600, FontWeight.SemiBold),
)

private fun TextStyle.o() = copy(fontFamily = Outfit)
private fun TextStyle.x() = copy(fontFamily = Oxanium)

private val base = Typography()
// Outfit throughout (clean and light); Oxanium stays for small caps labels.
private val HodorType = Typography(
    displayLarge = base.displayLarge.o(), displayMedium = base.displayMedium.o(), displaySmall = base.displaySmall.o(),
    headlineLarge = base.headlineLarge.o(), headlineMedium = base.headlineMedium.o(), headlineSmall = base.headlineSmall.o(),
    titleLarge = base.titleLarge.o(), titleMedium = base.titleMedium.o().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.o().copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.o(), bodyMedium = base.bodyMedium.o(), bodySmall = base.bodySmall.o(),
    labelLarge = base.labelLarge.x(), labelMedium = base.labelMedium.x(),
    labelSmall = base.labelSmall.x().copy(letterSpacing = 0.8.sp),
)

@Composable
fun HodorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Hodor.AccentInk,
            onPrimary = Hodor.OnAccent,
            secondary = Hodor.Violet,
            background = Hodor.Bg,
            surface = Hodor.Card,
            onSurface = Hodor.Ink,
            surfaceVariant = Hodor.Pill,
            onSurfaceVariant = Hodor.Muted,
            error = Hodor.Danger,
            outline = Hodor.Border,
        ),
        typography = HodorType,
        content = content,
    )
}
