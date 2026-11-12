package com.majidkabir.aulaplus

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.ui.theme.ThemeStore

class AulaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        context = this
        L.override = prefs.getString("lang", "") ?: ""
        ThemeStore.load()
    }

    companion object {
        lateinit var context: Context
            private set
        val isReady: Boolean get() = ::context.isInitialized
        val prefs: SharedPreferences get() = context.getSharedPreferences("aulaplus", Context.MODE_PRIVATE)
    }
}
