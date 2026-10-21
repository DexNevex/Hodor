package com.portwatch.vpn

import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.portwatch.data.Traffic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * A local-only VPN: every app's traffic enters this service, gets logged, and
 * leaves through the phone's normal network. Nothing is sent to a remote VPN server.
 */
class PortWatchVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.portwatch.START"
        const val ACTION_STOP = "com.portwatch.STOP"
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running
        private val _upstreamDns = MutableStateFlow<String?>(null)
        /** The real resolver our fake DNS address forwards to. */
        val upstreamDns: StateFlow<String?> = _upstreamDns
    }

    private var tun: ParcelFileDescriptor? = null
    private var engine: Engine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (engine != null) return
        val cm = getSystemService(ConnectivityManager::class.java)
        val link = cm.activeNetwork?.let { cm.getLinkProperties(it) }
        val upstream = link?.dnsServers?.firstOrNull { it is Inet4Address }
            ?: link?.dnsServers?.firstOrNull { it is Inet6Address && !it.isLinkLocalAddress }
            ?: InetAddress.getByName("8.8.8.8")
        // Only claim IPv6 if the real network has it; otherwise apps would try IPv6 first and stall.
        val hasIpv6 = link?.linkAddresses?.any {
            val a = it.address
            a is Inet6Address && !a.isLinkLocalAddress && !a.isSiteLocalAddress
        } == true

        val builder = Builder()
            .setSession("Hodor")
            .setMtu(Engine.MTU)
            .setBlocking(true)
            .setMetered(false) // follow the underlying network's metered state
            .addAddress("10.111.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(Engine.FAKE_DNS)
            .addDisallowedApplication(packageName)
        if (hasIpv6) {
            builder.addAddress("fd00:7077::1", 128).addRoute("::", 0)
        }
        val fd = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e("Hodor", "establish failed", e)
            null
        } ?: return

        tun = fd
        Traffic.reset()
        engine = Engine(this, fd, upstream).also { it.start() }
        _upstreamDns.value = upstream.hostAddress
        _running.value = true
    }

    private fun stopVpn() {
        engine?.stop()
        engine = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        _running.value = false
    }

    override fun onRevoke() {
        // Another VPN took over, or the user turned us off in Settings.
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
