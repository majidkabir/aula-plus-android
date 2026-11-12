package com.majidkabir.aulaplus.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.MainActivity
import com.majidkabir.aulaplus.core.AppJson
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.ui.theme.Palette
import com.majidkabir.aulaplus.ui.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class WidgetSnapshot(
    val children: List<Line>,
    val unread: Int,
    val latestSender: String? = null,
    val latestSubject: String? = null,
    val brand: Long = 0xFF2966ED,
    val second: Long = 0xFF33BDF2,
) {
    @Serializable
    data class Line(val name: String, val status: String?, val detail: String?, val color: Long)
}

/** Writes the snapshot the home-screen widget shows, then refreshes widgets. */
object WidgetWriter {
    private val file: File? get() = if (AulaApp.isReady) File(AulaApp.context.filesDir, "widget.json") else null

    fun read(): WidgetSnapshot? = runCatching { AppJson.decodeFromString(WidgetSnapshot.serializer(), file!!.readText()) }.getOrNull()

    fun update() {
        val f = file ?: return
        val today = Repo.events(AulaDate.today()).value ?: emptyList()
        val now = System.currentTimeMillis()
        val ids = AppState.children.map { it.id }
        val lines = AppState.children.map { c ->
            val p = Repo.presence.value?.firstOrNull { it.childId == c.id }
            val next = today.firstOrNull { !it.allDay && it.endMs > now && AulaDate.isToday(it.startMs) && (it.childIds.isEmpty() || c.id in it.childIds) }
            val detail = if (p?.plannedExit != null && p.checkOut == null) L.t("Hentes %s", p.plannedExit)
                else next?.let { "${it.title} ${AulaDate.time(it.startMs)}" }
            WidgetSnapshot.Line(c.firstName, p?.presenceStatus?.label, detail, (Palette.forChild(c.id, ids).value shr 32).toLong())
        }
        val threads = Repo.threads.value ?: emptyList()
        val latest = threads.firstOrNull { !it.isRead }
        val t = ThemeStore.theme
        val snap = WidgetSnapshot(lines, threads.count { !it.isRead }, latest?.sender, latest?.subject,
            (t.brandLight.value shr 32).toLong(), (t.second.value shr 32).toLong())
        runCatching { f.writeText(AppJson.encodeToString(WidgetSnapshot.serializer(), snap)) }
        CoroutineScope(Dispatchers.Default).launch { runCatching { AulaWidget().updateAll(AulaApp.context) } }
    }

    fun clear() { runCatching { file?.delete() }; CoroutineScope(Dispatchers.Default).launch { runCatching { AulaWidget().updateAll(AulaApp.context) } } }
}

private fun argb(v: Long) = Color(v.toInt())

class AulaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetWriter.read()
        provideContent { GlanceTheme { Content(snap) } }
    }

    @Composable
    private fun Content(s: WidgetSnapshot?) {
        val white = ColorProvider(Color.White)
        val brand = s?.let { argb(it.brand) } ?: Color(0xFF2966ED)
        Column(
            GlanceModifier.fillMaxSize().background(brand).cornerRadius(22.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            if (s == null) {
                Text("Aula+", style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 18.sp))
                Text(L.t("Åbn appen og log ind."), style = TextStyle(color = white, fontSize = 12.sp))
                return@Column
            }
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${s.unread}", style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 28.sp))
                Spacer(GlanceModifier.width(8.dp))
                Text(L.t(if (s.unread == 1) "ulæst besked" else "ulæste beskeder"), style = TextStyle(color = white, fontSize = 12.sp))
            }
            s.latestSubject?.let {
                Text(s.latestSender ?: "", style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 12.sp), maxLines = 1)
                Text(it, style = TextStyle(color = white, fontSize = 12.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(8.dp))
            s.children.take(3).forEach { c ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(GlanceModifier.size(8.dp).background(argb(c.color)).cornerRadius(4.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text("${c.name} · ${c.status ?: "–"}" + (c.detail?.let { "  $it" } ?: ""),
                        style = TextStyle(color = white, fontSize = 12.sp), maxLines = 1)
                }
            }
        }
    }
}

class AulaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AulaWidget()
}
