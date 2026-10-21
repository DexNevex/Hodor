package com.portwatch.vpn

import java.net.Inet6Address
import java.net.InetAddress

const val PROTO_TCP = 6
const val PROTO_UDP = 17

const val FIN = 0x01
const val SYN = 0x02
const val RST = 0x04
const val PSH = 0x08
const val ACK = 0x10

internal fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
internal fun i32(b: ByteArray, o: Int) = (u16(b, o) shl 16) or u16(b, o + 2)
internal fun put16(b: ByteArray, o: Int, v: Int) {
    b[o] = (v ushr 8).toByte()
    b[o + 1] = v.toByte()
}
internal fun put32(b: ByteArray, o: Int, v: Int) {
    put16(b, o, v ushr 16)
    put16(b, o + 2, v)
}

/** One flow as seen from the app: src is the app's socket, dst the remote end. */
data class FlowKey(
    val protocol: Int,
    val srcIp: InetAddress,
    val srcPort: Int,
    val dstIp: InetAddress,
    val dstPort: Int,
)

/** An IPv4 or IPv6 packet read from the tun interface. */
class IpPacket private constructor(
    val data: ByteArray,
    val length: Int,
    val headerLen: Int,
    val protocol: Int,
    val src: InetAddress,
    val dst: InetAddress,
) {
    companion object {
        fun parse(data: ByteArray, length: Int): IpPacket? {
            if (length < 20) return null
            return when ((data[0].toInt() ushr 4) and 0xF) {
                4 -> {
                    val ihl = (data[0].toInt() and 0xF) * 4
                    val total = u16(data, 2)
                    if (ihl < 20 || total > length || total < ihl) return null
                    if ((u16(data, 6) and 0x3FFF) != 0) return null // fragments are rare; drop them
                    IpPacket(
                        data, total, ihl, data[9].toInt() and 0xFF,
                        InetAddress.getByAddress(data.copyOfRange(12, 16)),
                        InetAddress.getByAddress(data.copyOfRange(16, 20)),
                    )
                }
                6 -> {
                    if (length < 40) return null
                    val total = 40 + u16(data, 4)
                    if (total > length) return null
                    // Extension headers are not followed; TCP/UDP almost always come directly.
                    IpPacket(
                        data, total, 40, data[6].toInt() and 0xFF,
                        InetAddress.getByAddress(data.copyOfRange(8, 24)),
                        InetAddress.getByAddress(data.copyOfRange(24, 40)),
                    )
                }
                else -> null
            }
        }
    }
}

class TcpSegment private constructor(val ip: IpPacket) {
    private val o = ip.headerLen
    val srcPort = u16(ip.data, o)
    val dstPort = u16(ip.data, o + 2)
    val seq = i32(ip.data, o + 4)
    val ack = i32(ip.data, o + 8)
    val dataOffset = ((ip.data[o + 12].toInt() and 0xFF) ushr 4) * 4
    val flags = ip.data[o + 13].toInt() and 0xFF
    val window = u16(ip.data, o + 14)
    val payloadOffset = o + dataOffset
    val payloadLength = ip.length - payloadOffset

    fun has(flag: Int) = (flags and flag) != 0

    /** MSS option from a SYN, or 0. */
    val mss: Int
        get() {
            var p = o + 20
            val end = o + dataOffset
            while (p < end) {
                val kind = ip.data[p].toInt() and 0xFF
                if (kind == 0) break
                if (kind == 1) { p++; continue }
                if (p + 1 >= end) break
                val len = ip.data[p + 1].toInt() and 0xFF
                if (len < 2) break
                if (kind == 2 && len == 4 && p + 4 <= end) return u16(ip.data, p + 2)
                p += len
            }
            return 0
        }

    companion object {
        fun parse(ip: IpPacket): TcpSegment? {
            if (ip.length < ip.headerLen + 20) return null
            val s = TcpSegment(ip)
            return if (s.dataOffset >= 20 && s.payloadOffset <= ip.length) s else null
        }
    }
}

/** Builds packets written back into the tun interface, toward the app. */
object PacketBuilder {

    fun tcp(
        src: InetAddress, dst: InetAddress, srcPort: Int, dstPort: Int,
        seq: Int, ack: Int, flags: Int, window: Int,
        payload: ByteArray? = null, off: Int = 0, len: Int = 0, mss: Int = 0,
    ): ByteArray {
        val optLen = if (mss > 0) 4 else 0
        val tcpLen = 20 + optLen + len
        val v6 = src is Inet6Address
        val ipLen = if (v6) 40 else 20
        val buf = ByteArray(ipLen + tcpLen)
        writeIpHeader(buf, v6, src, dst, PROTO_TCP, tcpLen)
        val o = ipLen
        put16(buf, o, srcPort)
        put16(buf, o + 2, dstPort)
        put32(buf, o + 4, seq)
        put32(buf, o + 8, ack)
        buf[o + 12] = (((20 + optLen) / 4) shl 4).toByte()
        buf[o + 13] = flags.toByte()
        put16(buf, o + 14, window)
        if (mss > 0) {
            buf[o + 20] = 2
            buf[o + 21] = 4
            put16(buf, o + 22, mss)
        }
        if (payload != null && len > 0) System.arraycopy(payload, off, buf, o + 20 + optLen, len)
        put16(buf, o + 16, transportChecksum(buf, v6, ipLen, tcpLen, PROTO_TCP))
        return buf
    }

    fun udp(
        src: InetAddress, dst: InetAddress, srcPort: Int, dstPort: Int,
        payload: ByteArray, off: Int, len: Int,
    ): ByteArray {
        val udpLen = 8 + len
        val v6 = src is Inet6Address
        val ipLen = if (v6) 40 else 20
        val buf = ByteArray(ipLen + udpLen)
        writeIpHeader(buf, v6, src, dst, PROTO_UDP, udpLen)
        put16(buf, ipLen, srcPort)
        put16(buf, ipLen + 2, dstPort)
        put16(buf, ipLen + 4, udpLen)
        System.arraycopy(payload, off, buf, ipLen + 8, len)
        val sum = transportChecksum(buf, v6, ipLen, udpLen, PROTO_UDP)
        put16(buf, ipLen + 6, if (sum == 0) 0xFFFF else sum)
        return buf
    }

    private fun writeIpHeader(buf: ByteArray, v6: Boolean, src: InetAddress, dst: InetAddress, proto: Int, payloadLen: Int) {
        if (v6) {
            buf[0] = 0x60
            put16(buf, 4, payloadLen)
            buf[6] = proto.toByte()
            buf[7] = 64
            System.arraycopy(src.address, 0, buf, 8, 16)
            System.arraycopy(dst.address, 0, buf, 24, 16)
        } else {
            buf[0] = 0x45
            put16(buf, 2, 20 + payloadLen)
            put16(buf, 6, 0x4000) // don't fragment
            buf[8] = 64
            buf[9] = proto.toByte()
            System.arraycopy(src.address, 0, buf, 12, 4)
            System.arraycopy(dst.address, 0, buf, 16, 4)
            put16(buf, 10, fold(sum(buf, 0, 20, 0)))
        }
    }

    private fun transportChecksum(buf: ByteArray, v6: Boolean, off: Int, len: Int, proto: Int): Int {
        var s: Long = if (v6) {
            sum(buf, 8, 32, 0) + len + proto
        } else {
            sum(buf, 12, 8, 0) + proto + len
        }
        s = sum(buf, off, len, s)
        return fold(s)
    }

    private fun sum(buf: ByteArray, off: Int, len: Int, init: Long): Long {
        var s = init
        var i = off
        val end = off + len
        while (i + 1 < end) {
            s += (((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)).toLong()
            i += 2
        }
        if (i < end) s += ((buf[i].toInt() and 0xFF) shl 8).toLong()
        return s
    }

    private fun fold(s: Long): Int {
        var x = s
        while ((x ushr 16) != 0L) x = (x and 0xFFFF) + (x ushr 16)
        return (x.inv() and 0xFFFF).toInt()
    }
}
