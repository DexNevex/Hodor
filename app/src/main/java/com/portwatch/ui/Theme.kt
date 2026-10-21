package com.portwatch.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.portwatch.R
import com.portwatch.data.Settings

/** One colour set. Names describe roles: "Card" is the default surface, "Ink" the text on it. */
class Palette(
    val bg: Color, val card: Color, val cardHi: Color,
    val dark: Color, val darkRow: Color, val onDark: Color, val onDarkMuted: Color,
    val border: Color, val pill: Color,
    val accent: Color, val accentInk: Color, val accentDim: Color, val onAccent: Color,
    val violet: Color, val ink: Color, val muted: Color,
    val danger: Color, val dangerTint: Color,
    val sea: Color, val land: Color, val landHot: Color, val mapDot: Color, val mapDotText: Color,
    val dark_: Boolean,
)

/** iOS grouped-list colours (systemGroupedBackground etc.) with Hodor's lime accent. */
val LightPalette = Palette(
    bg = Color(0xFFF2F2F7), card = Color(0xFFFFFFFF), cardHi = Color(0xFFF2F2F7),
    dark = Color(0xFF1C1C1E), darkRow = Color(0xFF3A3A3C), onDark = Color(0xFFFFFFFF), onDarkMuted = Color(0xFF98989F),
    border = Color(0xFFE5E5EA), pill = Color(0xFFE9E9EE),
    accent = Color(0xFFC6D96E), accentInk = Color(0xFF627314), accentDim = Color(0xFFEEF3D3), onAccent = Color(0xFF1C1C1E),
    violet = Color(0xFF5856D6), ink = Color(0xFF1C1C1E), muted = Color(0xFF8E8E93),
    danger = Color(0xFFFF3B30), dangerTint = Color(0xFFFFE5E3),
    sea = Color(0xFFF7F7FA), land = Color(0xFFDCDCE1), landHot = Color(0xFFC6D96E),
    mapDot = Color(0xFF1C1C1E), mapDotText = Color(0xFFFFFFFF),
    dark_ = false,
)

val DarkPalette = Palette(
    bg = Color(0xFF000000), card = Color(0xFF1C1C1E), cardHi = Color(0xFF2C2C2E),
    dark = Color(0xFF2C2C2E), darkRow = Color(0xFF3A3A3C), onDark = Color(0xFFFFFFFF), onDarkMuted = Color(0xFF98989F),
    border = Color(0xFF38383A), pill = Color(0xFF2C2C2E),
    accent = Color(0xFFC6D96E), accentInk = Color(0xFFC6D96E), accentDim = Color(0xFF2E3418), onAccent = Color(0xFF1C1C1E),
    violet = Color(0xFF7D7AFF), ink = Color(0xFFFFFFFF), muted = Color(0xFF98989F),
    danger = Color(0xFFFF453A), dangerTint = Color(0xFF3A1A18),
    sea = Color(0xFF161618), land = Color(0xFF3A3A3C), landHot = Color(0xFF8A9C37),
    mapDot = Color(0xFFC6D96E), mapDotText = Color(0xFF1C1C1E),
    dark_ = true,
)

/**
 * The colours in use. Backed by Compose state, so switching between light and dark
 * redraws everything that reads them, including Canvas drawings.
 */
object Hodor {
    var palette by mutableStateOf(LightPalette)
    val isDark get() = palette.dark_

    val Bg get() = palette.bg
    val BgDeep get() = palette.bg
    val Glow get() = palette.bg
    val Card get() = palette.card
    val CardHi get() = palette.cardHi
    val Dark get() = palette.dark
    val DarkRow get() = palette.darkRow
    val Border get() = palette.border
    val Pill get() = palette.pill
    val Accent get() = palette.accent
    val AccentInk get() = palette.accentInk
    val AccentDim get() = palette.accentDim
    val Violet get() = palette.violet
    val Ink get() = palette.ink
    val Muted get() = palette.muted
    val OnBg get() = palette.ink
    val OnBgMuted get() = palette.muted
    val OnDark get() = palette.onDark
    val OnDarkMuted get() = palette.onDarkMuted
    val OnAccent get() = palette.onAccent
    val Danger get() = palette.danger
    val DangerTint get() = palette.dangerTint
    val OkTint get() = palette.accentDim
    val OkInk get() = palette.accentInk
    val Sea get() = palette.sea
    val Land get() = palette.land
    val LandHot get() = palette.landHot
    val MapDot get() = palette.mapDot
    val MapDotText get() = palette.mapDotText
}

/** Inter: the closest freely licensed match to Apple's San Francisco (SIL Open Font License). */
val AppFont = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

/** Sizes and weights follow the iOS text styles (Large Title, Headline, Body, Footnote…). */
private val HodorType = Typography(
    displayLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 0.4.sp),
    displayMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.3.sp),
    displaySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    headlineLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = 0.3.sp),
    headlineMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 0.3.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.4).sp),
    titleSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = (-0.2).sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 17.sp, letterSpacing = (-0.4).sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = (-0.2).sp),
    bodySmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = (-0.1).sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = (-0.2).sp),
    labelMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.1.sp),
)

/** True when the app should be dark: the user's choice, or the phone's setting for "System". */
@Composable
fun rememberDarkMode(): Boolean {
    val mode by Settings.appearance.collectAsState()
    val system = isSystemInDarkTheme()
    return when (mode) {
        Settings.Appearance.LIGHT -> false
        Settings.Appearance.DARK -> true
        Settings.Appearance.SYSTEM -> system
    }
}

@Composable
fun HodorTheme(content: @Composable () -> Unit) {
    val dark = rememberDarkMode()
    val p = if (dark) DarkPalette else LightPalette
    if (Hodor.palette !== p) Hodor.palette = p
    val base = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = base.copy(
            primary = p.accentInk, onPrimary = p.onAccent, secondary = p.violet,
            background = p.bg, surface = p.card, onSurface = p.ink, surfaceContainer = p.card,
            surfaceVariant = p.pill, onSurfaceVariant = p.muted, error = p.danger, outline = p.border,
        ),
        typography = HodorType,
        content = content,
    )
}
