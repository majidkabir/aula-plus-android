package com.majidkabir.aulaplus.core

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.majidkabir.aulaplus.AulaApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class Tokens(val accessToken: String, val refreshToken: String?, val expiresAt: Long) {
    val expiringSoon: Boolean get() = expiresAt - System.currentTimeMillis() < 60_000
}

class NotLoggedInException : Exception("Du er ikke logget ind.")
class TokenEndpointException(val code: Int) : Exception("Aula login fejlede (HTTP $code).")

/** OIDC tokens in EncryptedSharedPreferences with coalesced refresh. */
object TokenStore {
    private val mutex = Mutex()
    private val http = OkHttpClient()
    private var cached: Tokens? = null

    private val prefs: SharedPreferences by lazy {
        val key = MasterKey.Builder(AulaApp.context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            AulaApp.context, "aula_tokens", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun read(): Tokens? = cached ?: prefs.getString("tokens", null)
        ?.let { runCatching { AppJson.decodeFromString(Tokens.serializer(), it) }.getOrNull() }
        ?.also { cached = it }

    val hasTokens: Boolean get() = runCatching { read() != null }.getOrDefault(false)

    fun store(t: Tokens) {
        cached = t
        prefs.edit().putString("tokens", AppJson.encodeToString(Tokens.serializer(), t)).apply()
    }

    fun clear() {
        cached = null
        runCatching { prefs.edit().clear().apply() }
    }

    suspend fun accessToken(): String {
        val t = read() ?: throw NotLoggedInException()
        if (!t.expiringSoon) return t.accessToken
        return refresh(t).accessToken
    }

    /** Refresh once even when several calls ask at the same time. */
    suspend fun refresh(seen: Tokens? = read()): Tokens = mutex.withLock {
        val current = read() ?: throw NotLoggedInException()
        if (seen != null && current.accessToken != seen.accessToken && !current.expiringSoon) return current
        val rt = current.refreshToken ?: run { clear(); throw NotLoggedInException() }
        try {
            requestTokens(
                mapOf("grant_type" to "refresh_token", "client_id" to AulaConfig.CLIENT_ID, "refresh_token" to rt),
                keepRefresh = rt,
            ).also(::store)
        } catch (e: TokenEndpointException) {
            if (e.code in 400..499) { clear(); throw NotLoggedInException() }
            throw e
        }
    }

    suspend fun exchange(code: String, verifier: String) {
        store(
            requestTokens(
                mapOf(
                    "grant_type" to "authorization_code", "code" to code, "client_id" to AulaConfig.CLIENT_ID,
                    "redirect_uri" to AulaConfig.REDIRECT_URI, "code_verifier" to verifier,
                ),
                keepRefresh = null,
            ),
        )
    }

    private suspend fun requestTokens(form: Map<String, String>, keepRefresh: String?): Tokens = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(AulaConfig.TOKEN_URL).post(body).header("Accept", "application/json").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw TokenEndpointException(resp.code)
            val o = AppJson.parseToJsonElement(resp.body?.string() ?: throw IOException("empty")).asObj()
            val access = o.str("access_token") ?: throw IOException("no access_token")
            Tokens(access, o.str("refresh_token") ?: keepRefresh,
                System.currentTimeMillis() + ((o.long("expires_in") ?: 3600L) * 1000))
        }
    }
}
