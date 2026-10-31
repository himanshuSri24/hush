package com.devwithcoffee.hush.core

/**
 * The decision engine. Pure Kotlin, no Android types, so every rule is unit tested.
 *
 * Hush puts the system in "alarms only" Do Not Disturb, so nothing makes a sound by
 * default. For each notification (or phone call) this decides whether Hush should
 * break through and alert the user itself.
 */

/** What the notification listener saw, flattened out of a StatusBarNotification. */
data class Incoming(
    val pkg: String,
    val title: String? = null,
    val text: String? = null,
    val category: String? = null,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
    /** Ranking importance (NotificationManager.IMPORTANCE_*), -1000 when unknown. */
    val importance: Int = UNKNOWN_IMPORTANCE,
    /** Person URIs / names attached to the notification ("tel:+9198...", "name:Rahul"). */
    val people: List<String> = emptyList(),
    val isGroupConversation: Boolean = false,
    val conversationTitle: String? = null,
    /** Sender of the newest message in a MessagingStyle notification. */
    val lastSender: String? = null,
) {
    companion object { const val UNKNOWN_IMPORTANCE = -1000 }
}

sealed interface Verdict {
    /** Not something to count (ongoing, summaries, silent, our own). */
    data object Ignore : Verdict
    /** Stays silent; counted in the "held" list. */
    data class Hold(val who: String, val kind: Kind) : Verdict
    /** Hush alerts the user. */
    data class Through(val reason: Reason, val kind: Kind, val who: String) : Verdict
}

object Packages {
    /** Apps where a VIP's name/number in the notification counts. */
    val MESSAGING = setOf(
        "com.whatsapp", "com.whatsapp.w4b",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.oneplus.mms", "com.coloros.mms",
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thoughtcrime.securesms",
        "com.truecaller",
    )

    /** System phone apps. Their call notifications are handled by the phone-state path. */
    val DIALERS = setOf(
        "com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer",
        "com.samsung.android.incallui", "com.android.incallui", "com.android.server.telecom",
        "com.oneplus.dialer", "com.coloros.phone", "com.miui.voip",
    )

    val LABELS = mapOf(
        "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business",
        "com.google.android.apps.messaging" to "Messages", "com.samsung.android.messaging" to "Messages",
        "com.android.mms" to "Messages", "org.telegram.messenger" to "Telegram",
        "org.thoughtcrime.securesms" to "Signal", "com.Slack" to "Slack",
        "com.microsoft.teams" to "Teams", "com.microsoft.office.outlook" to "Outlook",
        "phone" to "Phone",
    )
}

object Rules {
    const val CATEGORY_CALL = "call"
    private const val IMPORTANCE_LOW = 2
    private val CALL_WORDS = listOf("incoming voice call", "incoming video call", "incoming call", "calling…", "calling...")

    fun decide(
        cfg: Config,
        n: Incoming,
        tracker: RepeatTracker,
        now: Long,
        ownPkg: String,
        extraMessagingPkgs: Set<String> = emptySet(),
        phonePathActive: Boolean = true,
    ): Verdict {
        if (n.pkg == ownPkg || n.groupSummary) return Verdict.Ignore
        val isCall = isCall(n)
        if (n.ongoing && !isCall) return Verdict.Ignore
        if (n.importance != Incoming.UNKNOWN_IMPORTANCE && n.importance <= IMPORTANCE_LOW && !isCall) return Verdict.Ignore

        val who = displayWho(n)
        val kind = when {
            isCall -> Kind.CALL
            n.pkg in Packages.MESSAGING || n.pkg in extraMessagingPkgs -> Kind.MESSAGE
            else -> Kind.APP
        }

        // Normal phone calls arrive via the phone-state receiver (it has the number).
        if (n.pkg in Packages.DIALERS && isCall && phonePathActive) return Verdict.Ignore

        if (n.pkg in cfg.allowedApps) return Verdict.Through(Reason.ALLOWED_APP, kind, who)

        val messaging = n.pkg in Packages.MESSAGING || n.pkg in Packages.DIALERS || n.pkg in extraMessagingPkgs
        if (!messaging) return Verdict.Hold(who, kind)

        val vip = matchVip(cfg, n)
        if (vip != null) return Verdict.Through(Reason.VIP, kind, vip.name)

        if (isCall) {
            val key = callerKey(n) ?: return Verdict.Hold(who, kind)
            val count = tracker.register(key, now, cfg.repeatWindowMin * 60_000L)
            if (cfg.repeatCallers && count >= 2) return Verdict.Through(Reason.REPEAT_CALLER, Kind.CALL, who)
        }
        return Verdict.Hold(who, kind)
    }

    /** A normal (SIM) phone call. [contactName] is the address-book name if known. */
    fun decideCall(cfg: Config, number: String?, contactName: String?, tracker: RepeatTracker, now: Long): Verdict {
        val who = contactName?.takeIf { it.isNotBlank() } ?: number?.takeIf { it.isNotBlank() } ?: "Unknown caller"
        val vip = cfg.vips.firstOrNull { v ->
            (number != null && v.numbers.any { Phone.same(it, number) }) ||
                (contactName != null && Names.same(v.name, contactName))
        }
        if (vip != null) return Verdict.Through(Reason.VIP, Kind.CALL, vip.name)
        val key = number?.let { Phone.key(it) }?.takeIf { it.isNotEmpty() } ?: return Verdict.Hold(who, Kind.CALL)
        val count = tracker.register("tel:$key", now, cfg.repeatWindowMin * 60_000L)
        if (cfg.repeatCallers && count >= 2) return Verdict.Through(Reason.REPEAT_CALLER, Kind.CALL, who)
        return Verdict.Hold(who, Kind.CALL)
    }

    fun isCall(n: Incoming): Boolean {
        if (n.category == CATEGORY_CALL) return true
        val t = n.text?.lowercase() ?: return false
        return CALL_WORDS.any { t.startsWith(it) }
    }

    fun matchVip(cfg: Config, n: Incoming): Vip? {
        if (cfg.vips.isEmpty()) return null
        val numbers = n.people.filter { it.startsWith("tel:") }.map { it.removePrefix("tel:") }.toMutableList()
        val names = mutableListOf<String>()

        if (n.isGroupConversation) {
            if (!cfg.vipInGroups) return null
            n.lastSender?.let { names += it }
            // "Rahul @ Family" / "Family: Rahul" title shapes some apps use
            n.title?.let { t ->
                t.substringBefore(" @ ", "").takeIf { it.isNotBlank() }?.let { names += it }
                t.substringAfter(": ", "").takeIf { it.isNotBlank() }?.let { names += it }
            }
        } else {
            n.title?.let { names += it }
            n.lastSender?.let { names += it }
            n.conversationTitle?.let { names += it }
            n.people.filter { it.startsWith("name:") }.forEach { names += it.removePrefix("name:") }
        }
        // An unsaved sender shows up as a phone number in the title.
        names.filter { Phone.looksLikeNumber(it) }.forEach { numbers += it }

        return cfg.vips.firstOrNull { v ->
            numbers.any { num -> v.numbers.any { Phone.same(it, num) } } ||
                names.any { Names.same(v.name, it) }
        }
    }

    private fun callerKey(n: Incoming): String? {
        n.people.firstOrNull { it.startsWith("tel:") }?.let { return "tel:" + Phone.key(it.removePrefix("tel:")) }
        val t = n.title?.takeIf { it.isNotBlank() } ?: return null
        if (Phone.looksLikeNumber(t)) return "tel:" + Phone.key(t)
        return "name:" + Names.norm(t)
    }

    private fun displayWho(n: Incoming): String =
        n.title?.takeIf { it.isNotBlank() } ?: n.conversationTitle ?: n.lastSender ?: ""
}

/** Counts calls per caller inside a sliding window ("same person called twice in 15 min"). */
class RepeatTracker {
    private val calls = HashMap<String, MutableList<Long>>()

    /** Records a call and returns how many calls this caller made inside the window, including this one. */
    @Synchronized
    fun register(key: String, now: Long, windowMs: Long): Int {
        val list = calls.getOrPut(key) { mutableListOf() }
        list.removeAll { now - it > windowMs }
        list += now
        // keep the map small
        if (calls.size > 200) calls.entries.removeAll { e -> e.value.all { now - it > windowMs } }
        return list.size
    }

    @Synchronized fun clear() = calls.clear()
}

object Phone {
    fun digits(s: String) = s.filter { it.isDigit() }

    /** Last 10 digits: drops +91 / 0 trunk prefixes so "+91 98765 43210" == "098765 43210". */
    fun key(s: String): String = digits(s).let { if (it.length > 10) it.takeLast(10) else it }

    fun same(a: String, b: String): Boolean {
        val ka = key(a); val kb = key(b)
        return ka.length >= 5 && ka == kb
    }

    fun looksLikeNumber(s: String): Boolean {
        val d = digits(s)
        return d.length >= 6 && s.all { it.isDigit() || it in "+-() ." || it.isWhitespace() }
    }
}

object Names {
    private val COUNT_SUFFIX = Regex("""\s*\((\d+\s+\w+|\d+)\)\s*$""")

    fun norm(s: String): String {
        val noCount = s.replace(COUNT_SUFFIX, "")
        val letters = noCount.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim().replace(Regex("\\s+"), " ")
        return letters.ifEmpty { noCount.trim().lowercase() }
    }

    fun same(a: String, b: String): Boolean {
        val na = norm(a); val nb = norm(b)
        return na.isNotEmpty() && na == nb
    }
}
