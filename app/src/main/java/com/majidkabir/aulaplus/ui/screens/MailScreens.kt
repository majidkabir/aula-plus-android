package com.majidkabir.aulaplus.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.AulaNotificationSettings
import com.majidkabir.aulaplus.data.AutoReply
import com.majidkabir.aulaplus.data.CalendarFeed
import com.majidkabir.aulaplus.data.Demo
import com.majidkabir.aulaplus.data.MailFolder
import com.majidkabir.aulaplus.data.MessageDraft
import com.majidkabir.aulaplus.data.NamedSwitch
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.data.ThreadSummary
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.components.IconTile
import com.majidkabir.aulaplus.ui.components.Loading
import com.majidkabir.aulaplus.ui.components.card
import com.majidkabir.aulaplus.ui.components.openSafely
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

fun MailFolder.label() = if (isDeleted) L.t("Slettet post") else name

/** Inbox, drafts and Aula folders as chips. [folder] null = inbox, -1 = drafts. */
@Composable
fun FolderBar(folder: Int?, onSelect: (Int?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(folder == null, { onSelect(null) }, { Text(L.t("Indbakke")) })
        val drafts = Repo.drafts.size
        FilterChip(folder == -1, { onSelect(-1) }, { Text(if (drafts > 0) L.t("Kladder (%d)", drafts) else L.t("Kladder")) })
        Repo.folders.value.orEmpty().forEach { f -> FilterChip(folder == f.id, { onSelect(f.id) }, { Text(f.label(), maxLines = 1) }) }
    }
}

/** Actions for one thread: mark, mute, move, leave, delete. */
@Composable
fun ThreadActionsDialog(
    t: ThreadSummary,
    currentFolder: Int?,
    onDismiss: () -> Unit,
    onMark: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    /** Called after a change: the updated thread, or null when it left this list (moved or left). */
    onDone: (ThreadSummary?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var moving by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    fun act(result: ThreadSummary?, block: suspend () -> Unit) {
        onDismiss()
        scope.launch {
            runCatching { block() }.onSuccess { onDone(result) }
                .onFailure { Toast.makeText(ctx, L.t("Ikke ændret: %s", it.message ?: ""), Toast.LENGTH_LONG).show() }
        }
    }
    if (leaving) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(L.t("Forlad samtalen?")) },
            text = { Text(L.t("Du får ikke flere beskeder i denne tråd.")) },
            confirmButton = { TextButton(onClick = { act(null) { Repo.leave(t) } }) { Text(L.t("Forlad"), color = Color(0xFFFF3B30)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(L.t("Annuller")) } })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (moving) L.t("Flyt til") else t.subject, maxLines = 2) },
        text = {
            Column {
                if (moving) {
                    if (currentFolder != null) ActionRow(L.t("Indbakke"), Icons.Outlined.Inbox) { act(null) { Repo.move(t, null) } }
                    Repo.folders.value.orEmpty().filter { it.id != currentFolder }.forEach { f ->
                        ActionRow(f.label(), if (f.isDeleted) Icons.Filled.Delete else Icons.Filled.Folder) { act(null) { Repo.move(t, f.id) } }
                    }
                } else {
                    if (onMark != null) ActionRow(if (t.isMarked) L.t("Fjern markering") else L.t("Markér"), Icons.Filled.Star) { onDismiss(); onMark() }
                    if (t.subscriptionId != null) ActionRow(if (t.isMuted) L.t("Slå lyd til") else L.t("Slå lyd fra"),
                        if (t.isMuted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff) { act(t.copy(isMuted = !t.isMuted)) { Repo.setMuted(t, !t.isMuted) } }
                    ActionRow(L.t("Flyt til"), Icons.Filled.Folder) { moving = true }
                    ActionRow(L.t("Forlad samtalen"), Icons.AutoMirrored.Filled.ExitToApp) { leaving = true }
                    if (onDelete != null) ActionRow(L.t("Slet"), Icons.Filled.Delete, Color(0xFFFF3B30)) { onDismiss(); onDelete() }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L.t("Annuller")) } },
    )
}

@Composable
private fun ActionRow(title: String, icon: ImageVector, tint: Color? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = tint ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun DraftRow(d: MessageDraft, onClick: () -> Unit) {
    Column(Modifier.card(12.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row {
            Text(d.recipients.joinToString { it.name }.ifBlank { L.t("Ingen modtagere") }, Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(AulaDate.listDate(d.savedMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(d.subject.ifBlank { L.t("(Intet emne)") }, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (d.text.isNotBlank()) Text(d.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** Create, rename and delete your own message folders. */
@Composable
fun FoldersScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf<MailFolder?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MailFolder?>(null) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { Repo.folders.load(60) }
    fun run(block: suspend () -> Unit) = scope.launch {
        error = null
        runCatching { if (!Demo.isOn) block(); Repo.folders.refresh() }.onFailure { error = it.message }
    }
    AulaScreen(title = L.t("Mapper"), onBack = { nav.back() }, refreshing = Repo.folders.isLoading, onRefresh = { Repo.folders.refresh() }) {
        item {
            Column(Modifier.card(4.dp)) {
                Repo.folders.value.orEmpty().filterNot { it.isDeleted }.forEach { f ->
                    Row(Modifier.fillMaxWidth().clickable { name = f.name; naming = f }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Text(f.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { deleting = f }) { Text(L.t("Slet"), color = Color(0xFFFF3B30)) }
                    }
                    HorizontalDivider()
                }
                TextButton(onClick = { name = ""; creating = true }, modifier = Modifier.padding(4.dp)) { Text(L.t("Ny mappe")) }
            }
        }
        item {
            Text(L.t("Mapperne er de samme som på aula.dk. Tryk på en mappe for at omdøbe den."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
    if (creating || naming != null) {
        AlertDialog(
            onDismissRequest = { creating = false; naming = null },
            title = { Text(if (creating) L.t("Ny mappe") else L.t("Omdøb mappe")) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(L.t("Navn")) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val f = naming
                    creating = false; naming = null
                    run { if (f == null) AulaApi.createFolder(name.trim()) else AulaApi.renameFolder(f.id, name.trim()) }
                }) { Text(if (naming == null) L.t("Opret") else L.t("Gem")) }
            },
            dismissButton = { TextButton(onClick = { creating = false; naming = null }) { Text(L.t("Annuller")) } },
        )
    }
    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(L.t("Slet mappen \"%s\"?", f.name)) },
            text = { Text(L.t("Beskederne i mappen flyttes til Slettet post.")) },
            confirmButton = { TextButton(onClick = { deleting = null; run { AulaApi.deleteFolder(f.id) } }) { Text(L.t("Slet"), color = Color(0xFFFF3B30)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(L.t("Annuller")) } },
        )
    }
}

/** Aula's out-of-office reply ("Autosvar"). */
@Composable
fun AutoReplyScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<AutoReply?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var startTime by remember { mutableStateOf("08:00") }
    var endDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var hasEnd by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!Demo.isOn) runCatching { AulaApi.autoReply() }.getOrNull()?.let { r ->
            current = r; text = r.text
            r.startMs?.let { val z = java.time.Instant.ofEpochMilli(it).atZone(AulaDate.copenhagen); startDate = z.toLocalDate().toString(); startTime = z.toLocalTime().toString().take(5) }
            hasEnd = r.endMs != null
            r.endMs?.let { endDate = java.time.Instant.ofEpochMilli(it).atZone(AulaDate.copenhagen).toLocalDate().toString() }
        }
        loaded = true
    }
    fun ms(date: String, time: String): Long? = runCatching {
        ZonedDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim()), AulaDate.copenhagen).toInstant().toEpochMilli()
    }.getOrNull()
    AulaScreen(title = L.t("Autosvar"), onBack = { nav.back() }, actions = {
        TextButton(enabled = loaded && !busy && text.isNotBlank(), onClick = {
            val start = ms(startDate, startTime) ?: run { error = L.t("Ugyldig dato"); return@TextButton }
            val end = if (hasEnd) ms(endDate, "23:59") ?: run { error = L.t("Ugyldig dato"); return@TextButton } else null
            scope.launch {
                busy = true; error = null
                runCatching { if (!Demo.isOn) AulaApi.setAutoReply(text.trim(), start, end) }
                    .onSuccess { nav.back() }.onFailure { error = L.t("Ikke gemt: %s", it.message ?: "") }
                busy = false
            }
        }) { Text(L.t("Gem")) }
    }) {
        current?.let { r ->
            item {
                Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(L.t("Autosvar er slået til"), color = Color(0xFF34C759), style = MaterialTheme.typography.titleSmall)
                    TextButton(enabled = !busy, onClick = {
                        val id = r.id ?: return@TextButton
                        scope.launch {
                            busy = true
                            runCatching { if (!Demo.isOn) AulaApi.deleteAutoReply(id) }.onSuccess { current = null }.onFailure { error = it.message }
                            busy = false
                        }
                    }) { Text(L.t("Slå autosvar fra"), color = Color(0xFFFF3B30)) }
                }
            }
        }
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(L.t("Besked")) }, minLines = 3,
                    placeholder = { Text(L.t("Fx: Jeg er på ferie og svarer, når jeg er tilbage.")) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startDate, { startDate = it }, Modifier.weight(1.4f), label = { Text(L.t("Fra (dato)")) }, singleLine = true)
                    OutlinedTextField(startTime, { startTime = it }, Modifier.weight(1f), label = { Text(L.t("Kl.")) }, singleLine = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L.t("Slutdato"), Modifier.weight(1f)); Switch(hasEnd, { hasEnd = it })
                }
                if (hasEnd) OutlinedTextField(endDate, { endDate = it }, Modifier.fillMaxWidth(), label = { Text(L.t("Til (dato)")) }, singleLine = true)
                Text(L.t("Alle der skriver til dig i perioden, får automatisk dette svar."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
        if (!loaded || busy) item { Loading() }
    }
}

/** What Aula itself notifies about (push from the Aula app and e-mail), as on aula.dk. */
@Composable
fun AulaNotificationSettingsScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var s by remember { mutableStateOf<AulaNotificationSettings?>(null) }
    var saved by remember { mutableStateOf<AulaNotificationSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Demo.isOn) {
            s = AulaNotificationSettings(true, "0800", List(7) { false }, true, true, false, true, true, true, true, true, true, true, false, false,
                listOf(NamedSwitch("PickupActivity", L.t("Afhentning"), true)), emptyList())
        } else runCatching { AulaApi.notificationSettings() }.onSuccess { s = it }
            .onFailure { error = L.t("Kunne ikke hente indstillingerne: %s", it.message ?: "") }
        saved = s
    }
    AulaScreen(title = L.t("Aula-notifikationer"), onBack = { nav.back() }, actions = {
        TextButton(enabled = s != null && s != saved && !busy, onClick = {
            val cur = s ?: return@TextButton
            scope.launch {
                busy = true; error = null
                runCatching { if (!Demo.isOn) AulaApi.saveNotificationSettings(cur) }
                    .onSuccess { saved = cur; error = L.t("Gemt på Aula") }.onFailure { error = L.t("Ikke gemt: %s", it.message ?: "") }
                busy = false
            }
        }) { Text(L.t("Gem")) }
    }) {
        val v = s
        if (v == null) { item { if (error == null) Loading() else Text(error!!, color = Color(0xFFFF3B30)) }; return@AulaScreen }
        item {
            MenuGroup(L.t("Hvordan")) {
                if (v.pushAvailable) SwitchRow(L.t("Push i Aula-appen"), v.push) { s = v.copy(push = it) }
                if (v.emailAvailable) SwitchRow(L.t("E-mail"), v.email) { s = v.copy(email = it) }
            }
        }
        item {
            MenuGroup(L.t("Hvornår")) {
                SwitchRow(L.t("Med det samme"), v.instant) { s = v.copy(instant = it) }
                if (!v.instant) {
                    val t = v.scheduledTime ?: "0800"
                    var time by remember(t) { mutableStateOf("${t.take(2)}:${t.takeLast(2)}") }
                    OutlinedTextField(time, { time = it
                        Regex("^(\\d{1,2}):(\\d{2})$").find(it.trim())?.let { m ->
                            s = v.copy(scheduledTime = m.groupValues[1].padStart(2, '0') + m.groupValues[2])
                        }
                    }, Modifier.fillMaxWidth().padding(12.dp), label = { Text(L.t("Daglig opsummering kl.")) }, singleLine = true)
                    dayNames().forEachIndexed { i, name ->
                        SwitchRow(name, v.days[i]) { on -> s = v.copy(days = v.days.toMutableList().also { it[i] = on }) }
                    }
                }
            }
        }
        item {
            MenuGroup(L.t("Beskeder fra")) {
                SwitchRow(L.t("Personale"), v.fromEmployees) { s = v.copy(fromEmployees = it) }
                SwitchRow(L.t("Forældre"), v.fromGuardians) { s = v.copy(fromGuardians = it) }
                SwitchRow(L.t("Børn"), v.fromChildren) { s = v.copy(fromChildren = it) }
            }
        }
        item {
            MenuGroup(L.t("Andet")) {
                SwitchRow(L.t("Kalender"), v.calendar) { s = v.copy(calendar = it) }
                SwitchRow(L.t("Galleri"), v.gallery) { s = v.copy(gallery = it) }
                SwitchRow(L.t("Opslag"), v.posts) { s = v.copy(posts = it) }
            }
        }
        if (v.comeGo.isNotEmpty()) item {
            MenuGroup(L.t("Komme/gå")) {
                v.comeGo.forEach { c -> SwitchRow(c.title, c.on) { on -> s = v.copy(comeGo = v.comeGo.map { if (it.id == c.id) it.copy(on = on) else it }) } }
            }
        }
        if (v.widgets.isNotEmpty()) item {
            MenuGroup(L.t("Værktøjer")) {
                v.widgets.forEach { w -> SwitchRow(w.title, w.on) { on -> s = v.copy(widgets = v.widgets.map { if (it.id == w.id) it.copy(on = on) else it }) } }
            }
        }
        item {
            Text(L.t("Det er Aulas egne notifikationer. Aula+ giver besked om nyt for sig selv."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        error?.let { item { Text(it, color = if (it == L.t("Gemt på Aula")) Color(0xFF34C759) else Color(0xFFFF3B30)) } }
    }
}

private fun dayNames() = listOf("Mandag", "Tirsdag", "Onsdag", "Torsdag", "Fredag", "Lørdag", "Søndag").map { L.t(it) }

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

/** An iCal link from Aula, so events show up in the phone's own calendar and stay updated. */
@Composable
fun CalendarFeedScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var feeds by remember { mutableStateOf<List<CalendarFeed>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<CalendarFeed?>(null) }
    LaunchedEffect(Unit) {
        if (Demo.isOn) feeds = emptyList()
        else runCatching { AulaApi.calendarFeeds() }.onSuccess { feeds = it }.onFailure { error = it.message }
    }
    fun run(block: suspend () -> Unit) = scope.launch {
        busy = true; error = null
        runCatching { block(); feeds = AulaApi.calendarFeeds() }.onFailure { error = L.t("Ikke oprettet: %s", it.message ?: "") }
        busy = false
    }
    AulaScreen(title = L.t("Kalender på telefonen"), onBack = { nav.back() }) {
        val list = feeds
        if (list == null) { item { if (error == null) Loading() else Text(error!!, color = Color(0xFFFF3B30)) }; return@AulaScreen }
        val f = list.firstOrNull()
        if (f != null) {
            val url = f.oneYear ?: f.oneWeek
            item {
                MenuGroup(null) {
                    if (url != null) {
                        MenuRow(L.t("Abonnér i Kalender"), { IconTile(Icons.Filled.CalendarMonth, Color(0xFF007AFF)) }) {
                            ctx.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url.replaceFirst(Regex("^https?://"), "webcal://"))))
                        }
                        MenuRow(L.t("Kopiér link"), { IconTile(Icons.Filled.Folder, Color(0xFF8E8E93)) }) {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Aula", url))
                            Toast.makeText(ctx, L.t("Kopieret"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            item {
                Text(L.t("Kalender-appen henter ændringer fra Aula af sig selv. Linket er personligt – del det ikke."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            item { TextButton(enabled = !busy, onClick = { confirmDelete = f }) { Text(L.t("Slet kalenderlink"), color = Color(0xFFFF3B30)) } }
        } else {
            item {
                Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(L.t("Aula laver et personligt link med begivenheder, ferier, ture og møder. Når du opretter det, accepterer du Aulas vilkår for kalenderdeling: begivenhederne kopieres til din kalender, og Aula kan ikke slette dem derfra igen."),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(enabled = !busy, onClick = {
                        val me = Repo.session.value?.guardianProfileIds?.firstOrNull()
                        if (me == null) error = L.t("Kunne ikke finde din profil. Træk ned for at opdatere.") else run { AulaApi.createCalendarFeed(me) }
                    }) { Text(L.t("Opret kalenderlink")) }
                }
            }
        }
        if (busy) item { Loading() }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(L.t("Slet kalenderlinket?")) },
            text = { Text(L.t("Abonnementer på linket holder op med at blive opdateret.")) },
            confirmButton = { TextButton(onClick = { confirmDelete = null; run { AulaApi.deleteCalendarFeed(f.id) } }) { Text(L.t("Slet"), color = Color(0xFFFF3B30)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(L.t("Annuller")) } },
        )
    }
}
