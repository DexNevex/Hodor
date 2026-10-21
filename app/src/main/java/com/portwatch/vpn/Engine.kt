package com.portwatch.vpn

import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.portwatch.data.AppInfo
import com.portwatch.data.AppResolver
import com.portwatch.data.Filter
import com.portwatch.data.Notifier
import com.portwatch.data.ConnectionLog
import com.portwatch.data.ConnectionRecord
import com.portwatch.data.DnsCache
import com.portwatch.data.Settings
import com.portwatch.data.ThreatIntel
import com.portwatch.data.Traffic
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

interface SelectHandler {
    fun onReady(key: SelectionKey)
    fun onError(e: Exception)
}

/**
 * Reads packets from the tun interface, records who sent them, and relays them
 * through ordinary (protected) sockets. All flow state lives on the engine thread;
 * the reader thread only hands packets over.
 */
class Engine(
    val vpn: VpnService,
    private val tun: ParcelFileDescriptor,
    private val upstreamDns: InetAddress,
) {
    companion object {
        private const val TAG = "Hodor"
        const val MTU = 1500
        val FAKE_DNS: InetAddress = InetAddress.getByName("10.111.0.53")
        /** Filtered names resolve here; port 80 is answered with the block page. */
        val BLOCK_IP: InetAddress = InetAddress.getByName("10.111.0.80")
        val LOOPBACK: InetAddress = InetAddress.getByName("127.0.0.1")
    }

    enum class DnsVerdict { ALLOW, NXDOMAIN, NODATA, BLOCK_PAGE }

    val selector: Selector = Selector.open()
    val readBuffer: ByteBuffer = ByteBuffer.allocate(65535)
    private val inbound = ConcurrentLinkedQueue<ByteArray>()
    private val tunOut = FileOutputStream(tun.fileDescriptor)
    private val tcpFlows = HashMap<FlowKey, TcpFlow>()
    private val udpFlows = HashMap<FlowKey, UdpFlow>()
    private val blockedUdp = HashMap<FlowKey, Long>() // recently refused UDP sockets, dropped quietly
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private val onRulesChanged: () -> Unit = {
        tasks.add(Runnable { applyRules() })
        selector.wakeup()
    }
    private val cm = vpn.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var running = true

    fun start() {
        thread(name = "pw-tun-reader") {
            val input = FileInputStream(tun.fileDescriptor)
            val buf = ByteArray(32767)
            try {
                while (running) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) {
                        inbound.add(buf.copyOf(n))
                        selector.wakeup()
                    }
                }
            } catch (_: IOException) {
                // tun closed
            }
        }
        Settings.ruleListeners.add(onRulesChanged)
        try { BlockPage.start() } catch (e: Exception) { Log.w(TAG, "block page server failed", e) }
        thread(name = "pw-engine") { loop() }
    }

    fun stop() {
        running = false
        selector.wakeup()
    }

    private fun loop() {
        var lastSweep = 0L
        var lastPublish = 0L
        try {
            while (running) {
                selector.select(500)
                while (true) {
                    val pkt = inbound.poll() ?: break
                    try {
                        handleTunPacket(pkt)
                    } catch (e: Exception) {
                        Log.w(TAG, "packet error", e)
                    }
                }
                while (true) {
                    val task = tasks.poll() ?: break
                    task.run()
                }
                val it = selector.selectedKeys().iterator()
                while (it.hasNext()) {
                    val key = it.next()
                    it.remove()
                    val h = key.attachment() as SelectHandler
                    if (!key.isValid) continue
                    try {
                        h.onReady(key)
                    } catch (e: Exception) {
                        h.onError(e)
                    }
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastSweep > 5_000) {
                    tcpFlows.values.toList().forEach { it.sweep(now) }
                    udpFlows.values.toList().forEach { it.sweep(now) }
                    blockedUdp.values.removeAll { now - it > 60_000 }
                    lastSweep = now
                }
                if (now - lastPublish > 1_000) {
                    ConnectionLog.publish()
                    Traffic.tick()
                    lastPublish = now
                }
            }
        } finally {
            Settings.ruleListeners.remove(onRulesChanged)
            tcpFlows.values.toList().forEach { it.close("stopped") }
            udpFlows.values.toList().forEach { it.close("stopped") }
            try { selector.close() } catch (_: IOException) {}
            ConnectionLog.publish()
        }
    }

    fun writeTun(packet: ByteArray) {
        try {
            tunOut.write(packet)
        } catch (_: IOException) {
            // tun closed while stopping
        }
    }

    fun shouldBlock(r: ConnectionRecord): Boolean {
        if (Settings.isBlocked(r.app)) return true
        val host = r.host
        if (host != null && Filter.isAllowed(host)) return false // your allow rules win
        return (Settings.blockThreats.value && r.threat != null) ||
            filterReason(r) != null ||
            (r.ad != null && Settings.adsBlockedFor(r.app))
    }

    /** Why site filtering stops this connection, or null. */
    private fun filterReason(r: ConnectionRecord): String? {
        val host = r.host ?: return null
        if (r.remoteIp == BLOCK_IP || r.remoteIp == FAKE_DNS && host.startsWith("DNS via")) return null
        return Filter.match(r.app, host)
    }

    /**
     * Decides where a connection to the block address goes, now that its site name is known.
     * An app may still hold the block address from an earlier lookup after you tapped Allow,
     * so an allowed site is looked up again and connected for real.
     */
    fun routeDeferred(flow: TcpFlow, host: String?, port: Int) {
        val r = flow.record
        val reason = host?.let { Filter.match(r.app, it) }
        when {
            host != null && reason == null -> thread(name = "hodor-resolve", isDaemon = true) {
                val addr = try {
                    InetAddress.getAllByName(host).let { all -> all.firstOrNull { it is Inet4Address } ?: all.first() }
                } catch (e: Exception) {
                    null
                }
                tasks.add(Runnable {
                    if (addr == null) flow.block() else {
                        r.state = "open"
                        flow.connectTo(InetSocketAddress(addr, port))
                    }
                })
                selector.wakeup()
            }
            port == 80 && BlockPage.port != 0 -> {
                r.filter = reason
                if (host != null && reason != null) Notifier.held(r.app, host, reason)
                flow.connectTo(InetSocketAddress(LOOPBACK, BlockPage.port))
            }
            else -> {
                if (host != null && reason != null) {
                    r.filter = reason
                    Notifier.held(r.app, host, reason)
                }
                flow.block()
            }
        }
    }

    /** Records and notifies a connection held by site filtering. */
    fun noteHeld(r: ConnectionRecord) {
        val reason = filterReason(r) ?: return
        r.filter = reason
        Notifier.held(r.app, r.host ?: return, reason)
    }

    /**
     * What to answer a DNS query with. Your allow rules win; then threat lists (NXDOMAIN),
     * site filtering (the block page address), ads (NXDOMAIN). While filtering, "HTTPS"
     * records get an empty answer, which turns off Encrypted Client Hello so hostnames stay
     * visible to the TLS check.
     */
    fun dnsVerdict(app: AppInfo, name: String, qtype: Int): Pair<DnsVerdict, String?> {
        if (Filter.isAllowed(name)) return DnsVerdict.ALLOW to null
        if (Settings.blockThreats.value && ThreatIntel.matchHost(name) != null) return DnsVerdict.NXDOMAIN to null
        Filter.match(app, name)?.let { return DnsVerdict.BLOCK_PAGE to it }
        if (Settings.adsBlockedFor(app) && ThreatIntel.matchAd(name) != null) return DnsVerdict.NXDOMAIN to null
        if (qtype == 65 && Filter.active && Filter.stopBypass.value && !Filter.isExempt(app)) return DnsVerdict.NODATA to null
        return DnsVerdict.ALLOW to null
    }

    /** Cuts open connections that a newly blocked app or threat rule now forbids. */
    private fun applyRules() {
        tcpFlows.values.toList().forEach { if (shouldBlock(it.record)) it.block() }
        udpFlows.values.toList().forEach { if (shouldBlock(it.record)) it.close("blocked") }
    }

    /** Answers a DNS query with NXDOMAIN so the app gives up at once instead of retrying. */
    fun replyNxDomain(key: FlowKey, data: ByteArray, off: Int, len: Int) {
        if (len < 12) return
        val b = data.copyOfRange(off, off + len)
        b[2] = ((b[2].toInt() or 0x80) and 0xF9.toInt()).toByte() // QR=1, clear AA/TC, keep RD
        b[3] = 0x83.toByte()                                       // RA=1, RCODE=3 (NXDOMAIN)
        writeTun(PacketBuilder.udp(key.dstIp, key.srcIp, key.dstPort, key.srcPort, b, 0, len))
    }

    /** "This name exists but has no records of that type." */
    fun replyNoData(key: FlowKey, data: ByteArray, off: Int, len: Int) {
        if (len < 12) return
        val b = data.copyOfRange(off, off + len)
        b[2] = ((b[2].toInt() or 0x80) and 0xF9.toInt()).toByte()
        b[3] = 0x80.toByte() // RA=1, RCODE=0
        writeTun(PacketBuilder.udp(key.dstIp, key.srcIp, key.dstPort, key.srcPort, b, 0, len))
    }

    /** Answers an A query with [BLOCK_IP] (other types get NODATA). */
    fun replyBlockPage(key: FlowKey, data: ByteArray, off: Int, len: Int, qtype: Int) {
        val qEnd = DnsCache.questionEnd(data, off, len)
        if (qtype != 1 || qEnd == null) return replyNoData(key, data, off, len)
        val q = data.copyOfRange(off, qEnd)
        q[2] = ((q[2].toInt() or 0x80) and 0xF9.toInt()).toByte()
        q[3] = 0x80.toByte()
        put16(q, 6, 1); put16(q, 8, 0); put16(q, 10, 0) // 1 answer, no authority/additional
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C, 0, 1, 0, 1, // name -> question, type A, class IN
            0, 0, 0, 10, 0, 4,               // TTL 10 s (short, so an Allow takes effect fast), 4 bytes
        ) + BLOCK_IP.address
        val b = q + answer
        writeTun(PacketBuilder.udp(key.dstIp, key.srcIp, key.dstPort, key.srcPort, b, 0, b.size))
    }

    fun removeTcp(key: FlowKey) = tcpFlows.remove(key)
    fun removeUdp(key: FlowKey) = udpFlows.remove(key)

    private fun handleTunPacket(pkt: ByteArray) {
        val ip = IpPacket.parse(pkt, pkt.size) ?: return
        when (ip.protocol) {
            PROTO_TCP -> handleTcp(ip)
            PROTO_UDP -> handleUdp(ip)
        }
    }

    /** Where to really connect. DNS sent to our fake resolver goes to the network's resolver. */
    private fun target(dst: InetAddress, port: Int): InetSocketAddress? = when (dst) {
        FAKE_DNS -> if (port == 53) InetSocketAddress(upstreamDns, 53) else null
        // The page server listens on IPv4 loopback (getLoopbackAddress() would be ::1 on Android).
        else -> InetSocketAddress(dst, port)
    }

    private fun handleTcp(ip: IpPacket) {
        val seg = TcpSegment.parse(ip) ?: return
        val key = FlowKey(PROTO_TCP, ip.src, seg.srcPort, ip.dst, seg.dstPort)
        val flow = tcpFlows[key]
        if (flow != null) {
            flow.onClientSegment(seg)
            return
        }
        if (seg.has(SYN) && !seg.has(ACK) && ip.dst == BLOCK_IP) {
            if (seg.dstPort != 80 && seg.dstPort != 443) return rejectTcp(key, seg)
            val record = newRecord(key)
            try {
                tcpFlows[key] = TcpFlow(this, key, null, record, seg)
            } catch (e: Exception) {
                record.finish("failed")
                rejectTcp(key, seg)
            }
            return
        }
        if (seg.has(SYN) && !seg.has(ACK)) {
            val target = target(ip.dst, seg.dstPort)
            if (target == null) {
                rejectTcp(key, seg)
                return
            }
            val record = newRecord(key)
            if (shouldBlock(record)) {
                noteHeld(record)
                record.finish("blocked")
                rejectTcp(key, seg)
                return
            }
            try {
                tcpFlows[key] = TcpFlow(this, key, target, record, seg)
            } catch (e: Exception) {
                record.finish("failed")
                rejectTcp(key, seg)
            }
        } else if (!seg.has(RST)) {
            rejectTcp(key, seg)
        }
    }

    private fun rejectTcp(key: FlowKey, seg: TcpSegment) {
        val pkt = if (seg.has(ACK)) {
            PacketBuilder.tcp(key.dstIp, key.srcIp, key.dstPort, key.srcPort, seg.ack, 0, RST, 0)
        } else {
            var ack = seg.seq + seg.payloadLength
            if (seg.has(SYN)) ack++
            if (seg.has(FIN)) ack++
            PacketBuilder.tcp(key.dstIp, key.srcIp, key.dstPort, key.srcPort, 0, ack, RST or ACK, 0)
        }
        writeTun(pkt)
    }

    private fun handleUdp(ip: IpPacket) {
        val o = ip.headerLen
        if (ip.length < o + 8) return
        val srcPort = u16(ip.data, o)
        val dstPort = u16(ip.data, o + 2)
        val len = minOf(u16(ip.data, o + 4), ip.length - o) - 8
        if (len < 0) return
        val key = FlowKey(PROTO_UDP, ip.src, srcPort, ip.dst, dstPort)
        var flow = udpFlows[key]
        if (flow == null) {
            if (key in blockedUdp) {
                blockedUdp[key] = SystemClock.elapsedRealtime()
                if (dstPort == 53) replyNxDomain(key, ip.data, o + 8, len)
                return
            }
            val target = target(ip.dst, dstPort) ?: return
            if (dstPort == 443 && dropQuic(key)) {
                blockedUdp[key] = SystemClock.elapsedRealtime()
                return
            }
            val query = if (dstPort == 53) DnsCache.queryName(ip.data, o + 8, len) else null
            val record = newRecord(key, query)
            // DNS queries are judged one by one in UdpFlow; here only a blocked app stops them.
            if (if (dstPort == 53) Settings.isBlocked(record.app) else shouldBlock(record)) {
                record.finish("blocked")
                blockedUdp[key] = SystemClock.elapsedRealtime()
                if (dstPort == 53) replyNxDomain(key, ip.data, o + 8, len)
                return
            }
            flow = try {
                UdpFlow(this, key, target, record)
            } catch (e: Exception) {
                record.finish("failed")
                return
            }
            udpFlows[key] = flow
        }
        flow.sendToServer(ip.data, o + 8, len)
    }

    /**
     * While filtering, QUIC (HTTP/3 over UDP 443) is dropped so apps fall back to TCP, where
     * the hostname is visible. Dropped silently: apps retry over TCP straight away.
     */
    private fun dropQuic(key: FlowKey): Boolean {
        if (!Filter.active) return false
        val app = AppResolver.resolve(vpn, ownerUid(key))
        if (Filter.isExempt(app)) return false
        if (Filter.blockQuic.value) return true
        val name = DnsCache.lookup(key.dstIp) ?: return false
        return Filter.match(app, name) != null
    }

    private fun ownerUid(key: FlowKey): Int = try {
        cm.getConnectionOwnerUid(
            key.protocol,
            InetSocketAddress(key.srcIp, key.srcPort),
            InetSocketAddress(key.dstIp, key.dstPort),
        )
    } catch (e: Exception) {
        -1
    }

    private fun newRecord(key: FlowKey, queryName: String? = null): ConnectionRecord {
        val uid = try {
            cm.getConnectionOwnerUid(
                key.protocol,
                InetSocketAddress(key.srcIp, key.srcPort),
                InetSocketAddress(key.dstIp, key.dstPort),
            )
        } catch (e: Exception) {
            -1
        }
        val host = queryName ?: when (key.dstIp) {
            FAKE_DNS -> "DNS via ${upstreamDns.hostAddress}"
            BLOCK_IP -> "Hodor block page"
            else -> DnsCache.lookup(key.dstIp)
        }
        val record = ConnectionRecord(
            protocol = key.protocol,
            uid = uid,
            app = AppResolver.resolve(vpn, uid),
            localPort = key.srcPort,
            remoteIp = key.dstIp,
            remotePort = key.dstPort,
            initialHost = host,
        )
        ConnectionLog.add(record)
        return record
    }
}
