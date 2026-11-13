package com.majidkabir.aulaplus.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.majidkabir.aulaplus.core.AulaConfig
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.core.LoginAttempts
import com.majidkabir.aulaplus.core.Pkce
import com.majidkabir.aulaplus.core.TokenStore
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.ui.theme.ThemeStore
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    var pkce by remember { mutableStateOf(Pkce.make()) }
    var showWeb by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // One silent second attempt per tap: Aula sometimes rejects the code from the first MitID
    // round trip, while a login right after goes through at once without MitID.
    var retried by remember { mutableStateOf(false) }
    fun startLogin() { pkce = Pkce.make(); LoginAttempts.add(pkce); errorText = null; showWeb = true }
    val t = ThemeStore.theme

    if (showWeb) {
        MitIdWebView(pkce.authorizeUrl, onClose = { showWeb = false }) { callback ->
            showWeb = false
            scope.launch {
                working = true
                try {
                    (callback.getQueryParameter("error_description") ?: callback.getQueryParameter("error"))?.let { error(it) }
                    val code = callback.getQueryParameter("code") ?: error(L.t("Aula sendte ingen login-kode tilbage."))
                    val state = callback.getQueryParameter("state")
                    // Try the attempt this code belongs to (by state), then earlier attempts. PKCE makes
                    // sure only the right verifier works, so a mismatched or missing state is not fatal.
                    val verifiers = LoginAttempts.verifiers(state).ifEmpty { listOf(pkce.verifier) }
                    var last: Exception? = null
                    for (v in verifiers) {
                        try {
                            TokenStore.exchange(code, v)
                            LoginAttempts.clear()
                            AppState.didLogin()
                            last = null
                            break
                        } catch (e: java.io.IOException) {
                            throw e
                        } catch (e: Exception) {
                            last = e
                        }
                    }
                    if (last is com.majidkabir.aulaplus.core.TokenEndpointException && !retried) {
                        retried = true
                        startLogin()
                        return@launch
                    }
                    last?.let { throw it }
                } catch (e: Exception) {
                    errorText = when (e) {
                        is java.io.IOException -> L.t("Ingen forbindelse til Aula.")
                        else -> e.message
                    }
                } finally { working = false }
            }
        }
        return
    }

    ThemedBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            AppMark()
            Spacer(Modifier.height(20.dp))
            Text("Aula+", style = MaterialTheme.typography.displaySmall)
            Text(L.t("Beskeder, kalender og ugeplan —\nhurtigt og overskueligt."), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                Feature(Icons.Filled.Bolt, Color(0xFFFF9500), L.t("Åbner med det samme"), L.t("Alt gemmes på telefonen og opdateres i baggrunden."))
                Feature(Icons.Filled.FamilyRestroom, Color(0xFF30B0C7), L.t("Hele dagen på ét blik"), L.t("Komme/gå, skema og beskeder for hvert barn."))
                Feature(Icons.Filled.NotificationsActive, Color(0xFFFF3B30), L.t("Besked når der sker noget"), L.t("Notifikationer om nye beskeder og opslag."))
            }
            (errorText ?: AppState.loginNotice)?.let {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF9500)); Text(it, color = Color(0xFFFF9500), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { retried = false; startLogin() }, enabled = !working,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(L.t("Log ind med MitID"), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(L.t("Uofficiel app. Dine data går kun mellem din telefon og Aula."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, color: Color, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Runs Aula's OIDC → UniLogin → MitID pages and returns the app-private.aula.dk?code=… redirect. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MitIdWebView(url: String, onClose: () -> Unit, onCallback: (Uri) -> Unit) {
    BackHandler(onBack = onClose)
    var done by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, L.t("Annuller")) }
            Text("MitID", style = MaterialTheme.typography.titleMedium)
        }
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    private fun intercept(u: Uri): Boolean {
                        if (u.host == AulaConfig.REDIRECT_HOST) {
                            if (!done) { done = true; onCallback(u) }
                            return true
                        }
                        // "Open MitID app" is a normal https link (appswitch.mitid.dk). A WebView would just load it,
                        // so hand it to the MitID app. After approving, switch back to Aula+ and the login continues here.
                        if (u.host?.lowercase() in MITID_APPSWITCH_HOSTS) {
                            openMitIdApp(context, u)
                            return true
                        }
                        val scheme = u.scheme?.lowercase()
                        if (scheme == "intent") {
                            runCatching {
                                // Only what a browser would do with an intent:// link: no explicit component or selector.
                                val i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                                i.addCategory(Intent.CATEGORY_BROWSABLE)
                                i.component = null
                                i.selector = null
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(i)
                            }.onFailure { mitIdAppMissing(context) }
                            return true
                        }
                        if (scheme != null && scheme !in setOf("http", "https", "about", "data", "blob")) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .onFailure { mitIdAppMissing(context) }
                            return true
                        }
                        return false
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = intercept(request.url)
                    override fun onPageStarted(view: WebView, u: String, favicon: Bitmap?) {
                        if (intercept(Uri.parse(u))) view.stopLoading()
                    }
                }
                loadUrl(url)
            }
        }, modifier = Modifier.weight(1f))
    }
}

private val MITID_APPSWITCH_HOSTS = setOf("appswitch.mitid.dk", "appswitch.mitid.nets.eu", "appswitch-test.mitid.nets.eu")

/** Opens the MitID app itself, never a browser (a browser would just show the same page again). */
private fun openMitIdApp(ctx: android.content.Context, u: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (android.os.Build.VERSION.SDK_INT >= 30) intent.addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
    else intent.setPackage("dk.mitid.app.android")
    runCatching { ctx.startActivity(intent) }.onFailure { mitIdAppMissing(ctx) }
}

private fun mitIdAppMissing(ctx: android.content.Context) {
    android.widget.Toast.makeText(ctx, L.t("MitID-appen er ikke på denne telefon. Godkend i MitID-appen på din anden enhed."),
        android.widget.Toast.LENGTH_LONG).show()
}
