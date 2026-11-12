package com.majidkabir.aulaplus.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Aula is loose with types: these helpers read ids as numbers or strings and skip junk. */
val AppJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
fun JsonElement?.asArr(): List<JsonElement> = (this as? JsonArray) ?: emptyList()
fun JsonElement?.objects(): List<JsonObject> = asArr().mapNotNull { it as? JsonObject }

fun JsonObject?.el(key: String): JsonElement? = this?.get(key)?.takeUnless { it is JsonNull }
fun JsonObject?.obj(key: String): JsonObject? = el(key) as? JsonObject
fun JsonObject?.arr(key: String): List<JsonElement> = (el(key) as? JsonArray) ?: emptyList()
fun JsonObject?.objs(key: String): List<JsonObject> = arr(key).mapNotNull { it as? JsonObject }

/** String value; numbers are returned as text. Empty strings become null. */
fun JsonObject?.str(key: String): String? =
    (el(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

fun JsonObject?.int(key: String): Int? = (el(key) as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()
fun JsonObject?.long(key: String): Long? = (el(key) as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
fun JsonObject?.bool(key: String): Boolean? = (el(key) as? JsonPrimitive)?.booleanOrNull

/** Rich text that is either `{ "html": "..." }` or a plain string. */
fun JsonObject?.html(key: String): String = when (val e = el(key)) {
    is JsonObject -> e.str("html") ?: ""
    is JsonPrimitive -> e.content
    else -> ""
}

/** First non-blank of several keys. */
fun JsonObject?.firstStr(vararg keys: String): String? = keys.firstNotNullOfOrNull { str(it) }

fun JsonObject?.personName(): String? {
    if (this == null) return null
    firstStr("fullName", "displayName", "name")?.let { return it }
    val parts = listOfNotNull(str("firstName"), str("lastName"))
    return parts.joinToString(" ").takeIf { it.isNotBlank() }
}
