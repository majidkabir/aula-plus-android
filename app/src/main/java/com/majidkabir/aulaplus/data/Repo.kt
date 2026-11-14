package com.majidkabir.aulaplus.data

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.widget.WidgetWriter
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

/** All cached data sources, shared between screens. */
object Repo {
    val session = Loadable("session", Session.serializer()) { AulaApi.bootstrap() }
    val presence = Loadable("presence", ListSerializer(Presence.serializer())) {
        AulaApi.presence(AulaApi.bootstrap().children.map { it.id })
    }
    val threads = Loadable("threads", ListSerializer(ThreadSummary.serializer())) { AulaApi.threads().first }
    val posts = Loadable("posts", ListSerializer(Post.serializer())) { AulaApi.posts(AulaApi.bootstrap().institutionProfileIds) }
    val notifications = Loadable("notifications", ListSerializer(AulaNotification.serializer())) {
        val s = AulaApi.bootstrap()
        AulaApi.notifications(s.children.map { it.id }, s.children.mapNotNull { it.institutionCode }.distinct().sorted())
    }
    val importantDates = Loadable("important-dates", ListSerializer(ImportantDate.serializer())) {
        AulaApi.bootstrap(); AulaApi.importantDates()
    }
    val birthdays = Loadable("birthdays", ListSerializer(Birthday.serializer())) {
        // A whole year, so the calendar can show them in any week.
        AulaApi.birthdays(AulaApi.bootstrap().children.mapNotNull { it.institutionCode }.distinct().sorted(), 366)
    }
    /** Birthdays as all-day events in the week of [day] (the list runs one year ahead; other years reuse the day). */
    fun birthdayEvents(day: LocalDate): List<Event> {
        val start = AulaDate.weekStart(day)
        val days = (0L until 7L).map { start.plusDays(it) }
        return birthdays.value.orEmpty().mapNotNull { b ->
            val bd = runCatching { LocalDate.parse(b.date) }.getOrNull() ?: return@mapNotNull null
            val d = days.firstOrNull { it.monthValue == bd.monthValue && it.dayOfMonth == bd.dayOfMonth } ?: return@mapNotNull null
            val ms = d.atStartOfDay(AulaDate.copenhagen).toInstant().toEpochMilli()
            Event("bday-${b.id}-${d.year}", L.t("🎂 %s har fødselsdag", b.name), ms, ms + 86_400_000L, allDay = true,
                location = b.group, type = "birthday")
        }
    }

    private val filteredPosts = mutableMapOf<PostFilter, Loadable<List<Post>>>()
    fun posts(filter: PostFilter): Loadable<List<Post>> = if (filter == PostFilter.ALL) posts else
        filteredPosts.getOrPut(filter) {
            Loadable("posts-${filter.name.lowercase()}", ListSerializer(Post.serializer())) {
                AulaApi.posts(AulaApi.bootstrap().institutionProfileIds, filter)
            }
        }

    /** Saves or unsaves a post on Aula and in the cached lists. */
    suspend fun setBookmarked(post: Post, on: Boolean) {
        if (!Demo.isOn) AulaApi.setBookmarked(post.id, on)
        (listOf(posts) + filteredPosts.values).forEach { l -> l.mutate { list -> list.map { if (it.id == post.id) it.copy(isBookmarked = on) else it } } }
    }
    val albums = Loadable("albums", ListSerializer(Album.serializer())) { AulaApi.albums(AulaApi.bootstrap().institutionProfileIds) }
    val vacationRequests = Loadable("vacation-requests", ListSerializer(VacationRequest.serializer())) {
        AulaApi.vacationRequests(AulaApi.bootstrap().children.map { it.id })
    }

    init {
        presence.onChange = { WidgetWriter.update() }
        threads.onChange = { WidgetWriter.update() }
    }

    private val weeks = mutableMapOf<String, Loadable<List<Event>>>()
    private val threadPages = mutableMapOf<String, Loadable<ThreadPage>>()
    private val plans = mutableMapOf<String, Loadable<List<WeekPlan>>>()
    private val tasks = mutableMapOf<String, Loadable<List<SchoolTask>>>()
    private val albumMedia = mutableMapOf<Int, Loadable<List<Media>>>()

    /** The newest copy of a photo from any album loaded this session (dates and fresh links). */
    fun freshest(m: Media): Media = albumMedia.values.firstNotNullOfOrNull { l -> l.value?.firstOrNull { it.id == m.id } } ?: m
    private val dayPlans = mutableMapOf<String, Loadable<List<DayPlan>>>()

    val unreadCount: Int get() = threads.value?.count { !it.isRead } ?: 0

    fun events(date: LocalDate): Loadable<List<Event>> {
        val start = AulaDate.weekStart(date)
        val key = "events-${AulaDate.weekKey(start)}"
        return weeks.getOrPut(key) {
            Loadable(key, ListSerializer(Event.serializer())) {
                AulaApi.events(AulaApi.bootstrap().children.map { it.id }, start, start.plusDays(6))
            }.also { if (start == AulaDate.weekStart(AulaDate.today())) it.onChange = { WidgetWriter.update() } }
        }
    }

    fun thread(id: String) = threadPages.getOrPut(id) {
        Loadable("thread-$id", ThreadPage.serializer()) { AulaApi.messages(id) }
    }

    fun weekPlans(date: LocalDate): Loadable<List<WeekPlan>> {
        val start = AulaDate.weekStart(date)
        val key = "weekplan-${AulaDate.weekKey(start)}"
        return plans.getOrPut(key) {
            Loadable(key, ListSerializer(WeekPlan.serializer())) { AulaApi.weekPlans(AulaApi.bootstrap(), start) }
        }
    }

    fun schoolTasks(date: LocalDate): Loadable<List<SchoolTask>> {
        val start = AulaDate.weekStart(date)
        val key = "tasks-${AulaDate.weekKey(start)}"
        return tasks.getOrPut(key) {
            Loadable(key, ListSerializer(SchoolTask.serializer())) { AulaApi.schoolTasks(AulaApi.bootstrap(), start) }
        }
    }

    fun media(albumId: Int) = albumMedia.getOrPut(albumId) {
        Loadable.streaming("album-$albumId", ListSerializer(Media.serializer())) { emit ->
            AulaApi.media(albumId, AulaApi.bootstrap().institutionProfileIds, emit)
        }
    }

    /** "Billeder af <barn>": every photo the child is tagged in. Keyed by -childId so it never meets a real album. */
    fun taggedMedia(childId: Int) = albumMedia.getOrPut(-childId) {
        Loadable.streaming("tagged-$childId", ListSerializer(Media.serializer())) { emit ->
            AulaApi.taggedMedia(childId, AulaApi.bootstrap().institutionProfileIds, emit)
        }
    }

    /** Tags or untags [child] in a photo, on Aula and in every cached album. Returns the updated photo. */
    suspend fun setTagged(child: Child, tagged: Boolean, media: Media): Media {
        val tags = media.tags.filterNot { it.id == child.id } + if (tagged) listOf(MediaTag(child.id, child.name)) else emptyList()
        if (!Demo.isOn) AulaApi.setTag(child.id, media, tagged, tags.map { it.id })
        val updated = media.copy(tags = tags)
        albumMedia.forEach { (key, list) ->
            if (key == -child.id) {
                // The child's own album: add or drop the photo.
                list.mutate { items -> items.filterNot { it.id == media.id }.let { if (tagged) listOf(updated) + it else it } }
            } else {
                list.mutate { items -> items.map { if (it.id == media.id) updated else it } }
            }
        }
        return updated
    }

    // region Gallery "new" markers (from Aula's gallery notifications)

    /**
     * Gallery notifications already seen here. Kept on the device so the "new" counts stay gone
     * even when Aula keeps the notification (or deleting it there fails).
     */
    private var seenNotificationIds by androidx.compose.runtime.mutableStateOf(
        if (com.majidkabir.aulaplus.AulaApp.isReady)
            com.majidkabir.aulaplus.AulaApp.prefs.getString("seenNotificationIds", "")!!.split('\n').filter { it.isNotEmpty() }
        else emptyList()
    )

    private val galleryNotifications get() = seenNotificationIds.toSet().let { seen ->
        notifications.value.orEmpty().filter { it.isGallery && it.id !in seen }
    }

    /** Notifications that make photos "new" in album [albumId], or in child [childId]'s tagged album. */
    fun newNotifications(albumId: Int? = null, childId: Int? = null): List<AulaNotification> = galleryNotifications.filter {
        if (childId != null) it.isTaggedInMedia && it.relatedChildId == childId else !it.isTaggedInMedia && it.albumId == albumId
    }

    /** One per photo, however many notifications it has. */
    private fun photoCount(list: List<AulaNotification>) = list.mapNotNull { it.mediaId }.toSet().size + list.count { it.mediaId == null }

    fun newCount(albumId: Int? = null, childId: Int? = null) = photoCount(newNotifications(albumId, childId))

    val newMediaIds: Set<Int> get() = galleryNotifications.mapNotNull { it.mediaId }.toSet()

    val newGalleryCount: Int get() = photoCount(galleryNotifications.filter { it.isNewMedia || it.isTaggedInMedia })

    /** Clears the new markers for an album (and the photos shown in it), on Aula too. */
    suspend fun markSeen(albumId: Int? = null, childId: Int? = null, media: List<Media>) {
        if (Demo.isOn) return // keep screenshots stable
        val shown = media.map { it.id }.toSet()
        val seen = (newNotifications(albumId, childId) + galleryNotifications.filter { it.mediaId in shown }).distinctBy { it.id }
        if (seen.isEmpty()) return
        val ids = seen.map { it.id }.toSet()
        seenNotificationIds = (seenNotificationIds + ids).takeLast(3000)
        if (com.majidkabir.aulaplus.AulaApp.isReady) com.majidkabir.aulaplus.AulaApp.prefs.edit()
            .putString("seenNotificationIds", seenNotificationIds.joinToString("\n")).apply()
        runCatching {
            val owners = runCatching { AulaApi.bootstrap().guardianProfileIds }.getOrDefault(emptyList())
            AulaApi.deleteNotifications(seen, owners)
        }.onSuccess { notifications.mutate { list -> list.filterNot { it.id in ids } } }
    }

    // endregion

    fun dayPlans(childId: Int, date: LocalDate): Loadable<List<DayPlan>> {
        val start = AulaDate.weekStart(date)
        val key = "plans-$childId-${AulaDate.weekKey(start)}"
        return dayPlans.getOrPut(key) {
            Loadable(key, ListSerializer(DayPlan.serializer())) { AulaApi.dayPlans(listOf(childId), start, start.plusDays(6)) }
        }
    }

    /** The child's drop-off/pick-up plans for the next 10 days (to show the next drop-off on a day off). */
    fun upcomingPlans(childId: Int): Loadable<List<DayPlan>> {
        val today = AulaDate.today()
        val key = "next-plans-$childId-$today"
        return dayPlans.getOrPut(key) {
            Loadable(key, ListSerializer(DayPlan.serializer())) { AulaApi.dayPlans(listOf(childId), today.plusDays(1), today.plusDays(10)) }
        }
    }

    fun markRead(threadId: String) = updateThread(threadId) { it.copy(isRead = true) }
    fun setMarked(threadId: String, marked: Boolean) = updateThread(threadId) { it.copy(isMarked = marked) }
    fun removeThread(threadId: String) = allThreadLists.forEach { l -> l.mutate { list -> list.filterNot { it.id == threadId } } }

    // region Messages: folders, mute, drafts

    val folders = Loadable("mail-folders", ListSerializer(MailFolder.serializer())) { AulaApi.bootstrap(); AulaApi.folders() }
    private val folderThreads = mutableMapOf<Int, Loadable<List<ThreadSummary>>>()

    /** Threads in one folder; null = the inbox. */
    fun threads(folderId: Int?): Loadable<List<ThreadSummary>> = if (folderId == null) threads else
        folderThreads.getOrPut(folderId) {
            Loadable("threads-folder-$folderId", ListSerializer(ThreadSummary.serializer())) { AulaApi.threads(0, folderId).first }
        }

    private val allThreadLists get() = listOf(threads) + folderThreads.values

    fun updateThread(id: String, change: (ThreadSummary) -> ThreadSummary) =
        allThreadLists.forEach { l -> l.mutate { list -> list.map { if (it.id == id) change(it) else it } } }

    suspend fun setMuted(t: ThreadSummary, muted: Boolean) {
        val sub = t.subscriptionId ?: error(L.t("Kan ikke ændres for denne tråd"))
        if (!Demo.isOn) AulaApi.setMuted(sub, muted)
        updateThread(t.id) { it.copy(isMuted = muted) }
    }

    /** Moves a thread to a folder (null = inbox) and refreshes the target list. */
    suspend fun move(t: ThreadSummary, folderId: Int?) {
        if (!Demo.isOn) AulaApi.moveThread(t.id, folderId)
        removeThread(t.id)
        threads(folderId).refresh()
    }

    suspend fun leave(t: ThreadSummary) {
        if (!Demo.isOn) AulaApi.leaveThread(t.id)
        removeThread(t.id)
    }

    /** Unsent messages, kept on this phone only. */
    var drafts by androidx.compose.runtime.mutableStateOf(loadDrafts())
        private set

    private fun loadDrafts(): List<MessageDraft> = if (!com.majidkabir.aulaplus.AulaApp.isReady) emptyList() else
        runCatching {
            com.majidkabir.aulaplus.core.AppJson.decodeFromString(ListSerializer(MessageDraft.serializer()),
                com.majidkabir.aulaplus.AulaApp.prefs.getString("messageDrafts", "[]")!!)
        }.getOrDefault(emptyList())

    private fun storeDrafts() {
        if (com.majidkabir.aulaplus.AulaApp.isReady) com.majidkabir.aulaplus.AulaApp.prefs.edit()
            .putString("messageDrafts", com.majidkabir.aulaplus.core.AppJson.encodeToString(ListSerializer(MessageDraft.serializer()), drafts))
            .apply()
    }

    fun saveDraft(d: MessageDraft) { drafts = listOf(d) + drafts.filterNot { it.id == d.id }; storeDrafts() }
    fun deleteDraft(id: String) { drafts = drafts.filterNot { it.id == id }; storeDrafts() }

    // endregion

    fun reset() {
        listOf(session, presence, threads, posts, notifications, importantDates, birthdays, albums, vacationRequests).forEach { it.forget() }
        filteredPosts.values.forEach { it.forget() }
        folders.forget(); folderThreads.values.forEach { it.forget() }
        drafts = emptyList(); storeDrafts()
        weeks.clear(); threadPages.clear(); plans.clear(); tasks.clear(); albumMedia.clear(); dayPlans.clear()
    }
}
