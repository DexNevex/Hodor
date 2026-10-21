package com.portwatch.vpn

import android.util.Log
import com.portwatch.data.Filter
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Serves the "Hodor held the door" page for filtered plain-HTTP sites. Filtered names resolve
 * to [Engine.BLOCK_IP]; the engine relays port 80 there to this local server. HTTPS can't be
 * answered this way (the browser would show a certificate error), so those get a notification
 * and Hodor's own Blocked screen instead.
 */
object BlockPage {
    @Volatile var port = 0
        private set

    @Synchronized
    fun start() {
        if (port != 0) return
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        port = server.localPort
        thread(name = "hodor-blockpage", isDaemon = true) {
            while (true) {
                val s = try { server.accept() } catch (e: IOException) { break }
                thread(isDaemon = true) { serve(s) }
            }
        }
    }

    private fun serve(s: Socket) {
        try {
            s.soTimeout = 5_000
            val input = s.getInputStream()
            val head = StringBuilder()
            val buf = ByteArray(1024)
            while (head.length < 8192 && !head.contains("\r\n\r\n")) {
                val n = input.read(buf)
                if (n <= 0) break
                head.append(String(buf, 0, n, Charsets.ISO_8859_1))
            }
            val host = Regex("(?im)^host:\\s*([^\\s:]+)").find(head)?.groupValues?.get(1) ?: "this site"
            val reason = Filter.match(host) ?: "Blocked by Hodor"
            val body = page(host, reason).toByteArray()
            val out = s.getOutputStream()
            out.write(
                ("HTTP/1.1 403 Forbidden\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray()
            )
            out.write(body)
            out.flush()
        } catch (e: IOException) {
            Log.d("Hodor", "block page: ${e.message}")
        } finally {
            try { s.close() } catch (_: IOException) {}
        }
    }

    private fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(host: String, reason: String) = """
<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Hodor held the door</title>
<style>
:root{color-scheme:dark}
body{margin:0;min-height:100vh;font-family:system-ui,-apple-system,Roboto,sans-serif;color:#F1F4F3;
background:linear-gradient(180deg,#9DF2D2 0%,#2CC48F 14%,#07090A 42%,#050607 100%);display:flex;justify-content:center}
main{max-width:520px;width:100%;padding:28px 20px 40px;box-sizing:border-box}
.brand{display:flex;align-items:center;gap:10px;color:#04140D;font-weight:600;letter-spacing:.25em;font-size:18px}
.card{margin-top:22px;background:#131716;border:1px solid #262C2A;border-radius:24px;padding:24px;text-align:center}
svg.art{width:100%;height:auto;max-height:300px}
h1{font-size:13px;letter-spacing:.3em;color:#34E3A4;margin:14px 0 6px;font-weight:600}
.host{font-size:24px;font-weight:600;word-break:break-all;margin:6px 0}
.reason{display:inline-block;margin:10px 0 4px;padding:6px 12px;border-radius:999px;background:#3A1719;color:#FF5C61;font-size:13px}
p{color:#86908C;font-size:14px;line-height:1.5}
</style></head><body><main>
<div class="brand">$LOGO_SVG HODOR</div>
<div class="card">
$ART_SVG
<h1>HODOR HELD THE DOOR</h1>
<div class="host">${esc(host)}</div>
<div class="reason">${esc(reason)}</div>
<p>Hodor's site filter blocked this page on this phone.<br>To open it, go to <b>Hodor → Shield → Recently held</b> and tap <b>Allow</b>.</p>
</div></main></body></html>
""".trimIndent()

    private const val LOGO_SVG = """<svg width="34" height="34" viewBox="27 29 54 54" style="background:#07090A;border-radius:50%"><path d="M40 78V47a14 14 0 0 1 28 0v31Z" fill="#34E3A4" fill-opacity=".2"/><path d="M40 78V47a14 14 0 0 1 28 0v31" fill="none" stroke="#34E3A4" stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round"/><path d="M32 61h44" stroke="#F1F4F3" stroke-width="7" stroke-linecap="round"/></svg>"""

    /** Original illustration (not a likeness of any character): a giant bracing a door shut. */
    const val ART_SVG = """<svg class="art" viewBox="0 0 400 320" xmlns="http://www.w3.org/2000/svg">
<ellipse cx="200" cy="292" rx="170" ry="14" fill="#34E3A4" fill-opacity=".15"/>
<circle cx="200" cy="170" r="150" fill="#34E3A4" fill-opacity=".05"/>
<circle cx="200" cy="170" r="110" fill="#34E3A4" fill-opacity=".06"/>
<path d="M130 292V110a70 70 0 0 1 140 0v182Z" fill="#2A211B"/>
<path d="M165 50v242M200 40v252M235 50v242" stroke="#1C1611" stroke-width="3"/>
<path d="M130 160h140M130 245h140" stroke="#151515" stroke-width="7"/>
<path d="M130 292V110a70 70 0 0 1 140 0v182" fill="none" stroke="#34E3A4" stroke-width="5" stroke-linecap="round"/>
<path d="M126 120l-20-8M124 200h-24M126 270l-18 10M274 120l20-8M276 200h24M274 270l18 10M200 34V14M160 46l-10-16M240 46l10-16" stroke="#34E3A4" stroke-width="3" stroke-linecap="round" stroke-opacity=".85"/>
<g fill="#34E3A4" stroke="#34E3A4" stroke-linecap="round" stroke-linejoin="round" fill-opacity=".75" stroke-opacity=".75">
<path d="M150 128c20-10 80-10 100 0l-6 86c-14 12-74 12-88 0Z" stroke-width="6"/>
<path d="M152 134c-16 6-30 16-34 36M248 134c16 6 30 16 34 36M178 214l-18 76M222 214l18 76" fill="none" stroke-width="30"/>
<circle cx="200" cy="96" r="25"/><circle cx="120" cy="174" r="13"/><circle cx="280" cy="174" r="13"/>
</g>
<g fill="#0B0F0E" stroke="#0B0F0E" stroke-linecap="round" stroke-linejoin="round">
<path d="M150 128c20-10 80-10 100 0l-6 86c-14 12-74 12-88 0Z" stroke-width="1"/>
<path d="M152 134c-16 6-30 16-34 36M248 134c16 6 30 16 34 36M178 214l-18 76M222 214l18 76" fill="none" stroke-width="24"/>
<circle cx="200" cy="96" r="22"/><circle cx="120" cy="174" r="10"/><circle cx="280" cy="174" r="10"/>
<ellipse cx="156" cy="292" rx="18" ry="6"/><ellipse cx="244" cy="292" rx="18" ry="6"/>
</g>
</svg>"""
}
