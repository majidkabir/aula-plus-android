package com.majidkabir.aulaplus

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.Phase
import com.majidkabir.aulaplus.ui.AulaRoot
import com.majidkabir.aulaplus.ui.theme.AulaTheme
import com.majidkabir.aulaplus.work.RefreshWorker

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (AppState.lockEnabled && AppState.phase == Phase.LOGGED_IN) AppState.isLocked = true
            }
        })
        RefreshWorker.schedule(this)
        setContent { AulaTheme { AulaRoot() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("route")?.let { AppState.pendingRoute = it }
    }
}
