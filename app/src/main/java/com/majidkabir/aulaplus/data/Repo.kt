package com.majidkabir.aulaplus.data

import com.majidkabir.aulaplus.core.AulaDate
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
        Loadable("album-$albumId", ListSerializer(Media.serializer())) {
            AulaApi.media(albumId, AulaApi.bootstrap().institutionProfileIds)
        }
    }

    /** "Billeder af <barn>": every photo the child is tagged in. Keyed by -childId so it never meets a real album. */
    fun taggedMedia(childId: Int) = albumMedia.getOrPut(-childId) {
        Loadable("tagged-$childId", ListSerializer(Media.serializer())) {
            AulaApi.taggedMedia(childId, AulaApi.bootstrap().institutionProfileIds)
        }
    }

    /** Tags or untags [child] in a photo, on Aula and in every cached album. Returns the updated photo. */
    suspend fun setTagged(child: Child, tagged: Boolean, media: Media): Media {
        val tags = media.tags.filterNot { it.id == child.id } + if (tagged) listOf(MediaTag(child.id, child.name)) else emptyList()
        if (!Demo.isOn) AulaApi.setTags(media, tags.map { it.id })
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

    private val galleryNotifications get() = notifications.value.orEmpty().filter { it.isGallery }

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
        notifications.mutate { list -> list.filterNot { it.id in ids } }
        if (!Demo.isOn) runCatching { AulaApi.deleteNotifications(seen) }
    }

    // endregion

    fun dayPlans(childId: Int, date: LocalDate): Loadable<List<DayPlan>> {
        val start = AulaDate.weekStart(date)
        val key = "plans-$childId-${AulaDate.weekKey(start)}"
        return dayPlans.getOrPut(key) {
            Loadable(key, ListSerializer(DayPlan.serializer())) { AulaApi.dayPlans(listOf(childId), start, start.plusDays(6)) }
        }
    }

    fun markRead(threadId: String) = threads.mutate { list -> list.map { if (it.id == threadId) it.copy(isRead = true) else it } }
    fun setMarked(threadId: String, marked: Boolean) =
        threads.mutate { list -> list.map { if (it.id == threadId) it.copy(isMarked = marked) else it } }
    fun removeThread(threadId: String) = threads.mutate { list -> list.filterNot { it.id == threadId } }

    fun reset() {
        listOf(session, presence, threads, posts, notifications, importantDates, albums, vacationRequests).forEach { it.forget() }
        weeks.clear(); threadPages.clear(); plans.clear(); tasks.clear(); albumMedia.clear(); dayPlans.clear()
    }
}
