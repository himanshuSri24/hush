package com.devwithcoffee.hush.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.devwithcoffee.hush.core.Packages
import com.devwithcoffee.hush.core.Phone
import com.devwithcoffee.hush.core.Vip
import com.devwithcoffee.hush.engine.AppInfo
import com.devwithcoffee.hush.engine.AppNames
import com.devwithcoffee.hush.engine.Store
import java.util.UUID

object Contacts {
    fun phonesOf(ctx: Context, contactId: String): List<String> =
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(contactId), null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            ?.distinctBy { Phone.key(it) } ?: emptyList()

    fun fromUri(ctx: Context, uri: Uri): Vip? =
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else Vip(UUID.randomUUID().toString(), c.getString(1) ?: "Contact", phonesOf(ctx, c.getString(0)))
        }

    fun starred(ctx: Context): List<Vip> =
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
            "${ContactsContract.Contacts.STARRED} = 1", null, ContactsContract.Contacts.DISPLAY_NAME,
        )?.use { c ->
            buildList { while (c.moveToNext()) add(Vip(UUID.randomUUID().toString(), c.getString(1) ?: "Contact", phonesOf(ctx, c.getString(0)))) }
        } ?: emptyList()
}

private val SUGGESTED = listOf("com.Slack", "com.microsoft.teams", "com.microsoft.office.outlook", "com.google.android.gm", "com.google.android.calendar", "com.whatsapp")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowedScreen(tick: Int) {
    val ctx = LocalContext.current
    val cfg by Store.config.collectAsState()
    var pickApps by remember { mutableStateOf(false) }
    var addByHand by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(toast) { toast?.let { snack.showSnackbar(it); toast = null } }

    fun addVips(list: List<Vip>) {
        val existing = cfg.vips
        val fresh = list.filter { v -> existing.none { e -> e.name == v.name || e.numbers.any { n -> v.numbers.any { Phone.same(n, it) } } } }
        Store.updateConfig { it.copy(vips = it.vips + fresh) }
        toast = if (fresh.isEmpty()) "Already in your list" else "Added ${fresh.joinToString { it.name }}"
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let { Contacts.fromUri(ctx, it) }?.let { addVips(listOf(it)) }
    }
    var pendingStarred by remember { mutableStateOf(false) }
    val contactsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) toast = "Contacts permission is needed for that"
        else if (pendingStarred) addVips(Contacts.starred(ctx)) else picker.launch(null)
    }
    fun withContacts(starred: Boolean, run: () -> Unit) {
        pendingStarred = starred
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) run()
        else contactsPerm.launch(Manifest.permission.READ_CONTACTS)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Allowed", style = MaterialTheme.typography.headlineMedium)
            Text("These always reach you, even while Hush is on.", color = C.muted, modifier = Modifier.padding(top = 4.dp))

            // ---- people ----
            SectionTitle("People", "Their calls, WhatsApp calls & messages, and texts")
            Panel {
                if (cfg.vips.isEmpty()) {
                    Text("No one yet. Add the people who must always reach you — family, partner, your manager.", color = C.muted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                cfg.vips.forEach { v ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Initials(v.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name, fontWeight = FontWeight.Medium)
                            Text(v.numbers.joinToString(" · ").ifEmpty { "Matched by name" }, color = C.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton({ Store.updateConfig { c -> c.copy(vips = c.vips.filter { it.id != v.id }) } }) {
                            Icon(Icons.Rounded.Close, "Remove ${v.name}", tint = C.muted)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton({ withContacts(false) { picker.launch(null) } }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Contact")
                    }
                    FilledTonalButton({ withContacts(true) { addVips(Contacts.starred(ctx)) } }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.Star, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Starred")
                    }
                    FilledTonalButton({ addByHand = true }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.Dialpad, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Number")
                    }
                }
            }

            // ---- apps ----
            SectionTitle("Apps", "Every notification from these gets through")
            Panel {
                cfg.allowedApps.sortedBy { AppNames.label(ctx, it).lowercase() }.forEach { pkg ->
                    val installed = remember(pkg, tick) { AppNames.installed(ctx, pkg) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(AppNames.label(ctx, pkg), fontWeight = FontWeight.Medium)
                            if (!installed) Text("Not installed on this phone", color = C.muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton({ Store.updateConfig { it.copy(allowedApps = it.allowedApps - pkg) } }) {
                            Icon(Icons.Rounded.Close, "Remove", tint = C.muted)
                        }
                    }
                }
                val suggestions = remember(cfg.allowedApps, tick) {
                    SUGGESTED.filter { it !in cfg.allowedApps && AppNames.installed(ctx, it) }
                }
                if (suggestions.isNotEmpty()) {
                    Text("Suggested", color = C.muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    FlowRowCompat(suggestions) { pkg ->
                        AssistChip(
                            onClick = { Store.updateConfig { it.copy(allowedApps = it.allowedApps + pkg) } },
                            label = { Text(AppNames.label(ctx, pkg)) },
                            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                FilledTonalButton({ pickApps = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Add an app")
                }
            }

            // ---- smart rules ----
            SectionTitle("Also let through")
            Panel {
                ToggleRow(
                    Icons.Rounded.Repeat, "Repeat callers",
                    "Anyone who calls twice within ${cfg.repeatWindowMin} min — phone or WhatsApp",
                    cfg.repeatCallers,
                ) { v -> Store.updateConfig { it.copy(repeatCallers = v) } }
                if (cfg.repeatCallers) Row(Modifier.padding(start = 40.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 30).forEach { m ->
                        FilterChip(cfg.repeatWindowMin == m, { Store.updateConfig { it.copy(repeatWindowMin = m) } }, { Text("${m}m", maxLines = 1) })
                    }
                }
                HorizontalDivider(color = C.line, modifier = Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    Icons.Rounded.Groups, "Your people in group chats",
                    "Ring when one of your people posts in a WhatsApp group",
                    cfg.vipInGroups,
                ) { v -> Store.updateConfig { it.copy(vipInGroups = v) } }
            }
            Spacer(Modifier.height(32.dp))
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }

    if (pickApps) AppPickerSheet(cfg.allowedApps, onDismiss = { pickApps = false }) { pkg, on ->
        Store.updateConfig { it.copy(allowedApps = if (on) it.allowedApps + pkg else it.allowedApps - pkg) }
    }
    if (addByHand) AddNumberDialog(onDismiss = { addByHand = false }) { name, number ->
        addByHand = false
        addVips(listOf(Vip(UUID.randomUUID().toString(), name, listOf(number))))
    }
}

@Composable
private fun ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = C.moon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(sub, color = C.muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(value, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FlowRowCompat(items: List<T>, content: @Composable (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items.forEach { content(it) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(selected: Set<String>, onDismiss: () -> Unit, onToggle: (String, Boolean) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) {
        apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AppNames.launchable(ctx) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Allow an app", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
            )
            Spacer(Modifier.height(8.dp))
            val list = apps
            if (list == null) Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
                    .sortedByDescending { it.pkg in selected || it.pkg in Packages.MESSAGING }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                    items(filtered, key = { it.pkg }) { a ->
                        val on = a.pkg in selected
                        Row(Modifier.fillMaxWidth().clickable { onToggle(a.pkg, !on) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(a.pkg, 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(a.label, Modifier.weight(1f))
                            Checkbox(on, { onToggle(a.pkg, it) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AddNumberDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && Phone.digits(number).length >= 6
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.surface,
        title = { Text("Add a person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (as saved in WhatsApp)") }, singleLine = true)
                OutlinedTextField(number, { number = it }, label = { Text("Phone number") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { TextButton({ onAdd(name.trim(), number.trim()) }, enabled = valid) { Text("Add") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
