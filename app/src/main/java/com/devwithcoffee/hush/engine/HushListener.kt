package com.devwithcoffee.hush.engine

import android.Manifest
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager
import android.util.Log
import com.devwithcoffee.hush.BuildConfig
import com.devwithcoffee.hush.core.*

/**
 * Sees every notification. While Hush is on, decides hold vs. break through, and logs it.
 * Also owns the phone-call receiver, because this service stays bound by the system.
 */
class HushListener : NotificationListenerService() {

    private val seenCalls = HashMap<String, Long>()     // sbn key -> first seen
    private var phoneReceiver: BroadcastReceiver? = null

    override fun onListenerConnected() {
        Store.init(this)
        instance = this
        registerPhone()
        Hush.reconcile(this)
        Log.i(TAG, "listener connected")
    }

    override fun onListenerDisconnected() {
        instance = null
        phoneReceiver?.let { runCatching { unregisterReceiver(it) } }
        phoneReceiver = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        Store.init(this)
        val session = Store.session.value
        if (!session.active) return
        if (session.endsAt != 0L && System.currentTimeMillis() >= session.endsAt) { Hush.stop(this, byUser = false); return }

        val n = flatten(sbn, rankingMap)
        val now = System.currentTimeMillis()

        // A ringing call notification is re-posted many times; judge it once.
        if (Rules.isCall(n)) {
            seenCalls.entries.removeAll { now - it.value > 120_000 }
            if (seenCalls.containsKey(sbn.key)) return
            seenCalls[sbn.key] = now
        }

        val verdict = Rules.decide(
            Store.config.value, n, tracker, now, packageName,
            extraMessagingPkgs = if (BuildConfig.DEBUG) setOf("com.android.shell") else emptySet(),
            phonePathActive = phonePathActive(this),
        )
        val label = AppNames.label(this, sbn.packageName)
        when (verdict) {
            is Verdict.Ignore -> Unit
            is Verdict.Hold -> {
                Store.addEvent(Event(now, sbn.packageName, verdict.who, false, verdict.kind))
                Notifs.showStatus(this)
            }
            is Verdict.Through -> {
                Store.addEvent(Event(now, sbn.packageName, verdict.who, true, verdict.kind, verdict.reason))
                Alerter.alert(this, verdict.kind, verdict.who, label, verdict.reason,
                    sourceKey = if (verdict.kind == Kind.CALL) sbn.key else null,
                    original = sbn.notification.contentIntent)
                Notifs.showStatus(this)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (seenCalls.remove(sbn.key) != null) Alerter.sourceRemoved(this, sbn.key)
    }

    // ---- flattening ----

    private fun flatten(sbn: StatusBarNotification, rankingMap: RankingMap?): Incoming {
        val n = sbn.notification
        val e = n.extras
        val importance = rankingMap?.let { rm ->
            val r = Ranking()
            if (rm.getRanking(sbn.key, r)) r.importance else null
        } ?: Incoming.UNKNOWN_IMPORTANCE

        val people = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            val list = e.getParcelableArrayList<android.app.Person>(Notification.EXTRA_PEOPLE_LIST)
            list?.forEach { p ->
                p.uri?.let { people += it }
                p.name?.let { people += "name:$it" }
            }
        }
        var lastSender: String? = null
        @Suppress("DEPRECATION")
        val msgs = e.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!msgs.isNullOrEmpty()) {
            val last = msgs.last() as? android.os.Bundle
            lastSender = last?.getParcelable<android.app.Person>("sender_person")?.name?.toString()
                ?: last?.getCharSequence("sender")?.toString()
            last?.getParcelable<android.app.Person>("sender_person")?.uri?.let { if (it.startsWith("tel:")) people += it }
        }

        return Incoming(
            pkg = sbn.packageName,
            title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            category = n.category,
            ongoing = sbn.isOngoing,
            groupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            importance = importance,
            people = people,
            isGroupConversation = e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
            conversationTitle = e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            lastSender = lastSender,
        )
    }

    // ---- phone calls ----

    fun registerPhone() {
        if (phoneReceiver != null || !phonePathActive(this)) return
        phoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onPhoneState(c, i)
        }
        val f = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(phoneReceiver, f, RECEIVER_EXPORTED)
        else registerReceiver(phoneReceiver, f)
    }

    companion object {
        private const val TAG = "HushListener"
        val tracker = RepeatTracker()
        @Volatile var instance: HushListener? = null
        private var lastRinging: Pair<String, Long>? = null

        /** Phone + call-log permissions let us read the caller's number directly. */
        fun phonePathActive(ctx: Context) =
            ctx.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ctx.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

        fun onPhoneState(ctx: Context, i: Intent) {
            Store.init(ctx)
            val state = i.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (state != TelephonyManager.EXTRA_STATE_RINGING) { Alerter.stopCalls(ctx); return }
            @Suppress("DEPRECATION")
            val number = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: return  // first broadcast has no number
            if (!Store.session.value.active) return
            val now = System.currentTimeMillis()
            // Android sends RINGING twice (with and without number); judge each ring once.
            lastRinging?.let { (n, t) -> if (n == number && now - t < 20_000) return }
            lastRinging = number to now

            val name = contactName(ctx, number)
            val v = Rules.decideCall(Store.config.value, number, name, tracker, now)
            when (v) {
                is Verdict.Through -> {
                    Store.addEvent(Event(now, "phone", v.who, true, Kind.CALL, v.reason))
                    Alerter.alert(ctx, Kind.CALL, v.who, "Phone", v.reason, sourceKey = "phone:$number", original = null)
                }
                is Verdict.Hold -> Store.addEvent(Event(now, "phone", v.who, false, Kind.CALL))
                else -> Unit
            }
            Notifs.showStatus(ctx)
        }

        fun contactName(ctx: Context, number: String): String? {
            if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            return runCatching {
                ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull()
        }
    }
}
