package com.majidkabir.aulaplus.data

import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.core.AppJson
import com.majidkabir.aulaplus.core.AulaConfig
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.Html
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.core.NotLoggedInException
import com.majidkabir.aulaplus.core.TokenStore
import com.majidkabir.aulaplus.core.arr
import com.majidkabir.aulaplus.core.asObj
import com.majidkabir.aulaplus.core.bool
import com.majidkabir.aulaplus.core.el
import com.majidkabir.aulaplus.core.firstStr
import com.majidkabir.aulaplus.core.html
import com.majidkabir.aulaplus.core.int
import com.majidkabir.aulaplus.core.obj
import com.majidkabir.aulaplus.core.objects
import com.majidkabir.aulaplus.core.objs
import com.majidkabir.aulaplus.core.personName
import com.majidkabir.aulaplus.core.str
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

sealed class AulaException(msg: String) : Exception(msg) {
    class Http(val code: Int) : AulaException(L.t("Aula svarede med HTTP %d.", code))
    class Rejected(method: String, code: Int, sub: Int?) :
        AulaException(L.t("Aula afviste %s (kode %s).", method, sub?.let { "$code/$it" } ?: "$code"))
    class StepUp : AulaException(L.t("Kræver MitID-login på højeste niveau."))
    class Decoding(m: String) : AulaException(L.t("Uventet svar fra Aula: %s", m))
}

/** Keeps Aula's session cookies (PHPSESSID, Csrfp-Token…) for the life of the process. */
private class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store["${it.domain}|${it.name}"] = it }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store.values.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
    @Synchronized fun get(name: String): String? = store.values.firstOrNull { it.name == name }?.value
    @Synchronized fun clear() = store.clear()
}

/**
 * Aula's internal JSON API (`www.aula.dk/api/vNN/?method=...`), mirroring the official app:
 * access_token as query parameter, cookies, csrfp-token on POST, refresh + retry on 401 / sub-codes 9, 23,
 * and an automatic version bump on HTTP 410.
 */
object AulaApi {
    private val cookies = MemoryCookieJar()
    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("Accept", chain.request().header("Accept") ?: "application/json, text/plain")
                .header("User-Agent", "Android").header("App-Device-Type", "Phone").build())
        }
        .build()

    @Volatile private var version: Int = 0
        get() {
            if (field == 0) field = AulaApp.prefs.getInt("apiVersion", AulaConfig.DEFAULT_API_VERSION)
            return field
        }

    private val bootLock = Mutex()
    private var session: Session? = null
    private var sessionReady = false

    fun reset() {
        sessionReady = false
        session = null
        cookies.clear()
    }

    // region Transport

    private fun status(root: JsonObject?): Pair<Int?, Int?> =
        root.obj("status").let { it.int("code") to it.int("subCode") }

    private fun isAuthFailure(code: Int, root: JsonObject?): Boolean {
        if (code == 401) return true
        val sub = status(root).second
        return sub == 9 || sub == 23
    }

    private suspend fun send(method: String, query: List<Pair<String, String>>, body: JsonObject?): Pair<Int, JsonObject?> =
        withContext(Dispatchers.IO) {
            val token = TokenStore.accessToken()
            repeat(5) {
                val v = version
                val url = "${AulaConfig.API_BASE}$v/".toHttpUrl().newBuilder()
                    .addQueryParameter("method", method)
                    .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .addQueryParameter("access_token", token)
                    .build()
                val req = Request.Builder().url(url).apply {
                    if (body != null) {
                        post(body.toString().toRequestBody("application/json".toMediaType()))
                        cookies.get(AulaConfig.CSRF_COOKIE)?.let { header(AulaConfig.CSRF_HEADER, it) }
                    }
                }.build()
                http.newCall(req).execute().use { resp ->
                    if (resp.code == 410) {
                        // Parallel calls all see the 410; bump once, not once per call.
                        synchronized(this@AulaApi) {
                            if (version == v) {
                                version = v + 1
                                AulaApp.prefs.edit().putInt("apiVersion", version).apply()
                            }
                        }
                        return@repeat
                    }
                    val text = resp.body?.string().orEmpty()
                    val root = runCatching { AppJson.parseToJsonElement(text).asObj() }.getOrNull()
                    return@withContext resp.code to root
                }
            }
            throw AulaException.Http(410)
        }

    /** Calls an Aula method and returns its `data`. */
    suspend fun call(
        method: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JsonObject? = null,
        needsSession: Boolean = true,
    ): JsonElement? {
        if (needsSession && !sessionReady) bootstrap()
        var (code, root) = send(method, query, body)
        if (isAuthFailure(code, root)) {
            TokenStore.refresh()
            if (needsSession) { sessionReady = false; bootstrap() }
            val again = send(method, query, body)
            code = again.first; root = again.second
        }
        val (scode, sub) = status(root)
        if (sub == 8) throw AulaException.StepUp()
        if (isAuthFailure(code, root)) throw NotLoggedInException()
        if (scode != null && scode != 0) throw AulaException.Rejected(method, scode, sub)
        if (code !in 200..299) throw AulaException.Http(code)
        return root.el("data")
    }

    private suspend fun external(url: String, query: List<Pair<String, String>>, headers: Map<String, String>): JsonElement =
        withContext(Dispatchers.IO) {
            val u = url.toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val req = Request.Builder().url(u).apply { headers.forEach { (k, v) -> header(k, v) } }
                .header("Accept", "application/json").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw AulaException.Http(resp.code)
                AppJson.parseToJsonElement(resp.body?.string().orEmpty())
            }
        }

    // endregion

    // region Session

    suspend fun bootstrap(): Session = bootLock.withLock {
        session?.takeIf { sessionReady }?.let { return it }
        val profiles = call("profiles.getProfilesByLogin", needsSession = false).asObj()
        val profile = profiles.objs("profiles").firstOrNull() ?: throw AulaException.Decoding("ingen profil")
        val ctx = call("profiles.getProfileContext", listOf("portalrole" to "guardian"), needsSession = false).asObj()
        val children = profile.objs("children").mapNotNull { parseChild(it, null) }.toMutableList()
        for (inst in ctx.objs("institutions")) {
            for (c in inst.objs("children")) {
                val child = parseChild(c, inst.str("name")) ?: continue
                if (children.none { it.id == child.id }) children += child
            }
        }
        val widgets = ctx.obj("pageConfiguration").objs("widgetConfigurations")
            .filter { (it.str("aggregatedDisplayMode") ?: "Shown") == "Shown" }
            .mapNotNull { it.obj("widget").str("widgetId") }.toSet()
        val guardianIds = profile.objs("institutionProfiles").mapNotNull { it.int("id") }
        val s = Session(
            displayName = profile.str("displayName") ?: "",
            children = children,
            institutionProfileIds = (guardianIds + children.map { it.id }).distinct(),
            guardianUserId = ctx.str("userId"),
            widgetIds = widgets,
            guardianProfileIds = guardianIds,
        )
        session = s
        sessionReady = true
        s
    }

    private fun parseChild(o: JsonObject, fallbackInstitution: String?): Child? {
        val id = o.int("id") ?: return null
        val inst = o.obj("institutionProfile")
        return Child(
            id = id,
            name = o.str("name") ?: "Barn",
            institutionName = inst.str("institutionName") ?: fallbackInstitution ?: "",
            institutionCode = inst.str("institutionCode") ?: o.str("institutionCode"),
            userId = o.str("userId"),
            pictureUrl = o.obj("profilePicture").str("url"),
        )
    }

    // endregion

    // region Parsing helpers

    private fun attachment(o: JsonObject): Attachment {
        val url = listOf(o.obj("media").obj("file").str("url"), o.obj("file").str("url"), o.str("url"), o.obj("link").str("url"))
            .firstOrNull { it != null }
        val name = listOf(o.str("name"), o.obj("file").str("name"), o.obj("media").obj("file").str("name"),
            o.obj("media").str("title"), o.obj("link").str("name"), o.obj("document").str("title"))
            .firstOrNull { it != null } ?: L.t("Vedhæftet fil")
        val linkOnly = o.obj("link").str("url") != null && o.obj("media").obj("file").str("url") == null && o.obj("file").str("url") == null
        return Attachment(name, url, linkOnly)
    }

    private fun ids(list: List<Int>, name: String) = list.map { name to it.toString() }

    // endregion

    // region Today / presence

    suspend fun presence(childIds: List<Int>): List<Presence> = coroutineScope {
        childIds.map { id ->
            async {
                // A child without Komme/Gå (e.g. a school child) is rejected → no overview. Auth/network errors still surface.
                try { call("presence.getDailyOverview", listOf("childIds[]" to id.toString())) } catch (e: AulaException) { null }
                    .objects().mapNotNull { d ->
                        val cid = d.obj("institutionProfile").int("id") ?: return@mapNotNull null
                        val loc = when (val l = d.el("location")) {
                            is JsonPrimitive -> l.content
                            is JsonObject -> l.str("name")
                            else -> null
                        }
                        Presence(cid, d.int("status"), AulaDate.shortTime(d.str("checkInTime")),
                            AulaDate.shortTime(d.str("checkOutTime")), AulaDate.shortTime(d.str("entryTime")),
                            AulaDate.shortTime(d.str("exitTime")), d.str("exitWith"), d.str("comment"), loc)
                    }
            }
        }.awaitAll().flatten()
    }

    suspend fun setSick(childIds: List<Int>, sick: Boolean) {
        call("presence.updateStatusByInstitutionProfileIds", body = buildJsonObject {
            putJsonArray("institutionProfileIds") { childIds.forEach { add(JsonPrimitive(it)) } }
            put("status", if (sick) PresenceStatus.SICK.code else PresenceStatus.NOT_PRESENT.code)
        })
    }

    suspend fun addVacation(childIds: List<Int>, from: LocalDate, to: LocalDate, comment: String) {
        call("calendar.addVacation", body = buildJsonObject {
            putJsonArray("childIds") { childIds.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("intervals") {
                add(buildJsonObject { put("startDate", from.toString()); put("endDate", to.toString()) })
            }
            put("comment", comment.take(250))
        })
    }

    suspend fun dayPlans(childIds: List<Int>, from: LocalDate, to: LocalDate): List<DayPlan> {
        val data = call("presence.getPresenceTemplates",
            listOf("fromDate" to from.toString(), "toDate" to to.toString()) + ids(childIds, "filterInstitutionProfileIds[]")).asObj()
        return data.objs("presenceWeekTemplates").flatMap { w ->
            val cid = w.obj("institutionProfile").int("id") ?: return@flatMap emptyList()
            w.objs("dayTemplates").mapNotNull { d ->
                val date = d.str("byDate")?.take(10) ?: return@mapNotNull null
                DayPlan(d.int("id"), cid, date, AulaDate.shortTime(d.str("entryTime")), AulaDate.shortTime(d.str("exitTime")),
                    d.int("activityType"), d.str("exitWith"), d.str("comment"), d.bool("isOnVacation") ?: false,
                    (d.str("repeatPattern") ?: "never").lowercase() != "never")
            }
        }.sortedBy { it.date }
    }

    suspend fun savePlan(p: DayPlan) {
        val entry = p.entry ?: "08:00"
        val exit = p.exit ?: "16:00"
        val kind = PickupKind.of(p.kind) ?: PickupKind.PICKED_UP_BY
        val activity = buildJsonObject {
            put("activityType", kind.code)
            when (kind) {
                PickupKind.PICKED_UP_BY -> putJsonObject("pickup") { put("entryTime", entry); put("exitTime", exit); put("exitWith", p.exitWith ?: "") }
                PickupKind.GO_HOME_WITH -> putJsonObject("goHomeWith") { put("entryTime", entry); put("exitTime", exit); put("exitWith", p.exitWith ?: "") }
                PickupKind.SELF_DECIDER -> putJsonObject("selfDecider") { put("entryTime", entry); put("exitStartTime", exit); put("exitEndTime", exit) }
                PickupKind.SEND_HOME -> putJsonObject("sendHome") { put("entryTime", entry); put("exitTime", exit) }
            }
        }
        val d = LocalDate.parse(p.date)
        val endYear = if (d.monthValue > 6) d.year + 1 else d.year
        call("presence.updatePresenceTemplate", body = buildJsonObject {
            put("institutionProfileId", p.childId)
            put("byDate", p.date)
            put("presenceActivity", activity)
            put("comment", p.comment ?: "")
            put("repeatPattern", "never")
            put("expiresAt", "$endYear-06-30T00:00:00+00:00")
            if (p.templateId != null && !p.repeats) put("id", p.templateId)
        })
    }

    suspend fun presencePermissions(childIds: List<Int>): List<PresencePermissions> =
        call("presence.getPresenceConfigurationByChildIds", ids(childIds, "childIds[]")).objects().mapNotNull { item ->
            val cid = item.int("uniStudentId") ?: item.int("childId") ?: return@mapNotNull null
            val modules = item.obj("presenceConfiguration").objs("dashboardModuleSettings")
                .firstOrNull { it.str("presenceDashboardContext") == "guardian_dashboard" }.objs("presenceModules")
            fun editable(t: String) = modules.firstOrNull { it.str("moduleType") == t }.str("permission") == "editable"
            PresencePermissions(cid, editable("report_sick"), editable("vacation"), editable("pickup_times") || editable("pickup_type"))
        }

    suspend fun pickupNames(childId: Int): List<String> {
        val out = mutableListOf<String>()
        for (item in call("presence.getPickupResponsibles", listOf("uniStudentIds[]" to childId.toString())).objects()) {
            if (item.int("uniStudentId")?.let { it != childId } == true) continue
            for (p in item.objs("relatedPersons")) {
                val n = p.str("name") ?: continue
                out += p.str("relation")?.let { "$n ($it)" } ?: n
            }
            out += item.objs("pickupSuggestions").mapNotNull { it.str("pickUpName") }
        }
        return out.distinct()
    }

    suspend fun vacationRequests(childIds: List<Int>): List<VacationRequest> =
        call("presence.getVacationRegistrationsByChildren", ids(childIds, "childIds[]")).objects().flatMap { g ->
            val cid = g.obj("child").int("id") ?: return@flatMap emptyList()
            g.objs("vacationRegistrations").mapNotNull { r ->
                VacationRequest(
                    id = r.int("vacationRegistrationId") ?: return@mapNotNull null,
                    responseId = r.int("responseId") ?: return@mapNotNull null,
                    childId = cid, childName = g.obj("child").str("name") ?: "",
                    title = r.str("title") ?: L.t("Ferieanmodning"),
                    startMs = AulaDate.parse(r.str("startDate")) ?: return@mapNotNull null,
                    endMs = AulaDate.parse(r.str("endDate")) ?: return@mapNotNull null,
                    deadlineMs = AulaDate.parse(r.str("responseDeadline")),
                    note = r.str("noteToGuardian"),
                    isEditable = r.bool("isEditable") ?: true,
                    isMissingAnswer = r.bool("isMissingAnswer") ?: false,
                    needsTimes = r.bool("isPresenceTimesRequired") ?: false,
                )
            }
        }.sortedBy { it.startMs }

    data class DayAnswer(val date: LocalDate, val coming: Boolean, val entry: String, val exit: String)

    suspend fun answerVacation(r: VacationRequest, days: List<DayAnswer>, comment: String) {
        call("calendar.respondToVacationRegistrationRequest", body = buildJsonObject {
            put("childId", r.childId)
            put("vacationRegistrationResponseId", r.responseId)
            putJsonArray("days") {
                days.forEach { d ->
                    add(buildJsonObject {
                        put("date", d.date.toString())
                        put("isComing", d.coming)
                        put("entryTime", if (d.coming && r.needsTimes) JsonPrimitive(d.entry) else JsonNull)
                        put("exitTime", if (d.coming && r.needsTimes) JsonPrimitive(d.exit) else JsonNull)
                    })
                }
            }
            put("comment", comment)
        })
    }

    // endregion

    // region Messages

    private fun thread(t: JsonObject): ThreadSummary? {
        val id = t.str("id") ?: return null
        val latest = t.obj("latestMessage")
        return ThreadSummary(
            id = id,
            subject = t.str("subject") ?: L.t("(Intet emne)"),
            preview = Html.plain(latest.html("text")),
            sender = latest.obj("sender").personName() ?: t.obj("creator").personName() ?: "",
            dateMs = AulaDate.parse(latest.str("sendDateTime") ?: t.str("lastMessageDate") ?: t.str("lastUpdatedDate")),
            isRead = t.bool("read") ?: true,
            isSensitive = t.bool("sensitive") ?: t.bool("isSensitive") ?: false,
            regarding = t.objs("regardingChildren").mapNotNull { it.personName() },
            isMarked = t.bool("marked") ?: false,
        )
    }

    suspend fun threads(page: Int = 0): Pair<List<ThreadSummary>, Boolean> {
        val d = call("messaging.getThreads", listOf("sortOn" to "date", "orderDirection" to "desc", "page" to "$page")).asObj()
        return d.objs("threads").mapNotNull(::thread) to (d.bool("moreMessagesExist") ?: false)
    }

    suspend fun messages(threadId: String, page: Int = 0): ThreadPage {
        val d = call("messaging.getMessagesForThread", listOf("threadId" to threadId, "page" to "$page", "limit" to "20")).asObj()
        val msgs = d.objs("messages")
            .filter { (it.str("messageType") ?: "Message") in setOf("Message", "MessageEdited") }
            .map { m ->
                Message(m.str("id") ?: m.hashCode().toString(), m.obj("sender").personName() ?: "",
                    AulaDate.parse(m.str("sendDateTime")), m.html("text"), m.objs("attachments").map(::attachment))
            }
        return ThreadPage(d.str("subject") ?: "", msgs, d.bool("moreMessagesExist") ?: false,
            d.obj("mailBoxOwner").str("mailBoxOwnerType") == "institutionProfile")
    }

    private fun idValue(id: String): JsonPrimitive = id.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(id)

    suspend fun markRead(threadId: String, messageId: String) {
        call("messaging.setLastReadMessage", body = buildJsonObject {
            put("threadId", idValue(threadId)); put("messageId", messageId)
            put("commonInboxId", JsonNull); put("otpInboxId", JsonNull)
        })
    }

    suspend fun reply(threadId: String, text: String) {
        call("messaging.reply", body = buildJsonObject {
            put("threadId", idValue(threadId))
            putJsonObject("message") { put("text", Html.fromPlain(text)); put("attachmentIds", JsonArray(emptyList())) }
            put("commonInboxId", JsonNull); put("bundleId", JsonNull)
        })
    }

    suspend fun startThread(subject: String, text: String, recipients: List<Recipient>, sensitive: Boolean) {
        call("messaging.startNewThread", body = buildJsonObject {
            put("subject", subject)
            putJsonObject("message") { put("text", Html.fromPlain(text)); put("attachmentIds", JsonArray(emptyList())) }
            putJsonArray("recipients") {
                recipients.forEach { r ->
                    add(buildJsonObject {
                        put("id", r.id)
                        put("mailBoxOwnerType", if (r.type.contains("group", true)) "Group" else "InstitutionProfile")
                        put("profileId", r.id); put("otpInboxId", JsonNull)
                    })
                }
            }
            put("bccRecipients", JsonArray(emptyList())); put("sensitive", sensitive); put("creator", JsonNull)
        })
    }

    suspend fun findRecipients(text: String): List<Recipient> =
        call("search.findRecipients", listOf("Text" to text, "Limit" to "30", "TypeAhead" to "true")).asObj()
            .objs("results").mapNotNull { r ->
                val id = r.str("docId")?.toIntOrNull() ?: return@mapNotNull null
                Recipient(id, r.str("name") ?: L.t("Ukendt"), r.firstStr("description", "institutionName"), r.str("docType") ?: "Profile")
            }

    suspend fun setMarked(threadId: String, marked: Boolean) {
        call("messaging.setThreadsMarked", body = buildJsonObject {
            put("marked", marked); putJsonArray("threadIds") { add(idValue(threadId)) }
            put("subscriptionIds", JsonNull); put("commonInboxId", JsonNull)
        })
    }

    suspend fun deleteThread(threadId: String) {
        call("messaging.deleteThreads", body = buildJsonObject {
            putJsonArray("threadIds") { add(idValue(threadId)) }
            put("subscriptionIds", JsonNull); put("commonInboxId", JsonNull)
        })
    }

    // endregion

    // region Calendar

    suspend fun events(childIds: List<Int>, from: LocalDate, to: LocalDate): List<Event> {
        val off = { d: LocalDate -> d.atStartOfDay(AulaDate.copenhagen).offset.id.replace(":", "").replace("Z", "+0000") }
        val list = call("calendar.getEventsByProfileIdsAndResourceIds", body = buildJsonObject {
            putJsonArray("instProfileIds") { childIds.forEach { add(JsonPrimitive(it)) } }
            put("resourceIds", JsonArray(emptyList()))
            put("start", "$from 00:00:00.0000${off(from)}")
            put("end", "$to 23:59:59.0000${off(to)}")
        }).objects()
        val merged = linkedMapOf<String, Event>()
        for (e in list) {
            val id = e.str("id") ?: continue
            val start = AulaDate.parse(e.str("startDateTime")) ?: continue
            val people = e.obj("lesson").objs("participants")
            val ev = Event(
                id = id, title = e.str("title") ?: L.t("Begivenhed"), startMs = start,
                endMs = AulaDate.parse(e.str("endDateTime")) ?: start, allDay = e.bool("allDay") ?: false,
                location = e.obj("primaryResource").str("name"),
                teacher = people.firstOrNull { it.str("participantRole") == "primaryTeacher" }.str("teacherName"),
                substitute = people.firstOrNull { it.str("participantRole") == "substituteTeacher" }.str("teacherName"),
                hasSubstitute = e.obj("lesson").str("lessonStatus").equals("substitute", true),
                type = e.str("type"),
                childIds = e.arr("belongsToProfiles").mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() },
            )
            merged[id] = merged[id]?.let { it.copy(childIds = (it.childIds + ev.childIds).distinct()) } ?: ev
        }
        return merged.values.sortedBy { it.startMs }
    }

    suspend fun eventDetail(id: String): EventDetail {
        val d = call("calendar.getEventById", listOf("eventId" to id)).asObj()
        return EventDetail(
            id = d.int("id") ?: id.toIntOrNull() ?: 0, title = d.str("title") ?: L.t("Begivenhed"),
            html = d.html("description"), startMs = AulaDate.parse(d.str("startDateTime")),
            endMs = AulaDate.parse(d.str("endDateTime")), allDay = d.bool("allDay") ?: false,
            location = d.obj("primaryResource").str("name") ?: d.str("primaryResourceText"),
            creator = d.obj("creator").str("name"), responseRequired = d.bool("responseRequired") ?: false,
            deadlineMs = AulaDate.parse(d.str("responseDeadline")), deadlineExceeded = d.bool("isDeadlineExceeded") ?: false,
            invitees = d.objs("invitees").map { i ->
                Invitee(i.obj("instProfile").int("institutionProfileId"), i.obj("instProfile").personName() ?: "", i.str("responseType"))
            },
            attachments = d.objs("attachments").map(::attachment),
        )
    }

    suspend fun respond(eventId: Int, profileId: Int, rsvp: Rsvp) {
        call("calendar.respondToSimpleEvent", body = buildJsonObject {
            put("eventId", eventId); put("institutionProfileId", profileId); put("invitedInstProfileId", profileId)
            put("responseType", rsvp.wire); put("occurrenceDateTime", JsonNull)
        })
    }

    suspend fun importantDates(): List<ImportantDate> =
        call("calendar.getImportantDates", listOf("limit" to "10", "includeToday" to "true")).objects().mapNotNull { d ->
            val start = AulaDate.parse(d.str("startDateTime") ?: d.str("startDate")) ?: return@mapNotNull null
            ImportantDate(d.str("id") ?: start.toString(), d.str("title") ?: L.t("Begivenhed"), start, d.bool("allDay") ?: false)
        }

    // endregion

    // region Posts, gallery, notifications

    private fun post(p: JsonObject): Post? {
        val id = p.int("id") ?: return null
        return Post(id, p.str("title") ?: L.t("Opslag"), p.html("content"), AulaDate.parse(p.str("publishAt") ?: p.str("timestamp")),
            p.obj("ownerProfile").personName() ?: "", p.bool("isImportant") ?: false, p.objs("attachments").map(::attachment),
            (p.bool("allowComments") ?: false) && (p.bool("canCurrentUserComment") ?: true))
    }

    suspend fun posts(profileIds: List<Int>): List<Post> =
        call("posts.getAllPosts", listOf("parent" to "profile", "index" to "0", "limit" to "20") + ids(profileIds, "institutionProfileIds[]"))
            .asObj().objs("posts").mapNotNull(::post)

    suspend fun post(id: Int): Post? {
        val o = call("posts.getById", listOf("id" to "$id")).asObj() ?: return null
        return post(o)
    }

    suspend fun comments(postId: Int): List<Comment> {
        val d = call("comments.getComments", listOf("ParentType" to "Post", "ParentId" to "$postId", "Limit" to "100"))
        val list = (d as? JsonArray)?.objects() ?: d.asObj().objs("comments")
        return list.mapNotNull { c ->
            Comment(c.int("id") ?: return@mapNotNull null, c.obj("owner").str("name") ?: c.str("creatorName") ?: "",
                c.html("text"), AulaDate.parse(c.str("createdAt")))
        }
    }

    suspend fun addComment(postId: Int, text: String, profileId: Int) {
        call("comments.addComment", body = buildJsonObject {
            putJsonObject("commentableItem") { put("type", "post"); put("id", postId) }
            put("content", text); put("creatorInstProfileId", profileId)
        })
    }

    suspend fun albums(profileIds: List<Int>): List<Album> {
        val d = call("gallery.getAlbums", listOf("index" to "0", "limit" to "30", "sortOn" to "createdAt",
            "orderDirection" to "desc", "filterBy" to "all") + ids(profileIds, "filterInstProfileIds[]"))
        val list = (d as? JsonArray)?.objects() ?: d.asObj().objs("albums")
        return list.mapNotNull { a ->
            val cover = a.arr("thumbnailsUrls").firstNotNullOfOrNull { (it as? JsonPrimitive)?.content ?: it.asObj().str("url") }
            Album(a.int("id") ?: return@mapNotNull null, a.str("title") ?: "Album", AulaDate.parse(a.str("creationDate")),
                cover, a.int("size") ?: a.int("totalSize"))
        }.distinctBy { it.id } // Aula lists an item once per child profile
    }

    suspend fun media(albumId: Int, profileIds: List<Int>): List<Media> {
        val d = call("gallery.getMedia", listOf("albumId" to "$albumId", "index" to "0", "limit" to "500", "sortOn" to "uploadedAt",
            "orderDirection" to "desc", "filterBy" to "all") + ids(profileIds, "filterInstProfileIds[]"))
        val list = (d as? JsonArray)?.objects() ?: d.asObj().objs("results")
        return list.mapNotNull { m ->
            Media(m.int("id") ?: return@mapNotNull null,
                m.firstStr("mediumThumbnailUrl", "thumbnailUrl", "smallThumbnailUrl", "largeThumbnailUrl") ?: m.obj("file").str("url"),
                m.obj("file").str("url") ?: m.str("largeThumbnailUrl"),
                (m.str("mediaType") ?: "").contains("video", true))
        }.distinctBy { it.id } // Aula lists an item once per child profile
    }

    suspend fun notifications(): List<AulaNotification> =
        call("notifications.getNotificationsForActiveProfile", listOf("offset" to "0", "limit" to "50")).objects().map { n ->
            val title = n.firstStr("title", "heading", "postTitle", "threadSubject")
                ?: n.str("messageText")?.let(Html::plain) ?: n.str("notificationEventType") ?: L.t("Notifikation")
            AulaNotification(n.firstStr("id", "notificationId") ?: title.hashCode().toString(), title,
                n.firstStr("module", "notificationArea"), n.str("relatedChildName"),
                AulaDate.parse(n.str("createdAt") ?: n.str("triggered")), n.int("postId"), n.str("threadId"), n.int("albumId"))
        }

    // endregion

    // region Week plans and tasks (third-party widgets)

    private suspend fun widgetToken(id: String): String = "Bearer " + ((call("aulaToken.getAulaToken", listOf("widgetId" to id)) as? JsonPrimitive)?.content ?: "")

    suspend fun weekPlans(s: Session, d: LocalDate): List<WeekPlan> {
        val childFilter = s.children.mapNotNull { it.userId }
        val instFilter = s.children.mapNotNull { it.institutionCode }.distinct()
        if (AulaConfig.WIDGET_MEEBOOK in s.widgetIds) {
            val q = listOf("currentWeekNumber" to AulaDate.weekKey(d), "userProfile" to "guardian") +
                childFilter.map { "childFilter[]" to it } + instFilter.map { "institutionFilter[]" to it }
            val students = external("${AulaConfig.MEEBOOK_API}/relatedweekplan/all", q,
                mapOf("Authorization" to widgetToken(AulaConfig.WIDGET_MEEBOOK), "sessionUUID" to (s.guardianUserId ?: ""), "X-Version" to "1.0"))
            return students.objects().map { st ->
                WeekPlan(st.str("name") ?: "", st.objs("weekPlan").mapNotNull { day ->
                    val tasks = day.objs("tasks")
                    if (tasks.isEmpty()) return@mapNotNull null
                    val title = AulaDate.parse(day.str("date"))?.let { AulaDate.weekdayDayMonth(AulaDate.localDate(it)) } ?: (day.str("date") ?: "")
                    WeekPlanSection(title, tasks.joinToString("<br>") { "<p><b>${it.firstStr("title", "pill") ?: ""}</b></p>${it.str("content") ?: ""}" })
                })
            }
        }
        val mu = listOf(AulaConfig.WIDGET_MU_UGEPLAN, AulaConfig.WIDGET_MU_SSO).firstOrNull { it in s.widgetIds } ?: return emptyList()
        val resp = external("${AulaConfig.MIN_UDDANNELSE_API}/ugebrev", listOf(
            "assuranceLevel" to "3", "childFilter" to childFilter.joinToString(","),
            "currentWeekNumber" to "${AulaDate.weekYear(d)}-W${AulaDate.weekNumber(d)}",
            "institutionFilter" to instFilter.joinToString(","), "isMobileApp" to "false", "placement" to "narrow",
            "sessionUUID" to (s.guardianUserId ?: ""), "userProfile" to "guardian",
        ), mapOf("Authorization" to widgetToken(mu))).asObj()
        return resp.objs("personer").map { p ->
            WeekPlan(p.str("navn") ?: "", p.objs("institutioner").flatMap { it.objs("ugebreve") }.mapNotNull { l ->
                l.str("indhold")?.let { WeekPlanSection(l.str("tilknytningNavn") ?: L.t("Ugebrev"), it) }
            })
        }
    }

    suspend fun schoolTasks(s: Session, d: LocalDate): List<SchoolTask> {
        val widget = listOf(AulaConfig.WIDGET_MU_TASKS, AulaConfig.WIDGET_MU_SSO).firstOrNull { it in s.widgetIds } ?: return emptyList()
        val q = listOf("placement" to "narrow", "sessionUUID" to (s.guardianUserId ?: ""), "userProfile" to "guardian",
            "currentWeekNumber" to "${AulaDate.weekYear(d)}-W${AulaDate.weekNumber(d)}", "isMobileApp" to "false") +
            s.children.mapNotNull { it.userId }.map { "childFilter[]" to it } +
            s.children.mapNotNull { it.institutionCode }.distinct().map { "institutionFilter[]" to it }
        return external("${AulaConfig.MIN_UDDANNELSE_API}/opgaveliste", q, mapOf("Authorization" to widgetToken(widget)))
            .asObj().objs("opgaver").map { t ->
                SchoolTask(t.str("id") ?: t.hashCode().toString(), t.str("title") ?: t.str("opgaveType") ?: L.t("Opgave"),
                    t.objs("hold").firstNotNullOfOrNull { it.firstStr("fagNavn", "navn") },
                    AulaDate.parseDotNet(t.str("afleveringsdato")), t.str("ugedag"), t.str("kuvertnavn") ?: "", t.bool("erFaerdig") ?: false)
            }
    }

    // endregion

    // region Contacts and documents

    suspend fun groups(childIds: List<Int>): List<AulaGroup> {
        val d = call("groups.getGroupsByContext", ids(childIds, "childInstitutionProfileIds[]"))
        val groups = (d as? JsonArray)?.objects()?.flatMap { it.objs("groups") } ?: d.asObj().objs("groups")
        return groups.mapNotNull { g -> g.int("id")?.let { AulaGroup(it, g.str("name") ?: L.t("Gruppe")) } }.distinctBy { it.id }
    }

    suspend fun contacts(groupId: Int, filter: String): List<Contact> {
        val all = mutableListOf<Contact>()
        for (page in 1..15) {
            val batch = call("profiles.getContactlist", listOf("groupId" to "$groupId", "filter" to filter, "field" to "name",
                "page" to "$page", "order" to "asc")).objects().mapNotNull { c ->
                val id = c.int("institutionProfileId") ?: c.int("id") ?: return@mapNotNull null
                val related = c.objs("relations").mapNotNull { it.personName() }
                Contact(id, c.personName() ?: "", related.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: c.str("metadata") ?: c.str("role"),
                    listOfNotNull(c.str("mobilePhoneNumber"), c.str("phone"), c.str("homePhoneNumber"), c.str("workPhoneNumber")).distinct(),
                    c.str("email"), c.obj("profilePicture").str("url") ?: c.str("profilePictureUrl"))
            }
            all += batch
            if (batch.size < 20) break
        }
        return all
    }

    suspend fun secureDocuments(guardianIds: List<Int>): List<SecureDocument> {
        val out = mutableListOf<SecureDocument>()
        var index = 0
        while (index < 500) {
            val page = call("documents.getSecureDocuments", body = buildJsonObject {
                put("index", index); put("limit", 50)
                putJsonArray("filterInstitutionProfileIds") { guardianIds.forEach { add(JsonPrimitive(it)) } }
            }).asObj().objs("documents")
            out += page.mapNotNull { d ->
                SecureDocument(d.int("id") ?: return@mapNotNull null, d.str("title") ?: L.t("Dokument"), d.str("category"),
                    (d.str("documentType") ?: "internal").contains("internal", true),
                    AulaDate.parse(d.str("updatedAt") ?: d.str("createdAt")), d.obj("creator").str("name"))
            }
            if (page.size < 50) break
            index += page.size
        }
        return out.distinctBy { it.id }
    }

    suspend fun documentContent(doc: SecureDocument): DocumentContent {
        val d = call(if (doc.isInternal) "documents.getInternalSecureDocument" else "documents.getExternalSecureFile",
            listOf("documentId" to "${doc.id}")).asObj()
        val files = d.objs("attachments").map(::attachment) + listOfNotNull(d.obj("attachment")?.let(::attachment))
        return DocumentContent(d.html("content").ifEmpty { d.str("description") ?: "" }, files)
    }

    suspend fun commonFiles(codes: List<String>): List<CommonFile> {
        if (codes.isEmpty()) return emptyList()
        return call("commonFiles.getCommonFiles", listOf("index" to "0", "limit" to "100") + codes.map { "institutionCodes[]" to it })
            .asObj().objs("commonFiles").map { f ->
                val url = f.obj("file").obj("file").str("url")
                val name = f.obj("file").str("name") ?: f.obj("file").obj("file").str("name") ?: f.str("title") ?: L.t("Fil")
                CommonFile(f.str("id") ?: name, f.str("title") ?: name, f.obj("institution").str("name"),
                    AulaDate.parse(f.str("created")), url?.let { Attachment(name, it) })
            }
    }

    // endregion

    /** Deletes downloaded attachments (they can be sensitive documents). */
    fun clearDownloads() { runCatching { File(AulaApp.context.cacheDir, "attachments").deleteRecursively() } }

    /** Downloads a file with the Aula session to the cache dir. */
    suspend fun download(url: String, name: String): File = withContext(Dispatchers.IO) {
        val dir = File(AulaApp.context.cacheDir, "attachments").apply { mkdirs() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimStart('.').take(120).ifEmpty { "fil" }
        val file = File(dir, safe)
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw AulaException.Http(resp.code)
            file.outputStream().use { out -> resp.body?.byteStream()?.copyTo(out) }
        }
        file
    }
}
