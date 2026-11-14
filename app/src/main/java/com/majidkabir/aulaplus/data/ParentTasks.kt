package com.majidkabir.aulaplus.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.core.AppJson
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.Html
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Something a parent has to do, found in a message or post: bring something, reply, sign up, pay … */
@Serializable
data class ParentTask(
    val id: String,
    val title: String,
    /** ISO date (yyyy-MM-dd) or null. */
    val due: String? = null,
    /** "t<threadId>" or "p<postId>". */
    val source: String,
    val sourceTitle: String,
    val sourceMs: Long? = null,
    /** Found by Gemini Nano on the phone (else by keywords). */
    val byAI: Boolean = false,
) {
    val dueDate: LocalDate? get() = due?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val threadId: String? get() = source.takeIf { it.startsWith("t") }?.drop(1)
    val postId: Int? get() = source.takeIf { it.startsWith("p") }?.drop(1)?.toIntOrNull()
}

/**
 * Reads messages and posts on the phone and lists what the parent must do.
 * Nothing leaves the phone: Gemini Nano runs on the device (where it exists), else plain keyword rules.
 */
object ParentTaskFinder {
    @Serializable
    private data class Store(
        val tasks: List<ParentTask> = emptyList(),
        val read: Map<String, Long> = emptyMap(),
        val done: Set<String> = emptySet(),
    )

    private val file get() = File(AulaApp.context.filesDir, "parent-tasks.json")
    private var store by mutableStateOf(load())
    var progress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var usesAI by mutableStateOf(false)
        private set
    private var running: Job? = null

    var isEnabled: Boolean
        get() = enabled
        set(v) { enabled = v; if (AulaApp.isReady) AulaApp.prefs.edit().putBoolean("parentTasksOff", !v).apply() }
    private var enabled by mutableStateOf(!AulaApp.isReady || !AulaApp.prefs.getBoolean("parentTasksOff", false))

    val tasks get() = store.tasks
    val done get() = store.done

    private fun load(): Store = if (!AulaApp.isReady) Store() else
        runCatching { AppJson.decodeFromString(Store.serializer(), file.readText()) }.getOrDefault(Store())

    private fun save() {
        if (AulaApp.isReady) runCatching { file.writeText(AppJson.encodeToString(Store.serializer(), store)) }
    }

    /** Open tasks, soonest first (undated ones by when they were sent). */
    val open: List<ParentTask>
        get() {
            val yesterday = LocalDate.now(AulaDate.copenhagen).minusDays(1)
            val fortnight = System.currentTimeMillis() - 14 * 86_400_000L
            return store.tasks.filter { t ->
                t.id !in store.done && (t.dueDate?.let { it >= yesterday } ?: ((t.sourceMs ?: Long.MAX_VALUE) >= fortnight))
            }.sortedWith(compareBy<ParentTask> { it.dueDate == null }.thenBy { it.dueDate }.thenByDescending { it.sourceMs ?: 0L })
        }

    val finished get() = store.tasks.filter { it.id in store.done }

    fun forSource(key: String) = store.tasks.filter { it.source == key }

    fun toggle(t: ParentTask) {
        store = store.copy(done = if (t.id in store.done) store.done - t.id else store.done + t.id)
        save()
    }

    fun eraseAll() { store = Store(); save() }

    /** FNV-1a, the same on every launch. */
    fun stableHash(s: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (b in s.toByteArray()) { h = (h xor (b.toLong() and 0xff)) * 0x100000001b3L }
        return h
    }

    /** Reads messages and posts from the last three weeks that changed since last time. */
    fun update(threads: List<ThreadSummary>, posts: List<Post>, myName: String) {
        if (!isEnabled || running?.isActive == true || !AulaApp.isReady) return
        val since = System.currentTimeMillis() - 21 * 86_400_000L
        data class Item(val key: String, val title: String, val text: String, val ms: Long?)
        val todo = (threads.filter { (it.dateMs ?: 0) >= since && (myName.isBlank() || !it.sender.equals(myName, true)) }
            .map { Item("t${it.id}", it.subject, it.subject + "\n" + it.preview, it.dateMs) } +
            posts.filter { (it.dateMs ?: 0) >= since }.map { Item("p${it.id}", it.title, it.title + "\n" + Html.plain(it.html), it.dateMs) })
            .filter { store.read[it.key] != stableHash(it.text) }
        if (todo.isEmpty()) return
        running = AppScope.launch {
            try {
                val nano = NanoReader.ready()
                usesAI = nano
                todo.forEachIndexed { i, item ->
                    progress = i to todo.size
                    val sent = item.ms?.let { Instant.ofEpochMilli(it).atZone(AulaDate.copenhagen).toLocalDate() } ?: LocalDate.now()
                    val found = (if (nano) runCatching { NanoReader.read(item.text, sent) }.getOrNull() else null)
                        ?: KeywordReader.read(item.text, sent)
                    val made = found.take(4).map { f ->
                        ParentTask("${item.key}-${stableHash(f.title.lowercase())}", f.title, f.due?.toString(), item.key, item.title, item.ms, f.byAI)
                    }
                    store = store.copy(tasks = store.tasks.filterNot { it.source == item.key } + made,
                        read = store.read + (item.key to stableHash(item.text)))
                }
                val old = LocalDate.now().minusDays(60)
                val kept = store.tasks.filterNot { t ->
                    (t.dueDate ?: t.sourceMs?.let { Instant.ofEpochMilli(it).atZone(AulaDate.copenhagen).toLocalDate() } ?: LocalDate.now()) < old
                }
                store = store.copy(tasks = kept, done = store.done intersect kept.map { it.id }.toSet())
                save()
            } finally {
                progress = null
            }
        }
    }
}

data class FoundTask(val title: String, val due: LocalDate?, val byAI: Boolean)

/** Gemini Nano through ML Kit's Prompt API (Pixel 9/10 and similar phones). */
object NanoReader {
    private val model by lazy { Generation.getClient() }

    suspend fun ready(): Boolean = runCatching { model.checkStatus() == FeatureStatus.AVAILABLE }.getOrDefault(false)

    suspend fun read(text: String, sent: LocalDate): List<FoundTask> {
        val prompt = """
            You read a message from a Danish daycare or school to parents. List only concrete things the parent has to do
            or remember: bring or pack something, reply, sign up, pay, fill in or sign a form, pick up or drop off at a
            special time, or a day the place is closed. Ignore greetings, news and what the children did. Never invent tasks.
            Answer with JSON only: [{"title": "<short Danish instruction, max 8 words>", "date": "<yyyy-MM-dd or empty>"}].
            Answer [] when there is nothing to do. The message was sent ${sent} (${sent.dayOfWeek}).

            Message:
            ${text.take(2500)}
        """.trimIndent()
        val answer = model.generateContent(prompt).candidates.firstOrNull()?.text ?: return emptyList()
        val json = answer.substringAfter("[", "").substringBeforeLast("]", "")
        val list = AppJson.parseToJsonElement("[$json]") as? JsonArray ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val title = o["title"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            FoundTask(title, o["date"]?.jsonPrimitive?.content?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }, true)
        }.distinctBy { it.title.lowercase() }
    }
}

/** Danish keyword rules: sentences that ask the parent to do something, with the day they mention. */
object KeywordReader {
    private val triggers = listOf(
        """\bhusk""", """\bmedbring""", """\btag\b.{0,40}\bmed\b""", """\bskal have\b.{0,40}\bmed\b""", """\btilmeld""",
        """\bsvar\b""", """\bgiv (os )?besked""", """\bmeld (tilbage|fra|til)""", """\bsenest\b""", """\bfrist""",
        """\bbetal""", """\bmobilepay""", """\bunderskriv""", """\baflever""", """\budfyld""", """\bkryds af""",
        """\bafhent""", """\bhent(e|es)?\b.{0,30}\b(kl|klokken|senest|tidlig)""", """\blukket\b""", """\blukker\b""",
        """\bskiftetøj""", """\bregntøj""", """\bmadpakke""", """\bnavn i\b""", """\bpak(ke)?\b""", """\bpåmind""",
    ).map { Regex(it) }
    private val weekdays = mapOf("mandag" to DayOfWeek.MONDAY, "tirsdag" to DayOfWeek.TUESDAY, "onsdag" to DayOfWeek.WEDNESDAY,
        "torsdag" to DayOfWeek.THURSDAY, "fredag" to DayOfWeek.FRIDAY, "lørdag" to DayOfWeek.SATURDAY, "søndag" to DayOfWeek.SUNDAY)
    private val months = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec")

    fun read(text: String, sent: LocalDate): List<FoundTask> {
        val out = mutableListOf<FoundTask>()
        val seen = mutableSetOf<String>()
        for (sentence in text.split('.', '!', '\n', '•').map { it.trim() }.filter { it.isNotEmpty() }) {
            val lower = sentence.lowercase()
            if (lower.length < 8 || lower.endsWith("?") || triggers.none { it.containsMatchIn(lower) }) continue
            val title = cleanTitle(sentence)
            if (!seen.add(title.lowercase())) continue
            out += FoundTask(title, date(lower, sent), false)
            if (out.size >= 3) break
        }
        return out
    }

    private fun cleanTitle(s: String): String {
        var t = s
        for (p in listOf("vi vil gerne minde jer om at ", "vi minder om at ", "husk lige at ", "husk at ", "husk ")) {
            if (t.lowercase().startsWith(p)) { t = t.drop(p.length); break }
        }
        t = t.replaceFirstChar { it.uppercase() }
        if (t.length > 90) t = t.take(90).substringBeforeLast(' ') + "…"
        return t
    }

    fun date(lower: String, sent: LocalDate): LocalDate? {
        if ("i overmorgen" in lower) return sent.plusDays(2)
        if ("i morgen" in lower) return sent.plusDays(1)
        if ("i dag" in lower) return sent
        Regex("""\b(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2,4}))?\b""").find(lower)?.let { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
            if (d in 1..31 && mo in 1..12) return make(d, mo, m.groupValues[3].toIntOrNull(), sent)
        }
        Regex("""\b(\d{1,2})\.?\s+(jan|feb|mar|apr|maj|jun|jul|aug|sep|okt|nov|dec)[a-zæøå]*""").find(lower)?.let { m ->
            return make(m.groupValues[1].toInt(), months.indexOf(m.groupValues[2]) + 1, null, sent)
        }
        for ((name, wd) in weekdays) if (Regex("""\b$name""").containsMatchIn(lower)) return sent.with(TemporalAdjusters.next(wd))
        return null
    }

    private fun make(day: Int, month: Int, year: Int?, sent: LocalDate): LocalDate? {
        val y = year?.let { if (it < 100) 2000 + it else it } ?: sent.year
        var d = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: return null
        if (year == null && d < sent.minusDays(60)) d = d.plusYears(1)
        return d
    }
}
