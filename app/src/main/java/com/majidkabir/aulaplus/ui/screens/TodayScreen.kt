package com.majidkabir.aulaplus.ui.screens

import com.majidkabir.aulaplus.ui.components.MeAvatar
import com.majidkabir.aulaplus.data.ParentTaskFinder
import androidx.compose.material.icons.filled.Cake
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
import androidx.compose.ui.semantics.heading
import androidx.compose.material.icons.filled.PhotoLibrary
import com.majidkabir.aulaplus.ui.components.pastelCard
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
                async { Repo.notifications.load(300) }, async { Repo.birthdays.load(6 * 3600) },
            ).awaitAll()
        }
    }
    // Reads new messages and posts on the phone for things to do.
    LaunchedEffect(Repo.threads.value, Repo.posts.value) {
        ParentTaskFinder.update(Repo.threads.value.orEmpty(), Repo.posts.value.orEmpty(), Repo.session.value?.displayName.orEmpty())
    }
    val todays = (week.value ?: emptyList()).filter { it.occursOn(AulaDate.today()) }
    AulaScreen(
        title = L.t("I dag"),
        actions = {
            IconButton(onClick = { nav.open("settings") }, modifier = Modifier.semantics { contentDescription = L.t("Profil og indstillinger") }) {
                MeAvatar(ThemeStore.theme.brandLight, 34.dp)
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
        item { NewPhotosCard() }
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
        item { ParentTasksCard() }
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
        val today = java.time.LocalDate.now()
        val birthdays = (Repo.birthdays.value ?: emptyList()).filter { java.time.LocalDate.parse(it.date) < today.plusDays(30) }.take(4)
        if (birthdays.isNotEmpty()) {
            item {
                SectionTitle(L.t("Fødselsdage"), Icons.Filled.Cake, tint = Color(0xFFFF2D55)) {
                    TextButton(onClick = { nav.open("birthdays") }) { Text(L.t("Vis alle")) }
                }
            }
            item {
                Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val mine = AppState.children.map { it.id }.toSet()
                    birthdays.forEach { b -> BirthdayRow(b, b.id in mine) }
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    val hello = when { h < 10 -> L.t("Godmorgen"); h < 18 -> L.t("Hej"); else -> L.t("Godaften") }
    val first = Repo.session.value?.displayName?.substringBefore(' ').orEmpty()
    return if (first.isEmpty()) hello else "$hello, $first"
}

/** Header: date, a big friendly greeting, and three pastel tiles with the day's key numbers. */
@Composable
fun TodayHero(todays: List<Event>) {
    val nav = LocalNav.current
    val ids = AppState.visibleChildren.map { it.id }.toSet()
    val todays0 = (Repo.presence.value ?: emptyList()).filter { it.childId in ids }
    val pickup = if (todays0.isNotEmpty() && todays0.all { it.isDayOff }) L.t("Fri")
        else todays0.filter { it.checkOut == null && !it.isDayOff }.mapNotNull { it.plannedExit }.minOrNull()
    val now = System.currentTimeMillis()
    val nextEvent = todays.firstOrNull { it.startMs > now }
    val nextDate = (Repo.importantDates.value ?: emptyList()).sortedBy { it.startMs }.firstOrNull { it.startMs > now }
    val next: Pair<String, String>? = nextEvent?.let { it.title to AulaDate.time(it.startMs) }
        ?: nextDate?.let {
            val d = AulaDate.localDate(it.startMs)
            it.title to (if (d == AulaDate.today().plusDays(1)) L.t("I morgen") else AulaDate.dayMonthShort(d))
        }
    val large = LocalDensity.current.fontScale > 1.5f
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(AulaDate.weekdayDayMonth(AulaDate.today()).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(greeting(), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        }
        val purple = Color(0xFFAF52DE); val teal = Color(0xFF30B0C7); val orange = Color(0xFFFF9500)
        if (large) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.clickable { nav.tab(Tab.MESSAGES) }, Icons.AutoMirrored.Filled.Chat, "${Repo.unreadCount}", L.t("Ulæste"), purple)
                Tile(Modifier, Icons.Filled.FamilyRestroom, pickup ?: "–", L.t("Afhentning"), teal)
                Tile(Modifier, Icons.Filled.CalendarMonth, next?.second ?: "–", next?.first ?: L.t("Intet planlagt"), orange, wrap = true)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f).clickable { nav.tab(Tab.MESSAGES) }, Icons.AutoMirrored.Filled.Chat, "${Repo.unreadCount}", L.t("Ulæste"), purple)
                Tile(Modifier.weight(1f), Icons.Filled.FamilyRestroom, pickup ?: "–", L.t("Afhentning"), teal)
                Tile(Modifier.weight(1f), Icons.Filled.CalendarMonth, next?.second ?: "–", next?.first ?: L.t("Intet planlagt"), orange)
            }
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, icon: ImageVector, value: String, label: String, color: Color, wrap: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.16f)).padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (wrap) 3 else 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "12 nye billeder" — opens the gallery (its own tab when there's no week plan). */
@Composable
fun NewPhotosCard() {
    val nav = LocalNav.current
    val count = Repo.newGalleryCount
    if (count <= 0) return
    val purple = Color(0xFFAF52DE)
    Row(
        Modifier.card(14.dp).clickable {
            if (Repo.session.value?.hasWeekPlan != false) nav.open("gallery") else nav.tab(Tab.PHOTOS)
        },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(purple.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PhotoLibrary, null, tint = purple, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(L.t("%d nye billeder", count), style = MaterialTheme.typography.titleMedium)
            Text(L.t("Se dem i galleriet"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class Fact(val icon: ImageVector, val label: String, val value: String)

private fun facts(p: Presence): List<Fact> = listOfNotNull(
    p.checkIn?.let { Fact(Icons.Filled.Login, L.t("Kom"), it) },
    if (p.checkIn == null && !Presence.isPlaceholder(p.plannedEntry, p.plannedExit)) p.plannedEntry?.let { Fact(Icons.Filled.Login, L.t("Afleveres"), it) } else null,
    p.checkOut?.let { Fact(Icons.Filled.Logout, L.t("Gik"), it) },
    if (p.checkOut == null && !Presence.isPlaceholder(p.plannedEntry, p.plannedExit)) p.plannedExit?.let { Fact(Icons.Filled.Logout, L.t("Hentes"), it) } else null,
    p.exitWith?.let { Fact(Icons.Filled.Person, L.t("Hentes af"), it) },
    p.location?.let { Fact(Icons.Filled.Place, L.t("Lokation"), it) },
)

@Composable
fun ChildDayCard(child: Child, presence: Presence?, events: List<Event>, onOpen: () -> Unit) {
    val color = childColor(child.id)
    // Not coming today (weekend, holiday): no times, no timeline, the next drop-off instead.
    val dayOff = presence?.isDayOff == true
    val weekend = AulaDate.today().dayOfWeek.value >= 6
    val now = System.currentTimeMillis()
    val upcoming = events.filter { it.endMs >= now }
    val large = LocalDensity.current.fontScale > 1.5f
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.pastelCard(color, 18.dp).clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChildAvatar(child, 56.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(child.firstName, style = MaterialTheme.typography.headlineSmall)
                    Text(child.institutionName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (dayOff) StatusPill(if (weekend) L.t("Weekend") else L.t("Fri i dag"), if (weekend) Icons.Filled.WbSunny else Icons.Filled.Home, color)
                    else presence?.presenceStatus?.let { StatusPill(it.label, it.icon(), color) }
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
                                        Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                                            contentAlignment = Alignment.Center) {
                                            Icon(f.icon, null, tint = color, modifier = Modifier.size(18.dp))
                                        }
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
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.rowRadius)).background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.TextSnippet, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (dayOff) NextDropOff(child.id, weekend, color)
            else if (presence != null || events.isNotEmpty()) DayTimeline(presence, events, color)
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
    }
}

/** "Næste aflevering · Mandag 07:45 · hentes 16:00". */
@Composable
private fun NextDropOff(childId: Int, weekend: Boolean, color: Color) {
    val plans = Repo.upcomingPlans(childId)
    LaunchedEffect(childId) { plans.load(3600) }
    val next = (plans.value ?: emptyList()).sortedBy { it.date }
        .firstOrNull { !it.isOnVacation && it.entry != null && !Presence.isPlaceholder(it.entry, it.exit) }
    if (next?.entry != null) {
        val date = java.time.LocalDate.parse(next.date)
        val day = if (date == AulaDate.today().plusDays(1)) L.t("I morgen")
            else AulaDate.weekdayDayMonth(date).substringBefore(' ')
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CalendarMonth, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(L.t("Næste aflevering"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (next.exit != null) L.t("%s %s · hentes %s", day, next.entry, next.exit) else "$day ${next.entry}",
                    style = MaterialTheme.typography.titleSmall)
            }
        }
    } else {
        Text(L.t(if (weekend) "Ingen aflevering i weekenden" else "Ingen aflevering i dag"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun BirthdayRow(b: com.majidkabir.aulaplus.data.Birthday, mine: Boolean) {
    val date = java.time.LocalDate.parse(b.date)
    val isToday = date == java.time.LocalDate.now()
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🎂", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(b.name, style = MaterialTheme.typography.titleSmall, fontWeight = if (mine) FontWeight.Bold else null, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            b.group?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(if (isToday) L.t("I dag") else date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d. MMM", L.locale)),
            style = MaterialTheme.typography.labelMedium, color = if (isToday) Color(0xFFFF2D55) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
