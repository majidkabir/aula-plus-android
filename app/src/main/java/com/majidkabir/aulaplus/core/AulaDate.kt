package com.majidkabir.aulaplus.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** Parsing and formatting of Aula's timestamps. Naive timestamps are Copenhagen time. */
object AulaDate {
    val copenhagen: ZoneId = ZoneId.of("Europe/Copenhagen")
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun parse(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        // "+0100" → "+01:00" so java.time accepts it (iOS' ISO8601DateFormatter takes both).
        val iso = s.trim().replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
        runCatching { return OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(iso).toEpochMilli() }
        val cleaned = iso.replace(' ', 'T')
        runCatching { return OffsetDateTime.parse(cleaned).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(cleaned).atZone(copenhagen).toInstant().toEpochMilli() }
        runCatching { return LocalDate.parse(s.take(10)).atStartOfDay(copenhagen).toInstant().toEpochMilli() }
        return null
    }

    /** "/Date(1771196400000-0000)/" → millis */
    fun parseDotNet(s: String?): Long? =
        s?.let { Regex("""\d{10,}""").find(it)?.value?.toLongOrNull() } ?: parse(s)

    /** "07:45:00" or "2026-11-12T07:45:00" → "07:45" */
    fun shortTime(s: String?): String? = s?.let { Regex("""\d{2}:\d{2}""").find(it)?.value }

    fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun localDateTime(ms: Long): ZonedDateTime = Instant.ofEpochMilli(ms).atZone(zone)
    fun today(): LocalDate = LocalDate.now(zone)
    fun isToday(ms: Long) = localDate(ms) == today()

    fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun weekNumber(d: LocalDate): Int = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    fun weekYear(d: LocalDate): Int = d.get(IsoFields.WEEK_BASED_YEAR)
    fun weekKey(d: LocalDate): String = "%d-W%02d".format(weekYear(d), weekNumber(d))

    fun startOfDayMs(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun timeOn(hhmm: String, d: LocalDate): Long {
        val t = runCatching { LocalTime.parse(hhmm) }.getOrDefault(LocalTime.of(8, 0))
        return d.atTime(t).atZone(zone).toInstant().toEpochMilli()
    }

    // Formatting uses the app language (see L).
    private fun fmt(pattern: String) = DateTimeFormatter.ofPattern(pattern, L.locale)
    fun time(ms: Long): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(L.locale)
        .format(localDateTime(ms))
    fun dayMonth(ms: Long): String = fmt("d. MMM").format(localDateTime(ms))
    fun weekdayDayMonth(d: LocalDate): String = fmt("EEEE d. MMMM").format(d).replaceFirstChar { it.uppercase() }
    fun weekdayShort(d: LocalDate): String = fmt("EEE").format(d).replaceFirstChar { it.uppercase() }.trimEnd('.')
    fun weekdayNarrow(d: LocalDate): String = fmt("EEEEE").format(d).uppercase()
    fun dayMonthShort(d: LocalDate): String = fmt("d. MMM").format(d)
    fun dateTime(ms: Long): String = fmt("d. MMM yyyy HH:mm").format(localDateTime(ms))

    /** List style: time today, "Yesterday", weekday this week, else date. */
    fun listDate(ms: Long?): String {
        if (ms == null) return ""
        val d = localDate(ms)
        val today = today()
        return when {
            d == today -> time(ms)
            d == today.minusDays(1) -> L.t("I går")
            d.isAfter(today.minusDays(7)) -> fmt("EEEE").format(d).replaceFirstChar { it.uppercase() }
            else -> dayMonth(ms)
        }
    }

    fun relative(ms: Long): String {
        val mins = (System.currentTimeMillis() - ms) / 60000
        return when {
            mins < 1 -> L.t("lige nu")
            mins < 60 -> L.t("for %d min. siden", mins.toInt())
            mins < 60 * 24 -> L.t("for %d t. siden", (mins / 60).toInt())
            else -> dayMonth(ms)
        }
    }
}
