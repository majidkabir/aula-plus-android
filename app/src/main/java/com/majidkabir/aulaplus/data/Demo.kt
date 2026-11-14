package com.majidkabir.aulaplus.data

import com.majidkabir.aulaplus.core.AulaDate
import java.time.LocalDate

/** Sample data for screenshots (Paparazzi) and demos. No network when on. */
object Demo {
    var isOn = false

    private val today: LocalDate get() = AulaDate.today()
    private fun at(day: Long, h: Int, m: Int = 0) = AulaDate.timeOn("%02d:%02d".format(h, m), today.plusDays(day))
    private fun ago(minutes: Long) = System.currentTimeMillis() - minutes * 60_000

    val emma = Child(101, "Emma Ghafouri", "Valby Skole · 3.A", "101001", "emma01")
    val noah = Child(102, "Noah Ghafouri", "Børnehuset Solsikken", "101002", "noah01")
    val session = Session("Majid Ghafouri", listOf(emma, noah), listOf(11, 101, 102), "g1", setOf("0029", "0030"), listOf(11))

    val presence = listOf(
        Presence(101, 3, "07:52", null, "08:00", "15:30", "Majid (Far)", null, "Klasselokale"),
        Presence(102, 5, "07:40", null, "07:45", "16:00", "Sara (Mor)", "Har sovet godt i dag", null),
    )

    val threads get() = listOf(
        ThreadSummary("1", "Tur til Zoologisk Have på fredag", "Husk madpakke, drikkedunk og tøj efter vejret. Vi mødes kl. 8.15 ved skolens hovedindgang.", "Line Hansen", ago(25), false, false, listOf("Emma")),
        ThreadSummary("2", "Forældremøde i 3.A", "Kære forældre, vi inviterer til forældremøde tirsdag d. 24. kl. 17.", "Thomas Berg", ago(190), false, false, listOf("Emma"), true),
        ThreadSummary("3", "Noah har haft en god dag", "Han har bygget en stor hule med de andre og spist hele sin frokost.", "Pernille (pædagog)", ago(60 * 26), true, false, listOf("Noah")),
        ThreadSummary("4", "Fotografering", "Der er skolefotografering mandag. Søskendebilleder kan bestilles.", "Kontoret", ago(60 * 50), true),
        ThreadSummary("5", "Helbredsoplysninger", "Fortrolig besked fra sundhedsplejen.", "Sundhedsplejen", ago(60 * 24 * 4), true, true, listOf("Emma")),
    )

    val threadPage get() = ThreadPage("Tur til Zoologisk Have på fredag", listOf(
        Message("m3", "Line Hansen", ago(25), "<p>Husk madpakke, drikkedunk og tøj efter vejret.</p><p>Vi mødes kl. <b>8.15</b> ved skolens hovedindgang og er tilbage ca. 14.</p>",
            listOf(Attachment("Program fredag.pdf", "https://example.com/p.pdf"))),
        Message("m2", "Majid Ghafouri", ago(60 * 20), "<p>Tak! Skal de have penge med til is?</p>"),
        Message("m1", "Line Hansen", ago(60 * 22), "<p>Hej alle. På fredag tager 3.A i Zoologisk Have. Mere info følger.</p>"),
    ), false, true)

    val events: List<Event> get() {
        val lessons = listOf(Triple("Dansk", "Lokale 12", "Line Hansen"), Triple("Matematik", "Lokale 12", "Thomas Berg"),
            Triple("Idræt", "Hallen", "Kasper Holm"), Triple("Engelsk", "Lokale 8", "Anne Lund"), Triple("Natur/teknologi", "Faglokale", "Thomas Berg"))
        val ws = AulaDate.weekStart(today)
        val out = mutableListOf<Event>()
        for (day in 0L until 5L) {
            val off = java.time.temporal.ChronoUnit.DAYS.between(today, ws) + day
            lessons.forEachIndexed { i, l ->
                if ((i + day.toInt()) % 5 == 4) return@forEachIndexed
                val start = at(off, 8 + i, if (i == 0) 0 else 15)
                val sub = i == 1 && day == 2L
                out += Event("l$day-$i", l.first, start, start + 45 * 60_000, false, l.second, l.third,
                    if (sub) "Mads Nielsen" else null, sub, "lesson", listOf(101))
            }
            out += Event("s$day", "Sang og musik", at(off, 10), at(off, 10, 30), false, "Stuen", null, null, false, "event", listOf(102))
        }
        out += Event("e1", "Forældremøde 3.A", at(1, 17), at(1, 19), false, "Klasselokalet", type = "event", childIds = listOf(101))
        return out.sortedBy { it.startMs }
    }

    val posts get() = listOf(
        Post(1, "Julefrokost i SFO'en", "<p>Vi holder julefrokost torsdag d. 18. Alle forældre er velkomne fra kl. 15.</p>", ago(90), "SFO Solstrålen", true, emptyList(), true),
        Post(2, "Nyt fra skolebestyrelsen", "<p>Referat fra seneste møde er nu tilgængeligt.</p>", ago(60 * 30), "Skolebestyrelsen", false, listOf(Attachment("Referat.pdf", null))),
        Post(3, "Lus i 3.A", "<p>Tjek venligst jeres børns hår i weekenden.</p>", ago(60 * 24 * 3), "Line Hansen", false, emptyList(), true),
    )

    val importantDates get() = listOf(
        ImportantDate("d1", "Forældremøde 3.A", at(1, 17)),
        ImportantDate("d2", "Juleferie starter", at(9, 0), true),
    )

    val notifications get() = listOf(
        AulaNotification("n1", "Ny besked: Tur til Zoologisk Have", "messages", "Emma", ago(25), threadId = "1"),
        AulaNotification("n2", "Nyt opslag: Julefrokost i SFO'en", "posts", null, ago(90), postId = 1),
        AulaNotification("n3", "Nye billeder i albummet Efterårsferie", "gallery", "Noah", ago(60 * 30), albumId = 1),
        // 2 new photos in "Juletræet pyntes", Emma tagged in one of them.
        AulaNotification("g1", "Nye billeder i galleriet", "gallery", null, ago(40), albumId = 3, eventType = "NewMedia", mediaId = 1),
        AulaNotification("g2", "Nye billeder i galleriet", "gallery", null, ago(40), albumId = 3, eventType = "NewMedia", mediaId = 2),
        AulaNotification("g3", "Emma er tagget på et billede", "gallery", "Emma", ago(45), eventType = "TaggedInMedia", mediaId = 2, relatedChildId = 101),
    )

    val media get() = (1..12).map { i ->
        val tags = when (i % 4) {
            0 -> listOf(MediaTag(101, "Emma Ghafouri"))
            1 -> listOf(MediaTag(102, "Noah Ghafouri"), MediaTag(900, "Ida Holm"))
            2 -> listOf(MediaTag(101, "Emma Ghafouri"), MediaTag(102, "Noah Ghafouri"))
            else -> emptyList()
        }
        Media(i, isVideo = i == 7, tags = tags, canEditTags = true, dateMs = System.currentTimeMillis() - i * 9 * 3_600_000L)
    }

    val albums get() = listOf(
        Album(1, "Efterårsferie", ago(60 * 24 * 10), null, 24), Album(2, "Motionsdag", ago(60 * 24 * 30), null, 41),
        Album(3, "Juletræet pyntes", ago(60 * 24 * 2), null, 12), Album(4, "Skovtur", ago(60 * 24 * 60), null, 33),
    )

    val weekPlans get() = listOf(WeekPlan("Emma Ghafouri", listOf(
        WeekPlanSection("3.A — Ugebrev", "<p>Denne uge arbejder vi med <b>brøker</b> i matematik og læser højt af <i>Ronja Røverdatter</i>.</p><p>Husk idrætstøj tirsdag og torsdag.</p>"),
        WeekPlanSection("Dansk", "<p>Læs kapitel 4 og 5 til torsdag.</p>"),
    )))

    val tasks get() = listOf(
        SchoolTask("t1", "Læs kapitel 4-5", "Dansk", at(3, 8), null, "Emma Ghafouri", false),
        SchoolTask("t2", "Brøk-opgaver side 32", "Matematik", at(2, 8), null, "Emma Ghafouri", true),
    )

    val vacationRequests get() = listOf(
        VacationRequest(1, 1, 102, "Noah", "Juleferie 2026", at(10, 0), at(20, 0), at(4, 0),
            "Giv besked om Noah kommer i juleferien.", true, true, true),
    )

    val dayPlans: List<DayPlan> get() {
        val ws = AulaDate.weekStart(today)
        return (0L until 5L).map { DayPlan(1, 102, ws.plusDays(it).toString(), "07:45", "16:00", 0, "Sara (Mor)", null, false, true) }
    }

    fun value(key: String): Any? = when {
        key == "session" -> session
        key == "presence" -> presence
        key == "threads" -> threads
        key == "posts" -> posts
        key == "notifications" -> notifications
        key == "important-dates" -> importantDates
        key == "albums" -> albums
        key == "vacation-requests" -> vacationRequests
        key == "mail-folders" -> listOf(MailFolder(1, "Slettet post", "deleted"), MailFolder(2, "Fodbold", "normal"))
        key.startsWith("threads-folder-") -> emptyList<ThreadSummary>()
        key == "birthdays" -> listOf(
            Birthday(9001, "Liva Holm", java.time.LocalDate.now().plusDays(2).toString(), "Solsikken", "101002"),
            Birthday(noah.id, noah.name, java.time.LocalDate.now().plusDays(5).toString(), "Solsikken", "101002"),
        )
        key == "posts-important" -> posts.filter { it.isImportant }
        key == "posts-staff" || key == "posts-unread" -> posts
        key.startsWith("posts-") -> emptyList<Post>()
        key.startsWith("events-") -> events
        key.startsWith("thread-") -> threadPage
        key.startsWith("weekplan-") -> weekPlans
        key.startsWith("tasks-") -> tasks
        key.startsWith("plans-") -> dayPlans
        key.startsWith("album-") || key.startsWith("tagged-") -> media
        else -> null
    }
}
