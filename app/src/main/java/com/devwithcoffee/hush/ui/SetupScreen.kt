package com.devwithcoffee.hush.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devwithcoffee.hush.core.Kind
import com.devwithcoffee.hush.core.Reason
import com.devwithcoffee.hush.engine.Alerter
import com.devwithcoffee.hush.engine.Hush
import com.devwithcoffee.hush.engine.HushListener
import com.devwithcoffee.hush.engine.Store

data class SetupItem(
    val id: String,
    val title: String,
    val why: String,
    val required: Boolean,
    val done: Boolean,
    val perms: Array<String>? = null,
    val intent: Intent? = null,
)

object Setup {
    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun coreReady(ctx: Context) = Hush.hasListenerAccess(ctx) && Hush.hasDndAccess(ctx)

    fun items(ctx: Context): List<SetupItem> {
        val pkg = ctx.packageName
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val list = mutableListOf(
            SetupItem(
                "listener", "Notification access",
                "Lets Hush see who a notification is from, so it can hold it or let it through.",
                required = true, done = Hush.hasListenerAccess(ctx),
                intent = if (Build.VERSION.SDK_INT >= 30)
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            ComponentName(ctx, HushListener::class.java).flattenToString())
                else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            ),
            SetupItem(
                "dnd", "Do Not Disturb access",
                "Hush silences the phone by switching on Do Not Disturb (alarms only) and turns it back off after.",
                required = true, done = nm.isNotificationPolicyAccessGranted,
                intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            ),
            SetupItem(
                "phone", "Phone calls",
                "Reads the caller's number so normal calls from your people ring, and repeat callers are spotted.",
                required = true, done = HushListener.phonePathActive(ctx),
                perms = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG),
            ),
            SetupItem(
                "contacts", "Contacts",
                "To pick your people and match a caller's number to their name.",
                required = false, done = granted(ctx, Manifest.permission.READ_CONTACTS),
                perms = arrayOf(Manifest.permission.READ_CONTACTS),
            ),
        )
        if (Build.VERSION.SDK_INT >= 33) list += SetupItem(
            "post", "Show alerts",
            "Hush needs to post its own alert when someone gets through.",
            required = true, done = granted(ctx, Manifest.permission.POST_NOTIFICATIONS),
            perms = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        )
        if (Build.VERSION.SDK_INT >= 34) list += SetupItem(
            "fsi", "Full-screen call alerts",
            "When your person calls on WhatsApp, Hush lights up the screen like a real call.",
            required = false, done = nm.canUseFullScreenIntent(),
            intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")),
        )
        list += SetupItem(
            "battery", "Never put Hush to sleep",
            "Some phones kill background apps. This keeps Hush awake so it never misses a call.",
            required = false, done = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg),
            intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
        )
        return list
    }
}

@Composable
fun SetupScreen(tick: Int) {
    val ctx = LocalContext.current
    var localTick by remember { mutableIntStateOf(0) }
    val items = remember(tick, localTick) { Setup.items(ctx) }
    val cfg by Store.config.collectAsState()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        localTick++
        HushListener.instance?.registerPhone()
    }
    val done = items.count { it.done }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Setup", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (items.all { it.done || !it.required }) "Hush is ready. Optional steps make it bulletproof."
            else "A few switches so Hush can do its job.",
            color = C.muted, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            progress = { done / items.size.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = C.ok, trackColor = C.surfaceHi, drawStopIndicator = {},
        )
        Text("$done of ${items.size} done", color = C.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))

        SectionTitle("Permissions")
        Panel {
            items.forEachIndexed { i, it ->
                SetupRow(it) {
                    Store.onboarded = true
                    if (it.perms != null) permLauncher.launch(it.perms)
                    else it.intent?.let { intent -> runCatching { ctx.startActivity(intent) } }
                }
                if (i != items.lastIndex) HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        SectionTitle("Try it", "Plays exactly what you'll hear when someone gets through")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                Alerter.alert(ctx, Kind.MESSAGE, "Test message", "Hush", Reason.VIP, null, null)
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.ChatBubble, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Message")
            }
            OutlinedButton(onClick = {
                val id = Alerter.alert(ctx, Kind.CALL, "Test call", "Hush", Reason.VIP, "test-call", null)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ Alerter.silence(ctx, id) }, 6_000)
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Call (6s)")
            }
        }

        SectionTitle("Alert sound")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeUp, null, tint = C.muted)
                Spacer(Modifier.width(12.dp))
                Slider(
                    value = cfg.alertVolume, onValueChange = { v -> Store.updateConfig { it.copy(alertVolume = v) } },
                    valueRange = 0.2f..1f, modifier = Modifier.weight(1f),
                )
                Text("${(cfg.alertVolume * 100).toInt()}%", color = C.muted, modifier = Modifier.width(44.dp).padding(start = 8.dp))
            }
            HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrate too", modifier = Modifier.weight(1f))
                Switch(cfg.vibrate, { v -> Store.updateConfig { it.copy(vibrate = v) } })
            }
        }

        SectionTitle("Quick Settings tile", "Swipe down, tap Hush. It runs for:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(30 to "30m", 60 to "1h", 120 to "2h", 0 to "Until off").forEach { (m, l) ->
                FilterChip(cfg.tileMinutes == m, { Store.updateConfig { it.copy(tileMinutes = m) } }, { Text(l) })
            }
        }

        SectionTitle("How Hush works")
        Panel {
            HowLine(Icons.Rounded.DoNotDisturbOn, "While Hush is on, your phone is in Do Not Disturb (alarms only). Your nap alarm still rings.")
            HowLine(Icons.Rounded.NotificationsActive, "When an allowed app or one of your people pings, Hush rings for them on the alarm channel, so the system can't swallow it.")
            HowLine(Icons.Rounded.Repeat, "Anyone who calls twice within a few minutes gets through. That's usually an emergency.")
            HowLine(Icons.Rounded.Inbox, "Everything else waits quietly in your shade. Hush shows you a summary when you're back.")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SetupRow(item: SetupItem, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (item.done) C.ok.copy(alpha = 0.15f) else if (item.required) C.amberSoft else C.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (item.done) Icons.Rounded.Check else if (item.required) Icons.Rounded.PriorityHigh else Icons.Rounded.Add,
                null, tint = if (item.done) C.ok else if (item.required) C.amber else C.muted, modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold)
            if (!item.required && !item.done) Text("Recommended", color = C.amber, style = MaterialTheme.typography.labelSmall)
            Text(item.why, color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
        if (!item.done) {
            Spacer(Modifier.width(10.dp))
            FilledTonalButton(onClick = onFix, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Allow") }
        }
    }
}

@Composable
private fun HowLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Icon(icon, null, tint = C.moon, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = C.text.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
    }
}
