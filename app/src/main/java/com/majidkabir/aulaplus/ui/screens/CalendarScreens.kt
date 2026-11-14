package com.majidkabir.aulaplus.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.Event
import com.majidkabir.aulaplus.data.EventDetail
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.data.Rsvp
import com.majidkabir.aulaplus.ui.AulaBottomBar
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.NavCache
import com.majidkabir.aulaplus.ui.components.AttachmentList
import com.majidkabir.aulaplus.ui.components.ChildFilterBar
import com.majidkabir.aulaplus.ui.components.ChildTag
import com.majidkabir.aulaplus.ui.components.Dims
import com.majidkabir.aulaplus.ui.components.EmptyState
import com.majidkabir.aulaplus.ui.components.HtmlText
import com.majidkabir.aulaplus.ui.components.Loading
import com.majidkabir.aulaplus.ui.components.SectionTitle
import com.majidkabir.aulaplus.ui.components.StaleBanner
import com.majidkabir.aulaplus.ui.components.WeekSwitcher
import com.majidkabir.aulaplus.ui.components.card
import com.majidkabir.aulaplus.ui.components.openSafely
import com.majidkabir.aulaplus.ui.components.childColor
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarScreen() {
    val nav = LocalNav.current
    var day by remember { mutableStateOf(AulaDate.today()) }
    val week = Repo.events(day)
    LaunchedEffect(AulaDate.weekKey(day)) { week.load(600) }
    LaunchedEffect(Unit) { Repo.birthdays.load(6 * 3600) }
    val selected = AppState.selectedChildId
    fun matches(e: Event) = selected == null || e.childIds.isEmpty() || selected in e.childIds
    val all = ((week.value ?: emptyList()) + Repo.birthdayEvents(day)).filter(::matches)
    val dayEvents = all.filter { it.occursOn(day) }.sortedWith(compareByDescending<Event> { it.allDay }.thenBy { it.startMs })
    val isToday = day == AulaDate.today()
    val now = System.currentTimeMillis()

    AulaScreen(
        title = L.t("Kalender"),
        actions = { if (!isToday) TextButton(onClick = { day = AulaDate.today() }) { Text(L.t("I dag")) } },
        refreshing = week.isLoading, onRefresh = { week.refresh() },
        bottomBar = { AulaBottomBar() },
    ) {
        item { ChildFilterBar() }
        item {
            Column(Modifier.card(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WeekSwitcher(day, { day = it })
                DayStrip(day, all) { day = it }
            }
        }
        item { StaleBanner(week.error, week.savedAt) }
        item {
            FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)) {
                Text(AulaDate.weekdayDayMonth(day), style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterVertically))
                if (isToday) Text(L.t("I dag"), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterVertically).background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        if (dayEvents.isEmpty()) {
            item { if (week.isLoading && week.value == null) Loading() else EmptyState(L.t("Intet i kalenderen"), Icons.Filled.CalendarMonth, subtitle = L.t("Der er ingen begivenheder denne dag.")) }
        }
        val firstUpcoming = dayEvents.indexOfFirst { !it.allDay && it.startMs > now }.let { if (it < 0) dayEvents.size else it }
        dayEvents.forEachIndexed { i, e ->
            if (isToday && i == firstUpcoming) item(key = "now") { NowLine() }
            item(key = e.id) { EventCard(e) { nav.open("event/" + NavCache.put("event-${e.id}", e)) } }
        }
        if (isToday && firstUpcoming == dayEvents.size && dayEvents.isNotEmpty()) item(key = "now") { NowLine() }
    }
}

@Composable
private fun DayStrip(selected: LocalDate, events: List<Event>, onSelect: (LocalDate) -> Unit) {
    val start = AulaDate.weekStart(selected)
    Row(
        Modifier.fillMaxWidth().pointerInput(selected) {
            var total = 0f
            detectHorizontalDragGestures(onDragEnd = {
                if (total < -80) onSelect(selected.plusWeeks(1)) else if (total > 80) onSelect(selected.minusWeeks(1))
                total = 0f
            }) { _, dx -> total += dx }
        },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        (0L until 7L).map { start.plusDays(it) }.forEach { d ->
            val isSel = d == selected
            val isToday = d == AulaDate.today()
            val count = events.count { it.occursOn(d) }
            val primary = MaterialTheme.colorScheme.primary
            val onPrimary = MaterialTheme.colorScheme.onPrimary
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (isSel) primary else Color.Transparent)
                    .clickable { onSelect(d) }.padding(vertical = 6.dp)
                    .semantics { contentDescription = "${AulaDate.weekdayDayMonth(d)}, " + L.t("%d begivenheder", count); this.selected = isSel },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(AulaDate.weekdayNarrow(d), style = MaterialTheme.typography.labelSmall,
                    color = if (isSel) onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when { isSel -> onPrimary; isToday -> primary; else -> MaterialTheme.colorScheme.onSurface })
                Box(Modifier.height(18.dp), contentAlignment = Alignment.BottomCenter) {
                    if (count > 0) Box(Modifier.width(4.dp).height((minOf(count, 6) * 2.5f + 3).dp).clip(CircleShape)
                        .background(if (isSel) onPrimary else primary.copy(alpha = 0.55f)))
                }
            }
        }
    }
}

@Composable
fun NowLine() {
    val red = Color(0xFFFF3B30)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(red))
        Box(Modifier.weight(1f).height(1.5.dp).background(red))
        Text(AulaDate.time(System.currentTimeMillis()), color = red, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun EventCard(e: Event, onClick: () -> Unit) {
    val color = e.childIds.firstOrNull()?.let { childColor(it) } ?: Color.Gray
    val past = e.endMs < System.currentTimeMillis()
    val large = LocalDensity.current.fontScale > 1.5f
    val timeText = if (e.allDay) L.t("Hele dagen") else "${AulaDate.time(e.startMs)} – ${AulaDate.time(e.endMs)}"
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(Dims.rowRadius + 2.dp))
            .background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val alpha = if (past) 0.6f else 1f
        if (!large) Column(Modifier.widthIn(min = 52.dp), horizontalAlignment = Alignment.End) {
            if (e.allDay) Text(L.t("Hele dagen"), style = MaterialTheme.typography.labelMedium)
            else {
                Text(AulaDate.time(e.startMs), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(AulaDate.time(e.endMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.width(4.dp).fillMaxHeight().clip(CircleShape).background(color.copy(alpha = alpha)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (large) Text(timeText, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (e.hasSubstitute) {
                    val orange = Color(0xFFFF9500)
                    Row(Modifier.background(orange.copy(alpha = 0.18f), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SwapHoriz, null, tint = orange, modifier = Modifier.size(12.dp))
                        Text(L.t("Vikar"), color = orange, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val details = listOfNotNull(e.location, if (e.hasSubstitute) e.substitute else e.teacher)
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (large) 3 else 1, overflow = TextOverflow.Ellipsis)
            if (AppState.children.size > 1 && AppState.selectedChildId == null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                e.childIds.mapNotNull(AppState::child).forEach { ChildTag(it.firstName, childColor(it.id)) }
            }
        }
    }
}

fun addToCalendar(ctx: android.content.Context, e: Event) {
    val names = e.childIds.mapNotNull { AppState.child(it)?.firstName }
    ctx.openSafely(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, if (names.isEmpty()) e.title else "${e.title} (${names.joinToString()})")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.startMs)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, if (e.endMs > e.startMs) e.endMs else e.startMs + 3_600_000)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, e.allDay)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, e.location ?: "")
        .putExtra(CalendarContract.Events.DESCRIPTION, listOfNotNull(e.teacher?.let { L.t("Lærer: %s", it) },
            e.substitute?.let { L.t("Vikar: %s", it) }).joinToString("\n")))
}

@Composable
fun EventDetailScreen(key: String) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val event = NavCache.items[key] as? Event ?: run {
        // The app was restarted on this screen: the event is no longer in memory.
        AulaScreen(title = L.t("Begivenhed"), onBack = { nav.back() }) { item { EmptyState(L.t("Intet i kalenderen"), Icons.Filled.CalendarMonth) } }
        return
    }
    var detail by remember { mutableStateOf<EventDetail?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        runCatching { AulaApi.eventDetail(event.id) }
            .onSuccess { detail = it; error = null }
            // Lessons often have no detail page; only complain when there is nothing else to show.
            .onFailure { if (event.type != "lesson") error = L.t("Begivenheden kunne ikke hentes: %s", it.message ?: "") }
    }
    LaunchedEffect(event.id) { load() }
    val myIds = (Repo.session.value?.guardianProfileIds ?: emptyList()) + AppState.children.map { it.id }
    val mine = detail?.invitees?.filter { it.institutionProfileId in myIds } ?: emptyList()

    AulaScreen(
        title = L.t("Begivenhed"), onBack = { nav.back() },
        actions = { IconButton(onClick = { addToCalendar(ctx, event) }, modifier = Modifier.semantics { contentDescription = L.t("Tilføj til kalender") }) {
            Icon(Icons.Filled.EventAvailable, null) } },
        refreshing = false, onRefresh = { load() },
    ) {
        item {
            Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail?.title ?: event.title, style = MaterialTheme.typography.headlineSmall)
                val time = if (detail?.allDay ?: event.allDay) AulaDate.weekdayDayMonth(AulaDate.localDate(event.startMs))
                else "${AulaDate.weekdayDayMonth(AulaDate.localDate(event.startMs))} · ${AulaDate.time(event.startMs)} – ${AulaDate.time(event.endMs)}"
                InfoRow(Icons.Filled.Schedule, time)
                (detail?.location ?: event.location)?.let { InfoRow(Icons.Filled.Place, it) }
                detail?.creator?.let { InfoRow(Icons.Filled.Person, it) }
                if (event.hasSubstitute) InfoRow(Icons.Filled.SwapHoriz, L.t("Vikar") + (event.substitute?.let { ": $it" } ?: ""), Color(0xFFFF9500))
            }
        }
        detail?.html?.takeIf { it.isNotBlank() }?.let { html ->
            item { SectionTitle(L.t("Beskrivelse")) }
            item { Box(Modifier.card()) { HtmlText(html) } }
        }
        val d = detail
        if (d != null && d.responseRequired && mine.isNotEmpty()) {
            item { SectionTitle(L.t("Svar")) }
            mine.forEach { inv ->
                item {
                    Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row { Text(inv.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text((Rsvp.of(inv.response) ?: Rsvp.WAITING).label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (!d.deadlineExceeded && inv.institutionProfileId != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Rsvp.ACCEPTED to Color(0xFF34C759), Rsvp.TENTATIVE to Color(0xFFFF9500), Rsvp.DECLINED to Color(0xFFFF3B30)).forEach { (r, c) ->
                                FilterChip(
                                    selected = Rsvp.of(inv.response) == r, enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            runCatching { AulaApi.respond(d.id, inv.institutionProfileId, r) }
                                                .onSuccess { load() }.onFailure { error = L.t("Svaret blev ikke sendt: %s", it.message ?: "") }
                                            busy = false
                                        }
                                    },
                                    label = { Text(r.label) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.2f), selectedLabelColor = c),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        d.deadlineMs?.let {
                            Text(if (d.deadlineExceeded) L.t("Svarfristen er overskredet.") else L.t("Svarfrist %s", AulaDate.dateTime(it)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        detail?.attachments?.takeIf { it.isNotEmpty() }?.let { a ->
            item { SectionTitle(L.t("Filer")) }
            item { AttachmentList(a) }
        }
        detail?.invitees?.takeIf { it.size > mine.size }?.let { invitees ->
            item { SectionTitle(L.t("Inviterede (%d)", invitees.size)) }
            item {
                Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    invitees.forEach { i ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(i.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            when (Rsvp.of(i.response)) {
                                Rsvp.ACCEPTED -> Icon(Icons.Filled.CheckCircle, Rsvp.ACCEPTED.label, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                                Rsvp.DECLINED -> Icon(Icons.Filled.Cancel, Rsvp.DECLINED.label, tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp))
                                Rsvp.TENTATIVE -> Icon(Icons.Filled.HelpOutline, Rsvp.TENTATIVE.label, tint = Color(0xFFFF9500), modifier = Modifier.size(18.dp))
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
        error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
    }
}

@Composable
fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (tint == MaterialTheme.colorScheme.onSurfaceVariant) MaterialTheme.colorScheme.onSurface else tint)
    }
}
