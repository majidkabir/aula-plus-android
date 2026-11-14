package com.majidkabir.aulaplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.ParentTask
import com.majidkabir.aulaplus.data.ParentTaskFinder
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.components.EmptyState
import com.majidkabir.aulaplus.ui.components.SectionTitle
import com.majidkabir.aulaplus.ui.components.card
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val Orange = Color(0xFFFF9500)

/** "Skal gøres" on the Today screen. */
@Composable
fun ParentTasksCard() {
    val nav = LocalNav.current
    val open = ParentTaskFinder.open
    val progress = ParentTaskFinder.progress
    if (!ParentTaskFinder.isEnabled || (open.isEmpty() && progress == null)) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(L.t("Skal gøres"), Icons.Filled.Checklist, tint = Orange, count = open.size.takeIf { it > 0 }) {
            TextButton(onClick = { nav.open("tasks") }) { Text(L.t("Vis alle")) }
        }
        Column(Modifier.card(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            open.take(4).forEach { ParentTaskRow(it) }
            progress?.let { (i, n) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(L.t("Læser beskeder %d af %d …", i + 1, n), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Every task, grouped by when it's due. */
@Composable
fun ParentTasksScreen() {
    val nav = LocalNav.current
    val open = ParentTaskFinder.open
    val today = LocalDate.now(AulaDate.copenhagen)
    val groups = listOf(
        L.t("For sent") to open.filter { (it.dueDate ?: LocalDate.MAX) < today },
        L.t("I dag") to open.filter { it.dueDate == today },
        L.t("Kommende") to open.filter { (it.dueDate ?: LocalDate.MIN) > today },
        L.t("Uden dato") to open.filter { it.dueDate == null },
        L.t("Klaret") to ParentTaskFinder.finished,
    )
    AulaScreen(title = L.t("Skal gøres"), onBack = { nav.back() }) {
        if (groups.all { it.second.isEmpty() }) item {
            EmptyState(L.t("Intet at gøre"), Icons.Filled.Checklist, subtitle = L.t("Når en besked eller et opslag beder dig om noget, står det her."))
        }
        groups.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
            item { MenuGroup(title) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { list.forEach { ParentTaskRow(it) } } } }
        }
        item {
            Text(if (ParentTaskFinder.usesAI) L.t("Fundet af Gemini Nano på telefonen. Beskederne sendes ingen steder hen. Tjek altid den oprindelige besked.")
                else L.t("Fundet ud fra nøgleord som \"husk\", \"medbring\" og \"senest\". Alt sker på telefonen."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
fun ParentTaskRow(t: ParentTask) {
    val nav = LocalNav.current
    val isDone = t.id in ParentTaskFinder.done
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
            tint = if (isDone) Color(0xFF34C759) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(26.dp).clickable { ParentTaskFinder.toggle(t) }
                .semantics { contentDescription = if (isDone) L.t("Markér som ikke klaret") else L.t("Markér som klaret") })
        Column(Modifier.weight(1f).clickable {
            t.threadId?.let { nav.open("thread/$it") } ?: t.postId?.let { nav.open("post/$it") }
        }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall, textDecoration = if (isDone) TextDecoration.LineThrough else null,
                color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                t.dueDate?.let { d ->
                    Text(dueLabel(d), style = MaterialTheme.typography.bodySmall,
                        color = if (d < LocalDate.now(AulaDate.copenhagen) && !isDone) Color(0xFFFF3B30) else Orange)
                }
                Text(t.sourceTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun dueLabel(d: LocalDate): String {
    val today = LocalDate.now(AulaDate.copenhagen)
    return when {
        d == today -> L.t("I dag")
        d == today.plusDays(1) -> L.t("I morgen")
        d == today.minusDays(1) -> L.t("I går")
        d > today && d < today.plusDays(7) -> d.format(DateTimeFormatter.ofPattern("EEEE", L.locale)).replaceFirstChar { it.uppercase(L.locale) }
        else -> d.format(DateTimeFormatter.ofPattern("EEE d. MMM", L.locale))
    }
}

/** Tasks found in one message or post, shown on top of it. */
@Composable
fun SourceTasksBanner(key: String) {
    val list = ParentTaskFinder.forSource(key)
    if (!ParentTaskFinder.isEnabled || list.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(Orange.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Checklist, null, tint = Orange, modifier = Modifier.size(16.dp))
            Text(L.t("Skal gøres"), style = MaterialTheme.typography.labelLarge, color = Orange)
        }
        list.forEach { t ->
            val isDone = t.id in ParentTaskFinder.done
            Row(Modifier.fillMaxWidth().clickable { ParentTaskFinder.toggle(t) }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
                    tint = if (isDone) Color(0xFF34C759) else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                Column {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, textDecoration = if (isDone) TextDecoration.LineThrough else null)
                    t.dueDate?.let { Text(dueLabel(it), style = MaterialTheme.typography.bodySmall, color = Orange) }
                }
            }
        }
    }
}
