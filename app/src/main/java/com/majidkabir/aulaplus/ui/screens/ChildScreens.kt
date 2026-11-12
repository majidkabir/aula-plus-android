package com.majidkabir.aulaplus.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.DayPlan
import com.majidkabir.aulaplus.data.PickupKind
import com.majidkabir.aulaplus.data.PresencePermissions
import com.majidkabir.aulaplus.data.PresenceStatus
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.data.SchoolTask
import com.majidkabir.aulaplus.data.VacationRequest
import com.majidkabir.aulaplus.core.AulaConfig
import com.majidkabir.aulaplus.ui.AulaBottomBar
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.charts.ProgressRing
import com.majidkabir.aulaplus.ui.charts.WeekPlanChart
import com.majidkabir.aulaplus.ui.components.ChildAvatar
import com.majidkabir.aulaplus.ui.components.ChildFilterBar
import com.majidkabir.aulaplus.ui.components.Dims
import com.majidkabir.aulaplus.ui.components.EmptyState
import com.majidkabir.aulaplus.ui.components.HtmlText
import com.majidkabir.aulaplus.ui.components.IconTile
import com.majidkabir.aulaplus.ui.components.Loading
import com.majidkabir.aulaplus.ui.components.SectionTitle
import com.majidkabir.aulaplus.ui.components.StaleBanner
import com.majidkabir.aulaplus.ui.components.StatusPill
import com.majidkabir.aulaplus.ui.components.WeekSwitcher
import com.majidkabir.aulaplus.ui.components.card
import com.majidkabir.aulaplus.ui.components.childColor
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ChildPresenceScreen(childId: Int) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val child = AppState.child(childId) ?: return
    val color = childColor(childId)
    var weekDate by remember { mutableStateOf(AulaDate.today()) }
    val plans = Repo.dayPlans(childId, weekDate)
    var permissions by remember { mutableStateOf<PresencePermissions?>(null) }
    var confirmSick by remember { mutableStateOf(false) }
    var showVacation by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DayPlan?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val presence = Repo.presence.value?.firstOrNull { it.childId == childId }
    val isSick = presence?.presenceStatus == PresenceStatus.SICK
    LaunchedEffect(AulaDate.weekKey(weekDate)) { plans.refresh() }
    LaunchedEffect(childId) { permissions = runCatching { AulaApi.presencePermissions(listOf(childId)).firstOrNull() }.getOrNull() }
    val days = (0L until 5L).map { AulaDate.weekStart(weekDate).plusDays(it) }

    AulaScreen(title = child.firstName, onBack = { nav.back() }, refreshing = plans.isLoading, onRefresh = { plans.refresh() }) {
        item {
            Row(Modifier.card(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ChildAvatar(child, 58.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(child.name, style = MaterialTheme.typography.titleMedium)
                    Text(child.institutionName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    presence?.presenceStatus?.let { StatusPill(it.label, it.icon(), color) }
                }
            }
        }
        message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (permissions?.canReportSick != false) ActionTile(
                    L.t(if (isSick) "Fjern sygemelding" else "Meld syg i dag"),
                    if (isSick) Icons.Outlined.MedicalServices else Icons.Filled.MedicalServices,
                    if (isSick) MaterialTheme.colorScheme.primary else Color(0xFFFF3B30), Modifier.weight(1f),
                ) { confirmSick = true }
                if (permissions?.canAddVacation != false) ActionTile(L.t("Meld ferie/fri"), Icons.Filled.WbSunny, Color(0xFFFF9500), Modifier.weight(1f)) {
                    showVacation = true
                }
            }
        }
        item { SectionTitle(L.t("Afleveres og hentes")) }
        item {
            Column(Modifier.card(8.dp)) {
                WeekSwitcher(weekDate, { weekDate = it })
                plans.value?.takeIf { it.isNotEmpty() }?.let { WeekPlanChart(it, days, color, Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) }
                days.forEachIndexed { i, d ->
                    val p = plans.value?.firstOrNull { it.date == d.toString() }
                    val past = d.isBefore(AulaDate.today())
                    val canEdit = permissions?.canEditPickup != false && !past
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = canEdit) {
                            editing = p ?: DayPlan(null, childId, d.toString(), null, null, PickupKind.PICKED_UP_BY.code)
                        }.padding(horizontal = 8.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val alpha = if (past) 0.55f else 1f
                        Column(Modifier.width(110.dp)) {
                            Text(AulaDate.weekdayDayMonth(d).substringBefore(' '), style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                            Text(AulaDate.dayMonthShort(d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            when {
                                p == null -> Text(L.t("Ingen tider"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                p.isOnVacation -> Text(L.t("Ferie/fri"), color = Color(0xFFFF9500))
                                else -> {
                                    Text("${p.entry ?: "–"} – ${p.exit ?: "–"}", style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                                    (p.exitWith ?: PickupKind.of(p.kind)?.label)?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                        if (canEdit) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        if (permissions?.canEditPickup == false) item {
            Text(L.t("Institutionen tillader ikke at forældre ændrer tider her."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (confirmSick) AlertDialog(
        onDismissRequest = { confirmSick = false },
        title = { Text(if (isSick) L.t("Fjern sygemelding for %s?", child.firstName) else L.t("Meld %s syg i dag?", child.firstName)) },
        confirmButton = {
            TextButton(onClick = {
                confirmSick = false
                scope.launch {
                    runCatching { AulaApi.setSick(listOf(childId), !isSick) }
                        .onSuccess { message = if (isSick) L.t("Sygemelding fjernet.") else L.t("%s er meldt syg.", child.firstName); Repo.presence.refresh() }
                        .onFailure { message = L.t("Ikke gemt: %s", it.message ?: "") }
                }
            }) { Text(L.t(if (isSick) "Fjern sygemelding" else "Meld syg"), color = if (isSick) MaterialTheme.colorScheme.primary else Color(0xFFFF3B30)) }
        },
        dismissButton = { TextButton(onClick = { confirmSick = false }) { Text(L.t("Annuller")) } },
    )
    if (showVacation) VacationDialog(listOf(childId)) { saved ->
        showVacation = false
        if (saved) { message = L.t("Ferie/fri er meldt."); scope.launch { Repo.presence.refresh() } }
    }
    editing?.let { p ->
        DayPlanDialog(p, childId) { saved ->
            editing = null
            if (saved) { message = L.t("Tider gemt."); scope.launch { plans.refresh() } }
        }
    }
}

@Composable
fun ActionTile(title: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(Dims.cardRadius)).background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

/** Opens Android's own time picker for an "HH:mm" value. */
fun pickTime(ctx: android.content.Context, current: String, onPick: (String) -> Unit) {
    val (h, m) = current.split(":").map { it.toIntOrNull() ?: 0 }.let { (it.getOrElse(0) { 8 }) to (it.getOrElse(1) { 0 }) }
    TimePickerDialog(ctx, { _, hh, mm -> onPick("%02d:%02d".format(hh, mm)) }, h, m, true).show()
}

fun pickDate(ctx: android.content.Context, current: LocalDate, min: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, mo, d -> onPick(LocalDate.of(y, mo + 1, d)) }, current.year, current.monthValue - 1, current.dayOfMonth)
        .apply { datePicker.minDate = AulaDate.startOfDayMs(min) }.show()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayPlanDialog(plan: DayPlan, childId: Int, done: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf(plan.entry ?: "08:00") }
    var exit by remember { mutableStateOf(plan.exit ?: "16:00") }
    var kind by remember { mutableStateOf(PickupKind.of(plan.kind) ?: PickupKind.PICKED_UP_BY) }
    var who by remember { mutableStateOf(plan.exitWith ?: "") }
    var comment by remember { mutableStateOf(plan.comment ?: "") }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(childId) { names = runCatching { AulaApi.pickupNames(childId) }.getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = { done(false) },
        title = { Text(AulaDate.weekdayDayMonth(LocalDate.parse(plan.date))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pickTime(ctx, entry) { entry = it } }, Modifier.weight(1f)) { Text(L.t("Afleveres") + " $entry") }
                    OutlinedButton(onClick = { pickTime(ctx, exit) { exit = it } }, Modifier.weight(1f)) { Text(L.t("Hentes") + " $exit") }
                }
                Text(L.t("Hvordan"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PickupKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, { Text(k.label) }) }
                }
                if (kind.needsName) {
                    if (names.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        names.take(6).forEach { n -> FilterChip(who == n, { who = n }, { Text(n, maxLines = 1) }) }
                    }
                    OutlinedTextField(who, { who = it }, label = { Text(L.t("Navn (fx Mor)")) }, singleLine = true)
                }
                OutlinedTextField(comment, { comment = it }, label = { Text(L.t("Bemærkning")) })
                if (plan.repeats) Text(L.t("Dagen følger en fast ugeplan. Ændringen gælder kun denne dag."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = Color(0xFFFF3B30)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                if (kind.needsName && who.isBlank()) { error = L.t("Vælg hvem der henter."); return@TextButton }
                if (exit <= entry) { error = L.t("Hentetid skal være efter afleveringstid."); return@TextButton }
                scope.launch {
                    saving = true
                    runCatching { AulaApi.savePlan(plan.copy(entry = entry, exit = exit, kind = kind.code, exitWith = who, comment = comment)) }
                        .onSuccess { done(true) }.onFailure { error = L.t("Ikke gemt: %s", it.message ?: "") }
                    saving = false
                }
            }) { Text(L.t("Gem")) }
        },
        dismissButton = { TextButton(onClick = { done(false) }) { Text(L.t("Annuller")) } },
    )
}

@Composable
fun VacationDialog(childIds: List<Int>, done: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(AulaDate.today()) }
    var to by remember { mutableStateOf(AulaDate.today()) }
    var comment by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { done(false) },
        title = { Text(L.t("Ferie/fri")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { pickDate(ctx, from, AulaDate.today()) { from = it; if (to < it) to = it } }, Modifier.weight(1f)) {
                        Text(L.t("Fra") + " " + AulaDate.dayMonthShort(from))
                    }
                    OutlinedButton(onClick = { pickDate(ctx, to, from) { to = it } }, Modifier.weight(1f)) {
                        Text(L.t("Til") + " " + AulaDate.dayMonthShort(to))
                    }
                }
                if (AppState.children.size > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L.t("Gælder alle børn"), Modifier.weight(1f)); Switch(all, { all = it })
                }
                OutlinedTextField(comment, { if (it.length <= 250) comment = it }, label = { Text(L.t("Besked til institutionen")) },
                    supportingText = { Text("${comment.length}/250") })
                error?.let { Text(it, color = Color(0xFFFF3B30)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    runCatching { AulaApi.addVacation(if (all) AppState.children.map { it.id } else childIds, from, to, comment) }
                        .onSuccess { done(true) }.onFailure { error = L.t("Ikke gemt: %s", it.message ?: "") }
                    saving = false
                }
            }) { Text(L.t("Meld")) }
        },
        dismissButton = { TextButton(onClick = { done(false) }) { Text(L.t("Annuller")) } },
    )
}

@Composable
fun WeekPlanScreen() {
    var date by remember { mutableStateOf(AulaDate.today()) }
    val plan = Repo.weekPlans(date)
    val tasks = Repo.schoolTasks(date)
    LaunchedEffect(AulaDate.weekKey(date)) { plan.load(1800); tasks.load(1800) }
    val selected = AppState.selectedChildId?.let(AppState::child)
    val plans = (plan.value ?: emptyList()).filter { selected == null || it.childName.isBlank() || it.childName.contains(selected.firstName, true) }
    val visibleTasks = (tasks.value ?: emptyList()).filter { selected == null || it.childName.isBlank() || it.childName.contains(selected.firstName, true) }
    val widgets = Repo.session.value?.widgetIds ?: emptySet()
    val supported = listOf(AulaConfig.WIDGET_MEEBOOK, AulaConfig.WIDGET_MU_UGEPLAN, AulaConfig.WIDGET_MU_SSO).any { it in widgets }

    AulaScreen(
        title = L.t("Ugeplan"), refreshing = plan.isLoading, onRefresh = { plan.refresh(); tasks.refresh() },
        bottomBar = { AulaBottomBar() },
    ) {
        item { ChildFilterBar() }
        item { Column(Modifier.card(6.dp)) { WeekSwitcher(date, { date = it }) } }
        item { StaleBanner(plan.error, plan.savedAt) }
        if (visibleTasks.isNotEmpty()) {
            item {
                SectionTitle(L.t("Opgaver"), Icons.Filled.Checklist, Color(0xFF34C759),
                    trailing = { ProgressRing(visibleTasks.count { it.isDone }, visibleTasks.size, Color(0xFF34C759)) })
            }
            item { Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(12.dp)) { visibleTasks.forEach { TaskRow(it) } } }
        }
        if (!supported && Repo.session.value != null && visibleTasks.isEmpty()) {
            item { EmptyState(L.t("Ugeplan ikke understøttet endnu"), Icons.Outlined.Description,
                L.t("Din skole bruger en ugeplan-udbyder, som Aula+ ikke kender endnu (understøtter Meebook og Min Uddannelse).")) }
        } else if (plans.all { it.sections.isEmpty() }) {
            item { if (plan.isLoading) Loading() else EmptyState(L.t("Ingen ugeplan for denne uge"), Icons.Outlined.Description) }
        }
        plans.filter { it.sections.isNotEmpty() }.forEach { p ->
            if (p.childName.isNotBlank()) item { SectionTitle(p.childName, Icons.Filled.Description) }
            p.sections.forEach { s ->
                item {
                    Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        HtmlText(s.html)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskRow(t: SchoolTask) {
    val meta = listOfNotNull(
        t.subject,
        t.dueMs?.let { L.t("Afleveres %s", AulaDate.weekdayShort(AulaDate.localDate(it)) + " " + AulaDate.dayMonth(it)) }
            ?: t.weekday,
        if (AppState.children.size > 1) t.childName.substringBefore(' ').ifBlank { null } else null,
    ).joinToString(" · ")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(if (t.isDone) Icons.Filled.Checklist else Icons.Outlined.Description, null,
            tint = if (t.isDone) Color(0xFF34C759) else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
        Column {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, textDecoration = if (t.isDone) TextDecoration.LineThrough else null,
                color = if (t.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VacationRequestsScreen() {
    val nav = LocalNav.current
    var answering by remember { mutableStateOf<VacationRequest?>(null) }
    LaunchedEffect(Unit) { Repo.vacationRequests.load(600) }
    val list = Repo.vacationRequests.value ?: emptyList()
    AulaScreen(title = L.t("Ferieanmodninger"), onBack = { nav.back() }, refreshing = Repo.vacationRequests.isLoading,
        onRefresh = { Repo.vacationRequests.refresh() }) {
        item { StaleBanner(Repo.vacationRequests.error, Repo.vacationRequests.savedAt) }
        if (list.isEmpty()) item { EmptyState(L.t("Ingen ferieanmodninger"), Icons.Filled.WbSunny) }
        if (list.isNotEmpty()) item {
            Text(L.t("Fortæl institutionen, om dit barn kommer i ferien."), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        list.forEach { r ->
            item {
                Row(Modifier.card().clickable(enabled = r.isEditable) { answering = r }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Filled.WbSunny, Color(0xFFFF9500))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (r.isMissingAnswer) Text(L.t("Mangler svar"), color = Color.White, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.background(Color(0xFFFF3B30), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                        Text("${r.childName} · ${AulaDate.dayMonth(r.startMs)} – ${AulaDate.dayMonth(r.endMs)}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        r.deadlineMs?.let { Text(L.t("Svarfrist %s", AulaDate.dayMonth(it)), style = MaterialTheme.typography.bodySmall,
                            color = if (r.isMissingAnswer) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (r.isEditable) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    answering?.let { r -> VacationAnswerDialog(r) { saved -> answering = null } }
}

@Composable
fun VacationAnswerDialog(r: VacationRequest, done: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val days = remember {
        mutableStateListOf<AulaApi.DayAnswer>().apply {
            var d = AulaDate.localDate(r.startMs)
            val last = AulaDate.localDate(r.endMs)
            while (!d.isAfter(last) && size < 120) {
                if (d.dayOfWeek.value < 6) add(AulaApi.DayAnswer(d, false, "08:00", "16:00"))
                d = d.plusDays(1)
            }
        }
    }
    var comment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { done(false) },
        title = { Text(r.title) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                r.note?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                item {
                    Row {
                        TextButton(onClick = { days.indices.forEach { days[it] = days[it].copy(coming = true) } }) { Text(L.t("Kommer alle dage")) }
                        TextButton(onClick = { days.indices.forEach { days[it] = days[it].copy(coming = false) } }) { Text(L.t("Kommer ikke")) }
                    }
                }
                days.forEachIndexed { i, d ->
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(AulaDate.weekdayDayMonth(d.date), Modifier.weight(1f))
                                Switch(d.coming, { days[i] = d.copy(coming = it) })
                            }
                            if (d.coming && r.needsTimes) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pickTime(ctx, d.entry) { t -> days[i] = days[i].copy(entry = t) } }) { Text(d.entry) }
                                OutlinedButton(onClick = { pickTime(ctx, d.exit) { t -> days[i] = days[i].copy(exit = t) } }) { Text(d.exit) }
                            }
                        }
                    }
                }
                item { OutlinedTextField(comment, { comment = it }, label = { Text(L.t("Bemærkning")) }) }
                error?.let { item { Text(it, color = Color(0xFFFF3B30)) } }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    runCatching { AulaApi.answerVacation(r, days.toList(), comment) }
                        .onSuccess { Repo.vacationRequests.refresh(); done(true) }
                        .onFailure { error = L.t("Ikke sendt: %s", it.message ?: "") }
                }
            }) { Text(L.t("Send")) }
        },
        dismissButton = { TextButton(onClick = { done(false) }) { Text(L.t("Annuller")) } },
    )
}
