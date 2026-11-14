package com.majidkabir.aulaplus

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.ui.theme.ThemeStore

class AulaApp : Application(), coil.ImageLoaderFactory {
    /**
     * Photos are cached by Aula id (see [aulaImage]) and served from disk even when the server
     * says not to cache: Aula's signed URLs change and expire, the photos don't.
     */
    override fun newImageLoader(): coil.ImageLoader = coil.ImageLoader.Builder(this)
        .respectCacheHeaders(false)
        .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("aula-images")).maxSizeBytes(400L * 1024 * 1024).build() }
        .crossfade(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        context = this
        L.override = prefs.getString("lang", "") ?: ""
        ThemeStore.load()
        com.majidkabir.aulaplus.data.AppLock.register()
    }

    companion object {
        @SuppressLint("StaticFieldLeak") // the Application context lives as long as the process
        lateinit var context: Context
            private set
        val isReady: Boolean get() = ::context.isInitialized
        val prefs: SharedPreferences get() = context.getSharedPreferences("aulaplus", Context.MODE_PRIVATE)
    }
}
