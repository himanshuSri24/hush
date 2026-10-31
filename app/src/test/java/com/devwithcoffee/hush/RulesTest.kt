package com.devwithcoffee.hush

import com.devwithcoffee.hush.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class RulesTest {
    private val own = "com.devwithcoffee.hush"
    private val mom = Vip("1", "Mom ❤️", listOf("+91 98765 43210"))
    private val rahul = Vip("2", "Rahul Sharma", emptyList())
    private val cfg = Config(vips = listOf(mom, rahul))
    private val t0 = 1_700_000_000_000L

    private fun decide(n: Incoming, c: Config = cfg, tr: RepeatTracker = RepeatTracker(), now: Long = t0) =
        Rules.decide(c, n, tr, now, own)

    @Test fun allowedWorkAppsBreakThrough() {
        for (pkg in listOf("com.Slack", "com.microsoft.teams", "com.microsoft.office.outlook")) {
            val v = decide(Incoming(pkg, title = "Priya", text = "standup?"))
            assertTrue("$pkg should get through", v is Verdict.Through)
            assertEquals(Reason.ALLOWED_APP, (v as Verdict.Through).reason)
        }
    }

    @Test fun teamsCallRingsAsCall() {
        val v = decide(Incoming("com.microsoft.teams", title = "Priya", text = "Incoming call", category = "call", ongoing = true))
        assertEquals(Kind.CALL, (v as Verdict.Through).kind)
    }

    @Test fun otherAppsAreHeld() {
        assertTrue(decide(Incoming("com.instagram.android", title = "Rahul Sharma", text = "liked your reel")) is Verdict.Hold)
        assertTrue(decide(Incoming("com.swiggy.android", title = "Order", text = "on the way")) is Verdict.Hold)
    }

    @Test fun whatsappMessageFromVipByName() {
        val v = decide(Incoming("com.whatsapp", title = "Rahul Sharma", text = "you up?"))
        assertEquals(Reason.VIP, (v as Verdict.Through).reason)
        assertEquals(Kind.MESSAGE, v.kind)
    }

    @Test fun nameMatchIgnoresEmojiCaseAndCounts() {
        assertTrue(decide(Incoming("com.whatsapp", title = "mom ❤️ (3 messages)")) is Verdict.Through)
        assertTrue(decide(Incoming("com.whatsapp", title = "MOM")) is Verdict.Through)
        assertTrue(decide(Incoming("com.whatsapp", title = "Momo")) is Verdict.Hold)
    }

    @Test fun whatsappCallFromVip() {
        val v = decide(Incoming("com.whatsapp", title = "Mom ❤️", text = "Incoming voice call", category = "call", ongoing = true))
        assertEquals(Kind.CALL, (v as Verdict.Through).kind)
        assertEquals(Reason.VIP, v.reason)
    }

    @Test fun smsFromVipByNumberWhenUnsaved() {
        val v = decide(Incoming("com.google.android.apps.messaging", title = "098765 43210", text = "call me"))
        assertEquals("Mom ❤️", (v as Verdict.Through).who)
    }

    @Test fun smsFromVipByPersonUri() {
        val v = decide(Incoming("com.google.android.apps.messaging", title = "Some name", people = listOf("tel:9876543210")))
        assertTrue(v is Verdict.Through)
    }

    @Test fun groupMessagesFromVipOnlyWhenEnabled() {
        val n = Incoming("com.whatsapp", title = "Family", isGroupConversation = true, conversationTitle = "Family", lastSender = "Rahul Sharma")
        assertTrue(decide(n) is Verdict.Hold)
        assertTrue(decide(n, cfg.copy(vipInGroups = true)) is Verdict.Through)
    }

    @Test fun groupNamedLikeVipDoesNotLeakThrough() {
        // A group called "Rahul Sharma" with someone else talking must not count.
        val n = Incoming("com.whatsapp", title = "Rahul Sharma", isGroupConversation = true, conversationTitle = "Rahul Sharma", lastSender = "Amit")
        assertTrue(decide(n, cfg.copy(vipInGroups = true)) is Verdict.Hold)
    }

    @Test fun repeatWhatsappCallerGetsThroughOnSecondCall() {
        val tr = RepeatTracker()
        val call = Incoming("com.whatsapp", title = "Boss", text = "Incoming voice call", category = "call")
        assertTrue(decide(call, tr = tr, now = t0) is Verdict.Hold)
        val second = decide(call, tr = tr, now = t0 + 5 * 60_000)
        assertEquals(Reason.REPEAT_CALLER, (second as Verdict.Through).reason)
    }

    @Test fun repeatWindowExpires() {
        val tr = RepeatTracker()
        val call = Incoming("com.whatsapp", title = "Boss", category = "call")
        decide(call, tr = tr, now = t0)
        assertTrue(decide(call, tr = tr, now = t0 + 16 * 60_000) is Verdict.Hold)
    }

    @Test fun repeatCallersCanBeTurnedOff() {
        val tr = RepeatTracker(); val c = cfg.copy(repeatCallers = false)
        val call = Incoming("com.whatsapp", title = "Boss", category = "call")
        decide(call, c, tr, t0)
        assertTrue(decide(call, c, tr, t0 + 60_000) is Verdict.Hold)
    }

    @Test fun phoneCallVipByNumber() {
        val v = Rules.decideCall(cfg, "09876543210", null, RepeatTracker(), t0)
        assertEquals(Reason.VIP, (v as Verdict.Through).reason)
    }

    @Test fun phoneCallVipByContactName() {
        val v = Rules.decideCall(cfg, "+1 555 0100", "Rahul Sharma", RepeatTracker(), t0)
        assertTrue(v is Verdict.Through)
    }

    @Test fun phoneCallStrangerHeldThenRepeatGetsThrough() {
        val tr = RepeatTracker()
        assertTrue(Rules.decideCall(cfg, "+91 99999 11111", null, tr, t0) is Verdict.Hold)
        val v = Rules.decideCall(cfg, "9999911111", null, tr, t0 + 120_000)
        assertEquals(Reason.REPEAT_CALLER, (v as Verdict.Through).reason)
    }

    @Test fun dialerCallNotificationIgnoredWhenPhonePathActive() {
        val n = Incoming("com.google.android.dialer", title = "Mom ❤️", category = "call", ongoing = true)
        assertTrue(decide(n) is Verdict.Ignore)
        assertTrue(Rules.decide(cfg, n, RepeatTracker(), t0, own, phonePathActive = false) is Verdict.Through)
    }

    @Test fun ignoresNoise() {
        assertTrue(decide(Incoming("com.spotify.music", title = "Song", ongoing = true)) is Verdict.Ignore)
        assertTrue(decide(Incoming("com.whatsapp", title = "5 new messages", groupSummary = true)) is Verdict.Ignore)
        assertTrue(decide(Incoming(own, title = "x")) is Verdict.Ignore)
        assertTrue(decide(Incoming("com.Slack", title = "sync", importance = 2)) is Verdict.Ignore)
    }

    @Test fun phoneNumberEquivalence() {
        assertTrue(Phone.same("+91 98765-43210", "098765 43210"))
        assertFalse(Phone.same("123", "123"))
        assertTrue(Phone.looksLikeNumber("+91 98765 43210"))
        assertFalse(Phone.looksLikeNumber("Rahul 2"))
    }

    @Test fun configRoundTrip() {
        val c = cfg.copy(allowedApps = setOf("a.b"), repeatWindowMin = 10, vipInGroups = true)
        assertEquals(c, Config.fromJson(c.toJson()))
        val s = Session(true, 1, 2, "manual", listOf(Event(3, "p", "w", true, Kind.CALL, Reason.VIP), Event(4, "q", "", false, Kind.APP)))
        assertEquals(s, Session.fromJson(s.toJson()))
    }

    // --- schedules ---
    private val nap = Schedule("n", "Nap", setOf(1, 2, 3, 4, 5), 13 * 60 + 30, 14 * 60 + 15)
    private val night = Schedule("z", "Night", (1..7).toSet(), 23 * 60, 7 * 60)

    @Test fun scheduleActiveWindow() {
        val wedNap = LocalDateTime.of(2026, 11, 4, 13, 45) // Wednesday
        assertEquals("n", Schedules.activeAt(listOf(nap), wedNap)?.schedule?.id)
        assertNull(Schedules.activeAt(listOf(nap), wedNap.withHour(15)))
        assertNull(Schedules.activeAt(listOf(nap), LocalDateTime.of(2026, 11, 7, 13, 45))) // Saturday
    }

    @Test fun overnightWindowSpansMidnight() {
        val early = LocalDateTime.of(2026, 11, 4, 3, 0)
        val w = Schedules.activeAt(listOf(night), early)!!
        assertEquals(LocalDateTime.of(2026, 11, 4, 7, 0), w.end)
    }

    @Test fun nextStartSkipsWeekend() {
        val fri = LocalDateTime.of(2026, 11, 6, 15, 0)
        assertEquals(LocalDateTime.of(2026, 11, 9, 13, 30), Schedules.nextStart(listOf(nap), fri)!!.start)
    }

    @Test fun disabledScheduleNeverFires() {
        val off = nap.copy(enabled = false)
        assertNull(Schedules.nextStart(listOf(off), LocalDateTime.of(2026, 11, 4, 9, 0)))
    }
}

class ThrottleTest {
    @Test fun firstAlertSoundsRepeatWithinWindowDoesNot() {
        val th = Throttle(8_000)
        org.junit.Assert.assertTrue(th.allow("wa|Mom|MESSAGE", 0))
        org.junit.Assert.assertFalse(th.allow("wa|Mom|MESSAGE", 3_000))
    }

    @Test fun suppressedAlertsDoNotExtendTheWindow() {
        // The 1.0 bug: tapping every few seconds kept the chime silent forever.
        val th = Throttle(8_000)
        th.allow("k", 0)
        th.allow("k", 5_000)          // suppressed
        org.junit.Assert.assertTrue(th.allow("k", 8_500))
    }

    @Test fun differentSendersAreIndependent() {
        val th = Throttle(8_000)
        th.allow("wa|Mom|MESSAGE", 0)
        org.junit.Assert.assertTrue(th.allow("wa|Arjun|MESSAGE", 100))
    }
}
