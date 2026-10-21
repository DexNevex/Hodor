package com.portwatch

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.portwatch.ui.MainScreen
import com.portwatch.ui.HodorTheme
import com.portwatch.vpn.PortWatchVpnService

class MainActivity : ComponentActivity() {

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) send(PortWatchVpnService.ACTION_START)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark system-bar icons on the light background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            HodorTheme {
                MainScreen(onStart = ::start, onStop = { send(PortWatchVpnService.ACTION_STOP) })
            }
        }
    }

    private fun start() {
        // The first time, Android asks the user to allow Hodor to act as a VPN.
        val consent = VpnService.prepare(this)
        if (consent != null) vpnConsent.launch(consent) else send(PortWatchVpnService.ACTION_START)
    }

    private fun send(action: String) {
        startService(Intent(this, PortWatchVpnService::class.java).setAction(action))
    }
}
