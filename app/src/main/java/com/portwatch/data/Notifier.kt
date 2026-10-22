package com.portwatch.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.portwatch.BlockedActivity
import com.portwatch.R

/** "Hodor held the door" notifications for filtered sites, at most one per app+site every 2 min. */
object Notifier {
    private const val CHANNEL = "held"
    private const val QUIET_MS = 120_000L
    private lateinit var app: Context
    private val lastShown = HashMap<String, Long>()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sites held by Hodor", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shown when site filtering stops an app from opening a site"
            }
        )
    }

    /** Records the block and, unless it was just shown, notifies. Safe to call from any thread. */
    fun held(who: AppInfo, domain: String, reason: String) {
        BlockLog.add(who, domain, reason)
        Counters.held()
        val key = "${who.key}|$domain"
        val now = System.currentTimeMillis()
        synchronized(lastShown) {
            if (now - (lastShown[key] ?: 0) < QUIET_MS) return
            lastShown[key] = now
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val id = domain.hashCode()
        val open = PendingIntent.getActivity(
            app, id, BlockedActivity.intent(app, domain, reason, who.label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val allow = PendingIntent.getBroadcast(
            app, id, Intent(app, AllowReceiver::class.java).putExtra("domain", domain).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("Hodor held the door")
            .setContentText("$domain · ${reason.substringBefore(':')} · ${who.label}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Allow 15 min", allow)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        app.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

/** The notification's "Allow 15 min" button. */
class AllowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val domain = intent.getStringExtra("domain") ?: return
        Filter.allowFor(domain, 15)
        context.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("id", 0))
        Toast.makeText(context, "$domain allowed for 15 minutes", Toast.LENGTH_SHORT).show()
    }
}
