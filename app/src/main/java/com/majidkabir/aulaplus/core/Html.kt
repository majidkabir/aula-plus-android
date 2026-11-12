package com.majidkabir.aulaplus.core

object Html {
    private val entities = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'",
        "&apos;" to "'", "&aelig;" to "æ", "&oslash;" to "ø", "&aring;" to "å", "&AElig;" to "Æ",
        "&Oslash;" to "Ø", "&Aring;" to "Å", "&ndash;" to "–", "&mdash;" to "—", "&hellip;" to "…",
    )

    /** Fast tag strip for previews. */
    fun plain(html: String): String {
        var s = html.replace(Regex("<(br|/p|/div|/li)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<[^>]+>"), "")
        entities.forEach { (k, v) -> s = s.replace(k, v) }
        s = s.replace(Regex("&#\\d+;"), "")
        s = s.replace(Regex("[ \t]+"), " ").replace(Regex("\n\\s*\n+"), "\n")
        return s.trim()
    }

    fun fromPlain(text: String): String {
        val esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return esc.split("\n\n").joinToString("") { "<p>" + it.replace("\n", "<br>") + "</p>" }
    }
}
