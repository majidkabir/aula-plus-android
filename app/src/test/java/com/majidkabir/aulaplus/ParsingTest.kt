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

    @Test fun loginAttemptsPreferMatchingState() {
        com.majidkabir.aulaplus.core.LoginAttempts.store = mutableMapOf()
        val a = Pkce.make(); val b = Pkce.make()
        com.majidkabir.aulaplus.core.LoginAttempts.add(a)
        com.majidkabir.aulaplus.core.LoginAttempts.add(b)
        assertEquals(listOf(a.verifier, b.verifier), com.majidkabir.aulaplus.core.LoginAttempts.verifiers(a.state))
        assertEquals(listOf(b.verifier, a.verifier), com.majidkabir.aulaplus.core.LoginAttempts.verifiers(null))
        com.majidkabir.aulaplus.core.LoginAttempts.clear()
        assertEquals(emptyList<String>(), com.majidkabir.aulaplus.core.LoginAttempts.verifiers(null))
        com.majidkabir.aulaplus.core.LoginAttempts.store = null
    }

    @Test fun multiDayEvents() {
        val start = AulaDate.parse("2026-12-21T00:00:00")!!
        val holiday = com.majidkabir.aulaplus.data.Event("h", "Juleferie", start, AulaDate.parse("2026-12-24T00:00:00")!!, allDay = true)
        assertEquals(true, holiday.occursOn(java.time.LocalDate.of(2026, 12, 23)))
        assertEquals(false, holiday.occursOn(java.time.LocalDate.of(2026, 12, 24))) // all-day end is exclusive
        val lesson = com.majidkabir.aulaplus.data.Event("l", "Dansk", AulaDate.parse("2026-12-21T08:00:00")!!, AulaDate.parse("2026-12-21T08:45:00")!!)
        assertEquals(true, lesson.occursOn(java.time.LocalDate.of(2026, 12, 21)))
        assertEquals(false, lesson.occursOn(java.time.LocalDate.of(2026, 12, 22)))
    }

    @Test fun html() {
        assertEquals("Husk madpakke i morgen", Html.plain("<p>Husk <b>madpakke</b>&nbsp;i morgen</p>"))
        assertEquals("don’t – ok", Html.plain("don&#8217;t &#x2013; ok"))
        assertEquals("&lt;b&gt; & æ", Html.decode("&amp;lt;b&amp;gt; &amp; &aelig;"))
        assertEquals("&unknown;", Html.decode("&unknown;"))
        assertEquals("<p>Hej &lt;3<br>fra os</p><p>Tak &amp; hilsen</p>", Html.fromPlain("Hej <3\nfra os\n\nTak & hilsen"))
    }
}
