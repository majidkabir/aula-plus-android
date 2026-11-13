package com.majidkabir.aulaplus.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.withResumed
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppLock
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.Demo
import com.majidkabir.aulaplus.ui.theme.ThemeStore

/** The app's mark, shared by the login and lock screens. */
@Composable
fun AppMark(size: Dp = 96.dp) {
    val t = ThemeStore.theme
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.29f)).background(Brush.linearGradient(listOf(t.brandLight, t.second))),
        contentAlignment = Alignment.Center) {
        // Sized to the mark, not the user's font scale.
        val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        Text("A+", color = Color.White, fontSize = (size.value * 0.42f / scale).sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    var confirmLogout by remember { mutableStateOf(false) }
    val biometric = Demo.isOn || AppLock.hasBiometrics(ctx)
    fun unlock() { if (!Demo.isOn) activity?.let { AppLock.unlock(it) } }

    // Ask once when shown and once each time the app comes back. Not on every resume:
    // cancelling the dialog resumes the app, and asking again then would trap the user in a loop.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(AppLock.promptRequest) {
        lifecycle.withResumed {}
        unlock()
    }
    // Back leaves the app instead of navigating the screens behind the lock.
    BackHandler { activity?.moveTaskToBack(true) }

    ThemedBackground {
        Column(Modifier.fillMaxSize()
            // Nothing behind the lock screen may receive a touch.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .systemBarsPadding().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            AppMark(84.dp)
            Spacer(Modifier.height(20.dp))
            Text(L.t("Aula+ er låst"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(L.t("Lås op for at se beskeder, kalender og fravær."), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Button(onClick = { unlock() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(if (biometric) Icons.Filled.Fingerprint else Icons.Filled.Lock, null)
                Spacer(Modifier.width(10.dp))
                Text(L.t("Lås op"), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { confirmLogout = true }) {
                Text(L.t("Log ud"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text(L.t("Log ud af Aula+?")) },
        text = { Text(L.t("Du skal logge ind med MitID igen.")) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; AppState.logout() }) { Text(L.t("Log ud"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(L.t("Annuller")) } },
    )
}

/** One-time question after the first login. */
@Composable
fun LockOfferDialog() {
    val activity = LocalContext.current as? FragmentActivity
    if (!AppLock.showOffer) return
    AlertDialog(
        onDismissRequest = { AppLock.showOffer = false },
        icon = { Icon(Icons.Filled.Fingerprint, null) },
        title = { Text(L.t("Lås Aula+ med fingeraftryk?"), textAlign = TextAlign.Center) },
        text = { Text(L.t("Så kan andre, der låner din telefon, ikke læse beskeder om dine børn.")) },
        confirmButton = {
            TextButton(onClick = { AppLock.showOffer = false; activity?.let { AppLock.enable(it) } }) { Text(L.t("Slå til")) }
        },
        dismissButton = { TextButton(onClick = { AppLock.showOffer = false }) { Text(L.t("Ikke nu")) } },
    )
}
