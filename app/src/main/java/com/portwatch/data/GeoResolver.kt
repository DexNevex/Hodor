package com.portwatch.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

data class Geo(
    val ip: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val lat: Double,
    val lon: Double,
    val org: String,
)

/**
 * Looks up where public IPs are, in batches of up to 100 through ip-api.com
 * (free tier: plain HTTP, 15 batch requests a minute). Results are cached on disk.
 */
object GeoResolver {
    private const val ENDPOINT = "http://ip-api.com/batch?fields=status,country,countryCode,city,lat,lon,org,as,query"
    private const val MAX_CACHE = 5000

    private val cache = ConcurrentHashMap<String, Geo>()
    private val pending = LinkedHashSet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private lateinit var file: File

    private val _results = MutableStateFlow<Map<String, Geo>>(emptyMap())
    val results: StateFlow<Map<String, Geo>> = _results

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "geo.json")
        thread(name = "hodor-geo", isDaemon = true) {
            load()
            while (true) {
                val batch = synchronized(pending) {
                    pending.take(100).also { pending.removeAll(it.toSet()) }
                }
                if (batch.isEmpty() || !Settings.geoOnline.value) {
                    if (batch.isNotEmpty()) synchronized(pending) { pending.addAll(batch) }
                    Thread.sleep(2_000)
                    continue
                }
                val wait = lookup(batch)
                Thread.sleep(wait)
            }
        }
    }

    fun request(ip: InetAddress) {
        if (!isPublic(ip)) return
        val s = ip.hostAddress ?: return
        if (cache.containsKey(s) || s in failed) return
        synchronized(pending) { pending.add(s) }
    }

    /** Returns how long to wait before the next request. */
    private fun lookup(batch: List<String>): Long {
        try {
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(JSONArray(batch).toString().toByteArray()) }
            if (conn.responseCode == 429) {
                synchronized(pending) { pending.addAll(batch) }
                return 60_000
            }
            val body = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(body)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ip = o.optString("query")
                if (o.optString("status") == "success") cache[ip] = fromJson(o) else failed.add(ip)
            }
            publish()
            save()
            // Stay under the limit using the server's own counters.
            val left = conn.getHeaderField("X-Rl")?.toIntOrNull() ?: 1
            val ttl = conn.getHeaderField("X-Ttl")?.toLongOrNull() ?: 60
            return if (left <= 0) (ttl + 1) * 1000 else 4_500
        } catch (e: Exception) {
            Log.w("Hodor", "geo lookup failed", e)
            synchronized(pending) { pending.addAll(batch) }
            return 30_000
        }
    }

    private fun fromJson(o: JSONObject) = Geo(
        ip = o.optString("query"),
        country = o.optString("country"),
        countryCode = o.optString("countryCode"),
        city = o.optString("city"),
        lat = o.optDouble("lat"),
        lon = o.optDouble("lon"),
        org = o.optString("org").ifEmpty { o.optString("as") },
    )

    private fun publish() {
        _results.value = HashMap(cache)
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val g = fromJson(arr.getJSONObject(i))
                cache[g.ip] = g
            }
            publish()
        } catch (e: Exception) {
            Log.w("Hodor", "geo cache unreadable", e)
        }
    }

    private fun save() {
        val arr = JSONArray()
        cache.values.take(MAX_CACHE).forEach { g ->
            arr.put(
                JSONObject().put("query", g.ip).put("country", g.country).put("countryCode", g.countryCode)
                    .put("city", g.city).put("lat", g.lat).put("lon", g.lon).put("org", g.org)
            )
        }
        file.writeText(arr.toString())
    }
}
