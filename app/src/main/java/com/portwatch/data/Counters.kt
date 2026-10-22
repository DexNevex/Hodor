package com.portwatch.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Running totals of blocked ad/tracker requests and held sites, saved on the phone so they
 * survive "Clear closed", the connection-list limit, app restarts and updates. Unlike the
 * connection list these count events, so a site looked up for IPv4 and IPv6 counts twice.
 */
object Counters {
    data class Counts(val today: Long, val allTime: Long)
    data class Snapshot(val ads: Counts = Counts(0, 0), val held: Counts = Counts(0, 0))

    private lateinit var prefs: SharedPreferences
    private val adsAll = AtomicLong()
    private val adsToday = AtomicLong()
    private val heldAll = AtomicLong()
    private val heldToday = AtomicLong()
    @Volatile private var day = ""
    @Volatile private var dirty = false

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("counters", Context.MODE_PRIVATE)
        day = prefs.getString("day", "") ?: ""
        adsAll.set(prefs.getLong("ads_all", 0))
        adsToday.set(prefs.getLong("ads_today", 0))
        heldAll.set(prefs.getLong("held_all", 0))
        heldToday.set(prefs.getLong("held_today", 0))
        rollDay()
        publish()
    }

    fun adBlocked() = bump(adsAll, adsToday)
    fun held() = bump(heldAll, heldToday)

    private fun bump(all: AtomicLong, today: AtomicLong) {
        rollDay()
        all.incrementAndGet()
        today.incrementAndGet()
        dirty = true
    }

    /** Starts a new "today" at midnight. */
    @Synchronized
    private fun rollDay() {
        val now = LocalDate.now().toString()
        if (now != day) {
            day = now
            adsToday.set(0)
            heldToday.set(0)
            dirty = true
        }
    }

    /** Called about once a second by the engine: publishes and saves if anything changed. */
    fun flush() {
        rollDay()
        if (!dirty) return
        dirty = false
        publish()
        prefs.edit()
            .putString("day", day)
            .putLong("ads_all", adsAll.get()).putLong("ads_today", adsToday.get())
            .putLong("held_all", heldAll.get()).putLong("held_today", heldToday.get())
            .apply()
    }

    private fun publish() {
        _state.value = Snapshot(Counts(adsToday.get(), adsAll.get()), Counts(heldToday.get(), heldAll.get()))
    }
}
