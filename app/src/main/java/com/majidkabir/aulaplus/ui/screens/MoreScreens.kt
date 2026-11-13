package com.majidkabir.aulaplus.ui.screens

import com.majidkabir.aulaplus.data.AppLock
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Timer
import android.widget.Toast
import androidx.compose.material.icons.filled.PlayCircle
import com.majidkabir.aulaplus.data.Demo
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.majidkabir.aulaplus.ui.components.childColor
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.Html
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.AulaGroup
import com.majidkabir.aulaplus.data.AulaNotification
import com.majidkabir.aulaplus.data.Comment
import com.majidkabir.aulaplus.data.CommonFile
import com.majidkabir.aulaplus.data.Contact
import com.majidkabir.aulaplus.data.DocumentContent
import com.majidkabir.aulaplus.data.Loadable
import com.majidkabir.aulaplus.data.Post
import com.majidkabir.aulaplus.data.Recipient
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.data.SecureDocument
import com.majidkabir.aulaplus.ui.AulaBottomBar
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.NavCache
import com.majidkabir.aulaplus.ui.components.AttachmentList
import com.majidkabir.aulaplus.ui.components.ChildAvatar
import com.majidkabir.aulaplus.ui.components.CountBadge
import com.majidkabir.aulaplus.ui.components.Dims
import com.majidkabir.aulaplus.ui.components.EmptyState
import com.majidkabir.aulaplus.ui.components.HtmlText
import com.majidkabir.aulaplus.ui.components.IconTile
import com.majidkabir.aulaplus.ui.components.InitialsAvatar
import com.majidkabir.aulaplus.ui.components.Loading
import com.majidkabir.aulaplus.ui.components.SectionTitle
import com.majidkabir.aulaplus.ui.components.StaleBanner
import com.majidkabir.aulaplus.ui.components.card
import com.majidkabir.aulaplus.ui.components.openSafely
import com.majidkabir.aulaplus.ui.theme.AppTheme
import com.majidkabir.aulaplus.ui.theme.ThemeStore
import com.majidkabir.aulaplus.work.RefreshWorker
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

private val Purple = Color(0xFFAF52DE)

@Composable
fun MenuGroup(title: String?, content: @Composable () -> Unit) {
    Column {
        if (title != null) Text(title.uppercase(L.locale), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        Column(Modifier.card(4.dp)) { content() }
    }
}

@Composable
fun MenuRow(title: String, icon: (@Composable () -> Unit), badge: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.rowRadius)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (badge > 0) CountBadge(badge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    val missing = (Repo.vacationRequests.value ?: emptyList()).count { it.isMissingAnswer }
    AulaScreen(title = L.t("Mere"), bottomBar = { AulaBottomBar() }, spacing = 20.dp) {
        item {
            MenuGroup(L.t("Skole")) {
                MenuRow(L.t("Opslag"), { IconTile(Icons.Filled.Newspaper, Purple) }) { nav.open("posts") }
                MenuRow(L.t("Galleri"), { IconTile(Icons.Filled.PhotoLibrary, Color(0xFFFF2D55)) }, Repo.newGalleryCount) { nav.open("gallery") }
                MenuRow(L.t("Vigtige datoer"), { IconTile(Icons.Filled.Star, Color(0xFFFFCC00)) }) { nav.open("dates") }
                MenuRow(L.t("Dokumenter"), { IconTile(Icons.Filled.Description, Color(0xFF007AFF)) }) { nav.open("documents") }
                MenuRow(L.t("Kontakter"), { IconTile(Icons.Filled.People, Color(0xFF34C759)) }) { nav.open("contacts") }
            }
        }
        item {
            MenuGroup(L.t("Komme/gå")) {
                AppState.children.forEach { c -> MenuRow(c.firstName, { ChildAvatar(c, 30.dp) }) { nav.open("child/${c.id}") } }
                MenuRow(L.t("Ferieanmodninger"), { IconTile(Icons.Filled.WbSunny, Color(0xFFFF9500)) }, missing) { nav.open("vacations") }
            }
        }
        item {
            MenuGroup(null) {
                MenuRow(L.t("Notifikationer"), { IconTile(Icons.Filled.Notifications, Color(0xFFFF3B30)) }) { nav.open("notifications") }
                MenuRow(L.t("Profil og indstillinger"), { IconTile(Icons.Filled.Settings, Color(0xFF8E8E93)) }) { nav.open("settings") }
            }
        }
    }
}

@Composable
fun PostCard(p: Post, compact: Boolean, onClick: (() -> Unit)? = null) {
    Column(Modifier.card().let { if (onClick != null) it.clickable(onClick = onClick) else it }.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InitialsAvatar(p.author, Purple, 30.dp)
            Column(Modifier.weight(1f)) {
                Text(p.author, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(AulaDate.listDate(p.dateMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.isImportant) Row(Modifier.background(Color(0x1FFF3B30), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Filled.Error, null, tint = Color(0xFFFF3B30), modifier = Modifier.size(14.dp))
                Text(L.t("Vigtigt"), color = Color(0xFFFF3B30), style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(p.title, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            maxLines = if (compact) 2 else Int.MAX_VALUE)
        if (compact) {
            Text(Html.plain(p.html), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
            if (p.attachments.isNotEmpty()) Text("📎 ${p.attachments.size}", style = MaterialTheme.typography.bodySmall)
        } else {
            HorizontalDivider()
            HtmlText(p.html)
            AttachmentList(p.attachments)
        }
    }
}

@Composable
fun PostsScreen() {
    val nav = LocalNav.current
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { Repo.posts.load(300) }
    val posts = (Repo.posts.value ?: emptyList()).filter { search.isBlank() || listOf(it.title, it.author, Html.plain(it.html)).any { s -> s.contains(search, true) } }
    AulaScreen(title = L.t("Opslag"), onBack = { nav.back() }, large = true, refreshing = Repo.posts.isLoading, onRefresh = { Repo.posts.refresh() }) {
        item { SearchField(search, L.t("Søg i opslag")) { search = it } }
        item { StaleBanner(Repo.posts.error, Repo.posts.savedAt) }
        if (posts.isEmpty()) item { if (Repo.posts.isLoading) Loading() else EmptyState(L.t("Ingen opslag"), Icons.Filled.Newspaper) }
        posts.forEach { p -> item(key = p.id) { PostCard(p, true) { nav.open("post/${p.id}") } } }
    }
}

@Composable
fun PostDetailScreen(id: Int) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var post by remember { mutableStateOf(Repo.posts.value?.firstOrNull { it.id == id }) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        if (post == null) post = runCatching { AulaApi.post(id) }.getOrNull()
        comments = runCatching { AulaApi.comments(id) }.getOrDefault(emptyList())
    }
    AulaScreen(title = "", onBack = { nav.back() }) {
        val p = post
        if (p == null) { item { Loading() }; return@AulaScreen }
        item { PostCard(p, false) }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
        item { SectionTitle(L.t("Kommentarer (%d)", comments.size), Icons.AutoMirrored.Filled.Chat) }
        if (comments.isEmpty()) item {
            Text(L.t("Ingen kommentarer endnu"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        comments.forEach { c ->
            item(key = "c${c.id}") {
                Row(Modifier.card(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InitialsAvatar(c.author, MaterialTheme.colorScheme.primary, 28.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row { Text(c.author, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(AulaDate.listDate(c.dateMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        HtmlText(c.html)
                    }
                }
            }
        }
        if (p.canComment) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text(L.t("Skriv en kommentar")) },
                    shape = RoundedCornerShape(24.dp), maxLines = 4)
                FilledIconButton(enabled = draft.isNotBlank() && !posting, onClick = {
                    val me = Repo.session.value?.guardianProfileIds?.firstOrNull()
                    if (me == null) { error = L.t("Kunne ikke finde din profil. Træk ned for at opdatere."); return@FilledIconButton }
                    scope.launch {
                        posting = true
                        runCatching { AulaApi.addComment(id, draft, me) }
                            .onSuccess { draft = ""; comments = runCatching { AulaApi.comments(id) }.getOrDefault(comments) }
                            .onFailure { error = L.t("Ikke sendt: %s", it.message ?: "") }
                        posting = false
                    }
                }, modifier = Modifier.semantics { contentDescription = L.t("Send") }) {
                    if (posting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Filled.Send, null)
                }
            }
        }

    }
}

private val galleryTints = listOf(Color(0xFFFF2D55), Color(0xFFFF9500), Color(0xFF30B0C7), Color(0xFFAF52DE), Color(0xFF5856D6), Color(0xFF34C759))

/** Red "3 nye" pill for new photos. */
@Composable
fun NewBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Text(L.t("%d nye", count), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        modifier = modifier.clip(CircleShape).background(Color(0xFFFF3B30)).padding(horizontal = 7.dp, vertical = 3.dp)
            .semantics { contentDescription = L.t("%d nye billeder", count) })
}

@Composable
private fun AlbumTile(title: String, subtitle: String, coverUrl: String?, tint: Color, newCount: Int, modifier: Modifier,
                      placeholder: @Composable () -> Unit = { Icon(Icons.Filled.Collections, null, tint = tint, modifier = Modifier.size(36.dp)) }) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.40f), tint.copy(alpha = 0.14f)))),
            contentAlignment = Alignment.Center) {
            if (coverUrl != null) AsyncImage(coverUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else placeholder()
            NewBadge(newCount, Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GalleryScreen() {
    val nav = LocalNav.current
    LaunchedEffect(Unit) {
        launch { Repo.albums.load(600) }
        launch { Repo.notifications.load(60) }
    }
    val albums = Repo.albums.value ?: emptyList()
    val children = AppState.children
    AulaScreen(title = L.t("Galleri"), onBack = { nav.back() }, large = true, refreshing = Repo.albums.isLoading,
        onRefresh = { coroutineScope { launch { Repo.notifications.refresh() }; Repo.albums.refresh() } }) {
        item { StaleBanner(Repo.albums.error, Repo.albums.savedAt) }
        if (children.isNotEmpty()) {
            item { SectionTitle(L.t("Billeder af dine børn")) }
            children.chunked(2).forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c ->
                            val media = Repo.taggedMedia(c.id)
                            LaunchedEffect(c.id) { media.load(3600) }
                            AlbumTile(L.t("Billeder af %s", c.firstName),
                                media.value?.let { L.t("%d billeder", it.size) } ?: if (media.error != null) L.t("Kunne ikke hente") else L.t("Henter …"),
                                media.value?.firstOrNull()?.thumbUrl, childColor(c.id), Repo.newCount(childId = c.id),
                                Modifier.weight(1f).clickable { nav.open("tagged/${c.id}") }) { ChildAvatar(c, 64.dp) }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
            if (albums.isNotEmpty()) item { SectionTitle(L.t("Albums")) }
        }
        if (albums.isEmpty() && children.isEmpty()) item { if (Repo.albums.isLoading) Loading() else EmptyState(L.t("Ingen albums"), Icons.Filled.PhotoLibrary) }
        albums.chunked(2).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { a ->
                        AlbumTile(a.title, listOfNotNull(AulaDate.listDate(a.dateMs), a.count?.let { L.t("%d billeder", it) }).joinToString(" · "),
                            a.coverUrl, galleryTints[a.id.mod(galleryTints.size)], Repo.newCount(albumId = a.id),
                            Modifier.weight(1f).clickable { nav.open("album/${a.id}") })
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

private sealed interface PhotoFilter {
    data object All : PhotoFilter
    data object New : PhotoFilter
    data class Child(val id: Int) : PhotoFilter
}

/** An album, or with [childId] the child's own "Billeder af …" album. */
@Composable
fun AlbumScreen(albumId: Int, childId: Int? = null) {
    val nav = LocalNav.current
    val media = if (childId != null) Repo.taggedMedia(childId) else Repo.media(albumId)
    val children = AppState.children
    // Photos that were new when the album was opened; kept for this visit after Aula is told they're seen.
    var newIds by remember { mutableStateOf(emptySet<Int>()) }
    var filter by remember { mutableStateOf<PhotoFilter>(PhotoFilter.All) }
    LaunchedEffect(albumId, childId) {
        Repo.notifications.load(60)
        media.load(if (childId == null) 3600 else 600)
        // A new-photo notice means the cached list may be missing it.
        val at = media.savedAt
        if (Repo.newCount(albumId.takeIf { childId == null }, childId) > 0 && at != null && System.currentTimeMillis() - at > 30_000) media.refresh()
        val all = media.value.orEmpty()
        newIds = newIds + (Repo.newMediaIds intersect all.map { it.id }.toSet())
        Repo.markSeen(albumId.takeIf { childId == null }, childId, all)
    }
    val title = if (childId != null) L.t("Billeder af %s", children.firstOrNull { it.id == childId }?.firstName ?: "")
        else Repo.albums.value?.firstOrNull { it.id == albumId }?.title ?: L.t("Galleri")
    val all = media.value ?: emptyList()
    val shown = when (val f = filter) {
        PhotoFilter.All -> all
        PhotoFilter.New -> all.filter { it.id in newIds }
        is PhotoFilter.Child -> all.filter { it.isTagged(f.id) }
    }
    val newCount = all.count { it.id in newIds }
    // In a child's own album every photo is that child's: no child chips there.
    val kids = if (childId != null) emptyList() else children.mapNotNull { c -> all.count { it.isTagged(c.id) }.takeIf { it > 0 }?.let { c to it } }
    var viewing by remember { mutableStateOf<Int?>(null) }
    AulaScreen(title = title, onBack = { nav.back() }, refreshing = media.isLoading, onRefresh = { media.refresh() }, spacing = 3.dp) {
        item { StaleBanner(media.error, media.savedAt) }
        if (newCount > 0 || kids.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(L.t("Alle (%d)", all.size), filter == PhotoFilter.All, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary) { filter = PhotoFilter.All }
                if (newCount > 0) FilterPill(L.t("Nye (%d)", newCount), filter == PhotoFilter.New, Color(0xFFFF3B30)) {
                    filter = if (filter == PhotoFilter.New) PhotoFilter.All else PhotoFilter.New
                }
                kids.forEach { (c, n) ->
                    val f = PhotoFilter.Child(c.id)
                    FilterPill("${c.firstName} ($n)", filter == f, childColor(c.id)) { filter = if (filter == f) PhotoFilter.All else f }
                }
            }
        }
        if (all.isEmpty()) item {
            when {
                media.isLoading || (media.value == null && media.error == null) -> Loading()
                media.value == null -> EmptyState(L.t("Kunne ikke hente"), Icons.Filled.PhotoLibrary)
                else -> EmptyState(L.t("Ingen billeder"), Icons.Filled.PhotoLibrary)
            }
        }
        else if (shown.isEmpty()) item { EmptyState(L.t("Ingen billeder passer til filteret."), Icons.Filled.PhotoLibrary) }
        shown.chunked(3).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { m ->
                        val mine = if (childId != null) emptyList() else children.filter { m.isTagged(it.id) }
                        val label = listOfNotNull(if (m.isVideo) L.t("Video") else L.t("Billede"), if (m.id in newIds) L.t("Ny") else null,
                            m.tags.takeIf { it.isNotEmpty() }?.let { t -> L.t("Tagget: %s", t.joinToString(", ") { it.name }) }).joinToString(", ")
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).clickable { viewing = shown.indexOf(m) }
                            .clearAndSetSemantics { contentDescription = label; role = Role.Button },
                            contentAlignment = Alignment.Center) {
                            AsyncImage(m.thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (m.isVideo) Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            if (m.id in newIds) Text(L.t("NY"), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp).clip(CircleShape).background(Color(0xFFFF3B30))
                                    .padding(horizontal = 5.dp, vertical = 1.dp))
                            if (mine.isNotEmpty()) Row(Modifier.align(Alignment.BottomStart).padding(4.dp), horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                mine.forEach { ChildAvatar(it, 20.dp) }
                            }
                        }
                    }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
    viewing?.let { MediaViewer(shown, it, children, newIds) { viewing = null } }
}

@Composable
private fun FilterPill(title: String, selected: Boolean, color: Color, onColor: Color = Color.White, onClick: () -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge,
        color = if (selected) onColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).background(if (selected) color else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 7.dp))
}

private fun notificationLook(n: AulaNotification): Pair<ImageVector, Color> {
    val m = (n.module ?: "").lowercase()
    return when {
        n.threadId != null || "messag" in m -> Icons.AutoMirrored.Filled.Chat to Color(0xFF007AFF)
        n.albumId != null || "gallery" in m -> Icons.Filled.Photo to Color(0xFFFF2D55)
        n.postId != null || "post" in m -> Icons.Filled.Newspaper to Purple
        "calendar" in m -> Icons.Filled.CalendarMonth to Color(0xFFFF3B30)
        "presence" in m -> Icons.Filled.DirectionsWalk to Color(0xFF30B0C7)
        else -> Icons.Filled.Notifications to Color(0xFF8E8E93)
    }
}

@Composable
fun NotificationsScreen() {
    val nav = LocalNav.current
    LaunchedEffect(Unit) { Repo.notifications.load(60) }
    val list = Repo.notifications.value ?: emptyList()
    AulaScreen(title = L.t("Notifikationer"), onBack = { nav.back() }, large = true, refreshing = Repo.notifications.isLoading,
        onRefresh = { Repo.notifications.refresh() }) {
        item { StaleBanner(Repo.notifications.error, Repo.notifications.savedAt) }
        if (list.isEmpty()) item { if (Repo.notifications.isLoading) Loading() else EmptyState(L.t("Ingen notifikationer"), Icons.Outlined.NotificationsOff) }
        else item {
            Column(Modifier.card(4.dp)) {
                list.forEachIndexed { i, n ->
                    val (icon, color) = notificationLook(n)
                    val tagged = n.relatedChildId?.takeIf { n.isTaggedInMedia && AppState.children.any { c -> c.id == it } }
                    val route = tagged?.let { "tagged/$it" } ?: n.threadId?.let { "thread/$it" } ?: n.albumId?.let { "album/$it" } ?: n.postId?.let { "post/$it" }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.rowRadius)).clickable(enabled = route != null) { route?.let(nav::open) }
                        .padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon, color)
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(n.childName, AulaDate.listDate(n.dateMs)).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (route != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                    if (i < list.size - 1) HorizontalDivider(Modifier.padding(start = 54.dp))
                }
            }
        }
    }
}

@Composable
fun ImportantDatesScreen() {
    val nav = LocalNav.current
    LaunchedEffect(Unit) { Repo.importantDates.load(1800) }
    val list = Repo.importantDates.value ?: emptyList()
    AulaScreen(title = L.t("Vigtige datoer"), onBack = { nav.back() }, large = true, refreshing = Repo.importantDates.isLoading,
        onRefresh = { Repo.importantDates.refresh() }) {
        item { StaleBanner(Repo.importantDates.error, Repo.importantDates.savedAt) }
        if (list.isEmpty()) item { EmptyState(L.t("Ingen vigtige datoer"), Icons.Filled.Star) }
        else item { Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(14.dp)) { list.forEach { ImportantDateRow(it) } } }
    }
}

@Composable
fun ContactsScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    var groups by remember { mutableStateOf<List<AulaGroup>>(emptyList()) }
    var groupId by remember { mutableStateOf<Int?>(null) }
    var filter by remember { mutableStateOf("guardian") }
    var search by remember { mutableStateOf("") }
    var loadable by remember { mutableStateOf<Loadable<List<Contact>>?>(null) }
    var groupsLoaded by remember { mutableStateOf(Demo.isOn) }
    LaunchedEffect(Unit) {
        groups = runCatching { AulaApi.groups(AppState.children.map { it.id }) }.getOrDefault(emptyList())
        groupsLoaded = true
        if (groupId == null) groupId = groups.firstOrNull()?.id
    }
    LaunchedEffect(groupId, filter) {
        val gid = groupId ?: return@LaunchedEffect
        val f = filter
        loadable = Loadable("contacts-$gid-$f", ListSerializer(Contact.serializer())) { AulaApi.contacts(gid, f) }.also { it.load(86_400) }
    }
    val shown = (loadable?.value ?: emptyList()).filter { search.isBlank() || it.name.contains(search, true) || (it.detail ?: "").contains(search, true) }
    AulaScreen(title = L.t("Kontakter"), onBack = { nav.back() }, large = true) {
        if (groups.size > 1) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                groups.forEach { g ->
                    androidx.compose.material3.FilterChip(groupId == g.id, { groupId = g.id }, { Text(g.name, maxLines = 1) })
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("guardian" to L.t("Forældre"), "child" to L.t("Børn"), "employee" to L.t("Ansatte")).forEachIndexed { i, (key, label) ->
                    SegmentedButton(filter == key, { filter = key }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }
        }
        item { SearchField(search, L.t("Søg navn")) { search = it } }
        loadable?.let { l -> item { StaleBanner(l.error, l.savedAt) } }
        val waiting = !groupsLoaded || (groups.isNotEmpty() && (loadable == null || loadable?.isLoading == true))
        if (shown.isEmpty()) item { if (waiting) Loading() else EmptyState(L.t("Ingen kontakter"), Icons.Filled.People) }
        else item {
            Column(Modifier.card(4.dp)) {
                shown.forEachIndexed { i, c ->
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (c.pictureUrl != null) AsyncImage(c.pictureUrl, null, Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        else InitialsAvatar(c.name, Color.Gray, 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            c.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                        }
                        c.phones.firstOrNull()?.let { ph ->
                            IconButton(onClick = { ctx.openSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ph.filter { it.isDigit() || it == '+' }))) },
                                modifier = Modifier.semantics { contentDescription = ph }) { Icon(Icons.Filled.Call, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                        c.email?.let { em ->
                            IconButton(onClick = { ctx.openSafely(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$em"))) },
                                modifier = Modifier.semantics { contentDescription = em }) { Icon(Icons.Filled.Email, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                        IconButton(onClick = {
                            NavCache.put("compose-recipient", Recipient(c.id, c.name, c.detail))
                            nav.open("compose")
                        }, modifier = Modifier.semantics { contentDescription = L.t("Ny besked") }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (i < shown.size - 1) HorizontalDivider(Modifier.padding(start = 62.dp))
                }
            }
        }
    }
}

@Composable
fun DocumentsScreen() {
    val nav = LocalNav.current
    var tab by remember { mutableStateOf(0) }
    val docs = remember {
        Loadable("secure-docs", ListSerializer(SecureDocument.serializer())) {
            AulaApi.secureDocuments(AulaApi.bootstrap().guardianProfileIds)
        }
    }
    val files = remember {
        Loadable("common-files", ListSerializer(CommonFile.serializer())) {
            AulaApi.commonFiles(AulaApi.bootstrap().children.mapNotNull { it.institutionCode }.distinct())
        }
    }
    LaunchedEffect(Unit) { docs.load(600); files.load(3600) }
    AulaScreen(title = L.t("Dokumenter"), onBack = { nav.back() }, large = true, refreshing = docs.isLoading || files.isLoading,
        onRefresh = { docs.refresh(); files.refresh() }) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(tab == 0, { tab = 0 }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(L.t("Sikre dokumenter")) }
                SegmentedButton(tab == 1, { tab = 1 }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(L.t("Fælles filer")) }
            }
        }
        if (tab == 0) {
            item { StaleBanner(docs.error, docs.savedAt) }
            val list = docs.value ?: emptyList()
            if (list.isEmpty()) item { if (docs.isLoading) Loading() else EmptyState(L.t("Ingen dokumenter"), Icons.Outlined.Folder) }
            else item {
                Column(Modifier.card(4.dp)) {
                    list.forEachIndexed { i, d ->
                        MenuRow(d.title, { IconTile(Icons.Filled.Description, Color(0xFF007AFF)) }) { nav.open("doc/" + NavCache.put("doc-${d.id}", d)) }
                        if (i < list.size - 1) HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }
        } else {
            item { StaleBanner(files.error, files.savedAt) }
            val list = files.value ?: emptyList()
            if (list.isEmpty()) item { if (files.isLoading) Loading() else EmptyState(L.t("Ingen filer"), Icons.Outlined.Folder) }
            list.forEach { f ->
                item {
                    Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(f.title, style = MaterialTheme.typography.titleSmall)
                        f.institution?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        f.attachment?.let { AttachmentList(listOf(it)) }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentDetailScreen(key: String) {
    val nav = LocalNav.current
    val doc = NavCache.items[key] as? SecureDocument ?: run {
        // The app was restarted on this screen: the document is no longer in memory.
        AulaScreen(title = L.t("Dokumenter"), onBack = { nav.back() }) { item { EmptyState(L.t("Ingen dokumenter"), Icons.Outlined.Folder) } }
        return
    }
    var content by remember { mutableStateOf<DocumentContent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(doc.id) { runCatching { AulaApi.documentContent(doc) }.onSuccess { content = it }.onFailure { error = it.message } }
    AulaScreen(title = doc.title, onBack = { nav.back() }) {
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(doc.title, style = MaterialTheme.typography.headlineSmall)
                Text(listOfNotNull(doc.creator, AulaDate.listDate(doc.updatedMs)).joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                val c = content
                when {
                    c != null -> { if (c.html.isNotBlank()) HtmlText(c.html); AttachmentList(c.attachments) }
                    error != null -> Text(error ?: "", color = Color(0xFFFF3B30))
                    else -> Loading()
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val s = Repo.session.value
    var notificationsOn by remember { mutableStateOf(RefreshWorker.isEnabled) }
    var confirmLogout by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted
        RefreshWorker.setEnabled(ctx, granted)
        if (!granted) Toast.makeText(ctx, L.t("Tillad notifikationer for Aula+ i Androids indstillinger."), Toast.LENGTH_LONG).show()
    }
    AulaScreen(title = L.t("Profil"), onBack = { nav.back() }, spacing = 20.dp) {
        if (s != null) item {
            MenuGroup(L.t("Logget ind som")) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InitialsAvatar(s.displayName, ThemeStore.theme.brandLight, 42.dp)
                    Text(s.displayName, style = MaterialTheme.typography.titleMedium)
                }
                s.children.forEach { c ->
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChildAvatar(c, 36.dp)
                        Column {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            Text(c.institutionName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { MenuGroup(L.t("Tema")) { ThemePicker() } }
        item {
            MenuGroup(null) {
                ToggleRow(L.t("Notifikationer om nye beskeder og opslag"), Icons.Filled.NotificationsActive, Color(0xFFFF3B30), notificationsOn) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { notificationsOn = on; RefreshWorker.setEnabled(ctx, on) }
                }
                ToggleRow(L.t("Lås med fingeraftryk"), Icons.Filled.Fingerprint, Color(0xFF34C759), AppLock.isEnabled) { on ->
                    when {
                        !on -> AppLock.disable()
                        !AppLock.isAvailable(ctx) ->
                            Toast.makeText(ctx, L.t("Telefonen har ingen skærmlås eller fingeraftryk sat op."), Toast.LENGTH_LONG).show()
                        else -> (ctx as? androidx.fragment.app.FragmentActivity)?.let { AppLock.enable(it) }
                    }
                }
                if (AppLock.isEnabled) Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Filled.Timer, Color(0xFFFF9500))
                        Text(L.t("Lås igen"), style = MaterialTheme.typography.bodyLarge)
                    }
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLock.Delay.entries.forEach { d ->
                            FilterChip(AppLock.delay == d, { AppLock.delay = d }, { Text(d.label) })
                        }
                    }
                    Text(L.t("Beskeder og kalender skjules, når du skifter app, og kræver fingeraftryk eller kode for at åbne igen."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Filled.Language, Color(0xFF007AFF))
                        Text(L.t("Sprog"), style = MaterialTheme.typography.bodyLarge)
                    }
                    val langs = listOf("" to L.t("Automatisk"), "da" to "Dansk", "en" to "English")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        langs.forEachIndexed { i, (k, label) ->
                            SegmentedButton(L.override == k, {
                                L.override = k
                                if (AulaApp.isReady) AulaApp.prefs.edit().putString("lang", k).apply()
                            }, SegmentedButtonDefaults.itemShape(i, langs.size)) { Text(label, maxLines = 1) }
                        }
                    }
                }
            }
        }
        item {
            Text(L.t("Aula+ tjekker for nyt i baggrunden, når Android tillader det (typisk hvert 15.–60. minut)."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
        }
        item {
            MenuGroup(null) {
                TextButton(onClick = {
                    com.majidkabir.aulaplus.data.DiskCache.clear()
                    AulaApi.clearDownloads()
                    Toast.makeText(ctx, L.t("Cache ryddet."), Toast.LENGTH_SHORT).show()
                }, Modifier.fillMaxWidth()) { Text(L.t("Ryd cache")) }
                TextButton(onClick = { confirmLogout = true }, Modifier.fillMaxWidth()) { Text(L.t("Log ud"), color = Color(0xFFFF3B30)) }
            }
        }
        item {
            Text(L.t("Aula+ er en uofficiel app og er ikke lavet af eller godkendt af Aula/KOMBIT. Den bruger samme API som Aulas egen app."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp))
        }
    }
    if (confirmLogout) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text(L.t("Log ud af Aula+?")) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; AppState.logout() }) { Text(L.t("Log ud"), color = Color(0xFFFF3B30)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(L.t("Annuller")) } },
    )
}

@Composable
private fun ToggleRow(title: String, icon: ImageVector, color: Color, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        IconTile(icon, color)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@Composable
fun ThemePicker() {
    val options = AppTheme.entries.filter { it != AppTheme.DYNAMIC || Build.VERSION.SDK_INT >= 31 }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        options.forEach { t ->
            val sel = ThemeStore.theme == t
            Column(Modifier.clickable { ThemeStore.set(t) }.semantics { contentDescription = t.label }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(50.dp).border(2.dp, if (sel) t.brandLight else Color.Transparent, CircleShape).padding(4.dp)
                    .clip(CircleShape).background(Brush.linearGradient(listOf(t.brandLight, t.second))), contentAlignment = Alignment.Center) {
                    if (sel) Icon(Icons.Filled.Check, null, tint = Color.White)
                }
                Text(t.label, style = MaterialTheme.typography.labelSmall,
                    color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
