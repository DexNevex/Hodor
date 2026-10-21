package com.portwatch.data

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicLong

/** Session totals and per-second speeds across all apps, for the speed and totals cards. */
object Traffic {
    data class Snapshot(
        val totalIn: Long = 0,
        val totalOut: Long = 0,
        val downPerSec: Long = 0,
        val upPerSec: Long = 0,
        val down: List<Long> = emptyList(), // last 60 seconds, oldest first
        val up: List<Long> = emptyList(),
        val startedAt: Long = 0,          // wall clock, 0 when never started
    )

    private const val HISTORY = 60
    private val inBytes = AtomicLong()
    private val outBytes = AtomicLong()
    private var lastIn = 0L
    private var lastOut = 0L
    private var lastTick = 0L
    private val down = ArrayDeque<Long>()
    private val up = ArrayDeque<Long>()
    private var startedAt = 0L

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    fun addIn(n: Long) { inBytes.addAndGet(n) }
    fun addOut(n: Long) { outBytes.addAndGet(n) }

    @Synchronized
    fun reset() {
        inBytes.set(0); outBytes.set(0)
        lastIn = 0; lastOut = 0
        down.clear(); up.clear()
        lastTick = SystemClock.elapsedRealtime()
        startedAt = System.currentTimeMillis()
        _state.value = Snapshot(startedAt = startedAt)
    }

    /** Called about once a second by the engine. */
    @Synchronized
    fun tick() {
        val now = SystemClock.elapsedRealtime()
        val dt = (now - lastTick).coerceAtLeast(1)
        val i = inBytes.get()
        val o = outBytes.get()
        val d = (i - lastIn) * 1000 / dt
        val u = (o - lastOut) * 1000 / dt
        lastIn = i; lastOut = o; lastTick = now
        down.addLast(d); up.addLast(u)
        while (down.size > HISTORY) down.removeFirst()
        while (up.size > HISTORY) up.removeFirst()
        _state.value = Snapshot(i, o, d, u, down.toList(), up.toList(), startedAt)
    }
}
