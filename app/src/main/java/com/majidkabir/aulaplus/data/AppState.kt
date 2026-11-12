package com.majidkabir.aulaplus.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.core.TokenStore
import com.majidkabir.aulaplus.widget.WidgetWriter

enum class Phase { LAUNCHING, LOGGED_OUT, LOGGED_IN }
enum class Tab { TODAY, MESSAGES, CALENDAR, WEEK_PLAN, MORE }

/** App-wide UI state (Compose snapshot state). */
object AppState {
    var phase by mutableStateOf(Phase.LAUNCHING)
    var loginNotice by mutableStateOf<String?>(null)
    var selectedChildId by mutableStateOf<Int?>(null)
    var tab by mutableStateOf(Tab.TODAY)
    var isLocked by mutableStateOf(false)

    /** Pending deep link (from a notification) the UI should open. */
    var pendingRoute by mutableStateOf<String?>(null)

    var lockEnabled: Boolean
        get() = AulaApp.isReady && AulaApp.prefs.getBoolean("lock", false)
        set(v) { AulaApp.prefs.edit().putBoolean("lock", v).apply() }

    val children: List<Child> get() = Repo.session.value?.children ?: emptyList()
    val visibleChildren: List<Child> get() = selectedChildId?.let { id -> children.filter { it.id == id } } ?: children
    fun child(id: Int) = children.firstOrNull { it.id == id }

    suspend fun start() {
        if (Demo.isOn) { phase = Phase.LOGGED_IN; return }
        val has = TokenStore.hasTokens
        phase = if (has) Phase.LOGGED_IN else Phase.LOGGED_OUT
        isLocked = has && lockEnabled
        if (has) Repo.session.load()
    }

    suspend fun didLogin() {
        loginNotice = null
        AulaApi.reset()
        phase = Phase.LOGGED_IN
        Repo.session.refresh()
    }

    fun sessionExpired() {
        if (phase != Phase.LOGGED_IN) return
        loginNotice = L.t("Din Aula-session er udløbet. Log ind med MitID igen.")
        phase = Phase.LOGGED_OUT
    }

    fun logout() {
        TokenStore.clear()
        AulaApi.reset()
        DiskCache.clear()
        Repo.reset()
        selectedChildId = null
        WidgetWriter.clear()
        phase = Phase.LOGGED_OUT
    }
}
