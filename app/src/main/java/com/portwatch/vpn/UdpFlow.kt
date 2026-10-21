package com.portwatch.vpn

import android.os.SystemClock
import com.portwatch.data.ConnectionRecord
import com.portwatch.data.DnsCache
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey

/** Relays one app UDP socket's datagrams to one remote address. */
class UdpFlow(
    private val engine: Engine,
    private val key: FlowKey,
    target: InetSocketAddress,
    val record: ConnectionRecord,
) : SelectHandler {

    private val channel: DatagramChannel = DatagramChannel.open()
    private val selKey: SelectionKey
    private val isDns = key.dstPort == 53
    private var lastActivity = SystemClock.elapsedRealtime()

    init {
        try {
            channel.configureBlocking(false)
            engine.vpn.protect(channel.socket())
            channel.connect(target)
            selKey = channel.register(engine.selector, SelectionKey.OP_READ, this)
        } catch (e: Exception) {
            try { channel.close() } catch (_: IOException) {}
            throw e
        }
        record.state = "open"
    }

    fun sendToServer(data: ByteArray, off: Int, len: Int) {
        lastActivity = SystemClock.elapsedRealtime()
        record.lastSeen = System.currentTimeMillis()
        record.bytesOut += len
        if (isDns) {
            val name = DnsCache.queryName(data, off, len)
            if (name != null) {
                record.host = name
                if (engine.blocksName(record.app, name)) {
                    engine.replyNxDomain(key, data, off, len)
                    record.state = "blocked"
                    return
                }
            }
        }
        try {
            channel.write(ByteBuffer.wrap(data, off, len))
        } catch (e: IOException) {
            onError(e)
        }
    }

    override fun onReady(key: SelectionKey) {
        val buf = engine.readBuffer
        while (true) {
            buf.clear()
            val n = channel.read(buf)
            if (n <= 0) break
            lastActivity = SystemClock.elapsedRealtime()
            record.lastSeen = System.currentTimeMillis()
            record.bytesIn += n
            val arr = buf.array()
            if (isDns) DnsCache.onResponse(arr, 0, n)
            engine.writeTun(
                PacketBuilder.udp(this.key.dstIp, this.key.srcIp, this.key.dstPort, this.key.srcPort, arr, 0, n)
            )
        }
    }

    override fun onError(e: Exception) = close("failed")

    fun sweep(now: Long) {
        val limit = if (isDns) 15_000L else 120_000L
        if (now - lastActivity > limit) close("closed")
    }

    fun close(reason: String) {
        selKey.cancel()
        try { channel.close() } catch (_: IOException) {}
        engine.removeUdp(key)
        record.finish(reason)
    }
}
