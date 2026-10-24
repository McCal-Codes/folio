package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** What Spotlight offers for an app result: add it, say it is on Home, or name the folder that holds it (#spotlight-add). */
class HomeOfferTest {
    private val layout = HomeLayout(
        slots = listOf("mail", null, "maps"), dock = listOf("phone", null, null, null),
        leadingSlots = List(HOME_CELLS) { if (it == 3) "wallet" else null },
        folders = listOf(FolderEntry("folder:1", "Social", listOf("chat", "photos"))))

    @Test fun `an app that is nowhere on Home can be added`() = assertEquals(HomeOffer.Add, homeOffer(layout, "notes"))

    @Test fun `an app on a page, or on the unfolded-only page, is already on Home`() {
        assertEquals(HomeOffer.OnHome, homeOffer(layout, "maps"))
        assertEquals(HomeOffer.OnHome, homeOffer(layout, "wallet"))
    }

    @Test fun `an app in a folder is named with its folder, never offered`() {
        assertEquals(HomeOffer.InFolder("Social"), homeOffer(layout, "chat"))
        assertEquals(HomeOffer.InFolder("Social"), homeOffer(layout, "photos"))
    }

    /** Offering Add for an app the model would ignore is a button that does nothing, so the two must agree. */
    @Test fun `only an app the model will pin is ever offered`() {
        val ids = listOf("mail", "maps", "wallet", "chat", "photos", "notes", "phone")
        ids.forEach { id ->
            val pinsIt = layout.folders.none { id in it.appIds } && layout.indexOfShortcut(id) == null
            assertEquals(id, pinsIt, homeOffer(layout, id) == HomeOffer.Add)
        }
    }

    @Test fun `once added it is on Home and the offer goes away`() {
        val pinned = layout.copy(slots = pinHomeApp(layout.slots, "notes", true))
        assertEquals(HomeOffer.Add, homeOffer(layout, "notes"))
        assertEquals(HomeOffer.OnHome, homeOffer(pinned, "notes"))
    }
}
