package com.portwatch.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.Switch
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

/** Home: the map first, then live status, speeds, totals and protection. */
@Composable
fun HomeTab(list: List<ConnView>, running: Boolean, onToggle: () -> Unit, goTo: (Int) -> Unit) {
    val traffic by Traffic.state.collectAsState()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MapCard(list) }
        item { StatusCard(list, running, traffic.startedAt, onToggle) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeedCard("DOWNLOAD", "↓", traffic.downPerSec, traffic.down, Hodor.Accent, Modifier.weight(1f))
                SpeedCard("UPLOAD", "↑", traffic.upPerSec, traffic.up, Hodor.Violet, Modifier.weight(1f))
            }
        }
        item {
            val top = maxOf(traffic.totalIn, traffic.totalOut, 1L).toFloat()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalCard("DOWNLOADED", traffic.totalIn, traffic.totalIn / top, Hodor.Accent, Modifier.weight(1f))
                TotalCard("UPLOADED", traffic.totalOut, traffic.totalOut / top, Hodor.Violet, Modifier.weight(1f))
            }
        }
        item { ProtectionCard(list, running) { goTo(4) } }
        item { AdsSummaryCard(list) { goTo(4) } }
        item { CountriesCard(list) }
        item { VersionFooter() }
    }
}

// ---------- status ----------

@Composable
private fun StatusCard(list: List<ConnView>, running: Boolean, startedAt: Long, onToggle: () -> Unit) {
    val dns by PortWatchVpnService.upstreamDns.collectAsState()
    val now by produceState(System.currentTimeMillis(), running) {
        while (running) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = if (running && startedAt > 0) (now - startedAt).coerceAtLeast(0) / 1000 else 0
    HCard(Modifier.fillMaxWidth(), color = Hodor.Card, padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (running) "WATCHING" else "STOPPED",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (running) Hodor.Accent else Hodor.Muted,
                    letterSpacing = 2.sp,
                )
                Text(
                    "%02d:%02d:%02d".format(elapsed / 3600, elapsed / 60 % 60, elapsed % 60),
                    fontFamily = Oxanium, fontSize = 38.sp, color = Hodor.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (running) "DNS ${dns ?: "—"} · ${list.distinctBy { it.uid }.size} APPS · ${list.count { it.active }} OPEN"
                    else "TAP THE BUTTON TO START",
                    style = MaterialTheme.typography.labelSmall, color = Hodor.Muted,
                )
            }
            PowerButton(running, onToggle, Modifier.size(104.dp))
        }
    }
}

@Composable
private fun SpeedCard(label: String, arrow: String, now: Long, history: List<Long>, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    HCard(modifier, color = Hodor.Card, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Hodor.Ink, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text(arrow, color = color, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        Sparkline(history, color, Modifier.fillMaxWidth().height(52.dp))
        Spacer(Modifier.height(10.dp))
        val (v, unit) = rate(now)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v, fontFamily = Oxanium, fontSize = 28.sp, color = Hodor.Ink)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Hodor.Muted, modifier = Modifier.padding(bottom = 5.dp))
        }
    }
}

@Composable
private fun TotalCard(label: String, total: Long, fraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    HCard(modifier, color = Hodor.Card, padding = 16.dp) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Hodor.Ink, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(14.dp))
        val (v, unit) = amount(total)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(v, fontFamily = Oxanium, fontSize = 28.sp, color = Hodor.Ink)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelSmall, color = Hodor.Muted, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(10.dp))
        SegmentBar(fraction, color, Modifier.fillMaxWidth().height(10.dp))
        Spacer(Modifier.height(6.dp))
        Text("THIS SESSION", style = MaterialTheme.typography.labelSmall, color = Hodor.Muted)
    }
}

/**
 * Start at 100 and subtract for each thing that weakens protection; every
 * deduction is listed so the number explains itself.
 */
@Composable
private fun ProtectionCard(list: List<ConnView>, running: Boolean, open: () -> Unit) {
    val feeds by ThreatIntel.status.collectAsState()
    val blockThreats by Settings.blockThreats.collectAsState()
    val now = System.currentTimeMillis()
    val flaggedDest = list.filter { it.threat != null }.map { it.host ?: it.remoteIp }.distinct().size
    val listsOk = feeds.isNotEmpty() && feeds.all { it.entries > 0 && now - it.updated < THREE_DAYS }
    val reasons = buildList {
        if (!running) add("Not watching traffic" to 20)
        if (flaggedDest > 0) add("$flaggedDest known-bad destination${if (flaggedDest > 1) "s" else ""} contacted" to minOf(60, 15 * flaggedDest))
        if (!blockThreats) add("Flagged traffic is not blocked" to 10)
        if (!listsOk) add("Threat lists missing or out of date" to 15)
    }
    val score = (100 - reasons.sumOf { it.second }).coerceIn(0, 100)
    val color = when {
        score >= 80 -> Hodor.Accent
        score >= 50 -> Hodor.Violet
        else -> Hodor.Danger
    }
    HCard(Modifier.fillMaxWidth(), color = Hodor.Card, padding = 18.dp, onClick = open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PROTECTION", style = MaterialTheme.typography.labelMedium, color = Hodor.Ink, letterSpacing = 1.5.sp)
                Text(
                    when {
                        score >= 80 -> "LOW RISK"
                        score >= 50 -> "MEDIUM RISK"
                        else -> "HIGH RISK"
                    },
                    style = MaterialTheme.typography.labelSmall, color = color,
                )
            }
            Text("$score", fontFamily = Oxanium, fontSize = 34.sp, color = Hodor.Ink)
            Text("%", style = MaterialTheme.typography.labelMedium, color = Hodor.Muted, modifier = Modifier.padding(start = 2.dp, top = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        SegmentBar(score / 100f, color, Modifier.fillMaxWidth().height(10.dp), segments = 28)
        Spacer(Modifier.height(10.dp))
        if (reasons.isEmpty()) {
            Text("Watching, lists up to date, nothing flagged.", style = MaterialTheme.typography.bodySmall, color = Hodor.Accent)
        }
        reasons.forEach { (why, cost) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(why, style = MaterialTheme.typography.bodySmall, color = Hodor.Muted, modifier = Modifier.weight(1f))
                Text("−$cost", style = MaterialTheme.typography.labelSmall, color = if (cost >= 15) Hodor.Danger else Hodor.Muted)
            }
        }
    }
}

@Composable
private fun AdsSummaryCard(list: List<ConnView>, open: () -> Unit) {
    val blockAds by Settings.blockAds.collectAsState()
    val adLookups = list.count { it.ad != null }
    val blocked = list.count { it.ad != null && it.state == "blocked" }
    HCard(Modifier.fillMaxWidth(), color = Hodor.Card, padding = 18.dp, onClick = open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ADS BLOCKED", style = MaterialTheme.typography.labelMedium, color = Hodor.Ink, letterSpacing = 1.5.sp)
                Text(
                    if (blockAds) "$adLookups ad and tracker requests seen" else "OFF · TAP TO SET UP",
                    style = MaterialTheme.typography.labelSmall, color = if (blockAds) Hodor.Violet else Hodor.Muted,
                )
            }
            Text("$blocked", fontFamily = Oxanium, fontSize = 34.sp, color = if (blockAds) Hodor.Ink else Hodor.Muted)
        }
        if (blockAds) {
            Spacer(Modifier.height(10.dp))
            SegmentBar(if (adLookups == 0) 0f else blocked.toFloat() / adLookups, Hodor.Violet, Modifier.fillMaxWidth().height(10.dp), segments = 28)
        }
    }
}

@Composable
private fun VersionFooter() {
    val ctx = LocalContext.current
    val info = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }
    Text(
        "HODOR v${info.versionName} · BUILD ${info.longVersionCode}",
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
                Text("TRAFFIC MAP", style = MaterialTheme.typography.labelMedium, color = Hodor.Accent, letterSpacing = 2.sp)
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
private fun CountriesCard(list: List<ConnView>) {
    val geo by GeoResolver.results.collectAsState()
    val geoOnline by Settings.geoOnline.collectAsState()
    val points = remember(list, geo) { mapPoints(list, geo) }
    val countries = points.groupBy { it.geo.countryCode }
        .map { (_, ps) -> ps.first().geo to ps.flatMap { it.conns } }
        .sortedByDescending { it.second.size }
    HCard(Modifier.fillMaxWidth(), color = Hodor.Card, padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("COUNTRIES", style = MaterialTheme.typography.labelMedium, color = Hodor.Ink, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            Text("${countries.size}", fontFamily = Oxanium, fontSize = 28.sp, color = Hodor.Ink)
        }
        if (countries.isEmpty()) {
            Text("No located traffic yet.", style = MaterialTheme.typography.bodySmall, color = Hodor.Muted)
        }
        countries.forEach { (g, conns) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(flag(g.countryCode), fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.country, style = MaterialTheme.typography.bodyLarge, color = Hodor.Ink)
                    Text(
                        conns.map { it.appLabel }.distinct().joinToString(", "),
                        style = MaterialTheme.typography.labelSmall, color = Hodor.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${conns.size}",
                    fontFamily = Oxanium, fontSize = 18.sp,
                    color = if (conns.any { it.threat != null }) Hodor.Danger else Hodor.Accent,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Hodor.Border)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Look up IP locations", style = MaterialTheme.typography.bodyMedium, color = Hodor.Ink)
                Text("Sends public IPs to ip-api.com (plain HTTP)", style = MaterialTheme.typography.bodySmall, color = Hodor.Muted)
            }
            Switch(checked = geoOnline, onCheckedChange = { Settings.setGeoOnline(it) }, colors = hodorSwitchColors())
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
    val labelStyle = TextStyle(fontFamily = Oxanium, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)

    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged { view.size = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    view.zoomBy(zoom, centroid)
                    view.offset = view.clamp(view.offset + pan)
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
                drawPath(path, Hodor.Sea, style = Stroke(width = 0.6.dp.toPx() / view.scale))
            }
        }
        val unit = 1.dp.toPx()
        for (cl in clusters(points, view, w, h, mergeDist).sortedBy { it.flagged }) {
            val c = cl.center
            val r = (9 + sqrt(cl.count.toFloat()) * 1.1f).coerceAtMost(16f) * unit
            val fill = if (cl.flagged) Hodor.Danger else Hodor.Accent
            drawCircle(fill.copy(alpha = 0.18f), radius = r * 2f, center = c)
            if (selected != null && selected in cl.points) drawCircle(Hodor.Ink, radius = r + 3 * unit, center = c)
            drawCircle(fill, radius = r, center = c)
            val label = measurer.measure("${cl.count}", labelStyle.copy(color = if (cl.flagged) Hodor.Ink else Hodor.OnAccent))
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
        }
    }
}
