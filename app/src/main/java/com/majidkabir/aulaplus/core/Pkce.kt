package com.majidkabir.aulaplus.core

import android.net.Uri
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE (S256) values plus OAuth state for one login attempt. */
data class Pkce(val verifier: String, val challenge: String, val state: String) {
    val authorizeUrl: String
        get() = Uri.parse(AulaConfig.AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", AulaConfig.CLIENT_ID)
            .appendQueryParameter("scope", AulaConfig.SCOPE)
            .appendQueryParameter("redirect_uri", AulaConfig.REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build().toString()

    companion object {
        fun make(): Pkce {
            val verifier = random(32)
            return Pkce(verifier, challenge(verifier), random(16))
        }

        fun challenge(verifier: String): String =
            b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

        private fun random(n: Int): String = ByteArray(n).also { SecureRandom().nextBytes(it) }.let(::b64url)

        fun b64url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/**
 * Recent login attempts (state → PKCE verifier), kept for 30 minutes.
 *
 * Aula's login can come back with the state of an earlier attempt (or none) after the MitID
 * round trip. The code is still bound to its own PKCE challenge, so we exchange it with the
 * verifier of the attempt it belongs to instead of failing; a wrong verifier is rejected by Aula.
 */
object LoginAttempts {
    private const val KEY = "login.attempts"
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    /** Storage; replaced in unit tests. */
    var store: MutableMap<String, String>? = null

    private fun read(): String? = store?.get(KEY) ?: if (store == null) com.majidkabir.aulaplus.AulaApp.prefs.getString(KEY, null) else null
    private fun write(v: String?) {
        store?.let { if (v == null) it.remove(KEY) else it[KEY] = v; return }
        com.majidkabir.aulaplus.AulaApp.prefs.edit().apply { if (v == null) remove(KEY) else putString(KEY, v) }.apply()
    }

    private data class Attempt(val state: String, val verifier: String, val at: Long)

    private fun load(): List<Attempt> {
        val now = System.currentTimeMillis()
        return (read() ?: return emptyList()).lines().mapNotNull { line ->
            val p = line.split(' ')
            if (p.size != 3) null else Attempt(p[0], p[1], p[2].toLongOrNull() ?: 0)
        }.filter { now - it.at < MAX_AGE_MS }
    }

    private fun save(list: List<Attempt>) = write(list.takeLast(5).joinToString("\n") { "${it.state} ${it.verifier} ${it.at}" })

    fun add(p: Pkce) = save(load() + Attempt(p.state, p.verifier, System.currentTimeMillis()))

    /** Verifiers to try: the one matching [state] first, then the others, newest first. */
    fun verifiers(state: String?): List<String> {
        val list = load().reversed()
        return list.filter { it.state == state }.map { it.verifier } + list.filter { it.state != state }.map { it.verifier }
    }

    fun clear() = write(null)
}
