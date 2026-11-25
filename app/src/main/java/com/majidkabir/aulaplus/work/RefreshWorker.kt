package com.majidkabir.aulaplus.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.MainActivity
import com.majidkabir.aulaplus.R
import com.majidkabir.aulaplus.core.Html
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.core.TokenStore
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.Repo
import java.util.concurrent.TimeUnit

/** Polls Aula every ~15–60 min and posts notifications for new messages, posts, photos, events … No server needed. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!isEnabled || !TokenStore.hasTokens) return Result.success()
        val prefs = AulaApp.prefs
        runCatching {
            val threads = AulaApi.threads().first
            Repo.threads.replace(threads)
            val unread = threads.filter { !it.isRead }
            val seen = prefs.getStringSet("seenThreads", emptySet()) ?: emptySet()
            unread.filter { it.id !in seen }.forEach {
                notify("thread-${it.id}".hashCode(), it.sender.ifEmpty { L.t("Ny besked") }, it.subject + "\n" + it.preview, "thread/${it.id}")
            }
            prefs.edit().putStringSet("seenThreads", unread.map { it.id }.toSet()).apply()
        }
        runCatching {
            val posts = AulaApi.posts(AulaApi.bootstrap().institutionProfileIds)
            Repo.posts.replace(posts)
            val seen = prefs.getStringSet("seenPosts", emptySet()) ?: emptySet()
            posts.take(10).filter { "${it.id}" !in seen }.forEach {
                notify("post-${it.id}".hashCode(), L.t("Nyt opslag: %s", it.title), Html.plain(it.html), "post/${it.id}")
            }
            prefs.edit().putStringSet("seenPosts", posts.map { "${it.id}" }.toSet()).apply()
        }
        // Everything else Aula notifies about: new photos, albums, tags, events, invitations, holiday requests …
        runCatching {
            val s = AulaApi.bootstrap()
            val list = AulaApi.notifications(s.children.map { it.id }, s.children.mapNotNull { it.institutionCode }.distinct().sorted())
            Repo.notifications.replace(list)
            // First run after an update: only remember what's there, so old items don't all pop up.
            if (prefs.contains("seenNotifications")) {
                val seen = prefs.getStringSet("seenNotifications", emptySet()) ?: emptySet()
                val fresh = list.filter { it.id !in seen && it.threadId == null && it.postId == null }
                // Photos: one notification per album, however many pictures were added.
                fresh.filter { it.albumId != null }.groupBy { it.albumId!! }.forEach { (albumId, items) ->
                    val names = items.mapNotNull { it.childName }.distinct().sorted().joinToString(", ")
                    val count = if (items.size == 1) "" else L.t("%d nye billeder", items.size)
                    notify("album-$albumId".hashCode(), items.first().title, listOf(count, names).filter { it.isNotEmpty() }.joinToString(" · "), "album/$albumId")
                }
                // The rest: one each when there are a few, one summary when there are many.
                val others = fresh.filter { it.albumId == null }
                if (others.size > 3) notify("notes-summary".hashCode(), L.t("%d nye notifikationer i Aula", others.size),
                    others.take(3).joinToString(" · ") { it.title }, "notifications")
                else others.forEach { n -> notify("note-${n.id}".hashCode(), n.title, n.childName ?: "", "notifications") }
            }
            prefs.edit().putStringSet("seenNotifications", list.map { it.id }.toSet()).apply()
        }
        // Keeps the home-screen widget's status lines current.
        runCatching { Repo.presence.replace(AulaApi.presence(AulaApi.bootstrap().children.map { it.id })) }
        return Result.success()
    }

    private fun notify(id: Int, title: String, body: String, route: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java).putExtra("route", route)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: body).setStyle(NotificationCompat.BigTextStyle().bigText(body.take(400)))
            .setAutoCancel(true).setContentIntent(intent).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    companion object {
        private const val CHANNEL = "aula"
        private const val WORK = "aula-refresh"
        val isEnabled: Boolean get() = AulaApp.isReady && AulaApp.prefs.getBoolean("notifications", false)

        fun setEnabled(ctx: Context, on: Boolean) {
            AulaApp.prefs.edit().putBoolean("notifications", on).apply()
            if (on) {
                // Mark current items as seen so enabling doesn't spam.
                AulaApp.prefs.edit().putStringSet("seenThreads", Repo.threads.value?.filter { !it.isRead }?.map { it.id }?.toSet() ?: emptySet())
                    .putStringSet("seenPosts", Repo.posts.value?.map { "${it.id}" }?.toSet() ?: emptySet())
                    .putStringSet("seenNotifications", Repo.notifications.value?.map { it.id }?.toSet() ?: emptySet()).apply()
                schedule(ctx)
            } else WorkManager.getInstance(ctx).cancelUniqueWork(WORK)
        }

        fun schedule(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(NotificationChannel(CHANNEL, "Aula", NotificationManager.IMPORTANCE_DEFAULT))
            if (!isEnabled) return
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
