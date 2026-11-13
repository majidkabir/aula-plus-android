package com.majidkabir.aulaplus

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.fragment.app.FragmentActivity
import com.majidkabir.aulaplus.data.AppLock
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.ui.AulaRoot
import com.majidkabir.aulaplus.ui.theme.AulaTheme
import com.majidkabir.aulaplus.work.RefreshWorker

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        RefreshWorker.schedule(this)
        setContent {
            val lockOn = AppLock.isEnabled
            LaunchedEffect(lockOn) { hideFromRecents(lockOn) }
            AulaTheme { AulaRoot() }
        }
    }

    /** With the lock on, the recents screen shows no thumbnail of messages. */
    private fun hideFromRecents(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!on)
        else if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("route")?.let { AppState.pendingRoute = it }
    }
}
