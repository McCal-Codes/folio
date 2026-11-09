package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every What to Test item gets a real icon, not the fallback star, and What's New's first sheet stays at Apple's four entries. */
class WhatToTestIconsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }

    @Test fun `no What to Test item falls back to the star`() {
        val list = WhatToTest.parse(File(root, "app/src/main/assets/what-to-test.json").readText())!!
        val stars = list.items.filter { WhatsNew.symbol(it.title).second == FolioColors.Value.Yellow }.map { it.title }
        assertTrue("These items have no icon of their own: $stars", stars.isEmpty())
    }

    @Test fun `words inside other words do not pick the icon`() {
        assertEquals(WhatsNew.symbol("Folders"), WhatsNew.symbol("Folders blur Home"))
        assertEquals(WhatsNew.symbol("Discover"), WhatsNew.symbol("Swipe to Discover"))
        assertTrue(WhatsNew.symbol("Swipe to Discover") != WhatsNew.symbol("Cover screen"))
        assertEquals(WhatsNew.symbol("Six more wallpapers"), WhatsNew.symbol("Full-screen wallpaper preview"))
        assertTrue(WhatsNew.symbol("Six more wallpapers").second != FolioColors.Value.Yellow)
    }

    @Test fun `the first What's New sheet shows four features`() {
        assertEquals(4, FEATURES_SHOWN)
    }
<<<<<<< HEAD

    @Test fun `a feature on the sheet is its first sentence`() {
        assertEquals("Type \"dark\" and it lists settings.", WhatsNew.firstSentence("Type \"dark\" and it lists settings. It follows the same switch."))
        assertEquals("One sentence only", WhatsNew.firstSentence("One sentence only"))
        assertEquals("Version 0.7.0 is here.", WhatsNew.firstSentence("Version 0.7.0 is here. More below."))
    }
=======
>>>>>>> origin/what-to-test-apple
}
