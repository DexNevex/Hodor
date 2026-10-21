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
import androidx.compose.runtime.LaunchedEffect
import com.portwatch.ui.HodorTheme
import com.portwatch.ui.rememberDarkMode
import com.portwatch.vpn.PortWatchVpnService

class MainActivity : ComponentActivity() {

    private val vpnConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) send(PortWatchVpnService.ACTION_START)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = rememberDarkMode()
            // Status-bar icons light on dark, dark on light.
            LaunchedEffect(dark) { edgeToEdge(dark) }
            HodorTheme {
                MainScreen(onStart = ::start, onStop = { send(PortWatchVpnService.ACTION_STOP) })
            }
        }
    }

    private fun edgeToEdge(dark: Boolean) {
        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
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
