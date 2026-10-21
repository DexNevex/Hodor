package com.portwatch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.portwatch.BlockedActivity
import com.portwatch.data.BlockLog
import com.portwatch.data.Filter

/** Asks once for notification permission (Android 13+), so held sites can be announced. */
@Composable
private fun rememberNotifyAsker(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    return {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun FilterCard() {
    val enabled by Filter.enabled.collectAsState()
    val status by Filter.status.collectAsState()
    val downloading by Filter.downloading.collectAsState()
    val ask = rememberNotifyAsker()
    HCard(Modifier.fillMaxWidth(), color = if (enabled.isNotEmpty()) Color(0xFF241416) else Hodor.Card) {
        Text("Filter sites", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
        Text(
            "Hold the door on whole categories of sites, in every app. A list downloads only when you turn its category on.",
            style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
        )
        Spacer(Modifier.height(6.dp))
        Filter.categories.forEach { c ->
            val on = c.id in enabled
            val st = status[c.id]
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.bodyLarge, color = Hodor.Ink)
                    Text(
                        when {
                            c.id in downloading -> "Downloading the list… (${c.size})"
                            on && st != null && st.entries > 0 -> "%,d sites · ${c.about}".format(st.entries)
                            on && st?.error != null -> "Download failed: ${st.error}"
                            else -> c.about + if (c.size.isNotEmpty()) " · ${c.size}" else ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (on && st?.error != null) Hodor.Danger else Hodor.Muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { v ->
                        if (v) ask()
                        Filter.setCategory(c.id, v)
                    },
                    colors = hodorSwitchColors(danger = true),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesCard() {
    val rules by Filter.rules.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current
    val ask = rememberNotifyAsker()
    fun add(allow: Boolean) {
        if (Filter.addRule(input, allow)) {
            if (!allow) ask()
            input = ""
        } else {
            Toast.makeText(ctx, "Type a site name like example.com", Toast.LENGTH_SHORT).show()
        }
    }
    HCard(Modifier.fillMaxWidth(), color = Hodor.Card) {
        Text("Your rules", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
        Text(
            "Block or always allow a site and all its subdomains. Allow beats every list, including ads and threats.",
            style = MaterialTheme.typography.bodySmall, color = Hodor.Muted,
        )
        Spacer(Modifier.height(10.dp))
        TextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            placeholder = { Text("example.com") },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(allow = false) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Hodor.CardHi, unfocusedContainerColor = Hodor.CardHi,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Hodor.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Block", bg = Hodor.Danger, fg = Hodor.Ink) { add(allow = false) }
            Pill("Always allow", bg = Hodor.Accent, fg = Hodor.OnAccent) { add(allow = true) }
        }
        if (rules.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rules.forEach { r ->
                    Row(
                        Modifier.clip(CircleShape)
                            .background(if (r.allow) Hodor.OkTint else Hodor.DangerTint)
                            .clickable { Filter.removeRule(r.domain) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (r.allow) "✓ " else "⛔ ") + r.domain,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (r.allow) Hodor.Accent else Hodor.Danger,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("✕", style = MaterialTheme.typography.labelMedium, color = Hodor.Muted)
                    }
                }
            }
            Text("Tap a rule to remove it.", style = MaterialTheme.typography.labelSmall, color = Hodor.Muted,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun HeldCard() {
    val entries by BlockLog.entries.collectAsState()
    val ctx = LocalContext.current
    HCard(Modifier.fillMaxWidth(), color = Hodor.Dark, padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recently held", style = MaterialTheme.typography.titleMedium, color = Hodor.OnDark, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) Pill("Clear", bg = Hodor.DarkRow, fg = Hodor.OnDark) { BlockLog.clear() }
        }
        if (entries.isEmpty()) {
            Text("Nothing held yet.", style = MaterialTheme.typography.bodySmall, color = Hodor.OnDarkMuted,
                modifier = Modifier.padding(top = 6.dp))
        }
        entries.take(30).forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .clickable { ctx.startActivity(BlockedActivity.intent(ctx, e.domain, e.reason, e.appLabel)) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(e.packageName, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.domain, style = MaterialTheme.typography.bodyMedium, color = Hodor.OnDark,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${e.appLabel} · ${e.reason.substringBefore(':')} · ${time(e.time).dropLast(3)}" +
                            if (e.count > 1) " · ×${e.count}" else "",
                        style = MaterialTheme.typography.labelSmall, color = Hodor.OnDarkMuted, maxLines = 1,
                    )
                }
                Pill("Allow 15m", bg = Hodor.DarkRow, fg = Hodor.Accent) {
                    Filter.allowFor(e.domain, 15)
                    Toast.makeText(ctx, "${e.domain} allowed for 15 minutes", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
fun BypassCard() {
    val quic by Filter.blockQuic.collectAsState()
    val bypass by Filter.stopBypass.collectAsState()
    val status by Filter.status.collectAsState()
    HCard(Modifier.fillMaxWidth(), color = Hodor.Card) {
        Text("Bypass protection", style = MaterialTheme.typography.titleMedium, color = Hodor.Ink)
        Text("Used only while site filtering is on.", style = MaterialTheme.typography.bodySmall, color = Hodor.Muted)
        Spacer(Modifier.height(6.dp))
        SettingRow(
            "Block QUIC (HTTP/3)",
            "Browsers fall back to normal HTTPS, where Hodor can read the site name.",
            quic,
        ) { Filter.setBlockQuic(it) }
        SettingRow(
            "Stop encrypted-DNS and ECH bypass",
            "Blocks known DoH servers" + (status["doh"]?.entries?.takeIf { it > 0 }?.let { " (%,d)".format(it) } ?: "") +
                " and turns off Encrypted Client Hello, which hides site names.",
            bypass,
        ) { Filter.setStopBypass(it) }
    }
}

@Composable
private fun SettingRow(title: String, about: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Hodor.Ink)
            Text(about, style = MaterialTheme.typography.bodySmall, color = Hodor.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = hodorSwitchColors())
    }
}

@Composable
fun HeldLine(text: String) {
    Text("⛔ $text", style = MaterialTheme.typography.bodySmall, color = Hodor.Danger, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
