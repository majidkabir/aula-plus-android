package com.majidkabir.aulaplus.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.ClosedDays
import com.majidkabir.aulaplus.data.Contact
import com.majidkabir.aulaplus.data.Demo
import com.majidkabir.aulaplus.data.MyProfile
import com.majidkabir.aulaplus.data.OpeningDay
import com.majidkabir.aulaplus.data.PickupPerson
import com.majidkabir.aulaplus.data.Recipient
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.NavCache
import com.majidkabir.aulaplus.ui.components.ChildAvatar
import com.majidkabir.aulaplus.ui.components.EmptyState
import com.majidkabir.aulaplus.ui.components.InitialsAvatar
import com.majidkabir.aulaplus.ui.components.Loading
import com.majidkabir.aulaplus.ui.components.card
import com.majidkabir.aulaplus.ui.components.openSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Red = Color(0xFFFF3B30)

@Composable
fun BigAvatar(name: String, url: String?, size: Dp = 160.dp, bitmap: Bitmap? = null) {
    val m = Modifier.size(size).clip(CircleShape).border(4.dp, Color.White.copy(alpha = 0.8f), CircleShape)
    when {
        bitmap != null -> Image(bitmap.asImageBitmap(), null, m, contentScale = ContentScale.Crop)
        url != null -> AsyncImage(url, null, m, contentScale = ContentScale.Crop)
        else -> InitialsAvatar(name, MaterialTheme.colorScheme.primary, size)
    }
}

/** One contact on its own page: big photo and everything Aula shares about them. */
@Composable
fun ContactDetailScreen(key: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val c = remember { NavCache.items[key] as? Contact }
    if (c == null) { AulaScreen(title = "", onBack = { nav.back() }) { item { EmptyState(L.t("Ingen kontakter"), Icons.Filled.Call) } }; return }
    val phones = buildList {
        listOf(L.t("Mobil") to c.mobilePhone, L.t("Hjem") to c.homePhone, L.t("Arbejde") to c.workPhone).forEach { (l, n) ->
            if (!n.isNullOrBlank() && none { it.second == n }) add(l to n)
        }
        c.phones.forEach { p -> if (none { it.second == p }) add(L.t("Telefon") to p) }
    }
    fun dial(p: String) = ctx.openSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.filter { it.isDigit() || it == '+' })))
    val role = roleName(c.role)
    AulaScreen(title = c.name, onBack = { nav.back() }) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigAvatar(c.name, c.pictureUrl, 180.dp)
                Text(c.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                listOfNotNull(role, c.group, c.institutionName).firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    phones.firstOrNull()?.let { p -> RoundAction(L.t("Ring"), Icons.Filled.Call) { dial(p.second) } }
                    (c.mobilePhone ?: phones.firstOrNull()?.second)?.let { p ->
                        RoundAction(L.t("SMS"), Icons.Filled.Sms) { ctx.openSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + p.filter { it.isDigit() || it == '+' }))) }
                    }
                    c.email?.let { e -> RoundAction(L.t("Mail"), Icons.Filled.Email) { ctx.openSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) } }
                    RoundAction(L.t("Besked"), Icons.AutoMirrored.Filled.Chat) {
                        NavCache.put("compose-recipient", Recipient(c.id, c.name, c.detail)); nav.open("compose")
                    }
                }
            }
        }
        if (phones.isNotEmpty() || c.email != null) item {
            MenuGroup(L.t("Kontakt")) {
                phones.forEach { (l, p) -> InfoLine(l, p) { dial(p) } }
                c.email?.let { e -> InfoLine(L.t("E-mail"), e) { ctx.openSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) } }
            }
        }
        if (c.relations.isNotEmpty()) item {
            MenuGroup(if (c.role == "child") L.t("Forældre") else L.t("Børn")) {
                c.relations.forEach { r ->
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        InitialsAvatar(r.name, MaterialTheme.colorScheme.primary, 32.dp)
                        Column {
                            Text(r.name, style = MaterialTheme.typography.titleSmall)
                            r.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
        val info = listOfNotNull(role?.let { L.t("Rolle") to it }, c.group?.let { L.t("Gruppe") to it }, c.institutionName?.let { L.t("Institution") to it })
        if (info.isNotEmpty()) item { MenuGroup(L.t("Om")) { info.forEach { (k, v) -> InfoLine(k, v, null) } } }
    }
}

private fun roleName(role: String?) = when (role?.lowercase()) {
    "guardian" -> L.t("Forælder"); "child" -> L.t("Barn"); "employee" -> L.t("Ansat"); null, "" -> null; else -> role
}

@Composable
private fun RoundAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InfoLine(label: String, value: String, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/** "Min profil": my photo, phone numbers and e-mail, as on aula.dk. */
@Composable
fun MyProfileScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<MyProfile>?>(null) }
    var saved by remember { mutableStateOf<List<MyProfile>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picture by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(Unit) {
        if (Demo.isOn) {
            val p = MyProfile(1, 1, "Majid Ghafouri", "Valby Skole", null, "far@example.dk", "20 30 40 50", "", "", null, true)
            profiles = listOf(p); saved = listOf(p); return@LaunchedEffect
        }
        runCatching { AulaApi.myProfiles(AulaApi.bootstrap().guardianProfileIds) }
            .onSuccess { profiles = it; saved = it }.onFailure { error = L.t("Kunne ikke hente din profil: %s", it.message ?: "") }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val first = profiles?.firstOrNull() ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; error = null
            runCatching {
                val (bmp, jpeg) = withContext(Dispatchers.IO) { squareJpeg(ctx, uri) }
                picture = bmp
                if (!Demo.isOn) AulaApi.uploadProfilePicture(jpeg, first, true)
            }.onSuccess { note = L.t("Profilbillede skiftet") }
                .onFailure { picture = null; error = L.t("Billedet blev ikke skiftet: %s", it.message ?: "") }
            busy = false
        }
    }
    AulaScreen(title = L.t("Min profil"), onBack = { nav.back() }, actions = {
        TextButton(enabled = profiles != null && profiles != saved && !busy, onClick = {
            val list = profiles ?: return@TextButton
            scope.launch {
                busy = true; error = null
                runCatching { if (!Demo.isOn) AulaApi.saveContactInfo(list.filter { p -> saved.firstOrNull { it.id == p.id } != p }) }
                    .onSuccess { saved = list; note = L.t("Gemt på Aula") }.onFailure { error = L.t("Ikke gemt: %s", it.message ?: "") }
                busy = false
            }
        }) { Text(L.t("Gem")) }
    }) {
        val list = profiles
        if (list == null) { item { if (error == null) Loading() else Text(error!!, color = Red) }; return@AulaScreen }
        list.firstOrNull()?.let { first ->
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        BigAvatar(first.name, first.pictureUrl, 160.dp, picture)
                        if (busy) CircularProgressIndicator()
                    }
                    Text(first.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (first.canEditPicture) TextButton(enabled = !busy, onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Icon(Icons.Filled.CameraAlt, null); Text(" " + L.t("Skift profilbillede")) }
                }
            }
        }
        list.forEachIndexed { i, p ->
            item(key = "p${p.id}") {
                MenuGroup(p.institutionName ?: L.t("Kontaktoplysninger")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        fun upd(f: (MyProfile) -> MyProfile) { profiles = list.toMutableList().also { it[i] = f(p) } }
                        val phone = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        OutlinedTextField(p.mobilePhone, { v -> upd { it.copy(mobilePhone = v) } }, Modifier.fillMaxWidth(), label = { Text(L.t("Mobil")) }, singleLine = true, keyboardOptions = phone)
                        OutlinedTextField(p.homePhone, { v -> upd { it.copy(homePhone = v) } }, Modifier.fillMaxWidth(), label = { Text(L.t("Hjemme")) }, singleLine = true, keyboardOptions = phone)
                        OutlinedTextField(p.workPhone, { v -> upd { it.copy(workPhone = v) } }, Modifier.fillMaxWidth(), label = { Text(L.t("Arbejde")) }, singleLine = true, keyboardOptions = phone)
                        OutlinedTextField(p.email, { v -> upd { it.copy(email = v) } }, Modifier.fillMaxWidth(), label = { Text(L.t("E-mail")) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    }
                }
            }
        }
        item {
            Text(L.t("Personale og andre forældre kan se det, institutionen deler i kontaktlisten."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        note?.let { item { Text(it, color = Color(0xFF34C759)) } }
        error?.let { item { Text(it, color = Red) } }
    }
}

/** A square 800×800 JPEG from the picked photo, like Aula's own crop. */
private fun squareJpeg(ctx: android.content.Context, uri: Uri): Pair<Bitmap, ByteArray> {
    val src = ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } ?: error("image")
    val side = minOf(src.width, src.height)
    val crop = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val out = Bitmap.createScaledBitmap(crop, 800, 800, true)
    val bytes = ByteArrayOutputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it); it.toByteArray() }
    return out to bytes
}

/** "Må hente": the child's family from Aula plus names the parents added. */
@Composable
fun PickupPeopleCard(child: com.majidkabir.aulaplus.data.Child) {
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<PickupPerson>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<PickupPerson?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        people = if (Demo.isOn) listOf(PickupPerson("1", "Sara Ghafouri", "Mor", null), PickupPerson("2", "Mormor Lise", null, 2))
        else runCatching { AulaApi.pickupPeople(child.id) }.onFailure { error = it.message }.getOrDefault(emptyList())
    }
    LaunchedEffect(child.id) { load() }
    MenuGroup(L.t("Må hente %s", child.firstName)) {
        val list = people
        if (list == null) Loading()
        list.orEmpty().forEach { p ->
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InitialsAvatar(p.name, MaterialTheme.colorScheme.primary, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(p.relation ?: L.t("Tilføjet af jer"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.suggestionId != null) TextButton(onClick = { removing = p }) { Text(L.t("Fjern"), color = Red) }
            }
            HorizontalDivider()
        }
        TextButton(onClick = { name = ""; adding = true }, modifier = Modifier.padding(4.dp)) { Text(L.t("Tilføj person")) }
        error?.let { Text(it, color = Red, modifier = Modifier.padding(12.dp)) }
    }
    if (adding) AlertDialog(
        onDismissRequest = { adding = false },
        title = { Text(L.t("Tilføj person")) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(L.t("Navn, fx Mormor Lise")) }) },
        confirmButton = {
            TextButton(enabled = name.trim().length >= 2, onClick = {
                adding = false
                scope.launch {
                    runCatching { if (!Demo.isOn) AulaApi.addPickupPerson(name.trim(), listOf(child.id)) }
                        .onFailure { error = L.t("Ikke gemt: %s", it.message ?: "") }
                    load()
                }
            }) { Text(L.t("Tilføj")) }
        },
        dismissButton = { TextButton(onClick = { adding = false }) { Text(L.t("Annuller")) } },
    )
    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(L.t("Fjern %s?", p.name)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    scope.launch {
                        runCatching { if (!Demo.isOn) AulaApi.removePickupPerson(p.suggestionId ?: return@runCatching) }
                            .onFailure { error = L.t("Ikke ændret: %s", it.message ?: "") }
                        load()
                    }
                }) { Text(L.t("Fjern"), color = Red) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(L.t("Annuller")) } },
        )
    }
}

/** Opening hours for the next two weeks and upcoming closed days. */
@Composable
fun OpeningHoursCard(codes: List<String>) {
    var days by remember { mutableStateOf<List<OpeningDay>?>(null) }
    var closed by remember { mutableStateOf<List<ClosedDays>>(emptyList()) }
    LaunchedEffect(codes) {
        if (codes.isEmpty() || Demo.isOn) { days = emptyList(); return@LaunchedEffect }
        val from = LocalDate.now(AulaDate.copenhagen)
        days = runCatching { AulaApi.openingDays(codes, from, from.plusDays(14)) }.getOrDefault(emptyList())
            .filter { it.date.dayOfWeek.value < 6 || it.open != null }
        closed = runCatching { AulaApi.closedDays(codes) }.getOrDefault(emptyList())
    }
    val fmt = DateTimeFormatter.ofPattern("EEE d. MMM", L.locale)
    MenuGroup(L.t("Åbningstider og lukkedage")) {
        val list = days
        if (list == null) Loading()
        list.orEmpty().take(10).forEach { d ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(d.date.format(fmt).replaceFirstChar { it.uppercase(L.locale) }, Modifier.weight(1f),
                    fontWeight = if (d.date == LocalDate.now(AulaDate.copenhagen)) FontWeight.Bold else null)
                if (d.isClosed || (d.open == null && d.close == null)) Text(d.name ?: L.t("Lukket"), color = Red, fontWeight = FontWeight.SemiBold)
                else Text("${d.open ?: "?"} – ${d.close ?: "?"}", color = if (d.isSpecial) Color(0xFFFF9500) else MaterialTheme.colorScheme.onSurface)
            }
        }
        val today = LocalDate.now(AulaDate.copenhagen)
        closed.filter { it.end >= today }.take(5).forEach { c ->
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall)
                Text(if (c.start == c.end) c.start.format(fmt) else "${c.start.format(fmt)} – ${c.end.format(fmt)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (list != null && list.isEmpty() && closed.none { it.end >= today })
            Text(L.t("Institutionen har ikke lagt åbningstider i Aula."), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** "Komme/gå" in Mere: every child's times, who may pick up, and opening hours. */
@Composable
fun PresenceHubScreen() {
    val nav = LocalNav.current
    AulaScreen(title = L.t("Komme/gå"), onBack = { nav.back() }) {
        item {
            MenuGroup(L.t("Børn")) {
                AppState.children.forEach { c ->
                    MenuRow(c.name, { ChildAvatar(c, 36.dp) }) { nav.open("child/${c.id}") }
                }
            }
        }
        item { OpeningHoursCard(AppState.children.mapNotNull { it.institutionCode }.distinct().sorted()) }
    }
}

/** Every birthday in the coming year. */
@Composable
fun BirthdaysScreen() {
    val nav = LocalNav.current
    LaunchedEffect(Unit) { Repo.birthdays.load(6 * 3600) }
    val list = Repo.birthdays.value.orEmpty()
    val mine = AppState.children.map { it.id }.toSet()
    val months = list.groupBy { LocalDate.parse(it.date).withDayOfMonth(1) }.toSortedMap()
    AulaScreen(title = L.t("Fødselsdage"), onBack = { nav.back() }, refreshing = Repo.birthdays.isLoading, onRefresh = { Repo.birthdays.refresh() }) {
        if (list.isEmpty()) item { if (Repo.birthdays.isLoading) Loading() else EmptyState(L.t("Ingen fødselsdage"), Icons.Filled.Call) }
        months.forEach { (month, items) ->
            item(key = "m$month") {
                MenuGroup(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", L.locale)).replaceFirstChar { it.uppercase(L.locale) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items.forEach { BirthdayRow(it, it.id in mine) } }
                }
            }
        }
    }
}
