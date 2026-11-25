package com.majidkabir.aulaplus.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.majidkabir.aulaplus.core.Html
import com.majidkabir.aulaplus.core.L
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}

/** Danish → English on the phone (ML Kit; the language pack is downloaded once, ~30 MB). */
object Translator {
    private val client by lazy {
        Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.DANISH).setTargetLanguage(TranslateLanguage.ENGLISH).build())
    }
    private val cache = mutableMapOf<String, String>()

    suspend fun isDanish(text: String): Boolean =
        runCatching { LanguageIdentification.getClient().identifyLanguage(text.take(600)).await() == "da" }.getOrDefault(false)

    suspend fun toEnglish(text: String): String {
        cache[text]?.let { return it }
        client.downloadModelIfNeeded().await()
        // Paragraph by paragraph keeps the layout.
        val out = text.split("\n").map { if (it.isBlank()) it else client.translate(it).await() }.joinToString("\n")
        cache[text] = out
        return out
    }
}

/** Small "Oversæt" button under Danish text; shows the English text inline, tap again for the original. */
@Composable
fun TranslateBlock(html: String) {
    val plain = remember(html) { Html.plain(html).trim() }
    var danish by remember(html) { mutableStateOf(false) }
    var english by remember(html) { mutableStateOf<String?>(null) }
    var showing by remember(html) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(plain) { danish = plain.length > 3 && Translator.isDanish(plain) }
    if (!danish) return
    Column {
        if (showing && english != null) {
            Text(english!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = {
            if (showing) { showing = false; return@TextButton }
            scope.launch {
                busy = true; failed = false
                runCatching { Translator.toEnglish(plain) }.onSuccess { english = it; showing = true }.onFailure { failed = true }
                busy = false
            }
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Translate, null, Modifier.size(16.dp))
                Text("  " + when {
                    failed -> L.t("Kunne ikke oversætte – prøv igen")
                    showing -> L.t("Vis original")
                    else -> L.t("Oversæt")
                }, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
