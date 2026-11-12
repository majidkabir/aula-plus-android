package com.majidkabir.aulaplus.core

object Html {
    private val named = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "aelig" to "æ", "oslash" to "ø", "aring" to "å", "AElig" to "Æ", "Oslash" to "Ø", "Aring" to "Å",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "laquo" to "«", "raquo" to "»", "bull" to "•",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "euro" to "€", "eacute" to "é", "uuml" to "ü",
        "ouml" to "ö", "auml" to "ä",
    )
    private val entity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});")

    /** Decodes named and numeric entities in one pass (so "&amp;lt;" stays "&lt;"). */
    fun decode(s: String): String = entity.replace(s) { m ->
        val e = m.groupValues[1]
        val cp = when {
            e.startsWith("#x") || e.startsWith("#X") -> e.drop(2).toIntOrNull(16)
            e.startsWith("#") -> e.drop(1).toIntOrNull()
            else -> null
        }
        when {
            cp != null -> if (Character.isValidCodePoint(cp) && cp != 0) String(Character.toChars(cp)) else ""
            else -> named[e] ?: m.value
        }
    }

    /** Fast tag strip for previews. */
    fun plain(html: String): String {
        var s = html.replace(Regex("<(br|/p|/div|/li)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<[^>]+>"), "")
        s = decode(s)
        s = s.replace(Regex("[ \t\u00A0]+"), " ").replace(Regex("\n\\s*\n+"), "\n")
        return s.trim()
    }

    fun fromPlain(text: String): String {
        val esc = text.replace("\r\n", "\n").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return esc.split("\n\n").joinToString("") { "<p>" + it.replace("\n", "<br>") + "</p>" }
    }
}
