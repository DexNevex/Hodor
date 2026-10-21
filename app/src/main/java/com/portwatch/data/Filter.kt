package com.portwatch.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Site filtering by domain: category lists, your own block/allow rules, temporary allows
 * and per-app exemptions. A domain rule also covers its subdomains, and allow beats block.
 *
 * Only hostnames can be filtered: with HTTPS the rest of the URL is encrypted.
 */
object Filter {

    data class Category(
        val id: String,
        val name: String,
        val about: String,
        val url: String? = null,
        val builtin: List<String> = emptyList(),
        val size: String = "",
    )

    data class Rule(val domain: String, val allow: Boolean)

    data class ListStatus(val entries: Int, val bytes: Long, val updated: Long, val error: String?)

    private const val HAGEZI = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/"

    val categories = listOf(
        Category("adult", "Adult", "Pornography and adult sites · HaGeZi NSFW", HAGEZI + "nsfw-onlydomains.txt", size = "1.4 MB"),
        Category("gambling", "Gambling", "Betting and casino sites · HaGeZi Gambling (mini)", HAGEZI + "gambling.mini-onlydomains.txt", size = "2.3 MB"),
        Category("social", "Social media", "Facebook, Instagram, TikTok, X, Snapchat… · HaGeZi Social", HAGEZI + "social-onlydomains.txt", size = "0.1 MB"),
        Category("dating", "Dating", "Tinder, Bumble, Hinge, Grindr… · built in", builtin = DATING),
        Category("piracy", "Piracy", "Illegal streaming and torrent sites · HaGeZi Anti-Piracy", HAGEZI + "anti.piracy-onlydomains.txt", size = "1.1 MB"),
        Category("scam", "Scams", "Known scam sites · Block List Project", "https://blocklistproject.github.io/Lists/alt-version/scam-nl.txt", size = "0.2 MB"),
    )

    /** Public encrypted-DNS (DoH) servers apps could use to look names up behind Hodor's back. */
    private val bypassList = Category("doh", "Encrypted DNS bypass", "DoH servers · HaGeZi DoH", HAGEZI + "doh-onlydomains.txt")

    private lateinit var prefs: SharedPreferences
    private lateinit var dir: File

    private val _enabled = MutableStateFlow<Set<String>>(emptySet())
    val enabled: StateFlow<Set<String>> = _enabled
    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules
    private val _exempt = MutableStateFlow<Set<String>>(emptySet())
    val exempt: StateFlow<Set<String>> = _exempt
    private val _blockQuic = MutableStateFlow(true)
    val blockQuic: StateFlow<Boolean> = _blockQuic
    private val _stopBypass = MutableStateFlow(true)
    val stopBypass: StateFlow<Boolean> = _stopBypass
    private val _status = MutableStateFlow<Map<String, ListStatus>>(emptyMap())
    val status: StateFlow<Map<String, ListStatus>> = _status
    private val _downloading = MutableStateFlow<Set<String>>(emptySet())
    val downloading: StateFlow<Set<String>> = _downloading

    @Volatile private var sets: List<Pair<String, Set<String>>> = emptyList() // category name -> domains
    @Volatile private var bypass: Set<String> = emptySet()
    @Volatile private var blockRules: Set<String> = emptySet()
    @Volatile private var allowRules: Set<String> = emptySet()
    private val tempAllowed = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** True when anything would be filtered. */
    val active: Boolean get() = _enabled.value.isNotEmpty() || blockRules.isNotEmpty()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("filter", Context.MODE_PRIVATE)
        dir = File(ctx.filesDir, "filter").apply { mkdirs() }
        _enabled.value = prefs.getStringSet("enabled", emptySet())!!.toSet()
        _exempt.value = prefs.getStringSet("exempt", emptySet())!!.toSet()
        _blockQuic.value = prefs.getBoolean("block_quic", true)
        _stopBypass.value = prefs.getBoolean("stop_bypass", true)
        _rules.value = prefs.getStringSet("rules", emptySet())!!.mapNotNull { r ->
            val (kind, domain) = r.split(":", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            Rule(domain, kind == "allow")
        }.sortedBy { it.domain }
        applyRules()
        ThreatIntel.onWorker { reload() }
    }

    // ---------- changes from the UI ----------

    fun setCategory(id: String, on: Boolean) {
        val s = if (on) _enabled.value + id else _enabled.value - id
        _enabled.value = s
        prefs.edit().putStringSet("enabled", s).apply()
        ThreatIntel.onWorker {
            val cat = categories.first { it.id == id }
            if (on && cat.url != null && !file(cat).exists()) download(listOf(cat))
            reload()
            notifyRules()
        }
    }

    /** Adds a rule from free text: "https://www.Example.com/page" -> "www.example.com". */
    fun addRule(input: String, allow: Boolean): Boolean {
        val d = normalize(input) ?: return false
        val list = _rules.value.filter { it.domain != d } + Rule(d, allow)
        saveRules(list)
        return true
    }

    fun removeRule(domain: String) = saveRules(_rules.value.filter { it.domain != domain })

    fun setExempt(appKey: String, on: Boolean) {
        val s = if (on) _exempt.value + appKey else _exempt.value - appKey
        _exempt.value = s
        prefs.edit().putStringSet("exempt", s).apply()
        notifyRules()
    }

    fun setBlockQuic(on: Boolean) {
        _blockQuic.value = on
        prefs.edit().putBoolean("block_quic", on).apply()
        notifyRules()
    }

    fun setStopBypass(on: Boolean) {
        _stopBypass.value = on
        prefs.edit().putBoolean("stop_bypass", on).apply()
        ThreatIntel.onWorker {
            if (on && !file(bypassList).exists()) download(listOf(bypassList))
            reload()
            notifyRules()
        }
    }

    /** Lets [domain] (and its subdomains) through for [minutes]. */
    fun allowFor(domain: String, minutes: Int) {
        tempAllowed[domain.lowercase()] = System.currentTimeMillis() + minutes * 60_000L
        notifyRules()
    }

    fun allowAlways(domain: String) = addRule(domain, allow = true)

    // ---------- matching ----------

    fun isExempt(app: AppInfo) = app.key in _exempt.value

    /** True if one of your allow rules (or a temporary allow) covers [host]. */
    fun isAllowed(host: String): Boolean {
        val now = System.currentTimeMillis()
        for (d in chain(host)) {
            if (d in allowRules) return true
            val until = tempAllowed[d]
            if (until != null) {
                if (until > now) return true
                tempAllowed.remove(d)
            }
        }
        return false
    }

    /** Why [host] is filtered (e.g. "Gambling: bet.example"), or null. Ignores app exemptions. */
    fun match(host: String): String? {
        if (host.isEmpty() || isAllowed(host)) return null
        val cats = sets
        val stop = _stopBypass.value
        for (d in chain(host)) {
            if (d in blockRules) return "Your rule: $d"
            for ((name, set) in cats) if (d in set) return "$name: $d"
            if (stop && d in bypass) return "Encrypted DNS bypass: $d"
        }
        return null
    }

    fun match(app: AppInfo, host: String): String? = if (isExempt(app)) null else match(host)

    /** [host], then each parent domain down to two labels. */
    private fun chain(host: String): Sequence<String> = sequence {
        var h = host.lowercase().trimEnd('.')
        while (true) {
            yield(h)
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) break
            h = h.substring(dot + 1)
        }
    }

    private fun normalize(input: String): String? {
        var t = input.trim().lowercase()
        t = t.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        t = t.substringAfterLast('@').substringBefore(':').removePrefix("*.").trimEnd('.')
        return t.takeIf { it.contains('.') && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
    }

    // ---------- lists ----------

    /** Called from ThreatIntel's hourly check (on its worker) when list updates are allowed. */
    fun updateIfDue(maxAge: Long) {
        val now = System.currentTimeMillis()
        val due = remoteLists().filter { now - ThreatIntel.lastUpdated(listId(it)) > maxAge }
        if (due.isNotEmpty()) download(due)
        reload()
    }

    /** "Update now" (on ThreatIntel's worker). */
    fun refreshAll() {
        val lists = remoteLists()
        if (lists.isNotEmpty()) download(lists)
        reload()
    }

    private fun remoteLists(): List<Category> =
        categories.filter { it.id in _enabled.value && it.url != null } + if (_stopBypass.value) listOf(bypassList) else emptyList()

    private fun listId(c: Category) = "filter_${c.id}"
    private fun file(c: Category) = File(dir, "${c.id}.txt")

    private fun download(lists: List<Category>) {
        for (c in lists) {
            _downloading.value = _downloading.value + c.id
            val err = ThreatIntel.fetch(listId(c), c.url!!, file(c))
            _downloading.value = _downloading.value - c.id
            if (err != null) errors[c.id] = err else errors.remove(c.id)
        }
    }

    private val errors = HashMap<String, String>()

    private fun reload() {
        val loaded = ArrayList<Pair<String, Set<String>>>()
        val statuses = HashMap<String, ListStatus>()
        for (c in categories) {
            if (c.id !in _enabled.value) continue
            val set = if (c.url == null) c.builtin.toHashSet() else readList(file(c))
            loaded.add(c.name to set)
            statuses[c.id] = ListStatus(set.size, if (c.url == null) 0 else file(c).length(),
                if (c.url == null) 0 else ThreatIntel.lastUpdated(listId(c)), errors[c.id])
        }
        bypass = if (_stopBypass.value) readList(file(bypassList)) else emptySet()
        statuses[bypassList.id] = ListStatus(bypass.size, file(bypassList).length(), ThreatIntel.lastUpdated(listId(bypassList)), errors[bypassList.id])
        sets = loaded
        _status.value = statuses
    }

    private fun readList(f: File): Set<String> {
        if (!f.exists()) return emptySet()
        val set = HashSet<String>()
        try {
            f.forEachLine { line ->
                val t = line.trim()
                if (t.isNotEmpty() && !t.startsWith("#")) {
                    val d = t.split(' ', '\t').last().lowercase()
                    if (d.contains('.')) set.add(d)
                }
            }
        } catch (e: Exception) {
            Log.w("Hodor", "read ${f.name} failed", e)
        }
        return set
    }

    private fun saveRules(list: List<Rule>) {
        val sorted = list.sortedBy { it.domain }
        _rules.value = sorted
        prefs.edit().putStringSet("rules", sorted.map { (if (it.allow) "allow:" else "block:") + it.domain }.toSet()).apply()
        applyRules()
        notifyRules()
    }

    private fun applyRules() {
        blockRules = _rules.value.filter { !it.allow }.map { it.domain }.toSet()
        allowRules = _rules.value.filter { it.allow }.map { it.domain }.toSet()
    }

    private fun notifyRules() = Settings.ruleListeners.forEach { it() }
}

/** Recent sites Hodor held, newest first, merged per app and domain. */
object BlockLog {
    data class Entry(
        val time: Long,
        val appLabel: String,
        val packageName: String?,
        val domain: String,
        val reason: String,
        val count: Int,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    @Synchronized
    fun add(app: AppInfo, domain: String, reason: String) {
        val now = System.currentTimeMillis()
        val list = _entries.value.toMutableList()
        val i = list.indexOfFirst { it.domain == domain && it.appLabel == app.label }
        val count = if (i >= 0) list.removeAt(i).count + 1 else 1
        list.add(0, Entry(now, app.label, app.packageName, domain, reason, count))
        _entries.value = list.take(200)
    }

    @Synchronized
    fun clear() { _entries.value = emptyList() }
}

private val DATING = listOf(
    "tinder.com", "gotinder.com", "bumble.com", "bumble.app", "badoo.com", "hinge.co", "match.com",
    "okcupid.com", "pof.com", "plentyoffish.com", "grindr.com", "grindr.mobi", "happn.com", "zoosk.com",
    "eharmony.com", "coffeemeetsbagel.com", "lovoo.com", "meetic.com", "tantan.com", "feeld.co",
    "her.app", "weareher.com", "boo.world", "jdate.com", "christianmingle.com", "elitesingles.com",
    "parship.com", "lovescout24.de", "mamba.ru", "taimi.com", "scruff.com", "jackd.com",
)
