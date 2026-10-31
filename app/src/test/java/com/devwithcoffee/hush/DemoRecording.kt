package com.devwithcoffee.hush

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devwithcoffee.hush.core.*
import com.devwithcoffee.hush.engine.HushListener
import com.devwithcoffee.hush.engine.Store
import com.devwithcoffee.hush.ui.AlertActivity
import com.devwithcoffee.hush.ui.MainActivity
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.Calendar

/**
 * Records the real app UI frame by frame (30 fps of virtual time) for the demo video.
 * Not a unit test: skipped unless run with -PhushDemo=1.
 */
@OptIn(ExperimentalRoborazziApi::class, ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = [34], qualifiers = "w392dp-h850dp-xhdpi")
class DemoRecording {
    @get:Rule val rule = createEmptyComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<Application>()
    private val outRoot = File(System.getProperty("hush.out") ?: "build/demo")
    private lateinit var out: File
    private var frame = 0
    private val marks = JSONArray()
    private val base = Calendar.getInstance().apply { set(2026, 10, 4, 14, 1, 0) }.timeInMillis
    private var skew = 0L

    @Before fun setUp() {
        assumeTrue(System.getProperty("hush.demo").orEmpty().isNotBlank())
        Clock.now = { base + skew + rule.mainClock.currentTime }
        installApp("com.Slack", "Slack", 0xFF4A154B.toInt(), "S")
        installApp("com.microsoft.teams", "Teams", 0xFF5059C9.toInt(), "T")
        installApp("com.microsoft.office.outlook", "Outlook", 0xFF0F6CBD.toInt(), "O")
        installApp("com.whatsapp", "WhatsApp", 0xFF25D366.toInt(), "W")
        installApp("com.instagram.android", "Instagram", 0xFFE4405F.toInt(), "I")
        installApp("in.swiggy.android", "Swiggy", 0xFFFC8019.toInt(), "S")
        installApp("com.linkedin.android", "LinkedIn", 0xFF0A66C2.toInt(), "in")
        installApp("com.google.android.gm", "Gmail", 0xFFEA4335.toInt(), "M")
        shadowOf(ctx).grantPermissions(
            Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS,
        )
        shadowOf(ctx.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(true)
        Settings.Secure.putString(ctx.contentResolver, "enabled_notification_listeners",
            ComponentName(ctx, HushListener::class.java).flattenToString())
        shadowOf(ctx.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(ctx.packageName, true)
        Store.init(ctx)
        Store.onboarded = true
        Store.updateConfig { Config(vips = listOf(Vip("1", "Mom", listOf("+91 98765 43210")), Vip("2", "Arjun (manager)", listOf("+91 99887 76655")))) }
        Store.updateSchedules { emptyList() }
        rule.mainClock.autoAdvance = false
    }

    private fun installApp(pkg: String, label: String, color: Int, letter: String) {
        val ai = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label; flags = ApplicationInfo.FLAG_INSTALLED }
        val pi = PackageInfo().apply { packageName = pkg; applicationInfo = ai }
        val pm = shadowOf(ctx.packageManager)
        pm.installPackage(pi)
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawRoundRect(RectF(0f, 0f, 96f, 96f), 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            drawText(letter, 48f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = -1; textSize = 46f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            })
        }
        pm.setApplicationIcon(pkg, BitmapDrawable(ctx.resources, bmp))
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        pm.addResolveInfoForIntent(launcher, android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; applicationInfo = ai; nonLocalizedLabel = label }
        })
    }

    // ---- recording primitives ----
    private fun shoot(n: Int = 1) = repeat(n) {
        rule.mainClock.advanceTimeBy(33)
        captureScreenRoboImage(File(out, "f%05d.png".format(frame++)).path)
    }
    private fun hold(sec: Double) = shoot((sec * 30).toInt())
    private fun mark(kind: String, extra: JSONObject.() -> Unit = {}) = marks.put(JSONObject().put("f", frame).put("k", kind).apply(extra))

    private fun tapNode(node: SemanticsNodeInteraction, label: String) {
        val b = node.fetchSemanticsNode().boundsInWindow
        mark("tap") { put("x", b.center.x.toDouble()); put("y", b.center.y.toDouble()); put("label", label) }
        shoot(5)
        node.performClick()
        shoot(1)
    }
    private fun tap(text: String) = tapNode(rule.onAllNodesWithText(text).onFirst(), text)
    private fun tab(text: String) = tapNode(
        rule.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)), text)

    private fun type(field: String, text: String) {
        val n = rule.onNode(hasSetTextAction() and hasText(field))
        tapNode(n, field)
        for (i in text.indices) { n.performTextInput(text[i].toString()); shoot(2) }
    }

    /** Drag the main window by [dy] px over [frames] frames, slowly (no fling). */
    private fun scroll(dy: Float, frames: Int = 18) {
        val root = rule.onAllNodes(isRoot()).onFirst()
        root.performTouchInput { down(center) }
        mark("drag") { put("dy", dy.toDouble()) }
        repeat(frames) { root.performTouchInput { moveBy(Offset(0f, dy / frames)) }; shoot() }
        repeat(4) { root.performTouchInput { moveBy(Offset(0f, 0f)) }; rule.mainClock.advanceTimeBy(40) }
        root.performTouchInput { up() }
        shoot(6)
    }

    private fun event(pkg: String, who: String, through: Boolean, kind: Kind, reason: Reason? = null) =
        Store.addEvent(Event(Clock.now(), pkg, who, through, kind, reason))

    @Test fun recordApp() {
        out = File(outRoot, "app").apply { deleteRecursively(); mkdirs() }
        ActivityScenario.launch(MainActivity::class.java).use {
            shoot(3)
            mark("chapter") { put("t", "home") }
            hold(1.6)

            // 1. who gets through
            mark("chapter") { put("t", "allowed") }
            tab("Allowed"); hold(1.0)
            tap("Number"); hold(0.5)
            type("Name (as saved in WhatsApp)", "Priya")
            type("Phone number", "98111 22334")
            tap("Add"); hold(1.4)
            scroll(-760f); hold(1.2)
            tap("10m"); hold(0.8)

            // 2. schedule
            mark("chapter") { put("t", "schedule") }
            tab("Schedule"); hold(1.0)
            tap("Afternoon nap"); hold(1.4)
            tap("Save"); hold(1.2)

            // 3. hush on
            mark("chapter") { put("t", "on") }
            tab("Now"); hold(0.8)
            tap("1h"); hold(0.3)
            tap("Tap to hush"); hold(1.6)

            // 4. life happens (events as the listener would record them)
            mark("chapter") { put("t", "events") }
            listOf(
                Triple("com.instagram.android", "rahul.k", Kind.APP), Triple("com.whatsapp", "Family", Kind.MESSAGE),
                Triple("in.swiggy.android", "Hungry?", Kind.APP), Triple("com.linkedin.android", "New job alert", Kind.APP),
                Triple("com.instagram.android", "anya.m", Kind.APP), Triple("com.whatsapp", "College gang", Kind.MESSAGE),
            ).forEach { (p, w, k) -> event(p, w, false, k); hold(0.35) }
            event("com.Slack", "Priya: prod is down", true, Kind.APP, Reason.ALLOWED_APP); mark("through"); hold(1.0)
            event("com.whatsapp", "Mom", true, Kind.CALL, Reason.VIP); mark("through"); hold(1.0)
            scroll(-900f); hold(2.0)
            scroll(900f, 12); hold(0.4)

            // 5. back
            mark("chapter") { put("t", "back") }
            skew += 50 * 60_000L; hold(1.2)
            tap("I'm back"); hold(1.0)
            scroll(-700f); hold(1.8)

            // 6. setup
            mark("chapter") { put("t", "setup") }
            tab("Setup"); hold(1.6)
            scroll(-900f); hold(1.2)
        }
        File(out, "marks.json").writeText(marks.toString(1))
    }

    @Test fun recordAlert() {
        out = File(outRoot, "alert").apply { deleteRecursively(); mkdirs() }
        val id = com.devwithcoffee.hush.engine.Alerter.alert(ctx, Kind.CALL, "Mom", "WhatsApp", Reason.VIP, "demo-call", null)
        val i = AlertActivity.intent(ctx, Kind.CALL, "Mom", "WhatsApp", "Always-allowed person", id)
        ActivityScenario.launch<AlertActivity>(i).use { hold(3.0) }
    }
}
