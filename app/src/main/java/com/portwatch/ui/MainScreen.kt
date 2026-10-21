package com.portwatch.ui

import android.widget.Toast
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.portwatch.R
import com.portwatch.data.BlockLog
import com.portwatch.data.ConnectionLog
import com.portwatch.data.Settings
import androidx.compose.material3.AlertDialog
import com.portwatch.vpn.PortWatchVpnService

/** Screens. The dock shows the first four; Ports is reached from the ⋮ menu. */
object Screen {
    const val HOME = 0
    const val TRAFFIC = 1
    const val APPS = 2
    const val SHIELD = 3
    const val PORTS = 4
}

private data class DockItem(val screen: Int, @DrawableRes val icon: Int, val label: String)

private val dock = listOf(
    DockItem(Screen.HOME, R.drawable.ic_nav_home, "Home"),
    DockItem(Screen.TRAFFIC, R.drawable.ic_nav_traffic, "Traffic"),
    DockItem(Screen.APPS, R.drawable.ic_nav_apps, "Apps"),
    DockItem(Screen.SHIELD, R.drawable.ic_nav_shield, "Shield"),
)

private val titles = mapOf(
    Screen.HOME to "Hodor", Screen.TRAFFIC to "Traffic", Screen.APPS to "Apps",
    Screen.SHIELD to "Shield", Screen.PORTS to "Ports",
)

@Composable
fun MainScreen(onStart: () -> Unit, onStop: () -> Unit) {
    val running by PortWatchVpnService.running.collectAsState()
    val all by ConnectionLog.connections.collectAsState()
    val held by BlockLog.entries.collectAsState()
    var screen by rememberSaveable { mutableIntStateOf(Screen.HOME) }
    var shieldPage by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var appearanceOpen by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.matches(q) }
    }
    val toggle = if (running) onStop else onStart
    fun go(s: Int, page: String? = null) {
        screen = s
        shieldPage = page
    }

    Box(Modifier.fillMaxSize().background(Hodor.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header: logo, title, bell, menu
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Hodor.Card).border(1.dp, Hodor.Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_logo), contentDescription = "Hodor", modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(titles.getValue(screen), style = MaterialTheme.typography.headlineLarge, color = Hodor.Ink)
                    Text(
                        if (running) "${all.count { it.active }} open · ${all.distinctBy { it.uid }.size} apps" else "Not watching",
                        style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
                    )
                }
                RoundIcon(R.drawable.ic_bell, "Recently held", dot = held.isNotEmpty()) { go(Screen.SHIELD, "held") }
                Spacer(Modifier.width(8.dp))
                Box {
                    RoundIcon(R.drawable.ic_menu_dots, "Menu") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(if (searching) "Hide search" else "Search") {
                            menuOpen = false
                            searching = !searching
                            if (!searching) query = ""
                        }
                        MenuItem("Ports") { menuOpen = false; go(Screen.PORTS) }
                        MenuItem("Threat lists") { menuOpen = false; go(Screen.SHIELD, "threats") }
                        MenuItem("Clear closed connections") { menuOpen = false; ConnectionLog.clearFinished() }
                        MenuItem(if (running) "Stop watching" else "Start watching") { menuOpen = false; toggle() }
                        MenuItem("Appearance") { menuOpen = false; appearanceOpen = true }
                        MenuItem("About") {
                            menuOpen = false
                            val v = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                            Toast.makeText(ctx, "Hodor $v · hold the door", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            if (searching) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("App, site, IP or port") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Hodor.Muted) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = "Close search", tint = Hodor.Muted,
                            modifier = Modifier.clip(CircleShape).clickable { searching = false; query = "" })
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Hodor.Card, unfocusedContainerColor = Hodor.Card,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Hodor.AccentInk,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                when (screen) {
                    Screen.HOME -> HomeTab(shown, running, toggle) { s, page -> go(s, page) }
                    Screen.TRAFFIC -> ConnectionsTab(shown, running)
                    Screen.APPS -> AppsTab(shown, running)
                    Screen.PORTS -> PortsTab(shown, running)
                    else -> ShieldTab(shown, shieldPage) { shieldPage = it }
                }
            }
        }
        // Fade content out under the dock.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Hodor.Bg.copy(alpha = 0.95f), Hodor.Bg)))
        )
        Dock(screen, Modifier.align(Alignment.BottomCenter)) { go(it) }
    }
    if (appearanceOpen) AppearanceDialog { appearanceOpen = false }
}

/** System / Light / Dark, iOS-style list with a checkmark. */
@Composable
private fun AppearanceDialog(close: () -> Unit) {
    val current by Settings.appearance.collectAsState()
    AlertDialog(
        onDismissRequest = close,
        containerColor = Hodor.Card,
        title = { Text("Appearance", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink) },
        text = {
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Hodor.CardHi)) {
                Settings.Appearance.entries.forEachIndexed { i, a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Settings.setAppearance(a) }.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(a.label, style = MaterialTheme.typography.bodyLarge, color = Hodor.Ink, modifier = Modifier.weight(1f))
                        if (a == current) Text("✓", style = MaterialTheme.typography.titleMedium, color = Hodor.AccentInk)
                    }
                    if (i < Settings.Appearance.entries.size - 1) {
                        androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 16.dp), color = Hodor.Border)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = close) { Text("Done", color = Hodor.AccentInk) } },
    )
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text, style = MaterialTheme.typography.bodyLarge) }, onClick = onClick)
}

/** White round button with a line icon, optionally with a red dot. */
@Composable
fun RoundIcon(@DrawableRes icon: Int, label: String, size: Dp = 46.dp, dot: Boolean = false, onClick: () -> Unit) {
    Box {
        Box(
            Modifier.size(size).clip(CircleShape).background(Hodor.Card).border(1.dp, Hodor.Border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(icon), contentDescription = label,
                colorFilter = ColorFilter.tint(Hodor.Ink), modifier = Modifier.size(size * 0.45f),
            )
        }
        if (dot) Box(Modifier.align(Alignment.TopEnd).offset((-6).dp, 6.dp).size(9.dp).clip(CircleShape).background(Hodor.Danger))
    }
}

/** Floating dark capsule with round icon buttons, as in the reference design. */
@Composable
private fun Dock(selected: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    Row(
        modifier.navigationBarsPadding().padding(bottom = 14.dp)
            .clip(CircleShape).background(Hodor.Dark).padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dock.forEach { item ->
            val on = item.screen == selected
            Box {
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(if (on) Hodor.Card else Hodor.DarkRow)
                        .clickable { onSelect(item.screen) },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(item.icon), contentDescription = item.label,
                        colorFilter = ColorFilter.tint(if (on) Hodor.Ink else Hodor.OnDark),
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (on) Box(Modifier.align(Alignment.TopEnd).offset((-2).dp, 2.dp).size(10.dp).clip(CircleShape).background(Hodor.Accent))
            }
        }
    }
}

@Composable
fun EmptyHint(running: Boolean) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(
            if (running) "Waiting for traffic…" else "Tap the arrow on the Home screen to start watching.",
            color = Hodor.OnBgMuted,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
