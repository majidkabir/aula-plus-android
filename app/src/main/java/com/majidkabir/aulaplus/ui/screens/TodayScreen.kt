package com.majidkabir.aulaplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.Child
import com.majidkabir.aulaplus.data.Event
import com.majidkabir.aulaplus.data.Presence
import com.majidkabir.aulaplus.data.PresenceStatus
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.data.Tab
import com.majidkabir.aulaplus.ui.AulaBottomBar
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.charts.DayTimeline
import com.majidkabir.aulaplus.ui.components.ChildAvatar
import com.majidkabir.aulaplus.ui.components.ChildFilterBar
import com.majidkabir.aulaplus.ui.components.Dims
import com.majidkabir.aulaplus.ui.components.InitialsAvatar
import com.majidkabir.aulaplus.ui.components.SectionTitle
import com.majidkabir.aulaplus.ui.components.StaleBanner
import com.majidkabir.aulaplus.ui.components.StatusPill
import com.majidkabir.aulaplus.ui.components.card
import com.majidkabir.aulaplus.ui.components.childColor
import com.majidkabir.aulaplus.ui.theme.ThemeStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

fun PresenceStatus.icon(): ImageVector = when (this) {
    PresenceStatus.PRESENT, PresenceStatus.PHYSICAL_PLACEMENT -> Icons.Filled.CheckCircle
    PresenceStatus.SICK -> Icons.Filled.MedicalServices
    PresenceStatus.REPORTED_ABSENT -> Icons.Filled.WbSunny
    PresenceStatus.FIELD_TRIP -> Icons.Filled.DirectionsBus
    PresenceStatus.SLEEPING -> Icons.Filled.Hotel
    PresenceStatus.SPARE_TIME -> Icons.Filled.SportsSoccer
    PresenceStatus.CHECKED_OUT -> Icons.Filled.Home
    PresenceStatus.NOT_PRESENT, PresenceStatus.NOT_ARRIVED -> Icons.Filled.RadioButtonUnchecked
}

@Composable
fun TodayScreen() {
    val nav = LocalNav.current
    val week = Repo.events(AulaDate.today())
    LaunchedEffect(Unit) {
        coroutineScope {
            listOf(
                async { Repo.presence.load(60) }, async { week.load(300) }, async { Repo.threads.load(60) },
                async { Repo.posts.load(300) }, async { Repo.importantDates.load(1800) }, async { Repo.vacationRequests.load(1800) },
            ).awaitAll()
        }
    }
    val todays = (week.value ?: emptyList()).filter { it.occursOn(AulaDate.today()) }
    AulaScreen(
        title = L.t("I dag"),
        actions = {
            IconButton(onClick = { nav.open("settings") }, modifier = Modifier.semantics { contentDescription = L.t("Profil og indstillinger") }) {
                InitialsAvatar(Repo.session.value?.displayName ?: "", ThemeStore.theme.brandLight, 34.dp)
            }
        },
        refreshing = Repo.presence.isLoading,
        onRefresh = {
            coroutineScope {
                listOf(async { Repo.presence.refresh() }, async { week.refresh() }, async { Repo.threads.refresh() },
                    async { Repo.posts.refresh() }).awaitAll()
            }
        },
        bottomBar = { AulaBottomBar() },
        spacing = 14.dp,
    ) {
        item { TodayHero(todays) }
        item { ChildFilterBar() }
        item { StaleBanner(Repo.presence.error ?: Repo.session.error, Repo.presence.savedAt) }
        val missing = (Repo.vacationRequests.value ?: emptyList()).filter { it.isMissingAnswer && it.isEditable }
        if (missing.isNotEmpty()) item {
            val orange = Color(0xFFFF9500)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.cardRadius)).background(orange.copy(alpha = 0.13f))
                    .border(1.dp, orange.copy(alpha = 0.35f), RoundedCornerShape(Dims.cardRadius))
                    .clickable { nav.open("vacations") }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.WbSunny, null, tint = orange, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(L.t("Institutionen spørger om ferie"), style = MaterialTheme.typography.titleSmall)
                    Text(missing.joinToString("\n") { "${it.childName}: ${it.title}" }, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AppState.visibleChildren.forEach { child ->
            item(key = "child-${child.id}") {
                ChildDayCard(
                    child, Repo.presence.value?.firstOrNull { it.childId == child.id },
                    todays.filter { it.childIds.isEmpty() || child.id in it.childIds },
                ) { nav.open("child/${child.id}") }
            }
        }
        val unread = (Repo.threads.value ?: emptyList()).filter { !it.isRead }.take(3)
        if (unread.isNotEmpty()) {
            item {
                SectionTitle(L.t("Ulæste beskeder"), Icons.AutoMirrored.Filled.Chat, count = Repo.unreadCount,
                    trailing = { TextButton(onClick = { nav.tab(Tab.MESSAGES) }) { Text(L.t("Se alle")) } })
            }
            item {
                Column(Modifier.card(8.dp)) {
                    unread.forEachIndexed { i, t ->
                        ThreadRow(t) { nav.open("thread/${t.id}") }
                        if (i < unread.size - 1) HorizontalDivider(Modifier.padding(start = 24.dp))
                    }
                }
            }
        }
        val recent = (Repo.posts.value ?: emptyList()).filter {
            it.isImportant || (it.dateMs?.let { d -> System.currentTimeMillis() - d < 3 * 86_400_000L } ?: false)
        }.take(3)
        if (recent.isNotEmpty()) {
            item { SectionTitle(L.t("Nye opslag"), Icons.Filled.Newspaper, tint = Color(0xFFAF52DE)) }
            recent.forEach { p -> item(key = "post-${p.id}") { PostCard(p, compact = true) { nav.open("post/${p.id}") } } }
        }
        val soon = (Repo.importantDates.value ?: emptyList()).sortedBy { it.startMs }
            .filter { it.startMs > System.currentTimeMillis() - 86_400_000 && it.startMs < System.currentTimeMillis() + 14 * 86_400_000L }.take(3)
        if (soon.isNotEmpty()) {
            item { SectionTitle(L.t("Kommende"), Icons.Filled.Star, tint = Color(0xFFFFCC00)) }
            item { Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(12.dp)) { soon.forEach { ImportantDateRow(it) } } }
        }
    }
}

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    val hello = when { h < 10 -> L.t("Godmorgen"); h < 18 -> L.t("Hej"); else -> L.t("Godaften") }
    val first = Repo.session.value?.displayName?.substringBefore(' ').orEmpty()
    return if (first.isEmpty()) hello else "$hello, $first"
}

/** Brand gradient header with greeting, date and three key numbers. */
@Composable
fun TodayHero(todays: List<Event>) {
    val nav = LocalNav.current
    val t = ThemeStore.theme
    val ids = AppState.visibleChildren.map { it.id }.toSet()
    val pickup = (Repo.presence.value ?: emptyList()).filter { it.childId in ids && it.checkOut == null }.mapNotNull { it.plannedExit }.minOrNull()
    val now = System.currentTimeMillis()
    val nextEvent = todays.firstOrNull { it.startMs > now }
    val nextDate = (Repo.importantDates.value ?: emptyList()).sortedBy { it.startMs }.firstOrNull { it.startMs > now }
    val next: Pair<String, String>? = nextEvent?.let { it.title to AulaDate.time(it.startMs) }
        ?: nextDate?.let {
            val d = AulaDate.localDate(it.startMs)
            it.title to (if (d == AulaDate.today().plusDays(1)) L.t("I morgen") else AulaDate.dayMonthShort(d))
        }
    val large = LocalDensity.current.fontScale > 1.5f
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(t.brandLight, t.second))),
    ) {
        Box(Modifier.size(190.dp).offset(x = 220.dp, y = (-70).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
        Box(Modifier.size(120.dp).offset(x = 270.dp, y = 60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)))
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text(greeting(), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(AulaDate.weekdayDayMonth(AulaDate.today()), style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f))
            }
            val stats: @Composable (Modifier) -> Unit = { m ->
                Stat(m.clickable { nav.tab(Tab.MESSAGES) }, Icons.AutoMirrored.Filled.Chat, "${Repo.unreadCount}", L.t("Ulæste"))
            }
            if (large) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    stats(Modifier)
                    Stat(Modifier, Icons.Filled.FamilyRestroom, pickup ?: "–", L.t("Afhentning"))
                    Stat(Modifier, Icons.Filled.CalendarMonth, next?.second ?: "–", next?.first ?: L.t("Intet planlagt"), wrap = true)
                }
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    stats(Modifier.weight(1f))
                    Divider()
                    Stat(Modifier.weight(1f), Icons.Filled.FamilyRestroom, pickup ?: "–", L.t("Afhentning"))
                    Divider()
                    Stat(Modifier.weight(1.2f), Icons.Filled.CalendarMonth, next?.second ?: "–", next?.first ?: L.t("Intet planlagt"))
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.3f)))
}

@Composable
private fun Stat(modifier: Modifier, icon: ImageVector, value: String, label: String, wrap: Boolean = false) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.88f), modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f),
            maxLines = if (wrap) 3 else 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class Fact(val icon: ImageVector, val label: String, val value: String)

private fun facts(p: Presence): List<Fact> = listOfNotNull(
    p.checkIn?.let { Fact(Icons.Filled.Login, L.t("Kom"), it) },
    if (p.checkIn == null) p.plannedEntry?.let { Fact(Icons.Filled.Login, L.t("Afleveres"), it) } else null,
    p.checkOut?.let { Fact(Icons.Filled.Logout, L.t("Gik"), it) },
    if (p.checkOut == null) p.plannedExit?.let { Fact(Icons.Filled.Logout, L.t("Hentes"), it) } else null,
    p.exitWith?.let { Fact(Icons.Filled.Person, L.t("Hentes af"), it) },
    p.location?.let { Fact(Icons.Filled.Place, L.t("Lokation"), it) },
)

@Composable
fun ChildDayCard(child: Child, presence: Presence?, events: List<Event>, onOpen: () -> Unit) {
    val color = childColor(child.id)
    val now = System.currentTimeMillis()
    val upcoming = events.filter { it.endMs >= now }
    val large = LocalDensity.current.fontScale > 1.5f
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.card().clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChildAvatar(child, 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(child.firstName, style = MaterialTheme.typography.titleLarge)
                    Text(child.institutionName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    presence?.presenceStatus?.let { StatusPill(it.label, it.icon(), color) }
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
            presence?.let { p ->
                val list = facts(p)
                if (list.isNotEmpty()) {
                    val cols = if (large) 1 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.chunked(cols).forEach { rowFacts ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                rowFacts.forEach { f ->
                                    Row(Modifier.weight(1f).semantics(mergeDescendants = true) {},
                                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(f.icon, null, tint = color, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text(f.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(f.value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                                if (rowFacts.size < cols) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                p.comment?.let {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.rowRadius)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.TextSnippet, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (presence != null || events.isNotEmpty()) DayTimeline(presence, events, color)
            if (events.isNotEmpty()) {
                HorizontalDivider()
                if (upcoming.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(L.t("Dagens program er slut"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        upcoming.take(4).forEachIndexed { i, e ->
                            val isNow = e.startMs <= now && e.endMs >= now
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (e.allDay) L.t("Hele dagen") else AulaDate.time(e.startMs),
                                    style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.widthIn(min = 52.dp))
                                Text(e.title, style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (isNow) Text(L.t("Nu"), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.background(color, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp))
                                if (e.hasSubstitute) Icon(Icons.Filled.SwapHoriz, L.t("Vikar"), tint = Color(0xFFFF9500), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.weight(1f))
                                if (!large) e.location?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                        val more = upcoming.size - 4
                        val done = events.size - upcoming.size
                        val parts = listOfNotNull(if (more > 0) L.t("+%d mere", more) else null, if (done > 0) L.t("%d afsluttet", done) else null)
                        if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        // Colour stripe on the leading edge.
        Box(Modifier.padding(vertical = 14.dp).width(4.dp).height(56.dp).clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)).background(color))
    }
}

@Composable
fun ImportantDateRow(d: com.majidkabir.aulaplus.data.ImportantDate) {
    val date = AulaDate.localDate(d.startMs)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(java.time.format.DateTimeFormatter.ofPattern("MMM", L.locale).format(date).uppercase().trimEnd('.'),
                color = Color(0xFFFF3B30), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.titleLarge)
        }
        Column {
            Text(d.title, style = MaterialTheme.typography.titleSmall)
            Text(if (d.allDay) AulaDate.weekdayDayMonth(date) else "${AulaDate.weekdayDayMonth(date)} · ${AulaDate.time(d.startMs)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
