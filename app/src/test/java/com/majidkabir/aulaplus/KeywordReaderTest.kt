package com.majidkabir.aulaplus

import com.majidkabir.aulaplus.data.KeywordReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KeywordReaderTest {
    private val friday = LocalDate.of(2026, 11, 13)

    @Test fun findsThingsToBringWithDay() {
        val found = KeywordReader.read("Kære forældre. Vi har haft en dejlig uge. Husk gummistøvler og regntøj på mandag!", friday)
        assertEquals(1, found.size)
        assertEquals("Gummistøvler og regntøj på mandag", found[0].title)
        assertEquals(LocalDate.of(2026, 11, 16), found[0].due)
    }

    @Test fun readsDates() {
        assertEquals(LocalDate.of(2026, 11, 20), KeywordReader.date("tilmelding senest d. 20/11", friday))
        assertEquals(LocalDate.of(2026, 12, 3), KeywordReader.date("betal senest 3. december", friday))
        assertEquals(LocalDate.of(2026, 11, 14), KeywordReader.date("hent i morgen kl 14", friday))
        assertEquals(LocalDate.of(2027, 1, 5), KeywordReader.date("svar senest 5/1", friday))
    }

    @Test fun ignoresPlainNews() {
        assertTrue(KeywordReader.read("I dag har vi bagt boller. Børnene hyggede sig i sandkassen.", friday).isEmpty())
    }
}
