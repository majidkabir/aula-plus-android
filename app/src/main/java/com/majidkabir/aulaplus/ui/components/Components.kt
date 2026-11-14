package com.majidkabir.aulaplus.ui.components

import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.WifiOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.Attachment
import com.majidkabir.aulaplus.data.AulaApi
import com.majidkabir.aulaplus.data.Child
import com.majidkabir.aulaplus.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.LocalDate

object Dims {
    val cardRadius = 24.dp
    val rowRadius = 16.dp
    val pad = 16.dp
    val gap = 12.dp
}

/** Standard card surface: white, big round corners, soft warm shadow. */
@Composable
fun Modifier.card(padding: Dp = Dims.pad): Modifier = this
    .fillMaxWidth()
    .shadow(6.dp, RoundedCornerShape(Dims.cardRadius), clip = false,
        ambientColor = com.majidkabir.aulaplus.ui.theme.CardShadow.copy(alpha = 0.18f),
        spotColor = com.majidkabir.aulaplus.ui.theme.CardShadow.copy(alpha = 0.18f))
    .clip(RoundedCornerShape(Dims.cardRadius))
    .background(MaterialTheme.colorScheme.surface)
    .padding(padding)

/** A card tinted with a child's (or any) colour: pastel over the card colour. */
@Composable
fun Modifier.pastelCard(color: Color, padding: Dp = Dims.pad): Modifier = this
    .fillMaxWidth()
    .shadow(6.dp, RoundedCornerShape(Dims.cardRadius), clip = false,
        ambientColor = com.majidkabir.aulaplus.ui.theme.CardShadow.copy(alpha = 0.18f),
        spotColor = com.majidkabir.aulaplus.ui.theme.CardShadow.copy(alpha = 0.18f))
    .clip(RoundedCornerShape(Dims.cardRadius))
    .background(MaterialTheme.colorScheme.surface)
    .background(color.copy(alpha = 0.16f))
    .padding(padding)

@Composable
fun childColor(id: Int): Color = Palette.forChild(id, AppState.children.map { it.id })

@Composable
fun SectionTitle(title: String, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary, count: Int? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (count != null && count > 0) CountBadge(count)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun CountBadge(count: Int) {
    Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Color(0xFFFF3B30), CircleShape).padding(horizontal = 7.dp, vertical = 1.dp))
}

@Composable
fun ChildTag(name: String, color: Color) {
    Text(name, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(color.copy(alpha = 0.15f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
fun StatusPill(label: String, icon: ImageVector?, color: Color) {
    Row(
        Modifier.background(color.copy(alpha = 0.16f), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
fun InitialsAvatar(name: String, color: Color = Color.Gray, size: Dp = 32.dp) {
    val initials = name.split(" ").filter { it.firstOrNull()?.isLetter() == true }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.75f)))),
        contentAlignment = Alignment.Center,
    ) {
        // Sized to the circle, not the user's font scale, so initials never clip.
        val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
        Text(initials.ifEmpty { "?" }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f / scale).sp, maxLines = 1)
    }
}

@Composable
fun ChildAvatar(child: Child, size: Dp = 40.dp) {
    val color = childColor(child.id)
    Box(Modifier.size(size).border(2.dp, color, CircleShape).padding(2.dp)) {
        if (child.pictureUrl != null) {
            SubcomposeAsyncImage(
                model = child.pictureUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                error = { InitialsAvatar(child.firstName, color, size - 4.dp) },
                loading = { InitialsAvatar(child.firstName, color, size - 4.dp) },
            )
        } else {
            InitialsAvatar(child.firstName, color, size - 4.dp)
        }
    }
}

/** "Alle / Emma / Noah" pills. */
@Composable
fun ChildFilterBar(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    val children = AppState.children
    if (children.size < 2) return
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = inset, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(L.t("Alle"), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, AppState.selectedChildId == null) {
            AppState.selectedChildId = null
        }
        children.forEach { c ->
            Pill(c.firstName, childColor(c.id), Color.White, AppState.selectedChildId == c.id) { AppState.selectedChildId = c.id }
        }
    }
}

@Composable
private fun Pill(title: String, color: Color, onColor: Color, selected: Boolean, onClick: () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
        color = if (selected) onColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape)
            .background(if (selected) color else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Tab, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** ‹  Uge 46 · 9.–15. nov.  › */
@Composable
fun WeekSwitcher(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val start = AulaDate.weekStart(date)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(date.minusWeeks(1)) }, modifier = Modifier.semantics { contentDescription = L.t("Forrige uge") }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).clickable { onChange(AulaDate.today()) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(L.t("Uge %d", AulaDate.weekNumber(date)), style = MaterialTheme.typography.titleMedium)
            Text("${AulaDate.dayMonthShort(start)} – ${AulaDate.dayMonthShort(start.plusDays(6))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onChange(date.plusWeeks(1)) }, modifier = Modifier.semantics { contentDescription = L.t("Næste uge") }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun StaleBanner(error: String?, savedAt: Long?, modifier: Modifier = Modifier) {
    if (error == null) return
    val orange = Color(0xFFFF9500)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.rowRadius)).background(orange.copy(alpha = 0.12f))
            .border(1.dp, orange.copy(alpha = 0.3f), RoundedCornerShape(Dims.rowRadius)).padding(12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.WifiOff, null, tint = orange)
        Column {
            Text(L.t("Kunne ikke opdatere"), style = MaterialTheme.typography.titleSmall)
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            if (savedAt != null) Text(L.t("Viser data fra %s. Træk ned for at prøve igen.", AulaDate.relative(savedAt)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(title: String, icon: ImageVector, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Aula HTML rendered as native text with working links. */
@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val primary = MaterialTheme.colorScheme.primary
    val text = remember(html, primary) {
        runCatching {
            AnnotatedString.fromHtml(html.trim(), linkStyles = TextLinkStyles(SpanStyle(color = primary, fontWeight = FontWeight.Medium)))
        }.getOrElse { AnnotatedString(com.majidkabir.aulaplus.core.Html.plain(html)) }
    }
    Text(trimTrailing(text), modifier = modifier, style = MaterialTheme.typography.bodyLarge, color = color)
}

private fun trimTrailing(s: AnnotatedString): AnnotatedString {
    var end = s.length
    while (end > 0 && s[end - 1].isWhitespace()) end--
    return if (end == s.length) s else s.subSequence(0, end)
}

/** Starts another app; shows a short message instead of crashing when nothing can handle it. */
fun android.content.Context.openSafely(intent: Intent) {
    runCatching { startActivity(intent) }.onFailure { Toast.makeText(this, L.t("Ingen app kan åbne dette."), Toast.LENGTH_SHORT).show() }
}

/** Attachments: files open with the phone's viewer (downloaded with the Aula session), links in the browser. */
@Composable
fun AttachmentList(attachments: List<Attachment>, modifier: Modifier = Modifier) {
    if (attachments.isEmpty()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<Attachment?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { a ->
            val icon = when {
                a.isLink -> Icons.Default.Link
                a.name.endsWith(".pdf", true) -> Icons.Default.PictureAsPdf
                Regex(".*\\.(jpe?g|png|heic|gif)$", RegexOption.IGNORE_CASE).matches(a.name) -> Icons.Default.Image
                Regex(".*\\.(docx?|pages|txt)$", RegexOption.IGNORE_CASE).matches(a.name) -> Icons.Default.Description
                else -> Icons.Default.AttachFile
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Dims.rowRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = a.url != null && busy == null) {
                        val url = a.url ?: return@clickable
                        if (a.isLink) {
                            ctx.openSafely(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            return@clickable
                        }
                        busy = a
                        scope.launch {
                            try {
                                val file = AulaApi.download(url, a.name)
                                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                                ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            } catch (e: Exception) {
                                Toast.makeText(ctx, L.t("Kunne ikke åbne filen"), Toast.LENGTH_SHORT).show()
                            } finally { busy = null }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(a.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                if (busy == a) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

val ScreenPadding = PaddingValues(start = Dims.pad, end = Dims.pad, bottom = 24.dp)

@Composable
fun Gap(h: Dp = Dims.gap) = Spacer(Modifier.width(1.dp).padding(top = h))
