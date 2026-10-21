package com.portwatch.data

import com.portwatch.vpn.PROTO_TCP
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress

/** Live state of one connection, updated by the engine thread. */
class ConnectionRecord(
    val protocol: Int,
    val uid: Int,
    val app: AppInfo,
    val localPort: Int,
    val remoteIp: InetAddress,
    val remotePort: Int,
    initialHost: String?,
) {
    var id = 0L
    val started = System.currentTimeMillis()
    /** Which threat feed lists this destination, if any. */
    @Volatile var threat: String? = ThreatIntel.matchIp(remoteIp)
    /** Which ads/trackers list matches this destination, if any. */
    @Volatile var ad: String? = null
    /** Set when site filtering held this connection, e.g. "Gambling: bet.example". */
    @Volatile var filter: String? = null
    /** True once [host] came from the connection's own TLS SNI / HTTP Host. */
    @Volatile var hostFromSniff = false
    @Volatile var host: String? = null
        set(v) {
            field = v
            if (v != null && threat == null) threat = ThreatIntel.matchHost(v)
            if (v != null && ad == null) ad = ThreatIntel.matchAd(v)
        }
    @Volatile var bytesIn = 0L
        set(v) { Traffic.addIn(v - field); field = v }
    @Volatile var bytesOut = 0L
        set(v) { Traffic.addOut(v - field); field = v }
    @Volatile var state = "connecting"
    @Volatile var active = true
    @Volatile var lastSeen = started

    init {
        host = initialHost
    }

    fun finish(reason: String) {
        if (!active) return
        state = reason
        active = false
    }

    fun view() = ConnView(
        id, protocol, uid, app.label, app.packageName, app.key, localPort,
        remoteIp.hostAddress ?: "?", remotePort, host, threat, ad, filter, bytesIn, bytesOut, state, active, started, lastSeen,
    )
}

/** Immutable snapshot the UI renders. */
data class ConnView(
    val id: Long,
    val protocol: Int,
    val uid: Int,
    val appLabel: String,
    val packageName: String?,
    val appKey: String,
    val localPort: Int,
    val remoteIp: String,
    val remotePort: Int,
    val host: String?,
    val threat: String?,
    val ad: String?,
    val filter: String?,
    val bytesIn: Long,
    val bytesOut: Long,
    val state: String,
    val active: Boolean,
    val started: Long,
    val lastSeen: Long,
) {
    val protoName get() = if (protocol == PROTO_TCP) "TCP" else "UDP"
    val service get() = Ports.name(protocol, remotePort)

    val isDns get() = remotePort == 53

    fun matches(q: String) = listOf(appLabel, packageName, host, remoteIp, remotePort.toString(), service, threat, ad, filter)
        .any { it?.contains(q, ignoreCase = true) == true }
}

object ConnectionLog {
    private const val MAX = 3000
    private val lock = Any()
    private val records = ArrayList<ConnectionRecord>()
    private var nextId = 1L
    private val _connections = MutableStateFlow<List<ConnView>>(emptyList())
    val connections: StateFlow<List<ConnView>> = _connections

    fun add(r: ConnectionRecord) = synchronized(lock) {
        r.id = nextId++
        records.add(r)
        if (r.remotePort != 53) GeoResolver.request(r.remoteIp)
        if (records.size > MAX) {
            val i = records.indexOfFirst { !it.active }
            records.removeAt(if (i >= 0) i else 0)
        }
    }

    fun publish() = synchronized(lock) {
        _connections.value = records.asReversed().map { it.view() }
    }

    fun clearFinished() {
        synchronized(lock) { records.removeAll { !it.active } }
        publish()
    }
}
