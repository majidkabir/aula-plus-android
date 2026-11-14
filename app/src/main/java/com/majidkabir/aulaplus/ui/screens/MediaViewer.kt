package com.majidkabir.aulaplus.ui.screens

import com.majidkabir.aulaplus.ui.components.MediaThumb
import com.majidkabir.aulaplus.core.AulaDate
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
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
import com.majidkabir.aulaplus.ui.components.aulaImage
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
import androidx.compose.material.icons.filled.Face
import com.majidkabir.aulaplus.data.FaceIndex
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
    var faceMenu by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val learnFace: (Media, Child) -> Unit = { m, c ->
        scope.launch {
            if (!FaceIndex.confirm(m, c.id)) Toast.makeText(ctx, L.t("Fandt ikke et ansigt på billedet."), Toast.LENGTH_SHORT).show()
        }
    }
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var sharing by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                val m = items[i]
                Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClickLabel = L.t("Vis eller skjul tags")) { showInfo = !showInfo }, contentAlignment = Alignment.Center) {
                    var imageSize by remember(m.id) { mutableStateOf<androidx.compose.ui.geometry.Size?>(null) }
                    SubcomposeAsyncImage(
                        model = if (m.isVideo) aulaImage(ctx, "t${m.id}", m.thumbUrl) else aulaImage(ctx, "f${m.id}", m.fullUrl ?: m.thumbUrl),
                        contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                        onSuccess = { imageSize = it.painter.intrinsicSize },
                        // The cached thumbnail shows at once while the full photo loads.
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                MediaThumb(m, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                CircularProgressIndicator(color = Color.White)
                            }
                        },
                    )
                    // A face found by face recognition is outlined while the info is shown.
                    val box = if (showInfo) children.firstNotNullOfOrNull { FaceIndex.faceBox(m, it.id) } else null
                    val size = imageSize
                    if (box != null && size != null && size.width > 0 && size.height > 0) {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
                            val scale = minOf(w / size.width, h / size.height)
                            val dw = size.width * scale; val dh = size.height * scale
                            val ox = (w - dw) / 2; val oy = (h - dh) / 2
                            val padX = box[2] * dw * 0.15f; val padY = box[3] * dh * 0.15f
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            with(density) {
                                Box(Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset((ox + box[0] * dw - padX).toInt(), (oy + box[1] * dh - padY).toInt()) }
                                    .size((box[2] * dw + 2 * padX).toDp(), (box[3] * dh + 2 * padY).toDp())
                                    .border(5.dp, Color.White, RoundedCornerShape(14.dp))
                                    .padding(2.dp)
                                    .border(3.dp, Color(0xFFAF52DE), RoundedCornerShape(12.dp)))
                            }
                        }
                    }
                    if (m.isVideo && m.fullUrl != null) IconButton(onClick = { ctx.openSafely(Intent(Intent.ACTION_VIEW, Uri.parse(m.fullUrl))) },
                        modifier = Modifier.size(88.dp)) {
                        Icon(Icons.Filled.PlayCircle, L.t("Afspil video"), tint = Color.White, modifier = Modifier.size(72.dp))
                    }
                }
            }
            val current = items.getOrNull(pager.currentPage)
            AnimatedVisibility(showInfo && current != null, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                current?.let { MediaInfo(it, children, it.id in newIds, onLearn = { c -> learnFace(it, c) }) }
            }
            Row(Modifier.fillMaxWidth().systemBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, L.t("Luk"), tint = Color.White) }
                val at = items.getOrNull(pager.currentPage)?.dateMs?.let { java.time.Instant.ofEpochMilli(it).atZone(AulaDate.copenhagen) }
                Column(Modifier.weight(1f)) {
                    // Like the Photos app: the day on top, time and position below.
                    if (at != null) {
                        Text(PhotoDay.title(at.toLocalDate()), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("${at.toLocalTime().toString().take(5)} · ${pager.currentPage + 1} / ${items.size}", color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall)
                    } else Text(if (items.isEmpty()) "" else "${pager.currentPage + 1} / ${items.size}", color = Color.White,
                        style = MaterialTheme.typography.titleMedium)
                }
                val shown = items.getOrNull(pager.currentPage)
                // "Det er Emma" / "Ikke Emma": teaches face finding (only on this phone, nothing goes to Aula).
                if (shown != null && !shown.isVideo && FaceIndex.isEnabled && children.isNotEmpty()) Box {
                    IconButton(onClick = { faceMenu = true }) { Icon(Icons.Filled.Face, L.t("Ansigtsgenkendelse"), tint = Color.White) }
                    DropdownMenu(faceMenu, onDismissRequest = { faceMenu = false }) {
                        children.forEach { c ->
                            if (FaceIndex.isFaceMatch(shown, c.id)) DropdownMenuItem(text = { Text(L.t("Det er ikke %s", c.firstName)) },
                                onClick = { faceMenu = false; FaceIndex.reject(shown, c.id) })
                            else if (!shown.isTagged(c.id)) DropdownMenuItem(text = { Text(L.t("Det er %s", c.firstName)) },
                                onClick = { faceMenu = false; learnFace(shown, c) })
                        }
                    }
                }
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
private fun MediaInfo(m: Media, children: List<Child>, isNew: Boolean, onLearn: (Child) -> Unit) {
    val mine = children.map { it.id }.toSet()
    val tags = m.tags.sortedBy { if (it.id in mine) 0 else 1 }
    val maybe = children.filter { FaceIndex.isFaceMatch(m, it.id) }
    if (m.title == null && tags.isEmpty() && !isNew && maybe.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
        .navigationBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        maybe.forEach { c ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FaceBadge(size = 18.dp)
                    Text(L.t("Er det %s i rammen?", c.firstName), color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
                // Far apart on purpose, so "no" isn't tapped by mistake.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedButton(onClick = { FaceIndex.reject(m, c.id) }, modifier = Modifier.widthIn(min = 110.dp)) {
                        Text(L.t("Nej"), color = Color.White)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f).widthIn(min = 40.dp))
                    androidx.compose.material3.Button(onClick = { onLearn(c) }, modifier = Modifier.widthIn(min = 110.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))) {
                        Text(L.t("Ja, det er %s", c.firstName))
                    }
                }
            }
        }
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
