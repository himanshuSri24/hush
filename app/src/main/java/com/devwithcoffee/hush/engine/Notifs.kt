package com.devwithcoffee.hush.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.devwithcoffee.hush.R
import com.devwithcoffee.hush.core.Kind
import com.devwithcoffee.hush.core.Session
import com.devwithcoffee.hush.ui.AlertActivity
import com.devwithcoffee.hush.ui.MainActivity
import java.text.DateFormat
import java.util.Date

object Notifs {
    const val CH_STATUS = "status"
    const val CH_BREAK = "breakthrough"
    const val CH_SUMMARY = "summary"
    const val ID_STATUS = 1
    const val ID_SUMMARY = 2
    const val ID_BREAK_BASE = 1000

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "Hush is on", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows while Hush is holding notifications"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_BREAK, "Breakthroughs", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Calls and messages that got through Hush. Hush plays the sound itself."
            setSound(null, null)          // Alerter plays on the alarm stream
            enableVibration(false)        // Alerter vibrates with alarm attributes
            setBypassDnd(true)            // honoured because Hush holds DND access
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CH_SUMMARY, "Session summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "What Hush held while you were away"
            setSound(null, null)
        })
    }

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(ctx: Context, action: String, code: Int, extra: Int = 0) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, HushReceiver::class.java).setAction(action).putExtra("n", extra),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun timeOf(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    fun showStatus(ctx: Context) {
        val s = Store.session.value
        if (!s.active) return
        val title = if (s.endsAt == 0L) "Hush is on" else "Hush is on until ${timeOf(s.endsAt)}"
        val held = s.held.size; val through = s.through.size
        val text = when {
            held == 0 && through == 0 -> "Only your people and work apps can reach you"
            else -> "$held held" + (if (through > 0) " · $through got through" else "")
        }
        val b = Notification.Builder(ctx, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(0xFF9AA8FF.toInt())
            .setContentIntent(openApp(ctx))
            .setCategory(Notification.CATEGORY_STATUS)
        if (s.endsAt != 0L) {
            b.setWhen(s.endsAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            b.addAction(Notification.Action.Builder(null, "+15 min", action(ctx, Hush.ACTION_EXTEND, 10, 15)).build())
        }
        b.addAction(Notification.Action.Builder(null, "End now", action(ctx, Hush.ACTION_END + ".USER", 11)).build())
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(ID_STATUS, b.build()) }
    }

    fun clearStatus(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(ID_STATUS)

    fun showSummary(ctx: Context, s: Session) {
        val held = s.held
        if (held.isEmpty() && s.through.isEmpty()) return
        val apps = held.map { it.pkg }.distinct().size
        val title = "Welcome back · ${held.size} held from $apps app${if (apps == 1) "" else "s"}"
        val top = held.groupBy { it.pkg }.entries.sortedByDescending { it.value.size }.take(4)
            .joinToString(" · ") { "${AppNames.label(ctx, it.key)} ${it.value.size}" }
        val text = (if (s.through.isNotEmpty()) "${s.through.size} got through. " else "") + top
        val n = Notification.Builder(ctx, CH_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setColor(0xFF9AA8FF.toInt())
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(ID_SUMMARY, n) }
    }

    fun showBreakthrough(ctx: Context, id: Int, kind: Kind, who: String, appLabel: String, why: String, original: PendingIntent?) {
        val title = when (kind) {
            Kind.CALL -> "$who is calling"
            Kind.MESSAGE -> "$who messaged you"
            Kind.APP -> who.ifBlank { appLabel }
        }
        val full = PendingIntent.getActivity(
            ctx, id, AlertActivity.intent(ctx, kind, who, appLabel, why, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(ctx, CH_BREAK)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText("$appLabel · $why")
            .setSubText("Got through Hush")
            .setColor(0xFFFFB86B.toInt())
            .setCategory(Notification.CATEGORY_ALARM)   // passes the alarms-only filter
            .setAutoCancel(true)
            .setContentIntent(original ?: full)
            .setDeleteIntent(action(ctx, Alerter.ACTION_SILENCE, ID_BREAK_BASE + id + 50_000, id))
            .addAction(Notification.Action.Builder(null, if (kind == Kind.CALL) "Silence" else "Dismiss",
                action(ctx, Alerter.ACTION_SILENCE, ID_BREAK_BASE + id, id)).build())
        if (kind == Kind.CALL) b.setFullScreenIntent(full, true).setOngoing(true)
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(ID_BREAK_BASE + id, b.build()) }
    }

    fun cancelBreakthrough(ctx: Context, id: Int) =
        ctx.getSystemService(NotificationManager::class.java).cancel(ID_BREAK_BASE + id)
}
