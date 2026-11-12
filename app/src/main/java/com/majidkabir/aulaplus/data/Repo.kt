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
        AulaApi.bootstrap(); AulaApi.notifications()
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
