package com.portwatch.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/** User choices that survive restarts: blocked apps, threat blocking, online IP lookups. */
object Settings {
    private lateinit var prefs: SharedPreferences

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    val blocked: StateFlow<Set<String>> = _blocked
    private val _blockThreats = MutableStateFlow(false)
    val blockThreats: StateFlow<Boolean> = _blockThreats
    private val _geoOnline = MutableStateFlow(true)
    val geoOnline: StateFlow<Boolean> = _geoOnline

    /** Called (on the caller's thread) whenever blocking rules change. */
    val ruleListeners = CopyOnWriteArrayList<() -> Unit>()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("hodor", Context.MODE_PRIVATE)
        _blocked.value = prefs.getStringSet("blocked", emptySet())!!.toSet()
        _blockThreats.value = prefs.getBoolean("block_threats", false)
        _geoOnline.value = prefs.getBoolean("geo_online", true)
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
