package com.majidkabir.aulaplus.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.majidkabir.aulaplus.data.Repo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import com.majidkabir.aulaplus.data.Child
import com.majidkabir.aulaplus.ui.components.childColor
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
fun MediaViewer(initialItems: List<Media>, start: Int, children: List<Child> = emptyList(), newIds: Set<Int> = emptySet(), onClose: () -> Unit) {
    val ctx = LocalContext.current
    // Local copy so a new tag shows at once.
    var items by remember { mutableStateOf(initialItems) }
    var tagMenu by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var sharing by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                val m = items[i]
                Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClickLabel = L.t("Vis eller skjul tags")) { showInfo = !showInfo }, contentAlignment = Alignment.Center) {
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
            val current = items.getOrNull(pager.currentPage)
            AnimatedVisibility(showInfo && current != null, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                current?.let { MediaInfo(it, children, it.id in newIds) }
            }
            Row(Modifier.fillMaxWidth().systemBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, L.t("Luk"), tint = Color.White) }
                Text(if (items.isEmpty()) "" else "${pager.currentPage + 1} / ${items.size}", color = Color.White,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val shown = items.getOrNull(pager.currentPage)
                if (shown != null && shown.canEditTags && children.isNotEmpty()) Box {
                    IconButton(enabled = !tagging, onClick = { tagMenu = true }) {
                        if (tagging) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.PersonAddAlt1, L.t("Tag barn"), tint = Color.White)
                    }
                    DropdownMenu(tagMenu, onDismissRequest = { tagMenu = false }) {
                        children.forEach { c ->
                            val on = shown.isTagged(c.id)
                            DropdownMenuItem(
                                text = { Text(if (on) L.t("Fjern tag af %s", c.firstName) else L.t("Tag %s", c.firstName)) },
                                leadingIcon = { Icon(if (on) Icons.Filled.PersonRemove else Icons.Filled.PersonAddAlt1, null) },
                                onClick = {
                                    tagMenu = false
                                    tagging = true
                                    scope.launch {
                                        runCatching { Repo.setTagged(c, !on, shown) }
                                            .onSuccess { u -> items = items.map { if (it.id == u.id) u else it }; showInfo = true }
                                            .onFailure { Toast.makeText(ctx, L.t("Kunne ikke ændre tag") + ": " + (it.message ?: ""), Toast.LENGTH_LONG).show() }
                                        tagging = false
                                    }
                                },
                            )
                        }
                    }
                }
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

/** Title, "new" marker and the people tagged in the photo; own children first and in their colour. */
@Composable
private fun MediaInfo(m: Media, children: List<Child>, isNew: Boolean) {
    val mine = children.map { it.id }.toSet()
    val tags = m.tags.sortedBy { if (it.id in mine) 0 else 1 }
    if (m.title == null && tags.isEmpty() && !isNew) return
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
        .navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isNew) Text(L.t("NY"), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                modifier = Modifier.clip(CircleShape).background(Color(0xFFFF3B30)).padding(horizontal = 6.dp, vertical = 2.dp))
            m.title?.let { Text(it, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2) }
        }
        if (tags.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())
            .clearAndSetSemantics { contentDescription = L.t("Tagget: %s", tags.joinToString(", ") { it.name }) },
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalOffer, null, tint = Color.White, modifier = Modifier.size(16.dp))
            tags.forEach { t ->
                val own = t.id in mine
                Text(t.name, color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (own) FontWeight.Bold else FontWeight.Normal),
                    modifier = Modifier.clip(CircleShape).background(if (own) childColor(t.id) else Color.White.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
