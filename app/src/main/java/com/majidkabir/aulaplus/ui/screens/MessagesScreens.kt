package com.majidkabir.aulaplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.Message
import com.majidkabir.aulaplus.data.Recipient
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.data.ThreadSummary
import com.majidkabir.aulaplus.ui.AulaBottomBar
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.components.AttachmentList
import com.majidkabir.aulaplus.ui.components.ChildFilterBar
import com.majidkabir.aulaplus.ui.components.ChildTag
import com.majidkabir.aulaplus.ui.components.Dims
import com.majidkabir.aulaplus.ui.components.EmptyState
import com.majidkabir.aulaplus.ui.components.HtmlText
import com.majidkabir.aulaplus.ui.components.InitialsAvatar
import com.majidkabir.aulaplus.ui.components.Loading
import com.majidkabir.aulaplus.ui.components.StaleBanner
import com.majidkabir.aulaplus.ui.components.card
import com.majidkabir.aulaplus.ui.components.pastelCard
import com.majidkabir.aulaplus.ui.components.childColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var onlyUnread by remember { mutableStateOf(false) }
    var onlyMarked by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ThreadSummary?>(null) }
    val older = remember { mutableStateListOf<ThreadSummary>() }
    var nextPage by remember { mutableIntStateOf(1) }
    var loadingOlder by remember { mutableStateOf(false) }
    var noMoreOlder by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { Repo.threads.load(30) }
    fun updateOlder(id: String, change: (ThreadSummary) -> ThreadSummary) {
        val i = older.indexOfFirst { it.id == id }
        if (i >= 0) older[i] = change(older[i])
    }

    val all = ((Repo.threads.value ?: emptyList()) + older).distinctBy { it.id }
    val selected = AppState.selectedChildId?.let(AppState::child)
    val threads = all.filter { t ->
        (!onlyUnread || !t.isRead) && (!onlyMarked || t.isMarked) &&
            (selected == null || t.regarding.isEmpty() || t.regarding.any { it.contains(selected.firstName, true) }) &&
            (search.isBlank() || listOf(t.subject, t.sender, t.preview).any { it.contains(search, true) })
    }

    AulaScreen(
        title = L.t("Beskeder"),
        actions = {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = L.t("Filtrér") }) {
                    Icon(Icons.Filled.FilterList, null, tint = if (onlyUnread || onlyMarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(L.t("Kun ulæste")) }, onClick = { onlyUnread = !onlyUnread },
                        leadingIcon = { Checkbox(onlyUnread, null) })
                    DropdownMenuItem(text = { Text(L.t("Kun markerede")) }, onClick = { onlyMarked = !onlyMarked },
                        leadingIcon = { Checkbox(onlyMarked, null) })
                }
            }
            IconButton(onClick = { nav.open("compose") }, modifier = Modifier.semantics { contentDescription = L.t("Ny besked") }) {
                Icon(Icons.Filled.Edit, null)
            }
        },
        refreshing = Repo.threads.isLoading,
        onRefresh = { older.clear(); nextPage = 1; noMoreOlder = false; Repo.threads.refresh() },
        bottomBar = { AulaBottomBar() },
    ) {
        item { SearchField(search, L.t("Søg i beskeder")) { search = it } }
        item { ChildFilterBar() }
        item { StaleBanner(Repo.threads.error, Repo.threads.savedAt) }
        if (threads.isEmpty()) {
            item { if (Repo.threads.isLoading) Loading() else EmptyState(L.t(if (onlyUnread) "Ingen ulæste beskeder" else "Ingen beskeder"), Icons.Outlined.Inbox) }
        } else threads.forEach { t ->
            // Each thread on its own rounded card; unread ones get a soft brand tint.
            item(key = "thread-${t.id}") {
                Box(if (t.isRead) Modifier.card(6.dp) else Modifier.pastelCard(MaterialTheme.colorScheme.primary, 6.dp)) {
                    ThreadRow(t, onLongClick = { deleting = t }, onStar = {
                        scope.launch {
                            Repo.setMarked(t.id, !t.isMarked); updateOlder(t.id) { it.copy(isMarked = !t.isMarked) }
                            runCatching { AulaApi.setMarked(t.id, !t.isMarked) }
                                .onFailure { Repo.setMarked(t.id, t.isMarked); updateOlder(t.id) { it.copy(isMarked = t.isMarked) } }
                        }
                    }) {
                        if (!t.isRead) updateOlder(t.id) { it.copy(isRead = true) }
                        nav.open("thread/${t.id}")
                    }
                }
            }
        }
        if (search.isBlank() && threads.isNotEmpty() && !noMoreOlder) item {
            TextButton(enabled = !loadingOlder, onClick = {
                scope.launch {
                    loadingOlder = true
                    runCatching { AulaApi.threads(nextPage) }
                        .onSuccess { (list, more) -> older += list; nextPage++; noMoreOlder = !more || list.isEmpty() }
                        .onFailure { android.widget.Toast.makeText(ctx, it.message ?: "", android.widget.Toast.LENGTH_SHORT).show() }
                    loadingOlder = false
                }
            }, modifier = Modifier.fillMaxWidth()) {
                if (loadingOlder) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(L.t("Hent ældre beskeder"))
            }
        }
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(L.t("Slet tråden \"%s\"?", t.subject)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        runCatching { AulaApi.deleteThread(t.id) }
                            .onSuccess { Repo.removeThread(t.id); older.removeAll { it.id == t.id } }
                            .onFailure { android.widget.Toast.makeText(ctx, L.t("Ikke slettet: %s", it.message ?: ""), android.widget.Toast.LENGTH_LONG).show() }
                    }
                }) { Text(L.t("Slet"), color = Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(L.t("Annuller")) } },
        )
    }
}

@Composable
fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit) {
    TextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, L.t("Fjern")) } },
        singleLine = true,
        shape = RoundedCornerShape(Dims.rowRadius),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ThreadRow(t: ThreadSummary, onLongClick: (() -> Unit)? = null, onStar: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.rowRadius))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = (if (t.isRead) "" else L.t("Ulæst. ")) + "${t.sender}. ${t.subject}. ${t.preview}"
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            InitialsAvatar(t.sender.ifEmpty { "Aula" }, com.majidkabir.aulaplus.ui.theme.Palette.stable(t.sender), 40.dp)
            if (!t.isRead) Box(Modifier.align(Alignment.TopEnd).size(13.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.sender.ifEmpty { "Aula" }, style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (t.isRead) FontWeight.Bold else FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Text(AulaDate.listDate(t.dateMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (t.isMarked) Icon(Icons.Filled.Star, null, tint = Color(0xFFFFCC00), modifier = Modifier.size(14.dp))
                if (t.isSensitive) Icon(Icons.Filled.Lock, null, tint = Color(0xFFFF9500), modifier = Modifier.size(14.dp))
                Text(t.subject, style = MaterialTheme.typography.bodyLarge, fontWeight = if (t.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (t.preview.isNotEmpty()) Text(t.preview, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (t.regarding.isNotEmpty() && AppState.children.size > 1) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                t.regarding.forEach { name ->
                    val child = AppState.children.firstOrNull { name.contains(it.firstName, true) }
                    ChildTag(child?.firstName ?: name, child?.let { childColor(it.id) } ?: Color.Gray)
                }
            }
        }
        if (onStar != null) IconButton(onClick = onStar, modifier = Modifier.size(32.dp)
            .semantics { contentDescription = L.t(if (t.isMarked) "Fjern markering" else "Markér") }) {
            Icon(if (t.isMarked) Icons.Filled.Star else Icons.Filled.StarBorder, null,
                tint = if (t.isMarked) Color(0xFFFFCC00) else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun ThreadScreen(id: String) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val page = Repo.thread(id)
    val summary = Repo.threads.value?.firstOrNull { it.id == id }
    val older = remember { mutableStateListOf<Message>() }
    var nextPage by remember { mutableIntStateOf(1) }
    var exhausted by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    val me = Repo.session.value?.displayName.orEmpty()

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var scrolledToNewest by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        // Opened from a notification or an older page: we don't know if it's read, so treat it as unread.
        val unread = summary?.isRead != true
        page.load(if (unread) 0 else 300)
        val p = page.value
        if (unread && p != null && p.isOwnMailbox) {
            p.messages.firstOrNull()?.let { newest ->
                runCatching { AulaApi.markRead(id, newest.id) }.onSuccess { Repo.markRead(id) }
            }
        }
    }
    val messages = ((page.value?.messages ?: emptyList()) + older).distinctBy { it.id }.reversed()
    // Like any chat: open at the newest message (once; loading older ones keeps the position).
    LaunchedEffect(page.value != null) {
        if (page.value != null && !scrolledToNewest) {
            scrolledToNewest = true
            val count = androidx.compose.runtime.snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 1 }
            listState.scrollToItem(count - 1)
        }
    }

    AulaScreen(
        title = page.value?.subject?.ifBlank { null } ?: summary?.subject ?: "",
        onBack = { nav.back() },
        refreshing = page.isLoading,
        onRefresh = { page.refresh() },
        spacing = 8.dp,
        listState = listState,
        bottomBar = {
            if (page.value?.isOwnMailbox != false) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    sendError?.let { Text(it, color = Color(0xFFFF3B30), style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text(L.t("Skriv et svar")) },
                            maxLines = 6, shape = RoundedCornerShape(22.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                        FilledIconButton(
                            onClick = {
                                scope.launch {
                                    sending = true; sendError = null
                                    runCatching { AulaApi.reply(id, draft.trim()) }
                                        .onSuccess { draft = ""; page.refresh(); Repo.threads.refresh() }
                                        .onFailure { sendError = L.t("Svaret blev ikke sendt: %s", it.message ?: "") }
                                    sending = false
                                }
                            },
                            enabled = draft.isNotBlank() && !sending,
                            modifier = Modifier.size(48.dp).semantics { contentDescription = L.t("Send") },
                        ) {
                            if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            else Icon(Icons.AutoMirrored.Filled.Send, null)
                        }
                    }
                }
            }
        },
    ) {
        item { StaleBanner(page.error, page.savedAt) }
        if (page.value?.moreExist == true && !exhausted) item {
            TextButton(onClick = {
                scope.launch {
                    runCatching { AulaApi.messages(id, nextPage) }.onSuccess {
                        older += it.messages; nextPage++; exhausted = !it.moreExist || it.messages.isEmpty()
                    }.onFailure { exhausted = true }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.KeyboardArrowUp, null); Text(L.t("Vis ældre beskeder"))
            }
        }
        if (page.value == null && page.isLoading) item { Loading() }
        messages.forEachIndexed { i, m ->
            val prev = messages.getOrNull(i - 1)
            val day = m.dateMs?.let(AulaDate::localDate)
            if (day != null && prev?.dateMs?.let(AulaDate::localDate) != day) item(key = "d-${m.id}") {
                Text(AulaDate.weekdayDayMonth(day), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            item(key = m.id) {
                MessageBubble(m, mine = me.isNotEmpty() && m.sender.equals(me, true), showSender = prev?.sender != m.sender)
            }
        }
    }
}

@Composable
fun MessageBubble(m: Message, mine: Boolean, showSender: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mine) Spacer(Modifier.weight(1f).widthIn(min = 40.dp))
        else Box(Modifier.size(30.dp)) { if (showSender) InitialsAvatar(m.sender, Color.Gray, 30.dp) }
        Column(
            Modifier.weight(5f, fill = false).clip(RoundedCornerShape(20.dp))
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showSender && !mine) Text(m.sender.ifEmpty { L.t("Ukendt") }, style = MaterialTheme.typography.titleSmall)
            HtmlText(m.html)
            AttachmentList(m.attachments)
            m.dateMs?.let {
                Text(AulaDate.time(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End))
            }
        }
        if (!mine) Spacer(Modifier.width(24.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ComposeScreen() {
    val nav = LocalNav.current
    val initial = remember { com.majidkabir.aulaplus.ui.NavCache.items.remove("compose-recipient") as? Recipient }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Recipient>>(emptyList()) }
    val recipients = remember { mutableStateListOf<Recipient>().apply { if (initial != null) add(initial) } }
    var subject by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var sensitive by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300)
        results = runCatching { AulaApi.findRecipients(term) }.getOrDefault(emptyList())
    }
    val canSend = recipients.isNotEmpty() && subject.isNotBlank() && text.isNotBlank() && !sending
    AulaScreen(
        title = L.t("Ny besked"), onBack = { nav.back() },
        actions = {
            TextButton(enabled = canSend, onClick = {
                scope.launch {
                    sending = true; error = null
                    runCatching { AulaApi.startThread(subject, text, recipients.toList(), sensitive) }
                        .onSuccess { Repo.threads.refresh(); nav.back() }
                        .onFailure { error = L.t("Beskeden blev ikke sendt: %s", it.message ?: "") }
                    sending = false
                }
            }) { if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(L.t("Send")) }
        },
    ) {
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L.t("Til"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (recipients.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recipients.forEach { r ->
                        Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable { recipients.remove(r) }
                            .padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Icon(Icons.Filled.Close, L.t("Fjern"), Modifier.size(14.dp))
                        }
                    }
                }
                SearchField(query, L.t("Søg lærer, pædagog, forælder…")) { query = it }
                results.filter { r -> recipients.none { it.id == r.id } }.forEach { r ->
                    Column(Modifier.fillMaxWidth().clickable { recipients += r; query = ""; results = emptyList() }.padding(vertical = 6.dp)) {
                        Text(r.name, style = MaterialTheme.typography.bodyLarge)
                        r.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(subject, { subject = it }, Modifier.fillMaxWidth(), label = { Text(L.t("Emne")) }, singleLine = true)
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text(L.t("Besked")) }, minLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L.t("Følsom besked"), Modifier.weight(1f))
                    Switch(sensitive, { sensitive = it })
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
}
