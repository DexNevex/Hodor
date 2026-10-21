package com.portwatch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.portwatch.R

/**
 * Near-black cards under a mint glow. Names describe roles, not hues:
 * "Card" is the default surface and "Ink" the text on it.
 */
object Hodor {
    val Bg = Color(0xFF07090A)
    val BgDeep = Color(0xFF050607)
    val Glow = Color(0xFF9DF2D2)          // top-of-screen mint wash
    val Card = Color(0xFF131716)
    val CardHi = Color(0xFF1B201F)
    val Dark = Color(0xFF0D100F)
    val DarkRow = Color(0xFF1F2523)
    val Border = Color(0xFF262C2A)
    val Pill = Color(0xFF222826)
    val Accent = Color(0xFF34E3A4)
    val AccentDim = Color(0xFF1C7A59)
    val Violet = Color(0xFF8C7CF7)
    val Ink = Color(0xFFF1F4F3)
    val Muted = Color(0xFF86908C)
    val OnBg = Color(0xFFF1F4F3)
    val OnBgMuted = Color(0xFF9AA39F)
    val OnDark = Color(0xFFF1F4F3)
    val OnDarkMuted = Color(0xFF86908C)
    val OnAccent = Color(0xFF04140D)
    val Danger = Color(0xFFFF5C61)
    val DangerTint = Color(0xFF3A1719)
    val OkTint = Color(0xFF12301F)
    val OkInk = Color(0xFF34E3A4)
    val Sea = Color(0xFF0E1211)
    val Land = Color(0xFF232A28)
    val LandHot = Color(0xFF1E6B50)
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
private val HodorType = Typography(
    displayLarge = base.displayLarge.x(), displayMedium = base.displayMedium.x(), displaySmall = base.displaySmall.x(),
    headlineLarge = base.headlineLarge.x(), headlineMedium = base.headlineMedium.x(), headlineSmall = base.headlineSmall.x(),
    titleLarge = base.titleLarge.x(), titleMedium = base.titleMedium.x().copy(fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.x(),
    bodyLarge = base.bodyLarge.o(), bodyMedium = base.bodyMedium.o(), bodySmall = base.bodySmall.o(),
    labelLarge = base.labelLarge.x(), labelMedium = base.labelMedium.x(),
    labelSmall = base.labelSmall.x().copy(letterSpacing = 0.8.sp),
)

@Composable
fun HodorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Hodor.Accent,
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
