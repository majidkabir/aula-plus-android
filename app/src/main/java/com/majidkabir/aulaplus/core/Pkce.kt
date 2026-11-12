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
