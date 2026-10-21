package com.portwatch.data

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

/** Remembers which name each IP address was looked up as, from DNS answers passing through. */
object DnsCache {
    private val names = ConcurrentHashMap<InetAddress, String>()

    fun lookup(ip: InetAddress): String? = names[ip]

    /** The name an outgoing DNS query asks for, or null. */
    fun queryName(b: ByteArray, off: Int, len: Int): String? = try {
        if (len >= 12 && u16(b, off + 4) > 0) readName(b, off, off + 12, off + len).first else null
    } catch (_: Exception) {
        null
    }

    fun onResponse(b: ByteArray, off: Int, len: Int) {
        try {
            parse(b, off, off + len)
        } catch (_: Exception) {
            // malformed or truncated answer
        }
    }

    private fun parse(b: ByteArray, base: Int, end: Int) {
        if (end - base < 12) return
        if ((u16(b, base + 2) and 0x8000) == 0) return // not a response
        val questions = u16(b, base + 4)
        val answers = u16(b, base + 6)
        var p = base + 12
        var qname: String? = null
        repeat(questions) {
            val (name, next) = readName(b, base, p, end)
            if (qname == null) qname = name
            p = next + 4
        }
        val name = qname ?: return
        repeat(answers) {
            p = readName(b, base, p, end).second
            if (p + 10 > end) return
            val type = u16(b, p)
            val rdlen = u16(b, p + 8)
            p += 10
            if (p + rdlen > end) return
            if ((type == 1 && rdlen == 4) || (type == 28 && rdlen == 16)) {
                names[InetAddress.getByAddress(b.copyOfRange(p, p + rdlen))] = name
            }
            p += rdlen
        }
        if (names.size > 20_000) names.clear()
    }

    private fun readName(b: ByteArray, base: Int, start: Int, end: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var p = start
        var resume = -1
        var jumps = 0
        while (true) {
            require(p < end)
            val l = b[p].toInt() and 0xFF
            if (l == 0) {
                p++
                break
            }
            if ((l and 0xC0) == 0xC0) {
                require(p + 1 < end && ++jumps < 20)
                if (resume < 0) resume = p + 2
                p = base + (((l and 0x3F) shl 8) or (b[p + 1].toInt() and 0xFF))
            } else {
                require(p + 1 + l <= end)
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(b, p + 1, l, Charsets.US_ASCII))
                p += 1 + l
            }
        }
        return sb.toString() to (if (resume >= 0) resume else p)
    }
}

/** Finds the hostname in the first bytes of a connection: TLS SNI or HTTP Host. */
object Sniffer {
    const val MAX_BYTES = 8192

    sealed interface Result {
        data class Host(val name: String) : Result
        data object More : Result
        data object None : Result
    }

    private val methods = listOf("GET ", "POST ", "HEAD ", "PUT ", "DELETE ", "OPTIONS ", "PATCH ", "CONNECT ")

    fun sniff(b: ByteArray, len: Int): Result {
        if (len < 5) return Result.More
        return try {
            when {
                b[0] == 0x16.toByte() && b[1] == 3.toByte() -> tls(b, len)
                methods.any { m -> len >= m.length && String(b, 0, m.length, Charsets.US_ASCII) == m } -> http(b, len)
                methods.any { m -> m.startsWith(String(b, 0, minOf(len, m.length), Charsets.US_ASCII)) } -> Result.More
                else -> Result.None
            }
        } catch (_: IndexOutOfBoundsException) {
            Result.None
        }
    }

    private fun tls(b: ByteArray, len: Int): Result {
        val need = 5 + u16(b, 3)
        if (len < need) return if (need <= MAX_BYTES) Result.More else Result.None
        var p = 5
        if (b[p].toInt() != 1) return Result.None // not a ClientHello
        p += 4 + 2 + 32
        p += 1 + (b[p].toInt() and 0xFF)  // session id
        p += 2 + u16(b, p)                // cipher suites
        p += 1 + (b[p].toInt() and 0xFF)  // compression
        if (p + 2 > need) return Result.None
        val extEnd = minOf(p + 2 + u16(b, p), need)
        p += 2
        while (p + 4 <= extEnd) {
            val type = u16(b, p)
            val l = u16(b, p + 2)
            p += 4
            if (type == 0) {
                val nameLen = u16(b, p + 3)
                return if (p + 5 + nameLen <= extEnd) {
                    Result.Host(String(b, p + 5, nameLen, Charsets.US_ASCII))
                } else {
                    Result.None
                }
            }
            p += l
        }
        return Result.None
    }

    private fun http(b: ByteArray, len: Int): Result {
        val text = String(b, 0, len, Charsets.ISO_8859_1)
        val i = text.indexOf("\r\nhost:", ignoreCase = true)
        if (i >= 0) {
            val end = text.indexOf("\r\n", i + 7)
            if (end > 0) return Result.Host(text.substring(i + 7, end).trim().substringBefore(':'))
        }
        return if (text.contains("\r\n\r\n")) Result.None else Result.More
    }
}

object Ports {
    private val tcp = mapOf(
        20 to "FTP data", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP", 53 to "DNS",
        80 to "HTTP", 110 to "POP3", 143 to "IMAP", 443 to "HTTPS", 465 to "SMTPS", 587 to "Mail submission",
        853 to "DNS over TLS", 993 to "IMAPS", 995 to "POP3S", 1883 to "MQTT", 3389 to "RDP",
        5222 to "XMPP", 5223 to "Apple push", 5228 to "Google Play / FCM push", 5229 to "Google push",
        5230 to "Google push", 8080 to "HTTP alt", 8443 to "HTTPS alt", 8883 to "MQTT over TLS",
    )
    private val udp = mapOf(
        53 to "DNS", 67 to "DHCP", 68 to "DHCP", 123 to "NTP (time)", 137 to "NetBIOS", 443 to "QUIC / HTTP/3",
        500 to "IPsec IKE", 853 to "DNS over QUIC", 1900 to "SSDP", 3478 to "STUN / TURN", 4500 to "IPsec NAT-T",
        5353 to "mDNS",
    )

    fun name(protocol: Int, port: Int): String? = when (protocol) {
        com.portwatch.vpn.PROTO_TCP -> tcp[port]
        else -> udp[port] ?: if (port in 19302..19309) "Google STUN (calls)" else null
    }
}
