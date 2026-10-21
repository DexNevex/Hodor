package com.portwatch

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portwatch.data.Filter
import com.portwatch.ui.HCard
import com.portwatch.ui.Hodor
import com.portwatch.ui.HodorTheme
import com.portwatch.ui.Oxanium
import com.portwatch.ui.Pill
import com.portwatch.ui.StatusChip
import com.portwatch.ui.Tone

/** "Hodor held the door": shown when you tap a held-site notification or a Recently held entry. */
class BlockedActivity : ComponentActivity() {

    companion object {
        fun intent(ctx: Context, domain: String, reason: String, app: String): Intent =
            Intent(ctx, BlockedActivity::class.java)
                .putExtra("domain", domain).putExtra("reason", reason).putExtra("app", app)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        val domain = intent.getStringExtra("domain") ?: "this site"
        val reason = intent.getStringExtra("reason") ?: "Blocked by Hodor"
        val app = intent.getStringExtra("app") ?: "an app"
        setContent {
            HodorTheme {
                HeldScreen(
                    domain, reason, app,
                    allow15 = {
                        Filter.allowFor(domain, 15)
                        Toast.makeText(this, "${Filter.site(domain)} allowed for 15 minutes. Reload the page.", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    allowAlways = {
                        Filter.allowAlways(domain)
                        Toast.makeText(this, "${Filter.site(domain)} is always allowed now.", Toast.LENGTH_LONG).show()
                        finish()
                    },
                    close = { finish() },
                )
            }
        }
    }
}

@Composable
private fun HeldScreen(
    domain: String, reason: String, app: String,
    allow15: () -> Unit, allowAlways: () -> Unit, close: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Hodor.Glow, 0.16f to Color(0xFF2CC48F), 0.42f to Hodor.Bg, 1f to Hodor.BgDeep)
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("HODOR", fontFamily = Oxanium, fontSize = 22.sp, color = Hodor.OnAccent, letterSpacing = 3.sp,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            HCard(Modifier.fillMaxWidth(), color = Hodor.Card, padding = 22.dp) {
                Image(
                    painterResource(R.drawable.hold_the_door), contentDescription = "A giant holding a door shut",
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "HODOR HELD THE DOOR", fontFamily = Oxanium, fontSize = 15.sp, letterSpacing = 3.sp,
                    color = Hodor.AccentInk, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    domain, fontFamily = Oxanium, fontSize = 26.sp, color = Hodor.Ink,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    StatusChip(reason, Tone.Danger)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "$app tried to open this site. Hodor's site filter stopped it.",
                    style = MaterialTheme.typography.bodyMedium, color = Hodor.Muted,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Allow for 15 minutes", Modifier.fillMaxWidth(), bg = Hodor.Accent, fg = Hodor.OnAccent, onClick = allow15)
                Pill("Always allow ${Filter.site(domain)}", Modifier.fillMaxWidth(), bg = Hodor.Pill, onClick = allowAlways)
                Pill("Close", Modifier.fillMaxWidth(), bg = Hodor.Card, fg = Hodor.Muted, onClick = close)
            }
        }
    }
}
