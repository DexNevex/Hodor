package com.portwatch.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portwatch.R
import com.portwatch.data.ConnectionLog
import com.portwatch.vpn.PortWatchVpnService

private val sections = listOf("MAP", "TRAFFIC", "APPS", "PORTS", "THREATS")
private val titles = listOf("", "Traffic", "Apps", "Ports", "Threats")

@Composable
fun MainScreen(onStart: () -> Unit, onStop: () -> Unit) {
    val running by PortWatchVpnService.running.collectAsState()
    val all by ConnectionLog.connections.collectAsState()
    var section by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.matches(q) }
    }
    val toggle = if (running) onStop else onStart

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Hodor.Glow,
                0.16f to Color(0xFF2CC48F),
                0.38f to Hodor.Bg,
                1f to Hodor.BgDeep,
            )
        )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Hodor.Bg), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = "Hodor", modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("HODOR", fontFamily = Oxanium, fontSize = 22.sp, color = Hodor.OnAccent, letterSpacing = 3.sp)
                Spacer(Modifier.width(10.dp))
                LiveChip(running, toggle)
                Spacer(Modifier.weight(1f))
                CircleButton({ ConnectionLog.clearFinished() }, bg = Hodor.Bg.copy(alpha = 0.85f), size = 42.dp) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear closed", tint = Hodor.Ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                CircleButton({
                    searching = !searching
                    if (!searching) query = ""
                }, bg = Hodor.Bg.copy(alpha = 0.85f), size = 42.dp) {
                    Icon(
                        if (searching) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "Search", tint = Hodor.Ink, modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (titles[section].isNotEmpty()) {
                Text(
                    titles[section],
                    fontFamily = Oxanium, fontSize = 34.sp, color = Hodor.OnAccent,
                    modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp),
                )
            }
            if (searching) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("App, host, IP or port") },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Hodor.Card,
                        unfocusedContainerColor = Hodor.Card,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Hodor.Accent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp),
                )
            }
            Box(Modifier.weight(1f).padding(top = 4.dp)) {
                when (section) {
                    0 -> HomeTab(shown, running, toggle) { section = it }
                    1 -> ConnectionsTab(shown, running)
                    2 -> AppsTab(shown, running)
                    3 -> PortsTab(shown, running)
                    else -> ThreatsTab(shown)
                }
            }
        }
        // Fade the content out behind the dock.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Hodor.BgDeep.copy(alpha = 0.95f), Hodor.BgDeep)))
        )
        Dock(section, Modifier.align(Alignment.BottomCenter)) { section = it }
    }
}

/** "● LIVE" / "○ OFF"; tapping starts or stops watching. */
@Composable
private fun LiveChip(running: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Hodor.Bg).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (running) Hodor.Accent else Hodor.Muted))
        Spacer(Modifier.width(6.dp))
        Text(
            if (running) "LIVE" else "OFF",
            style = MaterialTheme.typography.labelSmall,
            color = if (running) Hodor.Accent else Hodor.Muted,
        )
    }
}

@Composable
private fun Dock(selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()
            .clip(RoundedCornerShape(26.dp)).background(Hodor.Dark.copy(alpha = 0.96f))
            .border(1.dp, Hodor.Border, RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        sections.forEachIndexed { i, label ->
            val on = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(if (on) Hodor.CardHi else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) Hodor.Accent else Color.Transparent))
                Spacer(Modifier.height(5.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (on) Hodor.Accent else Hodor.Muted,
                )
            }
        }
    }
}

@Composable
fun EmptyHint(running: Boolean) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            if (running) "Waiting for traffic…" else "Tap LIVE or the power button on the map screen to start watching.",
            color = Hodor.OnBgMuted,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
