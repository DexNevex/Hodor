package com.portwatch.vpn

import android.os.SystemClock
import com.portwatch.data.ConnectionRecord
import com.portwatch.data.Settings
import com.portwatch.data.Sniffer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import kotlin.random.Random

/**
 * Terminates the app's TCP connection inside the VPN and relays its bytes over a
 * real socket. Packets toward the app go over the local tun, which does not lose
 * them, so there is no retransmission timer; window scaling and SACK are not offered.
 */
class TcpFlow(
    private val engine: Engine,
    private val key: FlowKey,
    target: InetSocketAddress,
    val record: ConnectionRecord,
    syn: TcpSegment,
) : SelectHandler {

    private enum class State { CONNECTING, SYN_RECEIVED, ESTABLISHED }

    private val channel: SocketChannel = SocketChannel.open()
    private val selKey: SelectionKey
    private var state = State.CONNECTING

    private var clientNext = syn.seq + 1     // next byte we expect from the app
    private var ourSeq = Random.nextInt()    // next sequence number we send
    private var clientAcked = ourSeq         // highest ack the app has sent us
    private var clientWindow = syn.window
    private val mss = minOf(if (syn.mss > 0) syn.mss else 536, Engine.MTU - 60)

    private val toServer = ArrayDeque<ByteBuffer>()
    private var pendingBytes = 0
    private var lastAdvertised = MAX_WINDOW

    private var clientFin = false
    private var serverFin = false
    private var finAcked = false
    private var outputShut = false
    private var lastActivity = SystemClock.elapsedRealtime()

    private var sniffBuf: ByteArray? = ByteArray(Sniffer.MAX_BYTES)
    private var sniffLen = 0

    init {
        try {
            channel.configureBlocking(false)
            engine.vpn.protect(channel.socket())
            val connected = channel.connect(target)
            selKey = channel.register(engine.selector, SelectionKey.OP_CONNECT, this)
            if (connected) onConnected()
        } catch (e: Exception) {
            try { channel.close() } catch (_: IOException) {}
            throw e
        }
    }

    override fun onReady(key: SelectionKey) {
        if (key.isConnectable && channel.finishConnect()) onConnected()
        if (key.isValid && key.isReadable) readFromServer()
        if (key.isValid && key.isWritable) flushToServer()
        updateInterest()
    }

    override fun onError(e: Exception) {
        sendToApp(RST or ACK)
        close(if (state == State.CONNECTING) "failed" else "reset")
    }

    private fun onConnected() {
        sendToApp(SYN or ACK, mss = Engine.MTU - 40)
        ourSeq += 1
        state = State.SYN_RECEIVED
        record.state = "open"
        updateInterest()
    }

    fun onClientSegment(seg: TcpSegment) {
        lastActivity = SystemClock.elapsedRealtime()
        record.lastSeen = System.currentTimeMillis()
        if (seg.has(RST)) {
            close("reset")
            return
        }
        if (seg.has(SYN)) {
            // The app retransmitted its SYN; repeat our SYN-ACK if we already sent it.
            if (state == State.SYN_RECEIVED) sendToApp(SYN or ACK, seq = ourSeq - 1, mss = Engine.MTU - 40)
            return
        }
        if (state == State.CONNECTING) return

        if (seg.has(ACK)) {
            val a = seg.ack
            if (a - clientAcked > 0 && a - ourSeq <= 0) clientAcked = a
            clientWindow = seg.window
            if (state == State.SYN_RECEIVED && clientAcked == ourSeq) state = State.ESTABLISHED
            if (serverFin && clientAcked == ourSeq) finAcked = true
        }
        if (state != State.ESTABLISHED) return

        var needAck = false
        val len = seg.payloadLength
        if (len > 0) {
            if (seg.seq == clientNext && !clientFin) {
                toServer.addLast(ByteBuffer.wrap(seg.ip.data.copyOfRange(seg.payloadOffset, seg.payloadOffset + len)))
                pendingBytes += len
                clientNext += len
                record.bytesOut += len
                sniff(seg.ip.data, seg.payloadOffset, len)
                // The TLS/HTTP hostname may reveal a listed site only now.
                if (record.threat != null && Settings.blockThreats.value) {
                    block()
                    return
                }
            }
            needAck = true // also re-ack duplicates and out-of-order data
        }
        if (seg.has(FIN)) {
            if (!clientFin && seg.seq + len == clientNext) {
                clientFin = true
                clientNext += 1
            }
            needAck = true
        }
        if (needAck) sendToApp(ACK)
        if (pendingBytes > 0) {
            try {
                flushToServer()
            } catch (e: IOException) {
                onError(e)
                return
            }
        }
        maybeShutdownOutput()
        updateInterest()
        if (clientFin && finAcked) close("closed")
    }

    private fun flushToServer() {
        while (toServer.isNotEmpty()) {
            val b = toServer.first()
            pendingBytes -= channel.write(b)
            if (b.hasRemaining()) break
            toServer.removeFirst()
        }
        maybeShutdownOutput()
        // Tell the app the window opened up again after it had filled.
        if (lastAdvertised < MAX_WINDOW / 4 && window() >= MAX_WINDOW / 2) sendToApp(ACK)
    }

    private fun maybeShutdownOutput() {
        if (clientFin && pendingBytes == 0 && !outputShut) {
            outputShut = true
            try { channel.shutdownOutput() } catch (_: IOException) {}
        }
    }

    private fun readFromServer() {
        val room = clientWindow - (ourSeq - clientAcked)
        if (room <= 0) return
        val buf = engine.readBuffer
        buf.clear()
        buf.limit(minOf(room, buf.capacity()))
        val n = channel.read(buf)
        if (n < 0) {
            serverFin = true
            sendToApp(FIN or ACK)
            ourSeq += 1
            return
        }
        if (n == 0) return
        lastActivity = SystemClock.elapsedRealtime()
        record.lastSeen = System.currentTimeMillis()
        record.bytesIn += n
        val arr = buf.array()
        var off = 0
        while (off < n) {
            val len = minOf(mss, n - off)
            sendToApp(ACK or PSH, payload = arr, off = off, len = len)
            ourSeq += len
            off += len
        }
    }

    private fun updateInterest() {
        if (!selKey.isValid) return
        var ops = 0
        if (state == State.CONNECTING) {
            ops = SelectionKey.OP_CONNECT
        } else {
            if (state == State.ESTABLISHED && !serverFin && clientWindow - (ourSeq - clientAcked) > 0) {
                ops = ops or SelectionKey.OP_READ
            }
            if (pendingBytes > 0) ops = ops or SelectionKey.OP_WRITE
        }
        selKey.interestOps(ops)
    }

    private fun window() = maxOf(0, MAX_WINDOW - pendingBytes)

    private fun sendToApp(
        flags: Int, seq: Int = ourSeq, mss: Int = 0,
        payload: ByteArray? = null, off: Int = 0, len: Int = 0,
    ) {
        val w = window()
        lastAdvertised = w
        engine.writeTun(
            PacketBuilder.tcp(
                key.dstIp, key.srcIp, key.dstPort, key.srcPort,
                seq, clientNext, flags, w, payload, off, len, mss,
            )
        )
    }

    /** Pulls the TLS server name or HTTP Host out of the first bytes the app sends. */
    private fun sniff(data: ByteArray, off: Int, len: Int) {
        val buf = sniffBuf ?: return
        val n = minOf(len, buf.size - sniffLen)
        System.arraycopy(data, off, buf, sniffLen, n)
        sniffLen += n
        when (val r = Sniffer.sniff(buf, sniffLen)) {
            is Sniffer.Result.Host -> {
                record.host = r.name
                sniffBuf = null
            }
            Sniffer.Result.None -> sniffBuf = null
            Sniffer.Result.More -> if (sniffLen == buf.size) sniffBuf = null
        }
    }

    fun sweep(now: Long) {
        val idle = now - lastActivity
        val limit = when {
            state == State.CONNECTING -> 30_000L
            clientFin || serverFin -> 60_000L
            else -> 30 * 60_000L
        }
        if (idle > limit) {
            sendToApp(RST or ACK)
            close(if (state == State.CONNECTING) "timeout" else "idle")
        }
    }

    fun block() {
        sendToApp(RST or ACK)
        close("blocked")
    }

    fun close(reason: String) {
        selKey.cancel()
        try { channel.close() } catch (_: IOException) {}
        engine.removeTcp(key)
        record.finish(reason)
    }

    private companion object {
        const val MAX_WINDOW = 65535
    }
}
