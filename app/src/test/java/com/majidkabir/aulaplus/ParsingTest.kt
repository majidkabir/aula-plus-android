package com.majidkabir.aulaplus

import com.majidkabir.aulaplus.core.AulaDate
import com.majidkabir.aulaplus.core.Html
import com.majidkabir.aulaplus.core.Pkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ParsingTest {
    @Test fun pkceChallenge() =
        assertEquals("_DlCEJejw0P-_ZkBafI2EDiATci3OhR2jjENo-0iYaA", Pkce.challenge("dBjftJeZ4CVP-mB92K_uhtvbFJbYZwY8zfFk1Dc4cSU"))

    @Test fun dates() {
        assertNotNull(AulaDate.parse("2026-03-01T09:00:00Z"))
        assertNotNull(AulaDate.parse("2026-03-01T09:00:00+01:00"))
        assertNotNull(AulaDate.parse("2026-03-01T09:00:00"))
        val utc = AulaDate.parse("2026-03-01T08:00:00Z")
        assertEquals(utc, AulaDate.parse("2026-03-01T09:00:00+01:00"))
        assertEquals(utc, AulaDate.parse("2026-03-01T09:00:00+0100"))
        assertEquals(utc, AulaDate.parse("2026-03-01 09:00:00+01:00"))
        assertEquals(utc, AulaDate.parse("2026-03-01T09:00:00.000+01:00"))
        assertEquals(utc, AulaDate.parse("2026-03-01T09:00:00")) // naive = Copenhagen time
        assertEquals("07:45", AulaDate.shortTime("07:45:00"))
        assertEquals(1771196400000L, AulaDate.parseDotNet("/Date(1771196400000-0000)/"))
    }

    @Test fun html() {
        assertEquals("Husk madpakke i morgen", Html.plain("<p>Husk <b>madpakke</b>&nbsp;i morgen</p>"))
        assertEquals("<p>Hej &lt;3<br>fra os</p><p>Tak &amp; hilsen</p>", Html.fromPlain("Hej <3\nfra os\n\nTak & hilsen"))
    }
}
