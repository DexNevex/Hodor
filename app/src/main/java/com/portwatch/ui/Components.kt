package com.portwatch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portwatch.data.AppResolver
import com.portwatch.data.Geo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val CardShape = RoundedCornerShape(28.dp)

/** Rounded card used everywhere; [color] picks the grey, white or dark variant. */
@Composable
fun HCard(
    modifier: Modifier = Modifier,
    color: Color = Hodor.Card,
    padding: Dp = 18.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.clip(CardShape).background(color).border(1.dp, Hodor.Border, CardShape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Column(m.padding(padding), content = content)
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = Hodor.Pill,
    fg: Color = Hodor.Ink,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val m = modifier.clip(CircleShape).background(bg).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Row(m.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Hodor.Pill,
    size: Dp = 46.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun CircleIcon(icon: ImageVector, onClick: () -> Unit, bg: Color = Hodor.Pill, tint: Color = Hodor.Ink) =
    CircleButton(onClick, bg = bg) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }

enum class Tone { Ok, Danger, Neutral, Warn, Ad }

/** Small "● open" style badge. */
@Composable
fun StatusChip(text: String, tone: Tone) {
    val (bg, fg) = when (tone) {
        Tone.Ok -> Hodor.OkTint to Hodor.OkInk
        Tone.Danger -> Hodor.DangerTint to Hodor.Danger
        Tone.Warn -> Hodor.AccentDim to Hodor.Ink
        Tone.Neutral -> Hodor.Pill to Hodor.Muted
        Tone.Ad -> Color(0xFF221D3D) to Hodor.Violet
    }
    Row(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

fun stateTone(state: String, active: Boolean) = when {
    state == "blocked" -> Tone.Danger
    state == "failed" || state == "reset" || state == "timeout" -> Tone.Neutral
    active -> Tone.Ok
    else -> Tone.Neutral
}

@Composable
fun Field(label: String, value: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (dark) Hodor.OnDarkMuted else Hodor.Muted)
        Text(
            value, style = MaterialTheme.typography.bodyMedium, color = if (dark) Hodor.OnDark else Hodor.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ThreatLine(text: String, color: Color = Hodor.Danger) {
    Text(
        "⚠ $text",
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun AdLine(text: String) {
    Text(
        "◆ $text",
        style = MaterialTheme.typography.bodySmall,
        color = Hodor.Violet,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun AppIcon(pkg: String?, size: Dp = 42.dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { pkg?.let { AppResolver.icon(ctx, it) } }
    Box(Modifier.size(size).clip(CircleShape).background(Hodor.CardHi), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size * 0.82f))
        } else {
            Text("⚙", color = Hodor.Muted, fontSize = (size.value * 0.45f).sp)
        }
    }
}

fun flag(cc: String?): String {
    if (cc == null || cc.length != 2 || !cc.all { it.isLetter() }) return ""
    val u = cc.uppercase()
    return String(Character.toChars(0x1F1E6 + (u[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (u[1] - 'A')))
}

fun place(g: Geo?): String = g?.let { "${flag(it.countryCode)} ${it.city.ifEmpty { it.country }}".trim() } ?: "—"

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
fun time(ms: Long): String = timeFormat.format(Date(ms))

fun bytes(n: Long) = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.1f MB".format(n / (1024.0 * 1024))
}

@Composable
fun hodorSwitchColors(danger: Boolean = false) = SwitchDefaults.colors(
    checkedThumbColor = if (danger) Color.White else Hodor.OnAccent,
    checkedTrackColor = if (danger) Hodor.Danger else Hodor.Accent,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Hodor.Muted,
    uncheckedTrackColor = Hodor.Pill,
    uncheckedBorderColor = Hodor.Muted,
)

/** Rate split into number and unit, e.g. ("36.8", "KB/S"). */
fun rate(bps: Long): Pair<String, String> = when {
    bps < 1024 -> "$bps" to "B/S"
    bps < 1024 * 1024 -> "%.1f".format(bps / 1024.0) to "KB/S"
    else -> "%.1f".format(bps / (1024.0 * 1024)) to "MB/S"
}

fun amount(n: Long): Pair<String, String> = when {
    n < 1024 * 1024 -> "%.1f".format(n / 1024.0) to "KB"
    n < 1024L * 1024 * 1024 -> "%.1f".format(n / (1024.0 * 1024)) to "MB"
    else -> "%.2f".format(n / (1024.0 * 1024 * 1024)) to "GB"
}

/** Line graph of [values] with a fading fill underneath. */
@Composable
fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val grid = Hodor.Border
        for (i in 1..3) {
            val y = size.height * i / 4
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas
        val max = (values.max().coerceAtLeast(1)).toFloat()
        val step = size.width / (values.size - 1)
        val line = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / max) * size.height * 0.9f
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = androidx.compose.ui.graphics.Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Row of small blocks, the first [fraction] of them lit. */
@Composable
fun SegmentBar(fraction: Float, color: Color, modifier: Modifier = Modifier, segments: Int = 18) {
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val lit = (fraction.coerceIn(0f, 1f) * segments).toInt()
        for (i in 0 until segments) {
            val c = when {
                i < lit -> color
                i == lit -> Hodor.Ink
                else -> Hodor.Pill
            }
            drawRoundRect(
                c, topLeft = Offset(i * (w + gap), 0f), size = Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            )
        }
    }
}

/** Plain round power button with a tick ring that lights up while watching. */
@Composable
fun PowerButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(CircleShape).clickable(onClick = onClick)) {
        val c = center
        val r = size.minDimension / 2
        val ring = if (on) Hodor.Accent else Hodor.Pill
        if (on) drawCircle(Hodor.Accent.copy(alpha = 0.12f), radius = r, center = c)
        val ticks = 40
        for (i in 0 until ticks) {
            val a = Math.toRadians(i * 360.0 / ticks - 90)
            val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            drawLine(ring, c + dir * (r * 0.70f), c + dir * (r * 0.92f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
        }
        drawCircle(Hodor.CardHi, radius = r * 0.56f, center = c)
        drawCircle(if (on) Hodor.Accent.copy(alpha = 0.5f) else Hodor.Border, radius = r * 0.56f, center = c, style = Stroke(1.5.dp.toPx()))
        val g = r * 0.22f
        val glyph = if (on) Hodor.Accent else Hodor.Muted
        drawArc(
            glyph, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(c.x - g, c.y - g), size = Size(g * 2, g * 2),
            style = Stroke(r * 0.06f, cap = StrokeCap.Round),
        )
        drawLine(glyph, Offset(c.x, c.y - g * 1.25f), Offset(c.x, c.y - g * 0.2f), strokeWidth = r * 0.06f, cap = StrokeCap.Round)
    }
}
