package com.majidkabir.aulaplus.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.Media
import com.majidkabir.aulaplus.ui.components.openSafely
import kotlinx.coroutines.launch

/** Full-screen photo viewer: swipe between photos, play videos, save or share the current one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewer(items: List<Media>, start: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var sharing by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                val m = items[i]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SubcomposeAsyncImage(
                        model = if (m.isVideo) m.thumbUrl else (m.fullUrl ?: m.thumbUrl), contentDescription = null,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) } },
                    )
                    if (m.isVideo && m.fullUrl != null) IconButton(onClick = { ctx.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(m.fullUrl))) },
                        modifier = Modifier.size(88.dp)) {
                        Icon(Icons.Filled.PlayCircle, L.t("Afspil video"), tint = Color.White, modifier = Modifier.size(72.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().systemBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, L.t("Luk"), tint = Color.White) }
                Text(if (items.isEmpty()) "" else "${pager.currentPage + 1} / ${items.size}", color = Color.White,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(enabled = !sharing && items.isNotEmpty(), onClick = {
                    val m = items[pager.currentPage]
                    val url = m.fullUrl ?: m.thumbUrl ?: return@IconButton
                    sharing = true
                    scope.launch {
                        runCatching {
                            val ext = if (m.isVideo) "mp4" else "jpg"
                            val file = AulaApi.download(url, "aula-${m.id}.$ext")
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (m.isVideo) "video/*" else "image/*")
                                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), L.t("Gem eller del")))
                        }.onFailure { Toast.makeText(ctx, L.t("Kunne ikke åbne filen"), Toast.LENGTH_SHORT).show() }
                        sharing = false
                    }
                }) {
                    if (sharing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Share, L.t("Gem eller del"), tint = Color.White)
                }
            }
        }
    }
}
