package com.majidkabir.aulaplus.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.core.AppJson
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.core.NotLoggedInException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException

/** JSON files in the app's private storage; wiped on logout. */
object DiskCache {
    private val dir: File get() = File(AulaApp.context.filesDir, "cache").apply { mkdirs() }

    @Serializable
    private class Box(val savedAt: Long, val json: String)

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    fun <T> load(key: String, s: KSerializer<T>): Pair<T, Long>? = runCatching {
        val box = AppJson.decodeFromString(Box.serializer(), file(key).readText())
        AppJson.decodeFromString(s, box.json) to box.savedAt
    }.getOrNull()

    fun <T> save(key: String, s: KSerializer<T>, value: T) {
        runCatching {
            file(key).writeText(AppJson.encodeToString(Box.serializer(), Box(System.currentTimeMillis(), AppJson.encodeToString(s, value))))
        }
    }

    fun clear() { runCatching { dir.deleteRecursively() } }
}

/**
 * One cached, refreshable piece of data. `value` comes from disk immediately and is then
 * replaced by fresh data (stale-while-revalidate). Compose state, so screens recompose.
 */
class Loadable<T>(val key: String, private val serializer: KSerializer<T>, private val fetch: suspend () -> T) {
    var value by mutableStateOf<T?>(null)
        private set
    var savedAt by mutableStateOf<Long?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var onChange: (() -> Unit)? = null

    init {
        if (Demo.isOn) {
            @Suppress("UNCHECKED_CAST")
            value = Demo.value(key) as T?
            savedAt = System.currentTimeMillis()
        } else {
            DiskCache.load(key, serializer)?.let { (v, at) -> value = v; savedAt = at }
        }
    }

    suspend fun load(maxAgeSeconds: Long = 0) {
        val at = savedAt
        if (value != null && at != null && System.currentTimeMillis() - at < maxAgeSeconds * 1000) return
        refresh()
    }

    suspend fun refresh() {
        if (isLoading || Demo.isOn) return
        isLoading = true
        try {
            val fresh = withContext(Dispatchers.IO) { fetch() }
            value = fresh
            savedAt = System.currentTimeMillis()
            error = null
            withContext(Dispatchers.IO) { DiskCache.save(key, serializer, fresh) }
            onChange?.invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotLoggedInException) {
            AppState.sessionExpired()
        } catch (e: IOException) {
            error = L.t("Ingen forbindelse til Aula.")
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        } finally {
            isLoading = false
        }
    }

    fun replace(fresh: T) {
        value = fresh
        savedAt = System.currentTimeMillis()
        DiskCache.save(key, serializer, fresh)
        onChange?.invoke()
    }

    fun mutate(change: (T) -> T) {
        val v = value ?: return
        replace(change(v))
    }

    fun forget() {
        value = null; savedAt = null; error = null
    }
}
