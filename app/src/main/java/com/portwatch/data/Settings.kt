package com.portwatch.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/** User choices that survive restarts: blocked apps, threat and ad blocking, online IP lookups. */
object Settings {
    private lateinit var prefs: SharedPreferences

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked
    private val _blockThreats = MutableStateFlow(false)
    val blockThreats: StateFlow<Boolean> = _blockThreats
    private val _geoOnline = MutableStateFlow(true)
    val geoOnline: StateFlow<Boolean> = _geoOnline
    private val _blockAds = MutableStateFlow(false)
    val blockAds: StateFlow<Boolean> = _blockAds
    private val _adLevel = MutableStateFlow(ThreatIntel.AdLevel.NORMAL)
    val adLevel: StateFlow<ThreatIntel.AdLevel> = _adLevel
    /** Apps exempted from ad blocking (keys as in [blocked]). */
    private val _adAllowed = MutableStateFlow<Set<String>>(emptySet())
    val adAllowed: StateFlow<Set<String>> = _adAllowed

    /** Called (on the caller's thread) whenever blocking rules change. */
    val ruleListeners = CopyOnWriteArrayList<() -> Unit>()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("hodor", Context.MODE_PRIVATE)
        _blocked.value = prefs.getStringSet("blocked", emptySet())!!.toSet()
        _blockThreats.value = prefs.getBoolean("block_threats", false)
        _geoOnline.value = prefs.getBoolean("geo_online", true)
        _blockAds.value = prefs.getBoolean("block_ads", false)
        _adLevel.value = ThreatIntel.AdLevel.valueOf(prefs.getString("ad_level", ThreatIntel.AdLevel.NORMAL.name)!!)
        _adAllowed.value = prefs.getStringSet("ad_allowed", emptySet())!!.toSet()
    }

    fun adsBlockedFor(app: AppInfo) = _blockAds.value && app.key !in _adAllowed.value

    fun setBlockAds(on: Boolean) {
        _blockAds.value = on
        prefs.edit().putBoolean("block_ads", on).apply()
        ThreatIntel.adsChanged(levelChanged = false)
        ruleListeners.forEach { it() }
    }

    fun setAdLevel(level: ThreatIntel.AdLevel) {
        if (level == _adLevel.value) return
        _adLevel.value = level
        prefs.edit().putString("ad_level", level.name).apply()
        ThreatIntel.adsChanged(levelChanged = true)
    }

    fun setAdAllowed(key: String, allow: Boolean) {
        val s = if (allow) _adAllowed.value + key else _adAllowed.value - key
        _adAllowed.value = s
        prefs.edit().putStringSet("ad_allowed", s).apply()
        ruleListeners.forEach { it() }
    }

    fun isBlocked(app: AppInfo) = app.key in _blocked.value

    fun setBlocked(key: String, block: Boolean) {
        val s = if (block) _blocked.value + key else _blocked.value - key
        _blocked.value = s
        prefs.edit().putStringSet("blocked", s).apply()
        ruleListeners.forEach { it() }
    }

    fun setBlockThreats(on: Boolean) {
        _blockThreats.value = on
        prefs.edit().putBoolean("block_threats", on).apply()
        ruleListeners.forEach { it() }
    }

    fun setGeoOnline(on: Boolean) {
        _geoOnline.value = on
        prefs.edit().putBoolean("geo_online", on).apply()
    }
}

/** True for addresses on the internet, false for private, local, CGNAT and reserved ranges. */
fun isPublic(a: InetAddress): Boolean {
    if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress ||
        a.isSiteLocalAddress || a.isMulticastAddress
    ) return false
    val b = a.address
    val b0 = b[0].toInt() and 0xFF
    if (a is Inet4Address) {
        val b1 = b[1].toInt() and 0xFF
        if (b0 == 0 || b0 >= 240) return false
        if (b0 == 100 && b1 in 64..127) return false
        if (b0 == 192 && b1 == 0 && (b[2].toInt() and 0xFF) == 0) return false
    } else if ((b0 and 0xFE) == 0xFC) {
        return false
    }
    return true
}
