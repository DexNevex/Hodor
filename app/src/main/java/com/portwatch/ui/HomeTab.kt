package com.portwatch.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import com.portwatch.data.BlockLog
import com.portwatch.data.Counters
import com.portwatch.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portwatch.data.ConnView
import com.portwatch.data.Geo
import com.portwatch.data.GeoResolver
import com.portwatch.data.Settings
import com.portwatch.data.ThreatIntel
import com.portwatch.data.Traffic
import com.portwatch.vpn.PortWatchVpnService
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.sqrt

private const val THREE_DAYS = 3 * 24 * 3_600_000L

/** Home: status pill, map, speeds, where traffic goes, and short summaries. */
@Composable
fun HomeTab(list: List<ConnView>, running: Boolean, onToggle: () -> Unit, goTo: (Int, String?) -> Unit) {
    val traffic by Traffic.state.collectAsState()
    val feeds by ThreatIntel.status.collectAsState()
    val blockThreats by Settings.blockThreats.collectAsState()
    val blockAds by Settings.blockAds.collectAsState()
    val held by BlockLog.entries.collectAsState()
    val counts by Counters.state.collectAsState()
    val (score, reasons) = protection(list, running, feeds, blockThreats)
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusPill(running, traffic.startedAt, onToggle) }
        item { MapCard(list) }
        item { SpeedCard(traffic) }
        item { CountryStrip(list) }
        item {
            InfoRow(
                R.drawable.ic_nav_shield, "Protection",
                reasons.firstOrNull()?.first ?: "Watching, lists up to date",
                "$score", "%", warn = score < 80, tile = IosColor.Green,
            ) { goTo(Screen.SHIELD, null) }
        }
        item {
            InfoRow(
                R.drawable.ic_nav_traffic, "Ads blocked today",
                if (blockAds) "All time %,d".format(counts.ads.allTime) else "Off · tap to turn on",
                "%,d".format(counts.ads.today), "", tile = IosColor.Purple,
            ) { goTo(Screen.SHIELD, "ads") }
        }
        item {
            InfoRow(
                R.drawable.ic_bell, "Sites held today",
                "All time %,d".format(counts.held.allTime) + (held.firstOrNull()?.let { " · last ${it.domain}" } ?: ""),
                "%,d".format(counts.held.today), "", tile = IosColor.Orange,
            ) { goTo(Screen.SHIELD, "held") }
        }
        item { VersionFooter() }
    }
}

/** Dark capsule like an "incoming call" bar: status, uptime, and a start/stop button. */
@Composable
private fun StatusPill(running: Boolean, startedAt: Long, onToggle: () -> Unit) {
    val now by produceState(System.currentTimeMillis(), running) {
        while (running) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = if (running && startedAt > 0) (now - startedAt).coerceAtLeast(0) / 1000 else 0
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(Hodor.Dark).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(if (running) Hodor.Accent else Hodor.DarkRow),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_nav_shield), contentDescription = null,
                colorFilter = ColorFilter.tint(if (running) Hodor.Ink else Hodor.OnDarkMuted), modifier = Modifier.size(26.dp),
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (running) "Watching" else "Not watching",
                style = MaterialTheme.typography.titleMedium, color = Hodor.OnDark,
            )
            Text(
                if (running) "%02d:%02d:%02d".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60) else "Tap to start",
                style = MaterialTheme.typography.labelMedium, color = Hodor.OnDarkMuted,
            )
        }
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Hodor.Card).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (running) {
                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Hodor.Ink))
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Start", tint = Hodor.Ink, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun SpeedCard(t: Traffic.Snapshot) {
    HCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SpeedHalf("Download", t.downPerSec, t.down, t.totalIn, Hodor.AccentInk, Modifier.weight(1f))
            SpeedHalf("Upload", t.upPerSec, t.up, t.totalOut, Hodor.Violet, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SpeedHalf(label: String, now: Long, history: List<Long>, total: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = Hodor.Muted)
        }
        val (v, unit) = rate(now)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v, fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 32.sp, color = Hodor.Ink)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Hodor.Muted, modifier = Modifier.padding(bottom = 7.dp))
        }
        Sparkline(history, color, Modifier.fillMaxWidth().height(34.dp))
        Spacer(Modifier.height(6.dp))
        Text("${bytes(total)} this session", style = MaterialTheme.typography.bodySmall, color = Hodor.Muted)
    }
}

/** Countries as a scrolling strip, like a weather forecast row. */
@Composable
private fun CountryStrip(list: List<ConnView>) {
    val geo by GeoResolver.results.collectAsState()
    val geoOnline by Settings.geoOnline.collectAsState()
    val countries = remember(list, geo) {
        list.filter { !it.isDns }.mapNotNull { c -> geo[c.remoteIp]?.let { it to c } }
            .groupBy { it.first.countryCode }
            .map { (_, v) -> Triple(v.first().first, v.size, v.any { it.second.threat != null }) }
            .sortedByDescending { it.second }
    }
    HCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Where it goes", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink, modifier = Modifier.weight(1f))
            Text("${countries.size} countries", style = MaterialTheme.typography.bodySmall, color = Hodor.Muted)
        }
        Spacer(Modifier.height(12.dp))
        if (countries.isEmpty()) {
            Text(
                if (geoOnline) "No located traffic yet." else "Location lookups are off (Shield → Location lookups).",
                style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
            )
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                countries.take(20).forEach { (g, n, bad) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(flag(g.countryCode), fontSize = 28.sp)
                        Text(
                            "$n", fontFamily = AppFont, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                            color = if (bad) Hodor.Danger else Hodor.Ink,
                        )
                        Text(g.countryCode, style = MaterialTheme.typography.labelSmall, color = Hodor.Muted)
                    }
                }
            }
        }
    }
}

/** Compact summary row: soft lime icon, title and subtitle, big number. */
@Composable
private fun InfoRow(
    icon: Int, title: String, subtitle: String, value: String, unit: String,
    warn: Boolean = false, tile: Color = IosColor.Green, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(CardShape).background(Hodor.Card).border(1.dp, Hodor.Border, CardShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, if (warn) IosColor.Red else tile, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Hodor.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(value, fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 32.sp, color = if (warn) Hodor.Danger else Hodor.Ink)
        if (unit.isNotEmpty()) {
            Text(unit, style = MaterialTheme.typography.labelMedium, color = Hodor.Muted, modifier = Modifier.padding(start = 2.dp, top = 8.dp))
        }
    }
}

/**
 * Start at 100 and subtract for each thing that weakens protection; each reason comes with
 * its cost so the number explains itself.
 */
fun protection(
    list: List<ConnView>, running: Boolean, feeds: List<ThreatIntel.FeedStatus>, blockThreats: Boolean,
): Pair<Int, List<Pair<String, Int>>> {
    val now = System.currentTimeMillis()
    val flaggedDest = list.filter { it.threat != null }.map { it.host ?: it.remoteIp }.distinct().size
    val listsOk = feeds.isNotEmpty() && feeds.all { it.entries > 0 && now - it.updated < THREE_DAYS }
    val reasons = buildList {
        if (!running) add("Not watching traffic" to 20)
        if (flaggedDest > 0) add("$flaggedDest known-bad destination${if (flaggedDest > 1) "s" else ""} contacted" to minOf(60, 15 * flaggedDest))
        if (!blockThreats) add("Flagged traffic is not blocked" to 10)
        if (!listsOk) add("Threat lists missing or out of date" to 15)
    }
    return (100 - reasons.sumOf { it.second }).coerceIn(0, 100) to reasons
}

@Composable
private fun VersionFooter() {
    val ctx = LocalContext.current
    val info = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }
    Text(
        "Hodor ${info.versionName} · hold the door",
        style = MaterialTheme.typography.labelSmall, color = Hodor.Muted,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

// ---------- map ----------

/** Connections that end at one place (city, or country when the city is unknown). */
private data class MapPoint(val geo: Geo, val conns: List<ConnView>) {
    val id get() = "${geo.city}|${geo.countryCode}"
    val flagged get() = conns.any { it.threat != null }
}

private class Country(val cc: String, val rings: List<FloatArray>)

/** Natural Earth 1:110m country outlines, bundled so the map works offline. */
private object WorldMap {
    private var countries: List<Country>? = null

    fun load(ctx: Context): List<Country> = countries ?: run {
        val json = JSONObject(ctx.assets.open("countries.geojson").bufferedReader().readText())
        val features = json.getJSONArray("features")
        val out = ArrayList<Country>()
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val g = f.getJSONObject("geometry")
            val c = g.getJSONArray("coordinates")
            val polys = if (g.getString("type") == "Polygon") listOf(c) else (0 until c.length()).map { c.getJSONArray(it) }
            val rings = ArrayList<FloatArray>()
            for (poly in polys) {
                for (r in 0 until poly.length()) {
                    val ring = poly.getJSONArray(r)
                    val arr = FloatArray(ring.length() * 2)
                    for (p in 0 until ring.length()) {
                        val pt = ring.getJSONArray(p)
                        arr[p * 2] = pt.getDouble(0).toFloat()
                        arr[p * 2 + 1] = pt.getDouble(1).toFloat()
                    }
                    rings.add(arr)
                }
            }
            out.add(Country(f.getJSONObject("properties").optString("cc"), rings))
        }
        out.also { countries = it }
    }
}

/** Equirectangular projection; the world fills the width, cropped to 80°S–84°N. */
private fun project(lon: Double, lat: Double, w: Float, h: Float): Offset {
    val worldH = w / 2
    val top = (h - worldH * (164f / 180f)) / 2 - worldH * (6f / 180f)
    return Offset(((lon + 180) / 360 * w).toFloat(), (top + (90 - lat) / 180 * worldH).toFloat())
}

/** Places close together on screen, drawn as one dot until you zoom in. */
private class Cluster(val center: Offset, val points: List<MapPoint>) {
    val count get() = points.sumOf { it.conns.size }
    val flagged get() = points.any { it.flagged }
}

private class MapView {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var size = Size.Zero

    fun zoomBy(factor: Float, pivot: Offset = Offset(size.width / 2, size.height / 2)) {
        val newScale = (scale * factor).coerceIn(1f, 24f)
        val o = pivot - (pivot - offset) * (newScale / scale)
        scale = newScale
        offset = clamp(o)
    }

    fun clamp(o: Offset) = Offset(
        o.x.coerceIn(size.width * (1 - scale), 0f),
        o.y.coerceIn(size.height * (1 - scale), 0f),
    )
}

private fun clusters(points: List<MapPoint>, view: MapView, w: Float, h: Float, minDist: Float): List<Cluster> {
    val groups = ArrayList<Pair<Offset, MutableList<MapPoint>>>()
    for (p in points.sortedByDescending { it.conns.size }) {
        val s = project(p.geo.lon, p.geo.lat, w, h) * view.scale + view.offset
        val g = groups.firstOrNull { (it.first - s).getDistance() < minDist }
        if (g != null) g.second.add(p) else groups.add(s to mutableListOf(p))
    }
    return groups.map { Cluster(it.first, it.second) }
}

private fun mapPoints(list: List<ConnView>, geo: Map<String, Geo>) =
    list.filter { !it.isDns }
        .mapNotNull { c -> geo[c.remoteIp]?.let { it to c } }
        .groupBy { (g, _) -> "${g.city}|${g.countryCode}" }
        .map { (_, pairs) -> MapPoint(pairs.first().first, pairs.map { it.second }) }

@Composable
private fun MapCard(list: List<ConnView>) {
    val geo by GeoResolver.results.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    val view = remember { MapView() }
    val points = remember(list, geo) { mapPoints(list, geo) }
    val countries = remember(points) { points.map { it.geo.countryCode }.distinct().size }
    val sel = points.firstOrNull { it.id == selected }

    Column(Modifier.fillMaxWidth().clip(CardShape).background(Hodor.Sea).border(1.dp, Hodor.Border, CardShape)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.65f)) {
            WorldCanvas(points, sel, view) { selected = it?.id }
            Column(Modifier.padding(18.dp)) {
                Text("TRAFFIC MAP", style = MaterialTheme.typography.labelMedium, color = Hodor.AccentInk, letterSpacing = 2.sp)
                Text(
                    "$countries countries · ${points.size} places",
                    style = MaterialTheme.typography.labelSmall, color = Hodor.Muted,
                )
            }
            Column(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleButton({ view.zoomBy(1.8f) }, bg = Hodor.CardHi, size = 38.dp) { Text("+", fontSize = 18.sp, color = Hodor.Ink) }
                CircleButton({ view.zoomBy(1 / 1.8f) }, bg = Hodor.CardHi, size = 38.dp) { Text("−", fontSize = 18.sp, color = Hodor.Ink) }
            }
        }
        if (sel != null) PlaceCard(sel) { selected = null }
    }
}

@Composable
private fun PlaceCard(p: MapPoint, onClose: () -> Unit) {
    HCard(Modifier.fillMaxWidth(), color = Hodor.CardHi, padding = 18.dp, onClick = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(p.conns.first().packageName, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${flag(p.geo.countryCode)} " + listOf(p.geo.city, p.geo.country).filter { it.isNotEmpty() }.joinToString(", "),
                    style = MaterialTheme.typography.titleMedium, color = Hodor.Ink,
                )
                Text(
                    "${p.conns.size} connections · ${p.conns.map { it.appLabel }.distinct().size} apps",
                    style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
                )
            }
            Text("✕", color = Hodor.Muted, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Hodor.Border)
        p.conns.groupBy { it.remoteIp }.forEach { (ip, cs) ->
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    cs.firstNotNullOfOrNull { it.host } ?: ip,
                    style = MaterialTheme.typography.bodyMedium, color = Hodor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$ip · " + cs.map { it.appLabel }.distinct().joinToString(", ") + " · " +
                        cs.map { "${it.protoName} ${it.remotePort}" }.distinct().joinToString(", "),
                    style = MaterialTheme.typography.labelSmall, color = Hodor.Muted,
                )
                cs.firstNotNullOfOrNull { it.threat }?.let { ThreatLine(it) }
            }
        }
        if (p.geo.org.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("NETWORK  ${p.geo.org}", style = MaterialTheme.typography.labelSmall, color = Hodor.Muted)
        }
    }
}

@Composable
private fun WorldCanvas(points: List<MapPoint>, selected: MapPoint?, view: MapView, onSelect: (MapPoint?) -> Unit) {
    val ctx = LocalContext.current
    val countries = remember { WorldMap.load(ctx) }
    val hot = remember(points) { points.map { it.geo.countryCode }.toSet() }
    val pathCache = remember { HashMap<Size, List<Pair<String, Path>>>() }
    val tapRadius = with(LocalDensity.current) { 22.dp.toPx() }
    val mergeDist = with(LocalDensity.current) { 26.dp.toPx() }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)

    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged { view.size = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                // Two fingers zoom; one finger pans only once zoomed in. Otherwise the drag is
                // left alone so the page scrolls (the map can fill most of a tablet screen).
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || view.scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) view.zoomBy(zoom, event.calculateCentroid())
                            view.offset = view.clamp(view.offset + event.calculatePan())
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val hit = clusters(points, view, size.width.toFloat(), size.height.toFloat(), mergeDist)
                        .minByOrNull { (it.center - tap).getDistance() }
                        ?.takeIf { (it.center - tap).getDistance() < tapRadius }
                    when {
                        hit == null -> onSelect(null)
                        hit.points.size == 1 -> onSelect(hit.points[0])
                        else -> {
                            view.zoomBy(2.5f, hit.center) // spread the merged places apart
                            onSelect(null)
                        }
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val paths = pathCache.getOrPut(size) {
            countries.map { c ->
                c.cc to Path().apply {
                    for (r in c.rings) {
                        if (r.size < 4) continue
                        val first = project(r[0].toDouble(), r[1].toDouble(), w, h)
                        moveTo(first.x, first.y)
                        var i = 2
                        while (i < r.size) {
                            val p = project(r[i].toDouble(), r[i + 1].toDouble(), w, h)
                            lineTo(p.x, p.y)
                            i += 2
                        }
                        close()
                    }
                }
            }
        }
        withTransform({
            translate(view.offset.x, view.offset.y)
            scale(view.scale, view.scale, pivot = Offset.Zero)
        }) {
            for ((cc, path) in paths) {
                drawPath(path, if (cc in hot) Hodor.LandHot else Hodor.Land)
                drawPath(path, Hodor.Card, style = Stroke(width = 0.6.dp.toPx() / view.scale))
            }
        }
        val unit = 1.dp.toPx()
        for (cl in clusters(points, view, w, h, mergeDist).sortedBy { it.flagged }) {
            val c = cl.center
            val r = (9 + sqrt(cl.count.toFloat()) * 1.1f).coerceAtMost(16f) * unit
            val fill = if (cl.flagged) Hodor.Danger else Hodor.MapDot
            drawCircle(if (cl.flagged) fill.copy(alpha = 0.18f) else Hodor.Accent.copy(alpha = 0.45f), radius = r * 1.8f, center = c)
            if (selected != null && selected in cl.points) drawCircle(Hodor.Ink, radius = r + 3 * unit, center = c)
            drawCircle(fill, radius = r, center = c)
            val label = measurer.measure("${cl.count}", labelStyle.copy(color = if (cl.flagged) Color.White else Hodor.MapDotText))
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
        }
    }
}
