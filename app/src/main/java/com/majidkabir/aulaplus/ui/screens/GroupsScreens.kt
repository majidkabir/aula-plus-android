package com.majidkabir.aulaplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.Album
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.AulaGroup
import com.majidkabir.aulaplus.data.ConsentItem
import com.majidkabir.aulaplus.data.Contact
import com.majidkabir.aulaplus.data.Demo
import com.majidkabir.aulaplus.data.Event
import com.majidkabir.aulaplus.data.GroupMember
import com.majidkabir.aulaplus.data.Post
import com.majidkabir.aulaplus.data.SearchHit
import com.majidkabir.aulaplus.data.SearchKind
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.NavCache
import com.majidkabir.aulaplus.ui.components.EmptyState
import com.majidkabir.aulaplus.ui.components.IconTile
import com.majidkabir.aulaplus.ui.components.InitialsAvatar
import com.majidkabir.aulaplus.ui.components.Loading
import com.majidkabir.aulaplus.ui.components.MediaThumb
import com.majidkabir.aulaplus.ui.components.card
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
private fun PersonAvatar(name: String, url: String?, size: Int = 36) {
    if (url != null) AsyncImage(url, null, Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else InitialsAvatar(name, MaterialTheme.colorScheme.primary, size.dp)
}

/** "Mine grupper", as on aula.dk. */
@Composable
fun GroupsScreen() {
    val nav = LocalNav.current
    var groups by remember { mutableStateOf<List<AulaGroup>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (Demo.isOn) { groups = listOf(AulaGroup(1, "Solsikken")); return@LaunchedEffect }
        runCatching { AulaApi.myGroups() }.onSuccess { groups = it }.onFailure { error = it.message; groups = emptyList() }
    }
    AulaScreen(title = L.t("Grupper"), onBack = { nav.back() }) {
        val list = groups
        if (list == null) item { Loading() }
        else if (list.isEmpty()) item { EmptyState(L.t("Ingen grupper"), Icons.Filled.Groups) }
        else item {
            MenuGroup(null) {
                list.forEach { g -> MenuRow(g.name, { IconTile(Icons.Filled.Groups, Color(0xFF32ADE6)) }) { nav.open("group/" + NavCache.put("group-${g.id}", g)) } }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
}

/** One group: its posts, calendar, photos and members. */
@Composable
fun GroupScreen(key: String) {
    val nav = LocalNav.current
    val group = remember { NavCache.items[key] as? AulaGroup } ?: run { nav.back(); return }
    var tab by remember { mutableStateOf(0) }
    var posts by remember { mutableStateOf<List<Post>?>(null) }
    var events by remember { mutableStateOf<List<Event>?>(null) }
    var albums by remember { mutableStateOf<List<Album>?>(null) }
    var members by remember { mutableStateOf<List<GroupMember>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab) {
        if (Demo.isOn) { posts = emptyList(); events = emptyList(); albums = emptyList(); members = emptyList(); return@LaunchedEffect }
        error = null
        runCatching {
            when (tab) {
                0 -> if (posts == null) posts = AulaApi.groupPosts(group.id)
                1 -> if (events == null) { val f = LocalDate.now(AulaDate.copenhagen); events = AulaApi.groupEvents(group.id, f, f.plusDays(56)) }
                2 -> if (albums == null) albums = AulaApi.groupAlbums(group.id)
                else -> if (members == null) members = AulaApi.groupMembers(group.id)
            }
        }.onFailure {
            error = it.message
            when (tab) { 0 -> posts = posts ?: emptyList(); 1 -> events = events ?: emptyList(); 2 -> albums = albums ?: emptyList(); else -> members = members ?: emptyList() }
        }
    }
    val tabs = listOf(L.t("Opslag"), L.t("Kalender"), L.t("Galleri"), L.t("Medlemmer"))
    AulaScreen(title = group.name, onBack = { nav.back() }) {
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tabs.forEachIndexed { i, t -> FilterChip(tab == i, { tab = i }, { Text(t) }) }
            }
        }
        when (tab) {
            0 -> { val l = posts; if (l == null) item { Loading() } else if (l.isEmpty()) item { EmptyState(L.t("Ingen opslag"), Icons.Filled.Groups) }
                else l.forEach { p -> item(key = "p${p.id}") { PostCard(p, true) { nav.open("post/${p.id}") } } } }
            1 -> { val l = events; if (l == null) item { Loading() } else if (l.isEmpty()) item { EmptyState(L.t("Ingen begivenheder de næste 8 uger"), Icons.Filled.Groups) }
                else l.forEach { e -> item(key = "e${e.id}") { EventCard(e) { nav.open("event/" + NavCache.put("event-${e.id}", e)) } } } }
            2 -> { val l = albums; if (l == null) item { Loading() } else if (l.isEmpty()) item { EmptyState(L.t("Ingen albums"), Icons.Filled.Groups) }
                else item {
                    Column(Modifier.card(4.dp)) {
                        l.forEach { a ->
                            Row(Modifier.fillMaxWidth().clickable { nav.open("album/${a.id}") }.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                    if (a.coverUrl != null) AsyncImage(a.coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(a.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                    a.dateMs?.let { Text(AulaDate.listDate(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                } }
            else -> { val l = members; if (l == null) item { Loading() } else {
                val grouped = l.groupBy { roleGroup(it.role) }
                listOf("Medarbejdere", "Børn", "Forældre").forEach { title ->
                    val list = grouped[title].orEmpty()
                    if (list.isNotEmpty()) item(key = "g$title") {
                        MenuGroup("${L.t(title)} (${list.size})") {
                            list.forEach { m ->
                                Row(Modifier.fillMaxWidth().clickable {
                                    val c = Contact(m.id, m.name, m.detail, pictureUrl = m.pictureUrl, role = m.role, group = m.detail)
                                    nav.open("contact/" + NavCache.put("contact-${c.id}", c))
                                }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PersonAvatar(m.name, m.pictureUrl, 34)
                                    Column {
                                        Text(m.name, style = MaterialTheme.typography.bodyLarge)
                                        m.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            }
                        }
                    }
                }
            } }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
}

private fun roleGroup(role: String?) = when (role) {
    "guardian" -> "Forældre"; "child", "daycare", "early-student", "middle-late-student" -> "Børn"; else -> "Medarbejdere"
}

/** Search in all of Aula: posts, people, events and photos (messages have their own search). */
@Composable
fun GlobalSearchScreen() {
    val nav = LocalNav.current
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SearchKind.POSTS) }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(text, kind) {
        val t = text.trim()
        if (t.length < 2 || Demo.isOn) { hits = emptyList(); return@LaunchedEffect }
        delay(400)
        searching = true; error = null
        runCatching {
            val s = AulaApi.bootstrap()
            AulaApi.search(t, kind, s.guardianProfileIds, s.children.map { it.id })
        }.onSuccess { hits = it }.onFailure { error = it.message; hits = emptyList() }
        searching = false
    }
    AulaScreen(title = L.t("Søg i Aula"), onBack = { nav.back() }) {
        item { SearchField(text, L.t("Søg i Aula")) { text = it } }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, { Text(L.t(k.label)) }) }
            }
        }
        if (searching) item { Loading() }
        else if (hits.isEmpty()) item {
            EmptyState(if (text.length < 2) L.t("Søg i Aula") else L.t("Ingen resultater"), Icons.Filled.Search,
                subtitle = L.t("Opslag, personer, begivenheder og billeder. Beskeder søger du i under Beskeder."))
        } else if (kind == SearchKind.PHOTOS) {
            hits.chunked(3).forEachIndexed { r, row ->
                item(key = "r$r") {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEachIndexed { i, h ->
                            Box(Modifier.weight(1f).aspectRatio(1f).clickable { viewing = r * 3 + i }) { h.media?.let { MediaThumb(it, Modifier.fillMaxSize()) } }
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        } else item {
            Column(Modifier.card(4.dp)) {
                hits.forEachIndexed { i, h ->
                    Row(Modifier.fillMaxWidth().clickable {
                        when (h.kind) {
                            SearchKind.POSTS -> h.postId?.let { nav.open("post/$it") }
                            SearchKind.PEOPLE -> h.contact?.let { nav.open("contact/" + NavCache.put("contact-${it.id}", it)) }
                            SearchKind.EVENTS -> {
                                val ms = h.dateMs ?: System.currentTimeMillis()
                                val e = Event(h.eventId ?: h.id, h.title, ms, ms, location = h.detail)
                                nav.open("event/" + NavCache.put("event-${e.id}", e))
                            }
                            else -> {}
                        }
                    }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (h.kind == SearchKind.PEOPLE) PersonAvatar(h.title, h.pictureUrl)
                        Column(Modifier.weight(1f)) {
                            Text(h.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            h.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                            h.dateMs?.let { Text(AulaDate.listDate(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if (i < hits.lastIndex) HorizontalDivider()
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
    viewing?.let { i -> MediaViewer(hits.mapNotNull { it.media }, i, AppState.children) { viewing = null } }
}

/** "Samtykker": who may see contact details, photos etc. Same answers as on aula.dk. */
@Composable
fun ConsentsScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ConsentItem>?>(null) }
    var saved by remember { mutableStateOf<List<ConsentItem>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Demo.isOn) {
            val d = listOf(ConsentItem(1, 1, "Emma", "Mit barns kontaktoplysninger må deles med forældre og børn inden for mit barns:",
                listOf("class_or_stue", "institution", "not_at_all"), "institution", true))
            items = d; saved = d; return@LaunchedEffect
        }
        runCatching { AulaApi.consents() }.onSuccess { items = it; saved = it }.onFailure { note = it.message; items = emptyList() }
    }
    AulaScreen(title = L.t("Samtykker"), onBack = { nav.back() }, actions = {
        TextButton(enabled = items != null && items != saved && !busy, onClick = {
            val list = items ?: return@TextButton
            scope.launch {
                busy = true
                runCatching { if (!Demo.isOn) AulaApi.saveConsents(list.filter { c -> saved.firstOrNull { it.id == c.id && it.profileId == c.profileId } != c }) }
                    .onSuccess { saved = list; note = L.t("Gemt på Aula") }.onFailure { note = L.t("Ikke gemt: %s", it.message ?: "") }
                busy = false
            }
        }) { Text(L.t("Gem")) }
    }) {
        val list = items
        if (list == null) { item { Loading() }; return@AulaScreen }
        list.groupBy { it.profileName }.forEach { (person, cs) ->
            item(key = "c$person") {
                MenuGroup(person) {
                    cs.forEach { c ->
                        var open by remember { mutableStateOf(false) }
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(c.text, style = MaterialTheme.typography.bodyMedium)
                            Box {
                                TextButton(enabled = c.editable, onClick = { open = true }) { Text(c.answer?.let(::consentLabel) ?: L.t("Ikke besvaret")) }
                                DropdownMenu(open, { open = false }) {
                                    c.allowed.forEach { a ->
                                        DropdownMenuItem(text = { Text(consentLabel(a)) }, onClick = {
                                            open = false
                                            items = list.map { if (it.id == c.id && it.profileId == c.profileId) it.copy(answer = a) else it }
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        note?.let { item { Text(it, color = if (it == L.t("Gemt på Aula")) Color(0xFF34C759) else Color(0xFFFF3B30)) } }
    }
}

fun consentLabel(a: String) = when (a) {
    "class_or_stue" -> L.t("Klasse eller stue"); "institution" -> L.t("Hele institutionen"); "not_at_all" -> L.t("Slet ikke")
    "accepted" -> L.t("Ja"); "declined" -> L.t("Nej"); else -> a
}
