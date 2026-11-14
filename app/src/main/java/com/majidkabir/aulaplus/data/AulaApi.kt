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
import kotlinx.coroutines.sync.withPermit
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

/** Gets the list so far (and the expected total, if Aula said) after every page. */
typealias MediaProgress = suspend (List<Media>, Int?) -> Unit

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
        val shown = ctx.obj("pageConfiguration").objs("widgetConfigurations")
            .filter { (it.str("aggregatedDisplayMode") ?: "Shown") == "Shown" }
        val widgets = shown.mapNotNull { it.obj("widget").str("widgetId") }.toSet()
        val widgetNames = shown.mapNotNull { w ->
            val o = w.obj("widget")
            o.str("widgetId")?.let { id -> o.str("name")?.takeIf { it.isNotBlank() }?.let { id to it } }
        }.toMap()
        val widgetLinks = shown.mapNotNull { w ->
            val o = w.obj("widget")
            val id = o.str("widgetId") ?: return@mapNotNull null
            val name = o.str("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = o.str("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            WidgetLink(id, name, url)
        }.distinctBy { it.id }
        val guardianIds = profile.objs("institutionProfiles").mapNotNull { it.int("id") }
        val s = Session(
            displayName = profile.str("displayName") ?: "",
            children = children,
            institutionProfileIds = (guardianIds + children.map { it.id }).distinct(),
            guardianUserId = ctx.str("userId"),
            widgetIds = widgets,
            guardianProfileIds = guardianIds,
            widgetNames = widgetNames,
            widgetLinks = widgetLinks,
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
        val type = o.obj("media").str("mediaType").orEmpty().lowercase()
        val media = o.obj("media")?.takeIf { type.isEmpty() || "image" in type || "video" in type || "media" in type }
            ?.let(::mediaOf)?.takeIf { it.thumbUrl != null || it.fullUrl != null }
        return Attachment(name, url, linkOnly, media)
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
                        // No plan for the day (weekend, holiday): Aula fills in 05:00–23:59. Show no times then.
                        var entry = AulaDate.shortTime(d.str("entryTime"))
                        var exit = AulaDate.shortTime(d.str("exitTime"))
                        val weekend = AulaDate.today().dayOfWeek.value >= 6
                        if (Presence.isPlaceholder(entry, exit) || weekend) { entry = null; exit = null }
                        Presence(cid, d.int("status"), AulaDate.shortTime(d.str("checkInTime")),
                            AulaDate.shortTime(d.str("checkOutTime")), entry, exit, d.str("exitWith"), d.str("comment"), loc)
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

    /** [weekly]: the same times every week from that day. */
    suspend fun savePlan(p: DayPlan, weekly: Boolean = false) {
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
            put("repeatPattern", if (weekly) "weekly" else "never")
            put("expiresAt", "$endYear-06-30T00:00:00+00:00")
            if (p.templateId != null && (!p.repeats || weekly)) put("id", p.templateId)
        })
    }

    /** Stops a weekly plan from the given day on (earlier weeks keep it). */
    suspend fun deleteRepeatingPlan(templateId: Int, from: String) {
        call("presence.deleteRepeatingPresenceTemplate", body = buildJsonObject { put("presentTemplateId", templateId); put("deleteFromDay", from) })
    }

    suspend fun pickupPeople(childId: Int): List<PickupPerson> {
        val out = mutableListOf<PickupPerson>()
        call("presence.getPickupResponsibles", listOf("uniStudentIds[]" to "$childId")).objects()
            .filter { (it.int("uniStudentId") ?: childId) == childId }.forEach { item ->
                item.objs("relatedPersons").forEach { p ->
                    val n = p.str("name")?.takeIf { it.isNotBlank() } ?: return@forEach
                    out += PickupPerson("r-${p.int("institutionProfileId") ?: n.hashCode()}", n, p.str("relation")?.takeIf { it.isNotBlank() }, null)
                }
                item.objs("pickupSuggestions").forEach { s ->
                    val n = s.str("pickUpName")?.takeIf { it.isNotBlank() } ?: return@forEach
                    out += PickupPerson("s-${s.int("id") ?: n.hashCode()}", n, null, s.int("id"))
                }
            }
        return out.distinctBy { it.id }
    }

    suspend fun addPickupPerson(name: String, childIds: List<Int>) {
        call("presence.savePickupNames", body = buildJsonObject {
            putJsonArray("uniStudentIds") { childIds.forEach { add(JsonPrimitive(it)) } }; put("pickupName", name)
        })
    }

    suspend fun removePickupPerson(suggestionId: Int) {
        call("presence.deletePickupResponsible", body = buildJsonObject { put("presencePickupSuggestionId", suggestionId) })
    }

    /** "2026-11-14", "14-11-2026" or a full timestamp. */
    private fun parseDay(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        val head = s.take(10)
        runCatching { return LocalDate.parse(head) }
        val p = head.split("-")
        if (p.size == 3 && p[0].length == 2) runCatching { return LocalDate.of(p[2].toInt(), p[1].toInt(), p[0].toInt()) }
        return null
    }

    suspend fun openingDays(codes: List<String>, from: LocalDate, to: LocalDate): List<OpeningDay> =
        call("presence.getOpeningHoursByInstitutionCodes", listOf("startDate" to "$from", "endDate" to "$to") + codes.map { "institutionCodes[]" to it })
            .asObj().objs("openingHoursOverviewDto").flatMap { inst ->
                val code = inst.str("institutionCode") ?: return@flatMap emptyList()
                inst.objs("openingHoursDto").mapNotNull { d ->
                    val date = parseDay(d.str("date")) ?: return@mapNotNull null
                    val type = d.str("type").orEmpty().lowercase()
                    OpeningDay(code, date, AulaDate.shortTime(d.str("openTime")), AulaDate.shortTime(d.str("closeTime")),
                        type == "closed_day", type == "specific_opening_hours", d.str("name")?.takeIf { it.isNotBlank() })
                }
            }.sortedBy { it.date }

    suspend fun closedDays(codes: List<String>): List<ClosedDays> =
        call("presence.getClosedDays", codes.map { "institutionCodes[]" to it }).objects().flatMap { inst ->
            val code = inst.str("institutionCode") ?: return@flatMap emptyList()
            inst.obj("closedDaysOverview").objs("closedDays").mapNotNull { c ->
                val s = parseDay(c.str("startDate")) ?: return@mapNotNull null
                ClosedDays(code, s, parseDay(c.str("endDate")) ?: s, c.str("name")?.takeIf { it.isNotBlank() } ?: L.t("Lukket"))
            }
        }.sortedBy { it.start }

    // My profile

    suspend fun myProfiles(ids: List<Int>): List<MyProfile> =
        call("profiles.getProfileMasterData", ids.map { "instProfileIds[]" to "$it" }).asObj().objs("institutionProfiles").mapNotNull { p ->
            val id = p.int("id") ?: p.int("institutionProfileId") ?: return@mapNotNull null
            MyProfile(id, p.int("profileId"), p.personName() ?: p.str("name") ?: "",
                p.str("institutionName") ?: p.obj("institution").str("institutionName"),
                p.str("institutionCode") ?: p.obj("institution").str("institutionCode"),
                p.str("email").orEmpty(), p.str("mobilePhoneNumber").orEmpty(), p.str("homePhoneNumber").orEmpty(), p.str("workPhoneNumber").orEmpty(),
                p.obj("profilePicture").str("url") ?: p.str("profilePictureUrl"), p.bool("currentUserCanEditProfilePicture") ?: true)
        }

    suspend fun saveContactInfo(profiles: List<MyProfile>) {
        call("profiles.updateProfileMasterData", body = buildJsonObject {
            putJsonArray("institutionProfilesMasterData") {
                profiles.forEach { p ->
                    add(buildJsonObject {
                        if (p.profileId == null) put("profileId", JsonNull) else put("profileId", p.profileId)
                        put("institutionProfileId", p.id); put("homePhoneNumber", p.homePhone); put("mobilePhoneNumber", p.mobilePhone)
                        put("workPhoneNumber", p.workPhone); put("email", p.email)
                    })
                }
            }
            put("primaryInstitutionProfileId", JsonNull); put("isChangedPrimaryInstProfile", false)
        })
    }

    /** Uploads a new profile picture: Aula hands out an upload form for its storage, then links the photo. */
    suspend fun uploadProfilePicture(jpeg: ByteArray, profile: MyProfile, allProfiles: Boolean) {
        val uploadId = java.util.UUID.randomUUID().toString()
        val created = call("files.createAttachments", body = buildJsonObject {
            putJsonArray("media") {
                add(buildJsonObject {
                    putJsonObject("file") {}
                    put("name", "profilbillede.jpg"); put("mediaType", "image"); put("rotationDegree", 0)
                    put("title", JsonNull); put("description", JsonNull)
                    putJsonArray("tags") { add(JsonPrimitive(profile.id)) }
                    put("albumId", JsonNull); put("sensitivityLevel", JsonNull); put("allowsComments", true); put("uploadId", uploadId)
                })
            }
            putJsonArray("files") {}; putJsonArray("links") {}; putJsonArray("existingMedia") {}
            put("attachedSecureDocumentIds", JsonNull); put("ownerInstitutionProfileId", profile.id)
            if (profile.institutionCode == null) put("institutionCode", JsonNull) else put("institutionCode", profile.institutionCode)
        }).asObj()
        val media = created.objs("media").let { list -> list.firstOrNull { it.str("uploadId") == uploadId } ?: list.firstOrNull() }
            ?: error("files.createAttachments")
        val mediaId = media.int("id") ?: error("files.createAttachments")
        val form = media.obj("fileUploadInformation") ?: error("files.createAttachments")
        val action = form.str("action") ?: error("files.createAttachments")
        val builder = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
        form.keys.filter { it != "action" }.forEach { k -> builder.addFormDataPart(k.replace('_', '-'), form.str(k) ?: (form[k]?.toString() ?: "")) }
        builder.addFormDataPart("file", "profilbillede.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
        val res = withContext(kotlinx.coroutines.Dispatchers.IO) {
            okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().url(action).post(builder.build()).build()).execute().use { it.code }
        }
        if (res !in 200..299) error("HTTP $res")
        call("profiles.updateProfilePicture", body = buildJsonObject {
            put("institutionProfileId", profile.id); put("mediaId", mediaId); put("applyToAllInstitutionProfiles", allProfiles)
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
            isMuted = t.bool("muted") ?: false,
            subscriptionId = t.int("subscriptionId"),
            folderId = t.int("folderId"),
        )
    }

    /** [folderId] null = the inbox. */
    suspend fun threads(page: Int = 0, folderId: Int? = null): Pair<List<ThreadSummary>, Boolean> {
        val d = call("messaging.getThreads", listOf("sortOn" to "date", "orderDirection" to "desc", "page" to "$page") +
            listOfNotNull(folderId?.let { "folderId" to "$it" })).asObj()
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

    // Folders, mute, leave, search and auto-reply: the same calls aula.dk makes.

    suspend fun folders(): List<MailFolder> =
        call("messaging.getFolders", listOf("includeDeletedFolders" to "false")).objects().mapNotNull { f ->
            MailFolder(f.int("id") ?: return@mapNotNull null, f.str("name") ?: "", f.str("type"))
        }

    suspend fun createFolder(name: String) {
        call("messaging.createFolder", body = buildJsonObject { put("folderName", name); put("commonInboxId", JsonNull) })
    }

    suspend fun renameFolder(id: Int, name: String) {
        call("messaging.updateFolder", body = buildJsonObject { put("folderName", name); put("folderId", id) })
    }

    suspend fun deleteFolder(id: Int) {
        call("messaging.deleteFolder", body = buildJsonObject { put("folderId", id) })
    }

    /** [folderId] null moves the thread back to the inbox. */
    suspend fun moveThread(threadId: String, folderId: Int?) {
        call("messaging.moveThreadsToFolder", body = buildJsonObject {
            if (folderId == null) put("folderId", JsonNull) else put("folderId", folderId)
            putJsonArray("threadIds") { add(idValue(threadId)) }
            putJsonArray("subscriptionIds") {}
            put("commonInboxId", JsonNull)
        })
    }

    suspend fun setMuted(subscriptionId: Int, muted: Boolean) {
        call("messaging.setThreadsMuted", body = buildJsonObject {
            put("muted", muted); putJsonArray("subscriptionIds") { add(JsonPrimitive(subscriptionId)) }
            put("commonInboxId", JsonNull); put("otpInboxId", JsonNull)
        })
    }

    suspend fun leaveThread(threadId: String) {
        call("messaging.leaveThread", body = buildJsonObject { put("threadId", idValue(threadId)); put("otpInboxId", JsonNull) })
    }

    /** Full-text search in all messages on Aula. */
    suspend fun searchMessages(text: String, childIds: List<Int>, institutionCodes: List<String>): List<ThreadSummary> {
        val d = call("search.findMessage", body = buildJsonObject {
            put("text", text); put("typeahead", false); put("exactTerm", true)
            putJsonArray("activeChildrenInstitutionProfileIds") { childIds.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("institutionCodes") { institutionCodes.forEach { add(JsonPrimitive(it)) } }
            put("limit", 40); put("offset", 0); put("commonInboxID", JsonNull); put("folderId", JsonNull)
            put("filterBy", "all"); put("sortBy", "date"); put("sortDirection", "desc")
        }).asObj()
        return d.objs("results").mapNotNull { r ->
            val id = r.obj("thread").str("id") ?: r.str("threadId") ?: return@mapNotNull null
            val m = r.obj("searchMessage")
            ThreadSummary(
                id = id,
                subject = r.obj("thread").str("subject")?.takeIf { it.isNotBlank() } ?: L.t("(Intet emne)"),
                preview = Html.plain(m.html("text")),
                sender = m.str("senderDisplayName") ?: r.obj("creator").str("fullName") ?: "",
                dateMs = AulaDate.parse(m.str("sendDateTime")),
                isRead = r.bool("read") ?: true,
                isSensitive = (r.int("sensitivityLevel") ?: 1) > 1,
                regarding = r.objs("regardingChildren").mapNotNull { it.personName() },
                isMarked = r.bool("marked") ?: false,
                isMuted = r.bool("muted") ?: false,
                subscriptionId = r.int("subscriptionId"),
                folderId = r.int("folderId"),
            )
        }.distinctBy { it.id }
    }

    suspend fun autoReply(): AutoReply? {
        val d = call("messaging.getAutoReply").asObj() ?: return null
        val text = d.obj("replyText")?.str("html") ?: d.str("replyText") ?: return null
        return AutoReply(d.int("id"), Html.plain(text), AulaDate.parse(d.str("startDateTime")), AulaDate.parse(d.str("endDateTime")))
    }

    suspend fun setAutoReply(text: String, startMs: Long, endMs: Long?) {
        call("messaging.setAutoReply", body = buildJsonObject {
            put("replyText", text); put("startDateTime", aulaDateTime(startMs))
            if (endMs == null) put("endDateTime", JsonNull) else put("endDateTime", aulaDateTime(endMs))
        })
    }

    suspend fun deleteAutoReply(id: Int) {
        call("messaging.deleteAutoReply", body = buildJsonObject { put("autoReplyId", id) })
    }

    /** "2026-11-14T08:00:00.0000+01:00", the format aula.dk sends. */
    private fun aulaDateTime(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(AulaDate.copenhagen)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'.0000'xxx"))

    // Aula's own notification settings and calendar feed

    suspend fun notificationSettings(): AulaNotificationSettings {
        val d = call("Notifications.getNotificationSettingsForActiveProfile").asObj()
        val days = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday").map { d.bool(it) ?: false }
        return AulaNotificationSettings(
            instant = d.bool("instant") ?: true, scheduledTime = d.str("scheduledTime"), days = days,
            push = !(d.bool("appDisabled") ?: false), pushAvailable = d.bool("appAvailable") ?: true,
            email = !(d.bool("emailDisabled") ?: true), emailAvailable = d.bool("emailAvailable") ?: false,
            fromEmployees = d.bool("notifyMessagesFromEmployees") ?: true,
            fromGuardians = d.bool("notifyMessagesFromGuardians") ?: true,
            fromChildren = d.bool("notifyMessagesFromChildren") ?: true,
            calendar = d.bool("notifyCalendar") ?: true, gallery = d.bool("notifyGallery") ?: true, posts = d.bool("notifyPosts") ?: true,
            substitute = d.bool("notifyAssignedAsSubstituteTeacher") ?: false, lessonNote = d.bool("notifyLessonNoteChanged") ?: false,
            comeGo = d.objs("comeGoNotificationSettings").mapNotNull { c ->
                val type = c.str("comeGoType") ?: return@mapNotNull null
                NamedSwitch(type, comeGoTitle(type), c.bool("activated") ?: false)
            },
            widgets = d.objs("widgetNotificationSettingDtos").mapNotNull { w ->
                NamedSwitch((w.int("widgetId") ?: return@mapNotNull null).toString(), w.str("title") ?: "Widget", w.bool("isActive") ?: false)
            },
        )
    }

    fun comeGoTitle(type: String) = when (type) {
        "VacationRegistrationRequest" -> L.t("Ferieanmodninger")
        "PickupActivity" -> L.t("Afhentning")
        else -> type
    }

    suspend fun saveNotificationSettings(s: AulaNotificationSettings) {
        val names = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        call("Notifications.setNotificationSettingsForActiveProfile", body = buildJsonObject {
            if (s.instant) put("scheduledTime", JsonNull) else put("scheduledTime", s.scheduledTime ?: "0800")
            put("notifyMessages", s.fromEmployees || s.fromGuardians || s.fromChildren)
            put("notifyMessagesFromEmployees", s.fromEmployees); put("notifyMessagesFromGuardians", s.fromGuardians)
            put("notifyMessagesFromChildren", s.fromChildren); put("notifyCalendar", s.calendar)
            put("notifyAssignedAsSubstituteTeacher", s.substitute); put("notifyLessonNoteChanged", s.lessonNote)
            put("notifyGallery", s.gallery); put("notifyPosts", s.posts)
            putJsonArray("widgetSettings") {
                s.widgets.forEach { w -> add(buildJsonObject { put("widgetId", w.id.toIntOrNull() ?: 0); put("title", w.title); put("isActive", w.on) }) }
            }
            putJsonArray("comeGoNotificationSettings") {
                s.comeGo.forEach { c -> add(buildJsonObject { put("comeGoType", c.id); put("activated", c.on) }) }
            }
            put("appDisabled", !s.push); put("emailDisabled", !s.email); put("instant", s.instant)
            names.forEachIndexed { i, n -> put(n, !s.instant && s.days[i]) }
        })
    }

    suspend fun calendarFeeds(): List<CalendarFeed> = call("CalendarFeed.getFeedConfigurations").objects().mapNotNull { f ->
        val c = f.obj("calendarFeedConfiguration") ?: f
        CalendarFeed(c.int("id") ?: return@mapNotNull null, c.str("oneWeekFeed"), c.str("oneYearFeed"))
    }

    /** Accepts Aula's terms for calendar feeds and creates one with every event type a guardian can get. */
    suspend fun createCalendarFeed(institutionProfileId: Int) {
        call("CalendarFeed.setPolicyAnswer", body = buildJsonObject { put("policyAccepted", true) })
        val types = runCatching {
            call("calendarFeed.getEventTypesRelevantForPortalRole", listOf("PortalRole" to "guardian")).asObj().arr("eventTypes")
                .mapNotNull { (it as? JsonPrimitive)?.content }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: listOf("event", "holiday", "excursion", "other", "school_home_meeting", "parental_meeting")
        call("CalendarFeed.createFeedConfiguration", body = buildJsonObject {
            put("institutionProfileId", institutionProfileId)
            putJsonArray("filters") { types.forEach { add(JsonPrimitive(it)) } }
        })
    }

    suspend fun deleteCalendarFeed(id: Int) {
        call("CalendarFeed.removeFeedConfiguration", body = buildJsonObject { put("calendarFeedConfigurationId", id) })
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
            (p.bool("allowComments") ?: false) && (p.bool("canCurrentUserComment") ?: true), p.bool("isBookmarked") ?: false)
    }

    suspend fun posts(profileIds: List<Int>, filter: PostFilter = PostFilter.ALL): List<Post> =
        call("posts.getAllPosts", listOf("parent" to "profile", "index" to "0", "limit" to "20") + filter.query +
            ids(profileIds, "institutionProfileIds[]"))
            .asObj().objs("posts").mapNotNull(::post)

    /** Saves ("Gem") or unsaves a post, like aula.dk. */
    suspend fun setBookmarked(postId: Int, on: Boolean) {
        call(if (on) "posts.bookmark" else "posts.unbookmark", body = buildJsonObject { put("postId", postId) })
    }

    /** Birthdays of children at the given institutions in the next [days] days (this year's date, ISO). */
    suspend fun birthdays(institutionCodes: List<String>, days: Long = 14): List<Birthday> {
        if (institutionCodes.isEmpty()) return emptyList()
        val start = java.time.LocalDate.now()
        val end = start.plusDays(days)
        return call("calendar.getBirthdayEventsForInstitutions",
            listOf("start" to "${start}T00:00:00.0000Z", "end" to "${end}T23:59:59.0000Z") + institutionCodes.map { "instCodes[]" to it })
            .objects().mapNotNull { b ->
                val id = b.int("institutionProfileId") ?: return@mapNotNull null
                // Aula returns the birth date (midnight UTC); show the coming birthday.
                val born = b.str("birthday")?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                var next = runCatching { born.withYear(start.year) }.getOrNull() ?: return@mapNotNull null
                if (next.isBefore(start)) next = next.plusYears(1)
                if (next.isAfter(end)) return@mapNotNull null
                Birthday(id, b.str("name") ?: "", next.toString(), b.str("mainGroupName")?.takeIf { it.isNotBlank() }, b.str("institutionCode"))
            }.distinctBy { it.id }.sortedBy { it.date }
    }

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

    /** All photos in an album, newest first. A small first page comes back fast; [progress] gets each page. */
    suspend fun media(albumId: Int, profileIds: List<Int>, progress: MediaProgress? = null): List<Media> =
        pages(progress) { index, limit ->
            listOf("albumId" to "$albumId", "index" to "$index", "limit" to "$limit", "sortOn" to "uploadedAt",
                "orderDirection" to "desc", "filterBy" to "all") + ids(profileIds, "filterInstProfileIds[]")
        }

    /** `gallery.getMedia` page by page: 48 first, then 150 at a time. `totalSize` is unreliable: stop on a short or repeated page. */
    private suspend fun pages(progress: MediaProgress?, query: (index: Int, limit: Int) -> List<Pair<String, String>>): List<Media> {
        val all = mutableListOf<Media>()
        val seen = mutableSetOf<Int>()
        var index = 0
        for (page in 0 until 60) {
            val limit = if (page == 0) 48 else 150
            val d = call("gallery.getMedia", query(index, limit))
            val raw = (d as? JsonArray)?.objects() ?: d.asObj().objs("results")
            val fresh = mediaList(d).filter { seen.add(it.id) }
            all += fresh
            if (raw.size < limit || fresh.isEmpty()) break
            index += raw.size
            progress?.invoke(all.toList(), d.asObj().int("mediaCount") ?: d.asObj().int("totalSize"))
        }
        return all
    }

    /**
     * Every photo [childId] is tagged in, across all albums. Asks Aula for the child's own album
     * (`userSpecificAlbum`); if that fails or comes back empty, looks through the albums instead.
     */
    suspend fun taggedMedia(childId: Int, profileIds: List<Int>, progress: MediaProgress? = null): List<Media> {
        val first = runCatching { userSpecificMedia(childId, progress) }
        first.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        val scanned = scanAlbums(childId, profileIds, progress)
        if (scanned.isEmpty()) first.exceptionOrNull()?.let { throw it }
        return scanned
    }

    private suspend fun userSpecificMedia(childId: Int, progress: MediaProgress?): List<Media> {
        val keep = { list: List<Media> -> list.filter { it.isTagged(childId) || it.tags.isEmpty() } }
        val wrapped: MediaProgress? = if (progress == null) null else { list, total -> progress(keep(list), total) }
        return keep(pages(wrapped) { index, limit ->
            listOf("userSpecificAlbum" to "true", "filterInstProfileIds[]" to "$childId",
                "index" to "$index", "limit" to "$limit", "filterBy" to "all")
        })
    }

    /** Photos tagged with [childId] in the newest albums, newest album first. */
    private suspend fun scanAlbums(childId: Int, profileIds: List<Int>, progress: MediaProgress?): List<Media> = coroutineScope {
        val albums = albums(profileIds)
        val found = java.util.concurrent.ConcurrentHashMap<Int, List<Media>>()
        val lock = Mutex()
        fun merged() = albums.indices.flatMap { found[it].orEmpty() }.distinctBy { it.id }
        // At most 3 albums at a time, so an album the user opens isn't stuck behind the scan.
        val gate = kotlinx.coroutines.sync.Semaphore(3)
        albums.mapIndexed { i, a ->
            async {
                gate.withPermit {
                    found[i] = runCatching { media(a.id, profileIds) }.getOrDefault(emptyList()).filter { it.isTagged(childId) }
                }
                lock.withLock { progress?.invoke(merged(), null) }
            }
        }.awaitAll()
        merged()
    }

    private fun mediaList(d: JsonElement?): List<Media> {
        val list = (d as? JsonArray)?.objects() ?: d.asObj().objs("results")
        return list.mapNotNull(::mediaOf).distinctBy { it.id } // Aula lists an item once per child profile
    }

    private fun mediaOf(m: JsonObject): Media? {
        val tags = m.arr("tags").mapNotNull { t ->
            val o = t.asObj()
            val id = o.int("id") ?: o.int("institutionProfileId") ?: return@mapNotNull null
            val name = o.firstStr("name", "fullName") ?: o.obj("profile").firstStr("name", "fullName") ?: return@mapNotNull null
            MediaTag(id, name)
        }.distinctBy { it.id }
        return Media(m.int("id") ?: return null,
            m.firstStr("smallThumbnailUrl", "thumbnailUrl", "mediumThumbnailUrl", "largeThumbnailUrl") ?: m.obj("file").str("url"),
            m.obj("file").str("url") ?: m.str("largeThumbnailUrl"),
            (m.str("mediaType") ?: "").contains("video", true),
            m.str("title")?.takeIf { it.isNotBlank() }, tags, m.str("description"), m.bool("currentUserCanEditTags") ?: false,
            m.firstStr("largeThumbnailUrl", "mediumThumbnailUrl") ?: m.obj("file").str("url"),
            AulaDate.parse(m.obj("file").str("created")))
    }

    /** [childIds]/[institutionCodes] matter: without them Aula leaves out tag and child notifications. */
    suspend fun notifications(childIds: List<Int> = emptyList(), institutionCodes: List<String> = emptyList()): List<AulaNotification> =
        call("notifications.getNotificationsForActiveProfile", listOf("offset" to "0", "limit" to "50") +
            childIds.map { "activeChildrenIds[]" to "$it" } + institutionCodes.map { "activeInstitutionCodes[]" to it }).objects().map { n ->
            val type = n.str("notificationEventType")
            val title = n.firstStr("title", "heading", "postTitle", "threadSubject")
                ?: n.str("messageText")?.let(Html::plain) ?: galleryTitle(type) ?: type ?: L.t("Notifikation")
            val rawId = n.str("notificationId")?.takeIf { it.isNotBlank() }
            AulaNotification(n.firstStr("id", "notificationId") ?: title.hashCode().toString(), title,
                n.firstStr("module", "notificationArea"), n.str("relatedChildName"),
                AulaDate.parse(n.str("createdAt") ?: n.str("triggered")), n.int("postId"), n.str("threadId"), n.int("albumId"),
                eventType = type, notificationId = rawId, institutionProfileId = n.int("institutionProfileId"),
                mediaId = n.int("mediaId") ?: if (type == "TaggedInMedia") AulaNotification.mediaIdFromNotificationId(rawId) else null,
                relatedChildId = n.int("relatedChildInstitutionProfileId"))
        }

    private fun galleryTitle(type: String?): String? = when (type) {
        "NewMedia", "MediaAddedToAlbum" -> L.t("Nye billeder i galleriet")
        "TaggedInMedia" -> L.t("Dit barn er tagget på et billede")
        "AlbumShared" -> L.t("Nyt album delt")
        else -> null
    }

    /**
     * Replaces the people tagged in a photo, exactly like Aula's web app:
     * `files.updateAttachments` with the photo's full tag list (institution profile ids).
     */
    suspend fun setTag(profileId: Int, media: Media, tagged: Boolean, allTagIds: List<Int>) {
        call("files.updateAttachments", body = buildJsonObject {
            putJsonArray("media") {
                add(buildJsonObject {
                    put("id", media.id)
                    putJsonArray("tags") { allTagIds.forEach { add(JsonPrimitive(it)) } }
                    put("title", media.title)
                    put("description", media.description)
                })
            }
        })
    }

    /** Marks notifications as seen (Aula deletes them, like the web app does). */
    suspend fun deleteNotifications(list: List<AulaNotification>, fallbackOwners: List<Int> = emptyList()) {
        // A notification without an owner profile is sent once per guardian profile.
        val items = list.flatMap { n ->
            val id = n.notificationId ?: n.id
            (n.institutionProfileId?.let { listOf(it) } ?: fallbackOwners).map { id to it }
        }
        if (items.isEmpty()) return
        call("Notifications.deleteNotifications", body = buildJsonObject {
            put("notifications", buildJsonArray {
                items.forEach { (id, owner) -> add(buildJsonObject { put("notificationId", id); put("institutionProfileId", owner) }) }
            })
        })
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
                val rels = c.objs("relations").mapNotNull { r ->
                    val n = r.personName() ?: return@mapNotNull null
                    ContactRelation(n, listOfNotNull(r.str("relation"), r.obj("mainGroup").str("name") ?: r.str("metadata"))
                        .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null })
                }
                Contact(id, c.personName() ?: "", related.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: c.str("metadata") ?: c.str("role"),
                    listOfNotNull(c.str("mobilePhoneNumber"), c.str("phone"), c.str("homePhoneNumber"), c.str("workPhoneNumber")).distinct(),
                    c.str("email"), c.obj("profilePicture").str("url") ?: c.str("profilePictureUrl"),
                    role = c.str("role"), mobilePhone = c.str("mobilePhoneNumber") ?: c.str("phone"), homePhone = c.str("homePhoneNumber"),
                    workPhone = c.str("workPhoneNumber"), institutionName = c.str("institutionName"),
                    group = c.obj("mainGroup").str("name") ?: c.str("metadata"), relations = rels)
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
