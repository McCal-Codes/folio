package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStateReportTest {
    private val info = HomeStateInfo(FolioScreen.INNER, position = 1, shownPages = 4, totalPages = 5, hidden = setOf(2),
        coverRest = 0, innerRest = 1, focusOn = false, focusLimitsPages = false, profile = "custom")

    @Test fun `the report says the screen, the page, the hidden pages and what each screen rested on`() {
        val text = HomeStateReport.text(info)
        assertTrue(text, "Screen: inner, showing Home page 2 of 4" in text)
        assertTrue(text, "Pages: 5, 1 hidden (3)" in text)
        assertTrue(text, "Each screen last rested on: cover page 1, inner page 2" in text)
        assertTrue(text, "Focus: off" in text)
        assertTrue(text, "Profile: custom" in text)
    }

    @Test fun `the pager's other stops are named`() {
        assertEquals("the page left of Home", HomeStateReport.showing(info.copy(position = -1)))
        assertEquals("the App Library", HomeStateReport.showing(info.copy(position = 4)))
    }

    @Test fun `a screen that has not been used yet says so`() {
        assertTrue("cover not used yet, inner page 2" in HomeStateReport.text(info.copy(coverRest = null)))
    }

    @Test fun `a Focus that limits the pages says how many show`() {
        assertTrue("Focus: on, limiting Home to 2 of 5 pages" in HomeStateReport.text(info.copy(shownPages = 2, focusOn = true, focusLimitsPages = true)))
        assertTrue("Focus: on, every page showing" in HomeStateReport.text(info.copy(focusOn = true)))
    }

    @Test fun `the one-line form carries the same facts`() {
        val line = HomeStateReport.line(info)
        assertFalse("\n" in line)
        assertTrue(line, line.startsWith("Home: inner, Home page 2 of 4; pages 5, 1 hidden (3);"))
        assertEquals("Home: not read yet", HomeStateReport.line(null))
        assertEquals("Home:\n  (not read yet)", HomeStateReport.text(null))
    }

    @Test fun `no page count means no hidden pages in the line`() {
        assertTrue("pages 5;" in HomeStateReport.line(info.copy(hidden = emptySet())))
    }

    @Test fun `packages are listed by id with their version and state, and a long list is capped`() {
        val list = listOf(
            HomeStateReport.PackageLine("tweak.b", "1.0", false, "folio-source"),
            HomeStateReport.PackageLine("tweak.a", "2.1", true, "file"),
        )
        assertEquals("Market packages:\n  tweak.a 2.1 (on, file)\n  tweak.b 1.0 (off, folio-source)", HomeStateReport.packages(list))
        assertEquals("Market packages: none", HomeStateReport.packages(emptyList()))
        val many = (1..35).map { HomeStateReport.PackageLine("p%02d".format(it), "1", true, "file") }
        val text = HomeStateReport.packages(many)
        assertTrue(text, text.endsWith("and 5 more"))
        assertEquals(32, text.lines().size)
    }
}
