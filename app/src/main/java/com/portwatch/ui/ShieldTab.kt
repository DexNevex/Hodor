package com.portwatch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.portwatch.R
import com.portwatch.data.BlockLog
import com.portwatch.data.Counters
import com.portwatch.data.ConnView
import com.portwatch.data.Filter
import com.portwatch.data.Settings
import com.portwatch.data.ThreatIntel

private data class ShieldEntry(val id: String, val title: String, val icon: Int, val color: androidx.compose.ui.graphics.Color)

/**
 * Shield: a short menu; each row opens its own page instead of one long scroll of cards.
 * [page] is the open page id (null = the menu).
 */
@Composable
fun ShieldTab(list: List<ConnView>, page: String?, open: (String?) -> Unit) {
    if (page != null) {
        BackHandler { open(null) }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
                    .clip(CircleShape).clickable { open(null) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("‹  Shield", style = MaterialTheme.typography.titleMedium, color = Hodor.AccentInk)
                Text("  /  ${pages.firstOrNull { it.id == page }?.title ?: ""}", style = MaterialTheme.typography.titleMedium, color = Hodor.Muted)
            }
            Box(Modifier.weight(1f)) {
                when (page) {
                    "threats" -> ThreatsPage(list)
                    else -> LazyColumn(contentPadding = listPadding, verticalArrangement = listGap) {
                        when (page) {
                            "filter" -> item { FilterCard() }
                            "rules" -> item { RulesCard() }
                            "held" -> item { HeldCard() }
                            "ads" -> item { AdsCard(list) }
                            "bypass" -> item { BypassCard() }
                            "location" -> item { LocationCard() }
                        }
                    }
                }
            }
        }
        return
    }

    val enabled by Filter.enabled.collectAsState()
    val rules by Filter.rules.collectAsState()
    val held by BlockLog.entries.collectAsState()
    val counts by Counters.state.collectAsState()
    val blockAds by Settings.blockAds.collectAsState()
    val blockThreats by Settings.blockThreats.collectAsState()
    val feeds by ThreatIntel.status.collectAsState()
    val quic by Filter.blockQuic.collectAsState()
    val geoOnline by Settings.geoOnline.collectAsState()
    val flagged = list.count { it.threat != null }

    fun summary(id: String): Pair<String, Boolean> = when (id) {
        "filter" -> (if (enabled.isEmpty()) "Off" else Filter.categories.filter { it.id in enabled }.joinToString(", ") { it.name }) to enabled.isNotEmpty()
        "rules" -> (if (rules.isEmpty()) "No rules" else "${rules.count { !it.allow }} blocked · ${rules.count { it.allow }} allowed") to rules.isNotEmpty()
        "held" -> ("%,d today · %,d all time".format(counts.held.today, counts.held.allTime) +
            (held.firstOrNull()?.let { " · last ${it.domain}" } ?: "")) to held.isNotEmpty()
        "ads" -> (if (blockAds) "On · %,d blocked today".format(counts.ads.today) else "Off") to blockAds
        "threats" -> ("%,d entries · ".format(feeds.sumOf { it.entries }) + if (blockThreats) "blocking" else "flagging only" +
            if (flagged > 0) " · $flagged flagged" else "") to blockThreats
        "bypass" -> (if (quic) "QUIC blocked while filtering" else "QUIC allowed") to quic
        "location" -> (if (geoOnline) "On · via ip-api.com" else "Off") to geoOnline
        else -> "" to false
    }

    LazyColumn(contentPadding = listPadding, verticalArrangement = listGap) {
        groups.forEach { (heading, ids) ->
            item {
                Text(heading, style = MaterialTheme.typography.labelMedium, color = Hodor.Muted,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            }
            item {
                Column(Modifier.fillMaxWidth().clip(CardShape).background(Hodor.Card).border(1.dp, Hodor.Border, CardShape)) {
                    ids.forEachIndexed { i, id ->
                        val e = pages.first { it.id == id }
                        val (sub, on) = summary(id)
                        MenuRow(e, sub, on) { open(id) }
                        if (i < ids.size - 1) HorizontalDivider(Modifier.padding(start = 64.dp), color = Hodor.Border)
                    }
                }
            }
        }
    }
}

private val pages = listOf(
    ShieldEntry("filter", "Site filter", R.drawable.ic_nav_shield, IosColor.Red),
    ShieldEntry("rules", "Your rules", R.drawable.ic_nav_apps, IosColor.Blue),
    ShieldEntry("held", "Recently held", R.drawable.ic_bell, IosColor.Orange),
    ShieldEntry("ads", "Ads & trackers", R.drawable.ic_nav_traffic, IosColor.Purple),
    ShieldEntry("threats", "Threat lists", R.drawable.ic_nav_shield, IosColor.Pink),
    ShieldEntry("bypass", "Bypass protection", R.drawable.ic_nav_apps, IosColor.Gray),
    ShieldEntry("location", "Location lookups", R.drawable.ic_nav_home, IosColor.Teal),
)

private val groups = listOf(
    "FILTERING" to listOf("filter", "rules", "held"),
    "BLOCKING" to listOf("ads", "threats"),
    "SETTINGS" to listOf("bypass", "location"),
)

@Composable
private fun MenuRow(e: ShieldEntry, subtitle: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(e.icon, e.color)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Hodor.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(IosColor.Green))
        Spacer(Modifier.width(8.dp))
        Text("›", style = MaterialTheme.typography.headlineSmall, color = Hodor.Muted)
    }
}

@Composable
private fun LocationCard() {
    val geoOnline by Settings.geoOnline.collectAsState()
    HCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Look up IP locations", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
                Text(
                    "Sends public IP addresses to ip-api.com over plain HTTP, for the map and country list. Results are cached on the phone.",
                    style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
                )
            }
            IosSwitch(checked = geoOnline, onCheckedChange = { Settings.setGeoOnline(it) }, colors = hodorSwitchColors())
        }
    }
}
