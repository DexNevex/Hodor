package com.portwatch.data

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

data class AppInfo(val uid: Int, val label: String, val packageName: String?) {
    /** Stable id for block rules: the package, or the uid for system ids without one. */
    val key get() = packageName ?: "uid:$uid"
}

/** Turns the uid that owns a socket into an app name, and package names into icons. */
object AppResolver {
    private val apps = ConcurrentHashMap<Int, AppInfo>()
    private val icons = HashMap<String, ImageBitmap?>()

    fun resolve(ctx: Context, uid: Int): AppInfo = apps.getOrPut(uid) { lookup(ctx, uid) }

    private fun lookup(ctx: Context, uid: Int): AppInfo {
        if (uid < 0) return AppInfo(uid, "Unknown", null)
        special(uid)?.let { return AppInfo(uid, it, null) }
        val pm = ctx.packageManager
        val pkgs = pm.getPackagesForUid(uid)
        if (pkgs.isNullOrEmpty()) return AppInfo(uid, "uid $uid", null)
        val pkg = pkgs.first()
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        return AppInfo(uid, if (pkgs.size > 1) "$label +${pkgs.size - 1}" else label, pkg)
    }

    private fun special(uid: Int) = when (uid) {
        0 -> "Kernel / root"
        1000 -> "Android System"
        1001 -> "Phone (radio)"
        1021 -> "GPS"
        1051 -> "DNS resolver"
        1073 -> "Network stack"
        else -> null
    }

    /** Call from the main thread only. */
    fun icon(ctx: Context, pkg: String): ImageBitmap? = icons.getOrPut(pkg) {
        try {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
