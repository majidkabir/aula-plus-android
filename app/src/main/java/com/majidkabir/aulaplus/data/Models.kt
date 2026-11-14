package com.majidkabir.aulaplus.data

import java.time.LocalDate

import com.majidkabir.aulaplus.core.AulaConfig

import com.majidkabir.aulaplus.core.L
import kotlinx.serialization.Serializable

@Serializable
data class Child(
    val id: Int,
    val name: String,
    val institutionName: String,
    val institutionCode: String? = null,
    val userId: String? = null,
    val pictureUrl: String? = null,
) {
    val firstName: String get() = name.substringBefore(' ')
}

@Serializable
data class Session(
    val displayName: String,
    val children: List<Child>,
    val institutionProfileIds: List<Int>,
    val guardianUserId: String? = null,
    val widgetIds: Set<String> = emptySet(),
    val guardianProfileIds: List<Int> = emptyList(),
    /** Widget id → name, as Aula shows it. */
    val widgetNames: Map<String, String> = emptyMap(),
    /** Widgets with a web address (opened in the browser). */
    val widgetLinks: List<WidgetLink> = emptyList(),
) {
    /** Widgets that look like a week plan, by Aula's own name for them. */
    val weekPlanWidgetNames: List<String>
        get() = widgetNames.values.filter { WEEK_PLAN_HINT.containsMatchIn(it) }.sorted()

    /** False when Aula offers no week plan at all (typical for nurseries and kindergartens). */
    val hasWeekPlan: Boolean
        get() = widgetIds.any { it in setOf(AulaConfig.WIDGET_MEEBOOK, AulaConfig.WIDGET_MU_UGEPLAN, AulaConfig.WIDGET_MU_SSO) } ||
            weekPlanWidgetNames.isNotEmpty()
}

private val WEEK_PLAN_HINT = Regex("(?i)uge|plan|lektie|opgave|easyiq|meebook|docendo|huskeliste|forløb|skoleportal")

/** A tool Aula shows on the parent's front page, with a link to open it. */
@Serializable
data class WidgetLink(val id: String, val name: String, val url: String)

enum class PresenceStatus(val code: Int) {
    NOT_PRESENT(0), SICK(1), REPORTED_ABSENT(2), PRESENT(3), FIELD_TRIP(4), SLEEPING(5),
    SPARE_TIME(6), PHYSICAL_PLACEMENT(7), CHECKED_OUT(8), NOT_ARRIVED(9);

    val label: String
        get() = L.t(
            when (this) {
                NOT_PRESENT -> "Ikke kommet"; SICK -> "Syg"; REPORTED_ABSENT -> "Ferie/fri"
                PRESENT -> "Til stede"; FIELD_TRIP -> "På tur"; SLEEPING -> "Sover"
                SPARE_TIME -> "Til aktivitet"; PHYSICAL_PLACEMENT -> "Fysisk placering"
                CHECKED_OUT -> "Gået hjem"; NOT_ARRIVED -> "Ikke mødt"
            },
        )

    companion object {
        fun of(code: Int?) = entries.firstOrNull { it.code == code }
    }
}

@Serializable
data class Presence(
    val childId: Int,
    val status: Int? = null,
    val checkIn: String? = null,
    val checkOut: String? = null,
    val plannedEntry: String? = null,
    val plannedExit: String? = null,
    val exitWith: String? = null,
    val comment: String? = null,
    val location: String? = null,
) {
    val presenceStatus: PresenceStatus? get() = PresenceStatus.of(status)

    /** Not coming today: no drop-off or pick-up planned and not checked in. */
    val isDayOff: Boolean
        get() = checkIn == null && checkOut == null && isPlaceholder(plannedEntry, plannedExit) &&
            presenceStatus.let { it == null || it == PresenceStatus.NOT_ARRIVED || it == PresenceStatus.NOT_PRESENT }

    companion object {
        /** Aula's "no plan" times: the institution's whole opening window. */
        fun isPlaceholder(entry: String?, exit: String?) =
            (entry == null || entry == "05:00" || entry == "00:00") && (exit == null || exit == "23:59")
    }
}

@Serializable
data class Attachment(
    val name: String,
    val url: String? = null,
    val isLink: Boolean = false,
    /** Set when the attachment is a photo or video from Aula's media library: shown inline. */
    val media: Media? = null,
)

@Serializable
data class ThreadSummary(
    val id: String,
    val subject: String,
    val preview: String,
    val sender: String,
    val dateMs: Long? = null,
    val isRead: Boolean,
    val isSensitive: Boolean = false,
    val regarding: List<String> = emptyList(),
    val isMarked: Boolean = false,
    val isMuted: Boolean = false,
    /** Aula's per-user subscription to the thread (needed to mute it). */
    val subscriptionId: Int? = null,
    val folderId: Int? = null,
)

/** A message folder on Aula ("Slettet post" or one the user made). The inbox has no folder. */
@Serializable
data class MailFolder(val id: Int, val name: String, val type: String? = null) {
    val isDeleted get() = type.equals("deleted", true)
}

/** A message written in the app but not sent yet (kept on this phone only, like aula.dk's drafts). */
@Serializable
data class MessageDraft(
    val id: String = java.util.UUID.randomUUID().toString(),
    val recipients: List<Recipient> = emptyList(),
    val subject: String = "",
    val text: String = "",
    val sensitive: Boolean = false,
    val savedMs: Long = System.currentTimeMillis(),
)

/** Aula's out-of-office reply for messages. */
data class AutoReply(val id: Int?, val text: String, val startMs: Long?, val endMs: Long?)

/** Aula's own notification settings (push from the Aula app and e-mail), as on aula.dk. */
data class AulaNotificationSettings(
    val instant: Boolean,
    val scheduledTime: String?,          // "HHmm"
    val days: List<Boolean>,             // Monday…Sunday
    val push: Boolean, val pushAvailable: Boolean,
    val email: Boolean, val emailAvailable: Boolean,
    val fromEmployees: Boolean, val fromGuardians: Boolean, val fromChildren: Boolean,
    val calendar: Boolean, val gallery: Boolean, val posts: Boolean,
    val substitute: Boolean, val lessonNote: Boolean,
    val comeGo: List<NamedSwitch>,
    val widgets: List<NamedSwitch>,
)

data class NamedSwitch(val id: String, val title: String, val on: Boolean)

/** An iCal link Aula keeps up to date, for the phone's own calendar. */
data class CalendarFeed(val id: Int, val oneWeek: String?, val oneYear: String?)

@Serializable
data class Message(
    val id: String,
    val sender: String,
    val dateMs: Long? = null,
    val html: String,
    val attachments: List<Attachment> = emptyList(),
)

@Serializable
data class ThreadPage(
    val subject: String,
    val messages: List<Message>, // newest first
    val moreExist: Boolean,
    val isOwnMailbox: Boolean,
)

@Serializable
data class Event(
    val id: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean = false,
    val location: String? = null,
    val teacher: String? = null,
    val substitute: String? = null,
    val hasSubstitute: Boolean = false,
    val type: String? = null,
    val childIds: List<Int> = emptyList(),
) {
    /** True on every day the event covers (multi-day holidays too; an all-day end at midnight is exclusive). */
    fun occursOn(d: java.time.LocalDate): Boolean {
        val first = com.majidkabir.aulaplus.core.AulaDate.localDate(startMs)
        val last = com.majidkabir.aulaplus.core.AulaDate.localDate(maxOf(startMs, endMs - 1))
        return !d.isBefore(first) && !d.isAfter(last)
    }
}

@Serializable
data class Post(
    val id: Int,
    val title: String,
    val html: String,
    val dateMs: Long? = null,
    val author: String,
    val isImportant: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val canComment: Boolean = false,
    /** Saved ("Gemt") on Aula. */
    val isBookmarked: Boolean = false,
)

/** The overview filters on aula.dk, with the query each one sends. */
enum class PostFilter(val label: String, val query: List<Pair<String, String>>) {
    ALL("Alle", emptyList()),
    UNREAD("Ulæste", listOf("isUnread" to "true")),
    IMPORTANT("Vigtige", listOf("isImportant" to "true")),
    STAFF("Personale", listOf("creatorPortalRole" to "employee")),
    PARENTS("Forældre", listOf("creatorPortalRole" to "guardian")),
    OWN("Egne opslag", listOf("ownPost" to "true")),
    SAVED("Gemte", listOf("isBookmarked" to "true")),
}

@Serializable
data class Birthday(val id: Int, val name: String, val date: String, val group: String? = null, val institutionCode: String? = null)

@Serializable
data class WeekPlanSection(val title: String, val html: String)

@Serializable
data class WeekPlan(val childName: String, val sections: List<WeekPlanSection>)

@Serializable
data class SchoolTask(
    val id: String,
    val title: String,
    val subject: String? = null,
    val dueMs: Long? = null,
    val weekday: String? = null,
    val childName: String = "",
    val isDone: Boolean = false,
)

@Serializable
data class ImportantDate(val id: String, val title: String, val startMs: Long, val allDay: Boolean = false)

@Serializable
data class Album(val id: Int, val title: String, val dateMs: Long? = null, val coverUrl: String? = null, val count: Int? = null)

/** A person tagged in a photo (`id` is their institution profile id). */
@Serializable
data class MediaTag(val id: Int, val name: String)

@Serializable
data class Media(
    val id: Int,
    val thumbUrl: String? = null,
    val fullUrl: String? = null,
    val isVideo: Boolean = false,
    val title: String? = null,
    val tags: List<MediaTag> = emptyList(),
    val description: String? = null,
    /** Aula's `currentUserCanEditTags`: whether this user may tag people in the photo. */
    val canEditTags: Boolean = false,
    /** A mid-size version for finding faces (large/medium thumbnail, else the full photo). */
    val scanUrl: String? = null,
    /** When the photo was put on Aula (epoch ms). */
    val dateMs: Long? = null,
) {
    fun isTagged(childId: Int) = tags.any { it.id == childId }
}

@Serializable
data class AulaNotification(
    val id: String,
    val title: String,
    val module: String? = null,
    val childName: String? = null,
    val dateMs: Long? = null,
    val postId: Int? = null,
    val threadId: String? = null,
    val albumId: Int? = null,
    val eventType: String? = null,
    /** Raw `notificationId` + owner profile: what `deleteNotifications` needs. */
    val notificationId: String? = null,
    val institutionProfileId: Int? = null,
    val mediaId: Int? = null,
    val relatedChildId: Int? = null,
) {
    val isTaggedInMedia get() = eventType == "TaggedInMedia"
    /** A new photo in an album (not a tag). */
    val isNewMedia get() = eventType == "NewMedia" || eventType == "MediaAddedToAlbum"
    val isGallery get() = (module ?: "").equals("gallery", true) ||
        eventType in setOf("NewMedia", "MediaAddedToAlbum", "TaggedInMedia", "AlbumShared")

    companion object {
        /** `TaggedInMedia` has no mediaId; Aula's web app reads it from the notificationId (`<area>:<x>-<mediaId>…`). */
        fun mediaIdFromNotificationId(id: String?): Int? =
            id?.split(":")?.getOrNull(1)?.split("-")?.getOrNull(1)?.toIntOrNull()
    }
}

@Serializable
data class Comment(val id: Int, val author: String, val html: String, val dateMs: Long? = null)

enum class PickupKind(val code: Int) {
    PICKED_UP_BY(0), SELF_DECIDER(1), SEND_HOME(2), GO_HOME_WITH(3);

    val label: String
        get() = L.t(
            when (this) {
                PICKED_UP_BY -> "Hentes af"; SELF_DECIDER -> "Selvbestemmer"
                SEND_HOME -> "Sendes hjem"; GO_HOME_WITH -> "Går hjem med"
            },
        )
    val needsName: Boolean get() = this == PICKED_UP_BY || this == GO_HOME_WITH

    companion object {
        fun of(code: Int?) = entries.firstOrNull { it.code == code }
    }
}

@Serializable
data class DayPlan(
    val templateId: Int? = null,
    val childId: Int,
    val date: String, // yyyy-MM-dd
    val entry: String? = null,
    val exit: String? = null,
    val kind: Int? = null,
    val exitWith: String? = null,
    val comment: String? = null,
    val isOnVacation: Boolean = false,
    val repeats: Boolean = false,
)

@Serializable
data class PresencePermissions(
    val childId: Int,
    val canReportSick: Boolean,
    val canAddVacation: Boolean,
    val canEditPickup: Boolean,
)

@Serializable
data class VacationRequest(
    val id: Int,
    val responseId: Int,
    val childId: Int,
    val childName: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val deadlineMs: Long? = null,
    val note: String? = null,
    val isEditable: Boolean = true,
    val isMissingAnswer: Boolean = false,
    val needsTimes: Boolean = false,
)

enum class Rsvp(val wire: String) {
    ACCEPTED("accepted"), DECLINED("declined"), TENTATIVE("tentative"), WAITING("waiting");

    val label: String
        get() = L.t(
            when (this) {
                ACCEPTED -> "Deltager"; DECLINED -> "Deltager ikke"; TENTATIVE -> "Måske"; WAITING -> "Ikke svaret"
            },
        )

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.wire.equals(s, ignoreCase = true) }
    }
}

@Serializable
data class Invitee(val institutionProfileId: Int? = null, val name: String, val response: String? = null)

@Serializable
data class EventDetail(
    val id: Int,
    val title: String,
    val html: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    val creator: String? = null,
    val responseRequired: Boolean = false,
    val deadlineMs: Long? = null,
    val deadlineExceeded: Boolean = false,
    val invitees: List<Invitee> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
)

@Serializable
data class Recipient(val id: Int, val name: String, val detail: String? = null, val type: String = "Profile")

@Serializable
data class AulaGroup(val id: Int, val name: String)

@Serializable
data class Contact(
    val id: Int,
    val name: String,
    val detail: String? = null,
    val phones: List<String> = emptyList(),
    val email: String? = null,
    val pictureUrl: String? = null,
    val role: String? = null,
    val mobilePhone: String? = null,
    val homePhone: String? = null,
    val workPhone: String? = null,
    val institutionName: String? = null,
    val group: String? = null,
    /** Children of a parent, or parents of a child. */
    val relations: List<ContactRelation> = emptyList(),
)

@Serializable
data class ContactRelation(val name: String, val detail: String? = null)

/** One of my own institution profiles, with the contact info Aula has for it. */
data class MyProfile(
    val id: Int, val profileId: Int?, val name: String, val institutionName: String?, val institutionCode: String?,
    val email: String, val mobilePhone: String, val homePhone: String, val workPhone: String,
    val pictureUrl: String?, val canEditPicture: Boolean,
)

/** Someone who may pick up a child: family from Aula, or a name the parents added (removable). */
data class PickupPerson(val id: String, val name: String, val relation: String?, val suggestionId: Int?)

/** Opening hours for one day at one institution. */
data class OpeningDay(val institutionCode: String, val date: LocalDate, val open: String?, val close: String?,
                      val isClosed: Boolean, val isSpecial: Boolean, val name: String?)

data class ClosedDays(val institutionCode: String, val start: LocalDate, val end: LocalDate, val name: String)

@Serializable
data class SecureDocument(
    val id: Int,
    val title: String,
    val category: String? = null,
    val isInternal: Boolean = true,
    val updatedMs: Long? = null,
    val creator: String? = null,
)

@Serializable
data class DocumentContent(val html: String, val attachments: List<Attachment>)

@Serializable
data class CommonFile(
    val id: String,
    val title: String,
    val institution: String? = null,
    val dateMs: Long? = null,
    val attachment: Attachment? = null,
)
