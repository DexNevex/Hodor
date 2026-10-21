package com.portwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portwatch.data.ConnView
import com.portwatch.data.Geo
import com.portwatch.data.GeoResolver
import com.portwatch.data.Settings
import com.portwatch.data.ThreatIntel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val listPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp)
private val listGap = Arrangement.spacedBy(12.dp)

// ---------- Connections ----------

private sealed interface ListRow {
    val key: String
}

private data class Single(val c: ConnView) : ListRow {
    override val key get() = "c${c.id}"
}

/** All of one app's DNS lookups, placed where its newest lookup would be. */
private data class DnsGroup(val newest: ConnView, val lookups: List<ConnView>) : ListRow {
    override val key get() = "dns${newest.uid}"
}

@Composable
fun ConnectionsTab(list: List<ConnView>, running: Boolean) {
    if (list.isEmpty()) return EmptyHint(running)
    val geo by GeoResolver.results.collectAsState()
    val rows = remember(list) {
        val dnsByApp = list.filter { it.isDns }.groupBy { it.uid }
        val placed = HashSet<Int>()
        buildList {
            for (c in list) {
                if (!c.isDns) add(Single(c))
                else if (placed.add(c.uid)) add(DnsGroup(c, dnsByApp.getValue(c.uid)))
            }
        }
    }
    LazyColumn(contentPadding = listPadding, verticalArrangement = listGap) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is Single -> ConnectionCard(row.c, geo[row.c.remoteIp])
                is DnsGroup -> DnsCard(row)
            }
        }
    }
}

@Composable
fun ConnectionCard(c: ConnView, g: Geo?) {
    HCard(Modifier.fillMaxWidth(), color = if (c.threat != null) Hodor.CardHi else Hodor.Card) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(c.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.appLabel, style = MaterialTheme.typography.titleMedium, color = Hodor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.host ?: c.remoteIp, style = MaterialTheme.typography.bodySmall, color = Hodor.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (c.threat != null && c.state != "blocked") StatusChip("Flagged", Tone.Danger)
            else StatusChip(c.state, stateTone(c.state, c.active))
        }
        Spacer(Modifier.height(14.dp))
        Row {
            Field("Port", "${c.remotePort}" + (c.service?.let { " · $it" } ?: ""), Modifier.weight(1.3f))
            Field("Location", place(g), Modifier.weight(1f))
            Field("Data", "↑${bytes(c.bytesOut)} ↓${bytes(c.bytesIn)}", Modifier.weight(1.2f))
        }
        c.threat?.let {
            Spacer(Modifier.height(8.dp))
            ThreatLine(it)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Text("Started ${time(c.started)}", style = MaterialTheme.typography.labelSmall, color = Hodor.Muted, modifier = Modifier.weight(1f))
            Text(
                "${c.protoName} · ${c.remoteIp} · :${c.localPort}",
                style = MaterialTheme.typography.labelSmall, color = Hodor.Muted, fontFamily = FontFamily.Monospace,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsCard(g: DnsGroup) {
    var expanded by rememberSaveable(g.newest.uid) { mutableStateOf(false) }
    val names = remember(g.lookups) {
        g.lookups.mapNotNull { it.host?.takeUnless { h -> h.startsWith("DNS via") } }.distinct()
    }
    val bad = g.lookups.mapNotNull { it.threat }.distinct()
    HCard(Modifier.fillMaxWidth(), color = Hodor.Card, onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(g.newest.packageName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.newest.appLabel, style = MaterialTheme.typography.titleMedium, color = Hodor.Ink, maxLines = 1)
                Text("${g.lookups.size} DNS lookups · ${names.size} names", style = MaterialTheme.typography.bodySmall, color = Hodor.Muted)
            }
            StatusChip("DNS", if (bad.isNotEmpty()) Tone.Danger else Tone.Neutral)
        }
        Spacer(Modifier.height(12.dp))
        val shown = if (expanded) names.take(100) else names.take(4)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            shown.forEach { n ->
                val flagged = g.lookups.any { it.host == n && it.threat != null }
                StatusChip(n, if (flagged) Tone.Danger else Tone.Neutral)
            }
            if (names.size > shown.size) StatusChip("+${names.size - shown.size} more", Tone.Warn)
        }
        if (bad.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ThreatLine(if (bad.size == 1) bad[0] else "${bad.size} known-bad names")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Last ${time(g.newest.started)} · ${if (expanded) "tap to collapse" else "tap to show all"}",
            style = MaterialTheme.typography.labelSmall, color = Hodor.Muted,
        )
    }
}

// ---------- Apps ----------

private data class AppSummary(
    val first: ConnView,
    val count: Int,
    val open: Int,
    val flagged: Int,
    val bytes: Long,
    val ports: List<Pair<String, Int>>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsTab(list: List<ConnView>, running: Boolean) {
    if (list.isEmpty()) return EmptyHint(running)
    val blocked by Settings.blocked.collectAsState()
    val apps = remember(list) {
        list.groupBy { it.uid }.map { (_, cs) ->
            AppSummary(
                first = cs.first(),
                count = cs.size,
                open = cs.count { it.active },
                flagged = cs.count { it.threat != null },
                bytes = cs.sumOf { it.bytesIn + it.bytesOut },
                ports = cs.groupingBy { c -> "${c.protoName} ${c.remotePort}" + (c.service?.let { " · $it" } ?: "") }
                    .eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value },
            )
        }.sortedByDescending { it.bytes }
    }
    LazyColumn(contentPadding = listPadding, verticalArrangement = listGap) {
        items(apps, key = { it.first.uid }) { a ->
            val isBlocked = a.first.appKey in blocked
            HCard(Modifier.fillMaxWidth(), color = if (isBlocked) Hodor.CardHi else Hodor.Card) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.first.packageName, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.first.appLabel, style = MaterialTheme.typography.titleMedium, color = Hodor.Ink, maxLines = 1)
                        Text(
                            a.first.packageName ?: "uid ${a.first.uid}",
                            style = MaterialTheme.typography.bodySmall, color = Hodor.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(
                            checked = isBlocked,
                            onCheckedChange = { Settings.setBlocked(a.first.appKey, it) },
                            colors = hodorSwitchColors(danger = true),
                        )
                        Text(
                            if (isBlocked) "Blocked" else "Block",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBlocked) Hodor.Danger else Hodor.Muted,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Field("Connections", "${a.count}", Modifier.weight(1f))
                    Field("Open", "${a.open}", Modifier.weight(1f))
                    Field("Data", bytes(a.bytes), Modifier.weight(1f))
                }
                if (a.flagged > 0) {
                    Spacer(Modifier.height(8.dp))
                    ThreatLine("${a.flagged} connections to known-bad destinations")
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    a.ports.forEach { (port, n) -> StatusChip("$port ×$n", Tone.Neutral) }
                }
            }
        }
    }
}

// ---------- Ports ----------

@Composable
fun PortsTab(list: List<ConnView>, running: Boolean) {
    if (list.isEmpty()) return EmptyHint(running)
    val ports = remember(list) {
        list.groupBy { it.protoName to it.remotePort }.entries.sortedByDescending { it.value.size }
    }
    LazyColumn(contentPadding = listPadding, verticalArrangement = listGap) {
        items(ports, key = { "${it.key.first}/${it.key.second}" }) { (port, cs) ->
            HCard(Modifier.fillMaxWidth(), color = Hodor.Card) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${port.second}", fontSize = 34.sp, fontFamily = Outfit, fontWeight = FontWeight.Light, color = Hodor.Ink)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cs.first().service ?: "Unregistered port", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
                        Text(port.first, style = MaterialTheme.typography.labelMedium, color = Hodor.Muted)
                    }
                    StatusChip("${cs.size}×", if (cs.any { it.threat != null }) Tone.Danger else Tone.Warn)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    cs.map { it.appLabel }.distinct().joinToString(", "),
                    style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
                )
            }
        }
    }
}

// ---------- Threats ----------

private val dateFormat = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())

@Composable
fun ThreatsTab(list: List<ConnView>) {
    val feeds by ThreatIntel.status.collectAsState()
    val updating by ThreatIntel.updating.collectAsState()
    val frequency by ThreatIntel.frequency.collectAsState()
    val wifiOnly by ThreatIntel.wifiOnly.collectAsState()
    val blockThreats by Settings.blockThreats.collectAsState()
    val geo by GeoResolver.results.collectAsState()
    val flagged = list.filter { it.threat != null }

    LazyColumn(contentPadding = listPadding, verticalArrangement = listGap) {
        item {
            HCard(Modifier.fillMaxWidth(), color = if (blockThreats) Hodor.AccentDim else Hodor.Card) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Block flagged destinations", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
                        Text(
                            "Refuse connections and DNS lookups to anything on the lists.",
                            style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
                        )
                    }
                    Switch(checked = blockThreats, onCheckedChange = { Settings.setBlockThreats(it) }, colors = hodorSwitchColors())
                }
            }
        }
        item {
            HCard(Modifier.fillMaxWidth(), color = Hodor.Card) {
                Text("Update schedule", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThreatIntel.Frequency.entries.forEach { f ->
                        Pill(
                            f.label,
                            bg = if (f == frequency) Hodor.Accent else Hodor.Pill,
                            fg = if (f == frequency) Hodor.OnAccent else Hodor.Ink,
                            onClick = { ThreatIntel.setFrequency(f) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Only on Wi-Fi", style = MaterialTheme.typography.bodyMedium, color = Hodor.Ink, modifier = Modifier.weight(1f))
                    Switch(checked = wifiOnly, onCheckedChange = { ThreatIntel.setWifiOnly(it) }, colors = hodorSwitchColors())
                }
                Text(
                    "Checked every hour; a list is downloaded only when due and changed on the server. " +
                        "A full refresh is about ${bytes(feeds.sumOf { it.bytes })}.",
                    style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
                )
            }
        }
        item {
            HCard(Modifier.fillMaxWidth(), color = Hodor.Dark, padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Threat lists", style = MaterialTheme.typography.titleMedium, color = Hodor.OnDark, modifier = Modifier.weight(1f))
                    Pill(
                        if (updating) "Updating…" else "Update now",
                        bg = Hodor.Accent,
                        fg = Hodor.OnAccent,
                        onClick = if (updating) null else ({ ThreatIntel.refresh() }),
                    )
                }
                Spacer(Modifier.height(6.dp))
                if (feeds.isEmpty()) Text("Loading…", style = MaterialTheme.typography.bodySmall, color = Hodor.OnDarkMuted)
                feeds.forEach { s ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row {
                            Text(s.feed.name, style = MaterialTheme.typography.bodyLarge, color = Hodor.OnDark, modifier = Modifier.weight(1f))
                            Text("%,d".format(s.entries), style = MaterialTheme.typography.titleSmall, color = Hodor.Accent)
                        }
                        Text(
                            s.feed.about + " · " + bytes(s.bytes) + " · " +
                                if (s.updated == 0L) "not downloaded" else dateFormat.format(Date(s.updated)),
                            style = MaterialTheme.typography.bodySmall, color = Hodor.OnDarkMuted,
                        )
                        s.error?.let { ThreatLine("Last download failed: $it") }
                    }
                }
            }
        }
        item {
            Text(
                if (flagged.isEmpty()) "No traffic to listed destinations so far." else "${flagged.size} flagged connections",
                style = MaterialTheme.typography.titleMedium, color = Hodor.OnBg,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
        items(flagged, key = { it.id }) { c -> ConnectionCard(c, geo[c.remoteIp]) }
    }
}
