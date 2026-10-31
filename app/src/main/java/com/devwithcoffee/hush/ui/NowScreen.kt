package com.devwithcoffee.hush.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devwithcoffee.hush.core.Clock
import com.devwithcoffee.hush.core.Event
import com.devwithcoffee.hush.core.Kind
import com.devwithcoffee.hush.core.Session
import com.devwithcoffee.hush.engine.AppNames
import com.devwithcoffee.hush.engine.Alerter
import com.devwithcoffee.hush.engine.Hush
import com.devwithcoffee.hush.engine.Notifs
import com.devwithcoffee.hush.engine.Store
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun NowScreen(tick: Int, goTo: (Int) -> Unit) {
    val ctx = LocalContext.current
    val session by Store.session.collectAsState()
    val last by Store.lastSession.collectAsState()
    val cfg by Store.config.collectAsState()
    val ready = remember(tick) { Setup.coreReady(ctx) }
    var minutes by remember { mutableIntStateOf(60) }
    var untilPick by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(Clock.now()) }
    LaunchedEffect(session.active) { while (session.active) { now = Clock.now(); delay(1000) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hush", style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (session.active) "Only what matters gets through" else greeting(),
                    color = C.muted,
                )
            }
            if (session.active) Pill("ON", C.bg, C.moon)
        }

        if (!ready) {
            Spacer(Modifier.height(16.dp))
            Panel(accent = C.amber, onClick = { goTo(3) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = C.amber)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Finish setup first", fontWeight = FontWeight.SemiBold)
                        Text("Hush needs notification + Do Not Disturb access.", color = C.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = C.muted)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Hero(session, now, enabled = ready) { Hush.startFor(ctx, minutes) }
        Spacer(Modifier.height(24.dp))

        AnimatedContent(session.active, label = "controls") { on ->
            if (!on) {
                Column {
                    Text("For how long?", color = C.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30 to "30m", 60 to "1h", 120 to "2h", 0 to "∞").forEach { (m, l) ->
                            DurationChip(l, minutes == m, Modifier.weight(1f)) { minutes = m }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { untilPick = true }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Hush until a time…")
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (session.endsAt != 0L) OutlinedButton(onClick = { Hush.extend(ctx, 15) }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("15 min")
                    }
                    Button(
                        onClick = { Hush.stop(ctx, byUser = true) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.moon),
                    ) { Icon(Icons.Rounded.WbSunny, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("I'm back", fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        if (session.active) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Held", session.held.size, C.moon, Modifier.weight(1f))
                Stat("Got through", session.through.size, C.amber, Modifier.weight(1f))
                Stat("Apps", session.held.map { it.pkg }.distinct().size, C.muted, Modifier.weight(1f))
            }
        }

        SectionTitle("Who can reach you")
        Panel(onClick = { goTo(1) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp), modifier = Modifier.weight(1f)) {
                    cfg.allowedApps.take(4).forEach { AppIcon(it, 34.dp) }
                    cfg.vips.take(4).forEach { Initials(it.name, 34.dp) }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = C.muted)
            }
            Spacer(Modifier.height(10.dp))
            val apps = cfg.allowedApps.size; val ppl = cfg.vips.size
            Text(
                "$apps app${if (apps == 1) "" else "s"} · $ppl ${if (ppl == 1) "person" else "people"}" +
                    if (cfg.repeatCallers) " · repeat callers" else "",
                fontWeight = FontWeight.Medium,
            )
            if (ppl == 0) Text("Add the people who must always reach you →", color = C.amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }

        val feed = if (session.active) session else last
        if (feed != null && feed.events.isNotEmpty()) {
            SectionTitle(if (session.active) "This session" else "Last session", feedSub(feed))
            Feed(feed)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (untilPick) TimeDialog("Hush until", defaultUntil(), onDismiss = { untilPick = false }) { h, m ->
        untilPick = false
        val c = Calendar.getInstance().apply { timeInMillis = Clock.now(); set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }
        if (c.timeInMillis <= Clock.now()) c.add(Calendar.DAY_OF_YEAR, 1)
        Hush.start(ctx, c.timeInMillis)
    }
}

private fun defaultUntil(): Int {
    val c = Calendar.getInstance().apply { timeInMillis = Clock.now() }; c.add(Calendar.HOUR_OF_DAY, 1)
    return c.get(Calendar.HOUR_OF_DAY) * 60 + (c.get(Calendar.MINUTE) / 15) * 15
}

private fun greeting(): String = when (Calendar.getInstance().apply { timeInMillis = Clock.now() }.get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning. Need some quiet?"
    in 12..16 -> "Good afternoon. Nap time?"
    in 17..21 -> "Good evening. Wind down?"
    else -> "Late one. Time to rest?"
}

private fun feedSub(s: Session): String {
    val start = Notifs.timeOf(s.startedAt)
    return if (s.active) "Since $start" else "$start – ${Notifs.timeOf(s.endsAt)}"
}

@Composable
private fun Hero(session: Session, now: Long, enabled: Boolean, onStart: () -> Unit) {
    val on = session.active
    val pulse = rememberInfiniteTransition(label = "pulse")
    val breathe by pulse.animateFloat(1f, 1.04f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val total = (session.endsAt - session.startedAt).coerceAtLeast(1)
    val left = (session.endsAt - now).coerceAtLeast(0)
    val frac by animateFloatAsState(if (!on) 0f else if (session.endsAt == 0L) 1f else left / total.toFloat(), tween(600), label = "f")
    val ringColor by animateColorAsState(if (on) C.moon else C.line, label = "rc")

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(250.dp).scale(if (on) breathe else 1f).clip(CircleShape)
                .clickable(enabled = enabled && !on, interactionSource = remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple()) { onStart() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                drawCircle(
                    Brush.radialGradient(listOf(if (on) C.moonSoft else C.surfaceHi, C.bg), center = Offset(size.width / 2, size.height / 2), radius = size.minDimension / 2),
                )
                drawArc(C.surfaceHi, 0f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
                drawArc(ringColor, -90f, 360f * frac, false, style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!on) {
                    Icon(Icons.Rounded.Bedtime, null, tint = if (enabled) C.moon else C.muted, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Tap to hush", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) C.text else C.muted)
                } else if (session.endsAt == 0L) {
                    Icon(Icons.Rounded.Bedtime, null, tint = C.moon, modifier = Modifier.size(44.dp))
                    Text("Hushed", fontSize = 30.sp, fontWeight = FontWeight.Light)
                    Text("until you're back", color = C.muted)
                } else {
                    val mins = (left + 59_999) / 60_000
                    Text(if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins", fontSize = if (mins >= 60) 40.sp else 64.sp, fontWeight = FontWeight.Light)
                    Text(if (mins >= 60) "left" else "min left", color = C.muted)
                    Spacer(Modifier.height(4.dp))
                    Text("until ${Notifs.timeOf(session.endsAt)}", color = C.moon, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun DurationChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) C.moon else C.surface, label = "bg")
    val fg by animateColorAsState(if (selected) C.bg else C.text, label = "fg")
    Box(
        modifier.height(48.dp).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
private fun Stat(label: String, value: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Panel(modifier) {
        Text("$value", fontSize = 28.sp, fontWeight = FontWeight.Light, color = color)
        Text(label, color = C.muted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Feed(s: Session) {
    val ctx = LocalContext.current
    Panel {
        val through = s.through.reversed()
        through.forEach { e -> FeedRow(e, through = true) }
        if (through.isNotEmpty() && s.held.isNotEmpty()) HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 6.dp))
        s.held.groupBy { it.pkg }.entries.sortedByDescending { it.value.size }.forEach { (pkg, list) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (pkg == "phone") Initials("Phone", 32.dp) else AppIcon(pkg, 32.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(AppNames.label(ctx, pkg), fontWeight = FontWeight.Medium)
                    val who = list.map { it.who }.filter { it.isNotBlank() }.distinct().take(3).joinToString(", ")
                    if (who.isNotEmpty()) Text(who, color = C.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Pill("${list.size} held", C.muted, C.surfaceHi)
            }
        }
    }
}

@Composable
private fun FeedRow(e: Event, through: Boolean) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(C.amberSoft), contentAlignment = Alignment.Center) {
            Icon(
                when (e.kind) { Kind.CALL -> Icons.Rounded.Call; Kind.MESSAGE -> Icons.Rounded.ChatBubble; Kind.APP -> Icons.Rounded.Work },
                null, tint = C.amber, modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.who.ifBlank { AppNames.label(ctx, e.pkg) }, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${AppNames.label(ctx, e.pkg)} · ${e.reason?.let { Alerter.reasonText(it) } ?: ""}",
                color = C.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
            )
        }
        Text(Notifs.timeOf(e.at), color = C.muted, style = MaterialTheme.typography.labelSmall)
    }
}

/** A time picker in a dialog; returns hour + minute. [initialMin] is minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, initialMin: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialMin / 60 % 24, initialMin % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.surface,
        title = { Text(title) },
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = C.surfaceHi, selectorColor = C.moon,
                    timeSelectorSelectedContainerColor = C.moonSoft, timeSelectorUnselectedContainerColor = C.surfaceHi,
                    periodSelectorSelectedContainerColor = C.moonSoft, periodSelectorBorderColor = C.line,
                ),
            )
        },
        confirmButton = { TextButton({ onPick(state.hour, state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
