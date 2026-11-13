package com.majidkabir.aulaplus.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.core.L

/** Fingerprint / face lock for the whole app, with the phone's PIN, pattern or password as fallback. */
object AppLock {
    enum class Delay(val seconds: Int, private val key: String) {
        IMMEDIATELY(0, "Med det samme"), ONE_MINUTE(60, "Efter 1 minut"),
        FIVE_MINUTES(300, "Efter 5 minutter"), FIFTEEN_MINUTES(900, "Efter 15 minutter");
        val label: String get() = L.t(key)
    }

    private const val AUTH = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    private val prefs get() = AulaApp.prefs

    private var enabledState by mutableStateOf(AulaApp.isReady && AulaApp.prefs.getBoolean("lock", false))
    val isEnabled: Boolean get() = enabledState
    private fun setEnabled(v: Boolean) { enabledState = v; prefs.edit().putBoolean("lock", v).apply() }

    private var delayState by mutableStateOf(
        Delay.entries.firstOrNull { AulaApp.isReady && it.seconds == AulaApp.prefs.getInt("lock.delay", 0) } ?: Delay.IMMEDIATELY)
    var delay: Delay
        get() = delayState
        set(v) { delayState = v; prefs.edit().putInt("lock.delay", v.seconds).apply() }

    var isLocked by mutableStateOf(false)
        private set
    /** One-time "lock with fingerprint?" question after the first login. */
    var showOffer by mutableStateOf(false)

    /** Bumped each time the app comes back while locked: the lock screen asks once per bump. */
    var promptRequest by mutableStateOf(0)
        private set

    private var backgroundedAt: Long? = null
    private var authenticating = false

    // What this phone offers

    /** Fingerprint, face or at least a screen lock is set up. */
    fun isAvailable(ctx: Context) = BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS
    fun hasBiometrics(ctx: Context) = BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    // Lifecycle (whole process, registered once from AulaApp)

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = onBackground()
            override fun onStart(owner: LifecycleOwner) = onForeground()
        })
    }

    private val loggedIn get() = AppState.phase == Phase.LOGGED_IN

    fun onBackground() {
        // The PIN/pattern screen of the fingerprint dialog can send the app to the background;
        // that must not count as leaving the app (it would lock again right after unlocking).
        if (authenticating) return
        backgroundedAt = System.currentTimeMillis()
        // Lock before Android takes the recents thumbnail.
        if (isEnabled && loggedIn && delay == Delay.IMMEDIATELY) isLocked = true
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (isEnabled && loggedIn && System.currentTimeMillis() - since >= delay.seconds * 1000L) isLocked = true
        if (isLocked) promptRequest++
    }

    /** Called when the login state is known or changes. Cold start: locked if the lock is on. */
    fun sync(loggedIn: Boolean, coldStart: Boolean = false) {
        if (!loggedIn || !isEnabled) isLocked = false
        else if (coldStart) isLocked = true
    }

    fun showLockedForDemo() { isLocked = true }
    /** Screenshot tests: show the settings with the lock on, without touching storage. */
    fun previewEnabled(on: Boolean) { enabledState = on }

    // Actions

    private fun authenticate(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        if (authenticating) return
        if (!isAvailable(activity)) { onResult(false); return }
        authenticating = true
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authenticating = false; onResult(true)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authenticating = false; onResult(false)
            }
            // onAuthenticationFailed = one wrong finger; the prompt stays open for another try.
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("Aula+")
            .setAllowedAuthenticators(AUTH).build())
    }

    fun unlock(activity: FragmentActivity) {
        // Screen lock removed from the phone: there is nothing left to unlock with.
        if (!isAvailable(activity)) { isLocked = false; return }
        authenticate(activity, L.t("Lås Aula+ op")) { ok -> if (ok) isLocked = false }
    }

    /** Turning the lock on needs one successful check, so nobody locks themselves out by accident. */
    fun enable(activity: FragmentActivity, done: (Boolean) -> Unit = {}) {
        authenticate(activity, L.t("Slå lås til for Aula+")) { ok -> if (ok) setEnabled(true); done(ok) }
    }

    fun disable() { setEnabled(false); isLocked = false }

    fun offerAfterLogin(ctx: Context) {
        if (isEnabled || !isAvailable(ctx) || prefs.getBoolean("lock.offered", false)) return
        prefs.edit().putBoolean("lock.offered", true).apply()
        showOffer = true
    }
}
