@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.majidkabir.aulaplus.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.AulaGroup
import com.majidkabir.aulaplus.data.Demo
import com.majidkabir.aulaplus.data.EventDetail
import com.majidkabir.aulaplus.data.MeetingSlot
import com.majidkabir.aulaplus.data.NewEvent
import com.majidkabir.aulaplus.data.OwnerProfile
import com.majidkabir.aulaplus.data.Recipient
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.components.card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** A picked photo, downsized to a JPEG ready to upload. */
class PickedPhoto(val jpeg: ByteArray) {
    val preview by lazy { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.asImageBitmap() }
}

private fun toJpeg(ctx: android.content.Context, uri: android.net.Uri, max: Int = 2048): ByteArray? = runCatching {
    val src = android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)
    val bmp = android.graphics.ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
        val scale = max.toFloat() / maxOf(info.size.width, info.size.height)
        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
    }
    ByteArrayOutputStream().use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out); out.toByteArray() }
}.getOrNull()

/** Opens the system photo picker; picked photos are appended to [photos]. */
@Composable
fun rememberPhotoPicker(photos: SnapshotStateList<PickedPhoto>, max: Int = 20): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(max)) { uris ->
        scope.launch {
            val jpegs = withContext(Dispatchers.IO) { uris.mapNotNull { toJpeg(ctx, it) } }
            photos.addAll(jpegs.map(::PickedPhoto))
        }
    }
    return { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
fun PhotoStrip(photos: SnapshotStateList<PickedPhoto>) {
    if (photos.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.toList().forEach { p ->
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                p.preview?.let { Image(it, null, Modifier.size(64.dp), contentScale = ContentScale.Crop) }
                Icon(Icons.Filled.Cancel, L.t("Fjern billede"), Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp)
                    .clickable { photos.remove(p) }, tint = Color.White)
            }
        }
    }
}

@Composable
private fun PickPhotosButton(photos: SnapshotStateList<PickedPhoto>, max: Int, label: String) {
    val pick = rememberPhotoPicker(photos, max)
    OutlinedButton(onClick = pick) {
        Icon(Icons.Filled.AddPhotoAlternate, null, Modifier.size(18.dp)); Text("  $label")
    }
}

private fun pickDate(ctx: android.content.Context, ms: Long, done: (Long) -> Unit) {
    val z = AulaDate.localDateTime(ms)
    android.app.DatePickerDialog(ctx, { _, y, m, d ->
        done(z.withYear(y).withMonth(m + 1).withDayOfMonth(d).toInstant().toEpochMilli())
    }, z.year, z.monthValue - 1, z.dayOfMonth).show()
}

private fun pickTime(ctx: android.content.Context, ms: Long, done: (Long) -> Unit) {
    val z = AulaDate.localDateTime(ms)
    android.app.TimePickerDialog(ctx, { _, h, m -> done(z.withHour(h).withMinute(m).toInstant().toEpochMilli()) },
        z.hour, z.minute, true).show()
}

@Composable
private fun DateTimeRow(label: String, ms: Long, withTime: Boolean, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { pickDate(ctx, ms, onChange) }) { Text(AulaDate.weekdayDayMonth(AulaDate.localDate(ms))) }
        if (withTime) TextButton(onClick = { pickTime(ctx, ms, onChange) }) { Text(AulaDate.time(ms)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked, onChange) }
}

/** "Ny begivenhed" as a parent. */
@Composable
fun CreateEventScreen(day: String) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val date = runCatching { LocalDate.parse(day) }.getOrDefault(AulaDate.today())
    val start0 = ZonedDateTime.of(date, LocalTime.of(16, 0), AulaDate.copenhagen).toInstant().toEpochMilli()
    var form by remember { mutableStateOf(NewEvent(startMs = start0, endMs = start0 + 2 * 3600_000)) }
    var owners by remember { mutableStateOf<List<OwnerProfile>>(emptyList()) }
    var owner by remember { mutableStateOf<OwnerProfile?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Recipient>>(emptyList()) }
    var hasDeadline by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (Demo.isOn) { owner = OwnerProfile(1, "1", "Demo"); return@LaunchedEffect }
        runCatching { AulaApi.ownerProfiles() }.onSuccess { owners = it; owner = it.firstOrNull() }.onFailure { error = it.message }
    }
    LaunchedEffect(query) {
        val t = query.trim()
        if (t.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = runCatching { AulaApi.findRecipients(t) }.getOrDefault(emptyList())
    }
    val canSave = form.title.isNotBlank() && form.endMs >= form.startMs && owner != null && !saving
    AulaScreen(title = L.t("Ny begivenhed"), onBack = { nav.back() }, actions = {
        TextButton(enabled = canSave, onClick = {
            val o = owner ?: return@TextButton
            scope.launch {
                saving = true; error = null
                val e = if (hasDeadline) form else form.copy(deadlineMs = null)
                runCatching { if (!Demo.isOn) AulaApi.createEvent(e, o) }
                    .onSuccess { Repo.events(date).refresh(); nav.back() }
                    .onFailure { error = L.t("Begivenheden blev ikke oprettet: %s", it.message ?: "") }
                saving = false
            }
        }) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(L.t("Opret")) }
    }) {
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(form.title, { form = form.copy(title = it) }, Modifier.fillMaxWidth(), label = { Text(L.t("Titel")) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("event" to L.t("Begivenhed"), "other" to L.t("Andet")).forEach { (t, label) ->
                        FilterChip(form.type == t, { form = form.copy(type = t) }, { Text(label) })
                    }
                }
                OutlinedTextField(form.location, { form = form.copy(location = it) }, Modifier.fillMaxWidth(), label = { Text(L.t("Sted")) }, singleLine = true)
            }
        }
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(L.t("Hele dagen"), form.allDay) { form = form.copy(allDay = it) }
                DateTimeRow(L.t("Start"), form.startMs, !form.allDay) { v -> form = form.copy(startMs = v, endMs = form.endMs + (v - form.startMs)) }
                DateTimeRow(L.t("Slut"), form.endMs, !form.allDay) { v -> form = form.copy(endMs = maxOf(v, form.startMs)) }
            }
        }
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(L.t("Inviter"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                form.invitees.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, Modifier.weight(1f))
                        IconButton(onClick = { form = form.copy(invitees = form.invitees - r) }) { Icon(Icons.Filled.Close, L.t("Fjern")) }
                    }
                }
                SearchField(query, L.t("Søg forælder, barn, gruppe …")) { query = it }
                results.filter { r -> form.invitees.none { it.id == r.id } }.forEach { r ->
                    Column(Modifier.fillMaxWidth().clickable { form = form.copy(invitees = form.invitees + r); query = ""; results = emptyList() }.padding(vertical = 6.dp)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                        r.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (form.invitees.isNotEmpty()) {
                    SwitchRow(L.t("Kræver svar"), form.responseRequired) { form = form.copy(responseRequired = it) }
                    if (form.responseRequired) {
                        SwitchRow(L.t("Svarfrist"), hasDeadline) { hasDeadline = it; if (it && form.deadlineMs == null) form = form.copy(deadlineMs = form.startMs - 86_400_000) }
                        if (hasDeadline) DateTimeRow(L.t("Svar senest"), form.deadlineMs ?: form.startMs, true) { form = form.copy(deadlineMs = minOf(it, form.startMs)) }
                    }
                }
            }
        }
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(form.description, { form = form.copy(description = it) }, Modifier.fillMaxWidth(), label = { Text(L.t("Beskrivelse")) }, minLines = 3)
                SwitchRow(L.t("Privat"), form.isPrivate) { form = form.copy(isPrivate = it) }
                Text(L.t("Private begivenheder viser kun tidspunktet for andre end de inviterede."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (owners.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    owners.forEach { o -> FilterChip(owner == o, { owner = o }, { Text(o.institutionName) }) }
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
}

/** "Nyt album": name, who sees it, and photos. */
@Composable
fun CreateAlbumScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf<List<AulaGroup>?>(null) }
    var owners by remember { mutableStateOf<List<OwnerProfile>>(emptyList()) }
    val chosen = remember { mutableStateListOf<Int>() }
    val photos = remember { mutableStateListOf<PickedPhoto>() }
    var uploaded by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (Demo.isOn) { groups = listOf(AulaGroup(1, "Solsikken")); return@LaunchedEffect }
        runCatching { AulaApi.myGroups() to AulaApi.ownerProfiles() }
            .onSuccess { groups = it.first; owners = it.second }.onFailure { error = it.message; groups = emptyList() }
    }
    androidx.activity.compose.BackHandler(enabled = saving) {}
    AulaScreen(title = L.t("Nyt album"), onBack = { if (!saving) nav.back() }, actions = {
        TextButton(enabled = title.isNotBlank() && chosen.isNotEmpty() && !saving, onClick = {
            scope.launch {
                saving = true; error = null; uploaded = 0
                runCatching {
                    if (!Demo.isOn) {
                        val picked = groups.orEmpty().filter { it.id in chosen }
                        val code = picked.firstOrNull()?.institutionCode
                        val owner = owners.firstOrNull { it.institutionCode == code } ?: owners.firstOrNull() ?: error("profiles.getProfileContext")
                        val id = AulaApi.createAlbum(title, text, picked, owner) ?: error("gallery.createAlbum")
                        AulaApi.upload(photos.map { it.jpeg }, owner.institutionCode, id) { uploaded = it }
                    }
                }.onSuccess { Repo.albums.refresh(); nav.back() }
                    .onFailure { error = L.t("Albummet blev ikke oprettet: %s", it.message ?: "") }
                saving = false
            }
        }) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(L.t("Opret")) }
    }) {
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(L.t("Albumnavn")) }, singleLine = true)
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(L.t("Beskrivelse")) }, minLines = 2)
            }
        }
        item {
            MenuGroup(L.t("Del med")) {
                val list = groups
                if (list == null) Box(Modifier.padding(12.dp)) { CircularProgressIndicator(Modifier.size(20.dp)) }
                else list.forEach { g ->
                    Row(Modifier.fillMaxWidth().clickable { if (g.id in chosen) chosen.remove(g.id) else chosen.add(g.id) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, Modifier.weight(1f))
                        if (g.id in chosen) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item { Text(L.t("Albummet kan ses af medlemmerne i de valgte grupper."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickPhotosButton(photos, 100, L.t("Vælg billeder"))
                PhotoStrip(photos)
                if (saving && photos.isNotEmpty()) {
                    LinearProgressIndicator(progress = { uploaded / photos.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(L.t("Uploader %d af %d", uploaded, photos.size), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
}

/** "Tilføj billeder" to an existing album. */
@Composable
fun AddPhotosScreen(albumId: Int) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateListOf<PickedPhoto>() }
    var uploaded by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val title = Repo.albums.value?.firstOrNull { it.id == albumId }?.title ?: L.t("Tilføj billeder")
    androidx.activity.compose.BackHandler(enabled = saving) {}
    AulaScreen(title = title, onBack = { if (!saving) nav.back() }, actions = {
        TextButton(enabled = photos.isNotEmpty() && !saving, onClick = {
            scope.launch {
                saving = true; error = null; uploaded = 0
                runCatching { if (!Demo.isOn) AulaApi.upload(photos.map { it.jpeg }, albumId = albumId) { uploaded = it } }
                    .onSuccess { Repo.media(albumId).refresh(); nav.back() }
                    .onFailure { error = L.t("Billederne blev ikke uploadet: %s", it.message ?: "") }
                saving = false
            }
        }) { if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(L.t("Upload")) }
    }) {
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PickPhotosButton(photos, 100, L.t("Vælg billeder"))
                PhotoStrip(photos)
                if (saving) {
                    LinearProgressIndicator(progress = { uploaded / maxOf(photos.size, 1).toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text(L.t("Uploader %d af %d", uploaded, photos.size), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
}

/** Who I can book a meeting time for: a parent meeting is mine, a school-home talk is about a child. */
fun slotPeople(d: EventDetail, eventChildIds: List<Int>): List<Pair<Int, String>> {
    val guardians = Repo.session.value?.guardianProfileIds ?: emptyList()
    val invited = d.invitees.mapNotNull { it.institutionProfileId }.toSet()
    if (d.type != "parental_meeting") {
        val kids = AppState.children.filter { it.id in invited || it.id in eventChildIds }
        if (kids.isNotEmpty()) return kids.map { it.id to it.firstName }
    }
    val me = guardians.firstOrNull { it in invited } ?: guardians.firstOrNull()
    return if (me != null) listOf(me to L.t("Mig")) else AppState.children.map { it.id to it.firstName }
}

/** Bookable times for a school-home talk or parent meeting. */
@Composable
fun MeetingSlotsCard(d: EventDetail, people: List<Pair<Int, String>>, reload: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var who by remember { mutableStateOf(people.firstOrNull()?.first) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true; error = null
            runCatching { if (!Demo.isOn) action(); reload() }.onFailure { error = L.t("Ikke gemt: %s", it.message ?: "") }
            busy = false
        }
    }
    val person = who
    val mine = person?.let { p -> d.slots.firstOrNull { it.bookedFor == p } }
    Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(L.t("Vælg tid"), style = MaterialTheme.typography.titleSmall)
        if (people.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { (id, name) -> FilterChip(who == id, { who = id }, { Text(name) }) }
        }
        if (person != null && mine != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
            Text("  " + AulaDate.weekdayDayMonth(AulaDate.localDate(mine.startMs)) + " " + AulaDate.time(mine.startMs) + "–" + AulaDate.time(mine.endMs),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(enabled = !busy && !d.deadlineExceeded, onClick = { run { AulaApi.cancelBooking(d.id, mine, person) } }) {
                Text(L.t("Afmeld"), color = Color(0xFFFF3B30))
            }
        }
        d.slots.groupBy { AulaDate.localDate(it.startMs) }.toSortedMap().forEach { (day, slots) ->
            Text(AulaDate.weekdayDayMonth(day), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slots.forEach { s ->
                    val isMine = person != null && s.bookedFor == person
                    val taken = (s.bookedFor != null || s.blocked) && !isMine
                    val bg = when { isMine -> Color(0x4034C759); taken -> MaterialTheme.colorScheme.surfaceVariant; else -> MaterialTheme.colorScheme.primaryContainer }
                    Text(AulaDate.time(s.startMs), style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (taken) TextDecoration.LineThrough else null,
                        color = if (taken) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clip(CircleShape).background(bg)
                            .clickable(enabled = !taken && !isMine && !busy && !d.deadlineExceeded && person != null) {
                                val p = person ?: return@clickable
                                run { if (mine != null) AulaApi.moveBooking(d.id, s, p) else AulaApi.bookSlot(d.id, s, p) }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        Text(if (d.deadlineExceeded) L.t("Svarfristen er overskredet.") else L.t("Tryk på en ledig tid for at booke den."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = Color(0xFFFF3B30), style = MaterialTheme.typography.bodySmall) }
    }
}
