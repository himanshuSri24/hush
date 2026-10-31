package com.devwithcoffee.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import com.devwithcoffee.hush.core.Schedule
import com.devwithcoffee.hush.core.Schedules
import com.devwithcoffee.hush.engine.Hush
import com.devwithcoffee.hush.engine.Store
import java.util.UUID

private val TEMPLATES = listOf(
    Schedule("", "Afternoon nap", setOf(1, 2, 3, 4, 5), 13 * 60 + 30, 14 * 60 + 15),
    Schedule("", "Night", (1..7).toSet(), 23 * 60, 7 * 60),
    Schedule("", "Focus block", setOf(1, 2, 3, 4, 5), 10 * 60, 12 * 60),
)

@Composable
fun ScheduleScreen() {
    val ctx = LocalContext.current
    val list by Store.schedules.collectAsState()
    var editing by remember { mutableStateOf<Schedule?>(null) }

    fun save(s: Schedule) {
        Store.updateSchedules { l -> if (l.any { it.id == s.id }) l.map { if (it.id == s.id) s else it } else l + s }
        Hush.scheduleAlarms(ctx); Hush.reconcile(ctx)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Schedule", style = MaterialTheme.typography.headlineMedium)
        Text("Hush turns on and off by itself at these times.", color = C.muted, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))

        list.forEach { s ->
            Panel(Modifier.padding(bottom = 10.dp), onClick = { editing = s }, accent = if (s.enabled) C.moon else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.label, fontWeight = FontWeight.SemiBold)
                        Text("${Schedules.fmt(s.startMin)} – ${Schedules.fmt(s.endMin)}", fontSize = 22.sp, fontWeight = FontWeight.Light,
                            color = if (s.enabled) C.text else C.muted, modifier = Modifier.padding(vertical = 2.dp))
                        Text(Schedules.describeDays(s.days) + if (s.overnight) " · overnight" else "", color = C.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(s.enabled, { on -> save(s.copy(enabled = on)) })
                }
            }
        }

        if (list.isEmpty()) {
            SectionTitle("Start from one of these")
            TEMPLATES.forEach { t ->
                Panel(Modifier.padding(bottom = 10.dp), onClick = { editing = t.copy(id = UUID.randomUUID().toString()) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (t.label == "Night") Icons.Rounded.NightsStay else if (t.label.startsWith("Focus")) Icons.Rounded.CenterFocusStrong else Icons.Rounded.Bedtime, null, tint = C.moon)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.label, fontWeight = FontWeight.Medium)
                            Text("${Schedules.describeDays(t.days)} · ${Schedules.fmt(t.startMin)} – ${Schedules.fmt(t.endMin)}", color = C.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Rounded.Add, null, tint = C.muted)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            { editing = Schedule(UUID.randomUUID().toString(), "Quiet time", setOf(1, 2, 3, 4, 5), 13 * 60, 14 * 60) },
            Modifier.fillMaxWidth().height(52.dp),
        ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add quiet time") }
        Spacer(Modifier.height(32.dp))
    }

    editing?.let { s ->
        EditScheduleDialog(
            s, isNew = list.none { it.id == s.id },
            onDismiss = { editing = null },
            onDelete = {
                Store.updateSchedules { l -> l.filter { it.id != s.id } }
                Hush.scheduleAlarms(ctx); editing = null
            },
            onSave = { save(it); editing = null },
        )
    }
}

@Composable
private fun EditScheduleDialog(initial: Schedule, isNew: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (Schedule) -> Unit) {
    var s by remember { mutableStateOf(initial) }
    var pick by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.surface,
        title = { Text(if (isNew) "New quiet time" else "Edit quiet time") },
        text = {
            Column {
                OutlinedTextField(s.label, { s = s.copy(label = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeBox("Starts", s.startMin, Modifier.weight(1f)) { pick = "start" }
                    TimeBox("Ends", s.endMin, Modifier.weight(1f)) { pick = "end" }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                        val d = i + 1; val on = d in s.days
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(if (on) C.moon else C.surfaceHi)
                                .clickable { s = s.copy(days = if (on) s.days - d else s.days + d) },
                            contentAlignment = Alignment.Center,
                        ) { Text(l, color = if (on) C.bg else C.muted, fontWeight = FontWeight.SemiBold) }
                    }
                }
                if (s.overnight) Text("Ends the next morning", color = C.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
        },
        confirmButton = { TextButton({ onSave(s) }, enabled = s.days.isNotEmpty() && s.startMin != s.endMin) { Text("Save") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onDelete) { Text("Delete", color = C.bad) }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
    pick?.let { which ->
        TimeDialog(if (which == "start") "Starts at" else "Ends at", if (which == "start") s.startMin else s.endMin, { pick = null }) { h, m ->
            s = if (which == "start") s.copy(startMin = h * 60 + m) else s.copy(endMin = h * 60 + m); pick = null
        }
    }
}

@Composable
private fun TimeBox(label: String, min: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(C.surfaceHi).clickable(onClick = onClick).padding(12.dp)) {
        Text(label, color = C.muted, style = MaterialTheme.typography.labelMedium)
        Text(Schedules.fmt(min), fontSize = 20.sp, fontWeight = FontWeight.Medium)
    }
}
