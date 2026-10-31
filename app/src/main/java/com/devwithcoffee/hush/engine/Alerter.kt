package com.devwithcoffee.hush.engine

import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.devwithcoffee.hush.core.Kind
import com.devwithcoffee.hush.core.Reason

/**
 * Makes the noise for a breakthrough. The system is in alarms-only DND, so Hush plays
 * on the ALARM stream (always allowed) and vibrates with alarm attributes.
 */
object Alerter {
    private const val TAG = "HushAlert"
    const val ACTION_SILENCE = "com.devwithcoffee.hush.SILENCE"
    private const val CALL_RING_MAX_MS = 60_000L
    private const val THROTTLE_MS = 8_000L

    private class Active(
        val id: Int, val kind: Kind, val sourceKey: String?, val ringtone: Ringtone?,
        val original: PendingIntent?, val who: String, val app: String,
    )

    private val main = Handler(Looper.getMainLooper())
    private val active = LinkedHashMap<Int, Active>()
    private val throttle = com.devwithcoffee.hush.core.Throttle(THROTTLE_MS)
    private var nextId = 1
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile var lastFired: String? = null; private set

    fun reasonText(r: Reason) = when (r) {
        Reason.VIP -> "Always-allowed person"
        Reason.ALLOWED_APP -> "Allowed app"
        Reason.REPEAT_CALLER -> "Called again within minutes"
    }

    fun original(id: Int): PendingIntent? = synchronized(this) { active[id]?.original }

    /** Returns the alert id. [sourceKey] ties a ringing call to the notification that started it. */
    fun alert(
        ctx: Context, kind: Kind, who: String, appLabel: String, reason: Reason,
        sourceKey: String?, original: PendingIntent?, force: Boolean = false,
    ): Int = synchronized(this) {
        val app = ctx.applicationContext
        // Same call notification re-posted (WhatsApp updates it every second) -> already ringing.
        if (sourceKey != null) active.values.firstOrNull { it.sourceKey == sourceKey }?.let { return it.id }

        val id = nextId++
        val quiet = kind != Kind.CALL && !force && !throttle.allow("$appLabel|$who|$kind", System.currentTimeMillis())

        var ringtone: Ringtone? = null
        if (!quiet) {
            raiseAlarmVolume(app)
            ringtone = play(app, kind)
            vibrate(app, kind)
            if (kind == Kind.CALL) holdWake(app)
        }
        val a = Active(id, kind, sourceKey, ringtone, original, who, appLabel)
        active[id] = a
        Notifs.showBreakthrough(app, id, kind, who, appLabel, reasonText(reason), original)
        lastFired = "$kind|$who|$appLabel"
        Log.i(TAG, "breakthrough id=$id kind=$kind app=$appLabel reason=$reason quiet=$quiet")

        val ttl = if (kind == Kind.CALL) CALL_RING_MAX_MS else 4_000L
        main.postDelayed({ silence(app, id, cancelNotification = false) }, ttl)
        id
    }

    /** Stop sound for one alert. Calls also clear their full-screen notification. */
    fun silence(ctx: Context, id: Int, cancelNotification: Boolean = true) = synchronized(this) {
        val a = active.remove(id) ?: return@synchronized
        runCatching { a.ringtone?.stop() }
        if (a.kind == Kind.CALL) {
            stopVibration(ctx)
            Notifs.cancelBreakthrough(ctx, id)
        } else if (cancelNotification) Notifs.cancelBreakthrough(ctx, id)
        if (active.values.none { it.ringtone != null }) restoreAlarmVolume(ctx)
        if (active.values.none { it.kind == Kind.CALL }) releaseWake()
    }

    /** The source notification went away (call answered, declined, or missed). */
    fun sourceRemoved(ctx: Context, sourceKey: String) = synchronized(this) {
        active.values.filter { it.sourceKey == sourceKey && it.kind == Kind.CALL }.map { it.id }.forEach { silence(ctx, it) }
    }

    fun stopCalls(ctx: Context) = synchronized(this) {
        active.values.filter { it.kind == Kind.CALL }.map { it.id }.forEach { silence(ctx, it) }
    }

    fun stopAll(ctx: Context) = synchronized(this) {
        active.keys.toList().forEach { silence(ctx, it) }
        restoreAlarmVolume(ctx)
        releaseWake()
    }

    fun isRinging() = synchronized(this) { active.values.any { it.kind == Kind.CALL } }

    // ---- sound ----

    private val alarmAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun play(ctx: Context, kind: Kind): Ringtone? {
        val type = if (kind == Kind.CALL) RingtoneManager.TYPE_RINGTONE else RingtoneManager.TYPE_NOTIFICATION
        val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, type)
            ?: RingtoneManager.getDefaultUri(type)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        return runCatching {
            RingtoneManager.getRingtone(ctx, uri)?.apply {
                audioAttributes = alarmAttrs
                isLooping = kind == Kind.CALL
                play()
            }
        }.onFailure { Log.e(TAG, "play failed", it) }.getOrNull()
    }

    private fun raiseAlarmVolume(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val want = (Store.config.value.alertVolume * max).toInt().coerceIn(1, max)
        val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
        if (cur == want) return
        if (Store.savedAlarmVolume < 0) Store.savedAlarmVolume = cur
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, want, 0) }
    }

    private fun restoreAlarmVolume(ctx: Context) {
        val saved = Store.savedAlarmVolume
        if (saved < 0) return
        runCatching { ctx.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, saved, 0) }
        Store.savedAlarmVolume = -1
    }

    // ---- vibration ----

    private fun vibrator(ctx: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)

    private fun vibrate(ctx: Context, kind: Kind) {
        if (!Store.config.value.vibrate) return
        val effect = if (kind == Kind.CALL)
            VibrationEffect.createWaveform(longArrayOf(0, 900, 600), 0)
        else VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1)
        runCatching {
            val v = vibrator(ctx)
            if (Build.VERSION.SDK_INT >= 33) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            else @Suppress("DEPRECATION") v.vibrate(effect, alarmAttrs)
        }
    }

    private fun stopVibration(ctx: Context) = runCatching { vibrator(ctx).cancel() }

    private fun holdWake(ctx: Context) {
        if (wakeLock?.isHeld == true) return
        wakeLock = ctx.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hush:ring").apply { acquire(CALL_RING_MAX_MS + 5_000) }
    }

    private fun releaseWake() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }
}
