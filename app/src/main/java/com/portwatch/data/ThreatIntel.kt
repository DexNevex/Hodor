package com.portwatch.data

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Downloads public threat feeds and answers "is this host or IP known bad?", plus
 * an optional ads-and-trackers list (only fetched while ad blocking is on).
 * Lists are kept as files in app storage. An hourly check downloads the ones that
 * are due, asking the server first whether they changed (ETag / Last-Modified).
 */
object ThreatIntel {

    enum class Kind { HOSTS, DOMAINS, IPS }

    data class Feed(val id: String, val name: String, val url: String, val kind: Kind, val about: String)

    data class FeedStatus(val feed: Feed, val entries: Int, val bytes: Long, val updated: Long, val error: String?)

    enum class Frequency(val label: String, val maxAge: Long) {
        DAILY("Daily", 24 * 3_600_000L),
        WEEKLY("Weekly", 7 * 24 * 3_600_000L),
        MANUAL("Manual", Long.MAX_VALUE),
    }

    val feeds = listOf(
        Feed("urlhaus", "URLhaus", "https://urlhaus.abuse.ch/downloads/hostfile/", Kind.HOSTS,
            "Sites spreading malware (abuse.ch)"),
        Feed("phishing_army", "Phishing Army", "https://phishing.army/download/phishing_army_blocklist.txt", Kind.DOMAINS,
            "Phishing domains"),
        Feed("feodo", "Feodo Tracker", "https://feodotracker.abuse.ch/downloads/ipblocklist.txt", Kind.IPS,
            "Botnet command-and-control servers (abuse.ch)"),
        Feed("et_compromised", "ET Compromised", "https://rules.emergingthreats.net/blockrules/compromised-ips.txt", Kind.IPS,
            "Hosts known to be compromised (Proofpoint)"),
        Feed("firehol1", "FireHOL Level 1", "https://raw.githubusercontent.com/firehol/blocklist-ipsets/master/firehol_level1.netset", Kind.IPS,
            "Attack sources and hijacked networks"),
    )

    /** HaGeZi DNS blocklists, wildcard form: each domain also covers its subdomains. */
    enum class AdLevel(val label: String, val url: String) {
        LIGHT("Light", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/light-onlydomains.txt"),
        NORMAL("Normal", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/multi-onlydomains.txt"),
    }

    fun adsFeed() = Settings.adLevel.value.let {
        Feed("ads", "HaGeZi ${it.label}", it.url, Kind.DOMAINS, "Ads, trackers and telemetry")
    }

    private fun activeFeeds() = if (Settings.blockAds.value) feeds + adsFeed() else feeds

    private val worker = Executors.newSingleThreadScheduledExecutor()
    private lateinit var dir: File
    private lateinit var appCtx: Context

    @Volatile private var domains: Map<String, String> = emptyMap()
    @Volatile private var ranges: List<Pair<String, IpRanges>> = emptyList()
    @Volatile private var adDomains: Set<String> = emptySet()

    private val _status = MutableStateFlow<List<FeedStatus>>(emptyList())
    val status: StateFlow<List<FeedStatus>> = _status
    private val _adStatus = MutableStateFlow<FeedStatus?>(null)
    val adStatus: StateFlow<FeedStatus?> = _adStatus
    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating
    private val _frequency = MutableStateFlow(Frequency.DAILY)
    val frequency: StateFlow<Frequency> = _frequency
    private val _wifiOnly = MutableStateFlow(true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        dir = File(ctx.filesDir, "threats").apply { mkdirs() }
        _frequency.value = Frequency.valueOf(prefs().getString("frequency", Frequency.DAILY.name)!!)
        _wifiOnly.value = prefs().getBoolean("wifi_only", true)
        worker.execute { rebuild(errors = emptyMap()) }
        worker.scheduleWithFixedDelay({ updateIfDue() }, 0, 1, TimeUnit.HOURS)
    }

    /** "Update now": every list, on any network. */
    fun refresh() = worker.execute { download(activeFeeds()) }

    /** Ad blocking was switched on or off, or its level changed. */
    fun adsChanged(levelChanged: Boolean) = worker.execute {
        val file = File(dir, "ads.txt")
        if (levelChanged) {
            file.delete()
            prefs().edit().remove("etag_ads").remove("modified_ads").remove("updated_ads").apply()
        }
        if (Settings.blockAds.value && !file.exists()) download(listOf(adsFeed())) else rebuild(emptyMap())
    }

    fun setFrequency(f: Frequency) {
        _frequency.value = f
        prefs().edit().putString("frequency", f.name).apply()
        worker.execute { updateIfDue() }
    }

    fun setWifiOnly(on: Boolean) {
        _wifiOnly.value = on
        prefs().edit().putBoolean("wifi_only", on).apply()
        worker.execute { updateIfDue() }
    }

    private fun updateIfDue() {
        val now = System.currentTimeMillis()
        val due = activeFeeds().filter { now - prefs().getLong("updated_${it.id}", 0) > _frequency.value.maxAge }
        if (due.isEmpty()) return
        val cm = appCtx.getSystemService(ConnectivityManager::class.java)
        if (_wifiOnly.value && cm.isActiveNetworkMetered) return
        download(due)
    }

    /** Feed name and matched domain if [host] or one of its parent domains is listed. */
    fun matchHost(host: String): String? {
        val map = domains
        if (map.isEmpty()) return null
        var h = host.lowercase().trimEnd('.')
        while (true) {
            map[h]?.let { return "$it: $h" }
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return null // stop at the registrable part
            h = h.substring(dot + 1)
        }
    }

    /** List name and matched domain if [host] or a parent domain is an ad/tracker domain. */
    fun matchAd(host: String): String? {
        val set = adDomains
        if (set.isEmpty()) return null
        var h = host.lowercase().trimEnd('.')
        while (true) {
            if (h in set) return "HaGeZi: $h"
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return null
            h = h.substring(dot + 1)
        }
    }

    fun matchIp(ip: InetAddress): String? {
        if (ip !is Inet4Address || !isPublic(ip)) return null
        val v = ipToLong(ip.address)
        for ((name, r) in ranges) if (r.contains(v)) return "$name: ${ip.hostAddress}"
        return null
    }

    private fun prefs() = appCtx.getSharedPreferences("threats", Context.MODE_PRIVATE)

    private fun download(which: List<Feed>) {
        _updating.value = true
        val errors = HashMap<String, String>()
        for (feed in which) {
            try {
                val p = prefs()
                val file = File(dir, "${feed.id}.txt")
                val conn = URL(feed.url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.setRequestProperty("User-Agent", "Hodor (Android)")
                if (file.exists()) {
                    p.getString("etag_${feed.id}", null)?.let { conn.setRequestProperty("If-None-Match", it) }
                    p.getString("modified_${feed.id}", null)?.let { conn.setRequestProperty("If-Modified-Since", it) }
                }
                when (conn.responseCode) {
                    304 -> {} // unchanged since last time; nothing downloaded
                    200 -> {
                        val tmp = File(dir, "${feed.id}.tmp")
                        conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        tmp.renameTo(file)
                        p.edit().putString("etag_${feed.id}", conn.getHeaderField("ETag"))
                            .putString("modified_${feed.id}", conn.getHeaderField("Last-Modified")).apply()
                    }
                    else -> throw Exception("HTTP ${conn.responseCode}")
                }
                p.edit().putLong("updated_${feed.id}", System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                Log.w("Hodor", "feed ${feed.id} failed", e)
                errors[feed.id] = e.message ?: e.javaClass.simpleName
            }
        }
        rebuild(errors)
        _updating.value = false
    }

    private fun rebuild(errors: Map<String, String>) {
        val newDomains = HashMap<String, String>(200_000)
        val newRanges = ArrayList<Pair<String, IpRanges>>()
        val statuses = ArrayList<FeedStatus>()
        for (feed in feeds) {
            val file = File(dir, "${feed.id}.txt")
            var count = 0
            if (file.exists()) {
                try {
                    when (feed.kind) {
                        Kind.HOSTS, Kind.DOMAINS -> file.forEachLine { line ->
                            val d = parseDomain(line, feed.kind)
                            if (d != null) {
                                newDomains.putIfAbsent(d, feed.name)
                                count++
                            }
                        }
                        Kind.IPS -> {
                            val builder = IpRanges.Builder()
                            file.forEachLine { line -> if (builder.add(line)) count++ }
                            newRanges.add(feed.name to builder.build())
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Hodor", "parse ${feed.id} failed", e)
                }
            }
            statuses.add(FeedStatus(feed, count, if (file.exists()) file.length() else 0, prefs().getLong("updated_${feed.id}", 0), errors[feed.id]))
        }
        domains = newDomains
        ranges = newRanges
        _status.value = statuses

        // The ads list is large, so it is only held in memory while ad blocking is on.
        val adsFile = File(dir, "ads.txt")
        if (Settings.blockAds.value && adsFile.exists()) {
            val set = HashSet<String>(200_000)
            try {
                adsFile.forEachLine { line -> parseDomain(line, Kind.DOMAINS)?.let { set.add(it) } }
            } catch (e: Exception) {
                Log.w("Hodor", "parse ads failed", e)
            }
            adDomains = set
            _adStatus.value = FeedStatus(adsFeed(), set.size, adsFile.length(), prefs().getLong("updated_ads", 0), errors["ads"])
        } else {
            adDomains = emptySet()
            _adStatus.value = errors["ads"]?.let { FeedStatus(adsFeed(), 0, 0, 0, it) }
        }
    }

    private fun parseDomain(line: String, kind: Kind): String? {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) return null
        val parts = t.split(Regex("\\s+"))
        val d = if (kind == Kind.HOSTS) parts.getOrNull(1) else parts[0]
        if (d == null || d == "localhost" || !d.contains('.')) return null
        return d.lowercase()
    }

    private fun ipToLong(b: ByteArray): Long =
        ((b[0].toLong() and 0xFF) shl 24) or ((b[1].toLong() and 0xFF) shl 16) or
            ((b[2].toLong() and 0xFF) shl 8) or (b[3].toLong() and 0xFF)

    /** Sorted, merged IPv4 ranges with binary-search lookup. */
    class IpRanges(private val starts: LongArray, private val ends: LongArray) {
        fun contains(v: Long): Boolean {
            var lo = 0
            var hi = starts.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                when {
                    v < starts[mid] -> hi = mid - 1
                    v > ends[mid] -> lo = mid + 1
                    else -> return true
                }
            }
            return false
        }

        class Builder {
            private val items = ArrayList<LongArray>()

            /** Adds "a.b.c.d" or "a.b.c.d/n"; returns false for comments and junk. */
            fun add(line: String): Boolean {
                val t = line.trim().split(Regex("[\\s;#]+"))[0]
                if (t.isEmpty()) return false
                val slash = t.indexOf('/')
                val ipPart = if (slash >= 0) t.substring(0, slash) else t
                val bits = if (slash >= 0) t.substring(slash + 1).toIntOrNull() ?: return false else 32
                val octets = ipPart.split('.')
                if (octets.size != 4 || bits !in 0..32) return false
                var v = 0L
                for (o in octets) {
                    val n = o.toIntOrNull() ?: return false
                    if (n !in 0..255) return false
                    v = (v shl 8) or n.toLong()
                }
                val size = 1L shl (32 - bits)
                val start = v and (size - 1).inv() and 0xFFFFFFFFL
                items.add(longArrayOf(start, start + size - 1))
                return true
            }

            fun build(): IpRanges {
                items.sortBy { it[0] }
                val s = ArrayList<Long>()
                val e = ArrayList<Long>()
                for (r in items) {
                    if (e.isNotEmpty() && r[0] <= e.last() + 1) {
                        if (r[1] > e.last()) e[e.size - 1] = r[1]
                    } else {
                        s.add(r[0])
                        e.add(r[1])
                    }
                }
                return IpRanges(s.toLongArray(), e.toLongArray())
            }
        }
    }
}
