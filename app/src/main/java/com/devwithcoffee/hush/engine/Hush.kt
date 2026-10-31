package com.devwithcoffee.hush.engine

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.Log
import com.devwithcoffee.hush.core.Clock
import com.devwithcoffee.hush.core.Schedules
import com.devwithcoffee.hush.core.Session
import java.time.LocalDateTime

/** Starts, ends and reconciles quiet sessions. Everything else reacts to Store.session. */
object Hush {
    private const val TAG = "Hush"
    const val ACTION_END = "com.devwithcoffee.hush.END"
    const val ACTION_SCHEDULE = "com.devwithcoffee.hush.SCHEDULE"
    const val ACTION_EXTEND = "com.devwithcoffee.hush.EXTEND"
    const val ACTION_STOP_START = "com.devwithcoffee.hush.TOGGLE"

    fun isActive(ctx: Context): Boolean { Store.init(ctx); return Store.session.value.active }

    /** [minutes] == 0 means until turned off. */
    fun startFor(ctx: Context, minutes: Int, source: String = "manual") =
        start(ctx, if (minutes <= 0) 0 else Clock.now() + minutes * 60_000L, source)

    fun start(ctx: Context, endsAt: Long, source: String = "manual") {
        Store.init(ctx)
        val cur = Store.session.value
        if (cur.active) {
            // Already hushed: keep the session and its feed, just move the end.
            val newEnd = when {
                cur.endsAt == 0L || endsAt == 0L -> 0L
                else -> maxOf(cur.endsAt, endsAt)
            }
            Store.setSession(cur.copy(endsAt = newEnd))
        } else {
            Store.setSession(Session(active = true, startedAt = Clock.now(), endsAt = endsAt, source = source))
        }
        applyDnd(ctx, on = true)
        Notifs.showStatus(ctx)
        scheduleAlarms(ctx)
        refreshTile(ctx)
        Log.i(TAG, "hush on until=${Store.session.value.endsAt} src=$source")
    }

    fun extend(ctx: Context, minutes: Int) {
        Store.init(ctx)
        val s = Store.session.value
        if (!s.active) return
        val base = if (s.endsAt == 0L) return else maxOf(s.endsAt, Clock.now())
        Store.setSession(s.copy(endsAt = base + minutes * 60_000L))
        Notifs.showStatus(ctx)
        scheduleAlarms(ctx)
    }

    fun stop(ctx: Context, byUser: Boolean) {
        Store.init(ctx)
        val s = Store.session.value
        if (!s.active) return
        if (byUser) {
            // Ending a scheduled window early: don't let reconcile() restart it.
            Schedules.activeAt(Store.schedules.value, LocalDateTime.now())?.let {
                Store.dismissedWindowEnd = Schedules.toMillis(it.end)
            }
        }
        val ended = s.copy(active = false, endsAt = Clock.now())
        Store.setSession(ended)
        Store.archive(ended)
        Alerter.stopAll(ctx)
        applyDnd(ctx, on = false)
        Notifs.clearStatus(ctx)
        Notifs.showSummary(ctx, ended)
        scheduleAlarms(ctx)
        refreshTile(ctx)
        Log.i(TAG, "hush off held=${ended.held.size} through=${ended.through.size}")
    }

    /** Make reality match state: expired sessions end, scheduled windows start, DND re-applied. */
    fun reconcile(ctx: Context) {
        Store.init(ctx)
        val now = Clock.now()
        val s = Store.session.value
        if (s.active && s.endsAt != 0L && now >= s.endsAt) { stop(ctx, byUser = false); return }
        if (!s.active) {
            val w = Schedules.activeAt(Store.schedules.value, LocalDateTime.now())
            if (w != null) {
                val end = Schedules.toMillis(w.end)
                if (end != Store.dismissedWindowEnd) start(ctx, end, "schedule:${w.schedule.id}")
            }
        } else {
            applyDnd(ctx, on = true)
            Notifs.showStatus(ctx)
        }
        scheduleAlarms(ctx)
        refreshTile(ctx)
    }

    // ---- DND ----

    fun hasDndAccess(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    private fun applyDnd(ctx: Context, on: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) { Log.w(TAG, "no DND access"); return }
        runCatching {
            if (on) {
                if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                    if (Build.VERSION.SDK_INT < 35) Store.previousFilter = nm.currentInterruptionFilter
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                }
            } else {
                val restore = if (Build.VERSION.SDK_INT < 35) Store.previousFilter
                    .takeIf { it != NotificationManager.INTERRUPTION_FILTER_ALARMS && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN }
                    ?: NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_ALL
                nm.setInterruptionFilter(restore)
            }
        }.onFailure { Log.e(TAG, "DND change failed", it) }
    }

    // ---- alarms ----

    private fun pi(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, HushReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun scheduleAlarms(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val endPi = pi(ctx, ACTION_END, 1)
        val schedPi = pi(ctx, ACTION_SCHEDULE, 2)
        am.cancel(endPi); am.cancel(schedPi)
        val s = Store.session.value
        if (s.active && s.endsAt != 0L) setExact(am, s.endsAt, endPi)
        Schedules.nextStart(Store.schedules.value, LocalDateTime.now())?.let {
            setExact(am, Schedules.toMillis(it.start), schedPi)
        }
    }

    private fun setExact(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun refreshTile(ctx: Context) = runCatching {
        TileService.requestListeningState(ctx, ComponentName(ctx, HushTile::class.java))
    }

    // ---- setup state ----

    fun hasListenerAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(ctx, HushListener::class.java).flattenToString())
    }
}
