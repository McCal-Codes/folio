package com.mccal.folio

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Turkish is the language that catches this: uppercasing "i" there gives "İ", not "I". Anything Folio reads as data —
 * a supporter code someone typed, a status in roadmap.json, the English words a What's New symbol is chosen by — has
 * to be cased in the root locale, or Folio behaves differently depending on the phone's language.
 */
class TurkishLocaleTest {
    private val was: Locale = Locale.getDefault()
    @After fun restore() = Locale.setDefault(was)

    private fun inTurkish(body: () -> Unit) { Locale.setDefault(Locale.forLanguageTag("tr-TR")); body() }

    @Test fun `a code with an i in it still decodes`() = inTurkish {
        // A person copying a code out of email may well send it in lower case.
        assertNotNull("lower-case i broke decoding", BetaCodes.decode("filo1"))
        assertEquals(BetaCodes.decode("FILO1")?.toList(), BetaCodes.decode("filo1")?.toList())
    }

    @Test fun `a code reads back the same whatever the language`() = inTurkish {
        assertEquals("FILO1", BetaCodes.group("filo1"))
    }

    @Test fun `a roadmap status spelled with an i is still understood`() = inTurkish {
        assertEquals(Roadmap.Status.BUILDING, Roadmap.Status.valueOf("building".uppercase(Locale.ROOT)))
    }

    @Test fun `a What's New title keeps its symbol`() = inTurkish {
        // "Icons" lower-cases to "ıcons" in Turkish, which matches no English word at all.
        assertEquals(WhatsNew.symbol("Icons everywhere"), WhatsNew.symbol("icons everywhere"))
    }
}
