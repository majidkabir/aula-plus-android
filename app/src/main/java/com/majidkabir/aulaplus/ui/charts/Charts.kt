package com.majidkabir.aulaplus.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.DayPlan
import com.majidkabir.aulaplus.data.Event
import com.majidkabir.aulaplus.data.Presence
import java.time.LocalDate

private fun minutes(hhmm: String?): Int? = hhmm?.let { AulaDate.shortTime(it) }?.split(":")?.let { it[0].toInt() * 60 + it[1].toInt() }
private fun minutesOf(ms: Long): Int = AulaDate.localDateTime(ms).let { it.hour * 60 + it.minute }
private fun hourLabel(h: Int): String = if (L.isEnglish) {
    val hh = if (h % 12 == 0) 12 else h % 12
    "$hh${if (h < 12) "am" else "pm"}"
} else "%02d".format(h)

/** One-line timeline of a child's day: time at the institution (filled up to now), lessons, and a red now-line. */
@Composable
fun DayTimeline(presence: Presence?, events: List<Event>, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val stayStart = minutes(presence?.checkIn ?: presence?.plannedEntry)
    val stayEnd = minutes(presence?.checkOut ?: presence?.plannedExit)
    // Timed events that start and end today; all-day and multi-day ones have no place on a clock axis.
    val todays = events.filter { !it.allDay && AulaDate.isToday(it.startMs) && AulaDate.isToday(it.endMs) }
    val starts = todays.map { minutesOf(it.startMs) } + listOfNotNull(stayStart)
    val ends = todays.map { minutesOf(it.endMs) } + listOfNotNull(stayEnd)
    val lo = (minOf(starts.minOrNull() ?: 480, 480) / 60) * 60
    val hi = ((maxOf(ends.maxOrNull() ?: 960, 960) + 59) / 60) * 60
    val now = minutesOf(System.currentTimeMillis())
    val desc = if (stayStart != null && stayEnd != null) "%02d:%02d – %02d:%02d".format(stayStart / 60, stayStart % 60, stayEnd / 60, stayEnd % 60) else ""

    Canvas(modifier.fillMaxWidth().height(if (todays.isEmpty()) 40.dp else 60.dp)
        .semantics { contentDescription = L.t("Tidslinje for dagen") + " " + desc }) {
        val axisH = 16.dp.toPx()
        val w = size.width
        fun x(m: Int) = (m - lo).toFloat() / (hi - lo) * w
        // grid + labels every 2 hours
        var h = lo / 60
        while (h * 60 <= hi) {
            val gx = x(h * 60)
            drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height - axisH), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            if ((h - lo / 60) % 2 == 0) {
                val t = measurer.measure(hourLabel(h), TextStyle(fontSize = 10.sp, color = axisColor))
                val tx = (gx - t.size.width / 2).coerceIn(0f, w - t.size.width)
                drawText(t, topLeft = Offset(tx, size.height - t.size.height))
            }
            h++
        }
        val stayY = 6.dp.toPx()
        val stayH = 10.dp.toPx()
        if (stayStart != null && stayEnd != null && stayEnd > stayStart) {
            drawRoundRect(color.copy(alpha = 0.22f), Offset(x(stayStart), stayY), Size(x(stayEnd) - x(stayStart), stayH), CornerRadius(stayH / 2))
            if (now > stayStart) {
                val e = minOf(now, stayEnd)
                drawRoundRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)), Offset(x(stayStart), stayY),
                    Size(x(e) - x(stayStart), stayH), CornerRadius(stayH / 2))
            }
        }
        val lessonY = stayY + stayH + 6.dp.toPx()
        val lessonH = 14.dp.toPx()
        todays.forEach { ev ->
            val s = minutesOf(ev.startMs); val e = minutesOf(ev.endMs)
            drawRoundRect(if (e < now) color.copy(alpha = 0.35f) else color, Offset(x(s), lessonY),
                Size((x(e) - x(s)).coerceAtLeast(3f), lessonH), CornerRadius(4.dp.toPx()))
        }
        if (now in lo..hi) {
            val nx = x(now)
            drawLine(Color(0xFFFF3B30), Offset(nx, 0f), Offset(nx, size.height - axisH), 2.dp.toPx())
        }
    }
}

/** The week's drop-off → pick-up ranges as horizontal bars, one per weekday. */
@Composable
fun WeekPlanChart(plans: List<DayPlan>, days: List<LocalDate>, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val rows = days.mapNotNull { d ->
        val p = plans.firstOrNull { it.date == d.toString() } ?: return@mapNotNull null
        if (p.isOnVacation) Triple(d, 8 * 60 to 16 * 60, true)
        else {
            val s = minutes(p.entry) ?: return@mapNotNull null
            val e = minutes(p.exit) ?: return@mapNotNull null
            Triple(d, s to e, false)
        }
    }
    if (rows.isEmpty()) return
    val rowH = 26.dp
    Canvas(modifier.fillMaxWidth().height(rowH * rows.size + 22.dp)
        .semantics { contentDescription = L.t("Ugens tider") }) {
        val labelW = 40.dp.toPx()
        val endW = 44.dp.toPx()
        val lo = 6 * 60; val hi = 19 * 60
        val w = size.width - labelW - endW
        fun x(m: Int) = labelW + (m - lo).toFloat() / (hi - lo) * w
        val chartH = rowH.toPx() * rows.size
        listOf(6, 9, 12, 15, 18).forEach { h ->
            val gx = x(h * 60)
            drawLine(gridColor, Offset(gx, 0f), Offset(gx, chartH), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            val t = measurer.measure(hourLabel(h), TextStyle(fontSize = 10.sp, color = axisColor))
            drawText(t, topLeft = Offset(gx - t.size.width / 2, chartH + 4.dp.toPx()))
        }
        rows.forEachIndexed { i, (d, range, vacation) ->
            val cy = rowH.toPx() * i + rowH.toPx() / 2
            val label = measurer.measure(AulaDate.weekdayShort(d), TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = labelColor))
            drawText(label, topLeft = Offset(0f, cy - label.size.height / 2))
            val barH = 14.dp.toPx()
            val (rawS, e) = range
            // Keep bars inside the 06–19 axis even for unusual times; the label still shows the real time.
            val s = rawS.coerceIn(lo, hi)
            val barEnd = e.coerceIn(s, hi)
            drawRoundRect(
                if (vacation) Brush.horizontalGradient(listOf(Color(0x66FF9500), Color(0x66FF9500)))
                else Brush.horizontalGradient(listOf(color.copy(alpha = 0.8f), color)),
                Offset(x(s), cy - barH / 2), Size((x(barEnd) - x(s)).coerceAtLeast(barH), barH), CornerRadius(barH / 2),
            )
            val end = measurer.measure(if (vacation) L.t("Fri") else "%02d:%02d".format(e / 60, e % 60),
                TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = axisColor))
            drawText(end, topLeft = Offset((x(barEnd) + 4.dp.toPx()).coerceAtMost(size.width - end.size.width), cy - end.size.height / 2))
        }
    }
}

/** Small circular progress (e.g. tasks done). */
@Composable
fun ProgressRing(done: Int, total: Int, color: Color, size: Dp = 46.dp) {
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Box(Modifier.size(size).semantics { contentDescription = L.t("%d af %d færdige", done, total) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 5.dp.toPx()
            drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(stroke), topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(this.size.width - stroke, this.size.height - stroke))
            drawArc(color, -90f, 360f * fraction, false, style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2), size = Size(this.size.width - stroke, this.size.height - stroke))
        }
        Text("$done/$total", fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
