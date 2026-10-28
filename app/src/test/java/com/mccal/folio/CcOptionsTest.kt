package com.mccal.folio

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deeper Control Center settings (FeatureGate.CC_SETTINGS_V2): defaults equal to what Control Center always did, the pages that
 * are shown (needed AND switched on), the page it opens on, which tiles close it, and how long an Edit controls request lives.
 */
class CcOptionsTest {
    private val plan = CcPlan(listOf(8), slidersOnFirst = true, focusOnFirst = true)

    @Test fun `the defaults are what Control Center did before the options existed`() {
        val o = CcOptions()
        assertTrue(o.mediaPage); assertTrue(o.connectivityPage)
        assertEquals(CcOpenOn.CONTROLS, o.openOn)
        assertTrue("slider ticks stay on", o.sliderTicks)
        assertFalse("closing after a tap is off", o.closeAfterToggle)
    }

    @Test fun `an old saved state with none of the keys reads as the defaults`() {
        assertEquals(CcOptions(), CcOptions.read(JSONObject()))
        assertEquals(CcOptions(), decodeLauncherState("{}", legacyRaw = null).ccOptions)
        // A value from the future or damaged text does not break reading.
        assertEquals(CcOpenOn.CONTROLS, CcOptions.read(JSONObject().put("ccOpenOn", "SOMETHING_NEW")).openOn)
    }

    @Test fun `options survive being written and read`() {
        val o = CcOptions(mediaPage = false, connectivityPage = false, openOn = CcOpenOn.LAST, sliderTicks = false, closeAfterToggle = true)
        assertEquals(o, CcOptions.read(o.writeTo(JSONObject())))
    }

    @Test fun `a page is shown only when it is needed and its switch is on`() {
        val both = listOf(CcPage.MEDIA, CcPage.CONNECTIVITY)
        assertEquals(both, ccShownExtraPages(both, CcOptions()))
        assertEquals(listOf(CcPage.CONNECTIVITY), ccShownExtraPages(both, CcOptions(mediaPage = false)))
        assertEquals(listOf(CcPage.MEDIA), ccShownExtraPages(both, CcOptions(connectivityPage = false)))
        assertEquals(emptyList<CcPage>(), ccShownExtraPages(both, CcOptions(mediaPage = false, connectivityPage = false)))
        // A switch that is on cannot bring back a page the first page already covers.
        assertEquals(emptyList<CcPage>(), ccShownExtraPages(emptyList(), CcOptions()))
        assertEquals(listOf(CcPage.MEDIA), ccShownExtraPages(listOf(CcPage.MEDIA), CcOptions()))
    }

    @Test fun `the switches work on top of the automatic hiding at every size`() {
        for (height in listOf(null, 120f, 200f, 400f, 900f)) {
            val needed = ccExtraPages(plan, height, 64f, 12f, focus = true, dedupe = true)
            assertEquals(needed, ccShownExtraPages(needed, CcOptions()))
            assertTrue(ccShownExtraPages(needed, CcOptions(mediaPage = false, connectivityPage = false)).isEmpty())
            assertTrue("switches only ever remove pages", needed.containsAll(ccShownExtraPages(needed, CcOptions(mediaPage = false))))
        }
    }

    @Test fun `the Controls pages are never switched off`() {
        val pages = ccPageSpecs(2, ccShownExtraPages(listOf(CcPage.MEDIA, CcPage.CONNECTIVITY), CcOptions(mediaPage = false, connectivityPage = false)))
        assertEquals(listOf(CcPageSpec(CcPage.CONTROLS, 0), CcPageSpec(CcPage.CONTROLS, 1)), pages)
    }

    @Test fun `Open on Controls always starts at the first page`() {
        val pages = ccPageSpecs(1, listOf(CcPage.MEDIA, CcPage.CONNECTIVITY))
        assertEquals(0, ccStartPage(CcOpenOn.CONTROLS, CcPage.CONNECTIVITY, pages))
        assertEquals(0, ccStartPage(CcOpenOn.CONTROLS, null, pages))
    }

    @Test fun `Open on Last returns to the page last used when it is still there`() {
        val pages = ccPageSpecs(1, listOf(CcPage.MEDIA, CcPage.CONNECTIVITY))
        assertEquals(1, ccStartPage(CcOpenOn.LAST, CcPage.MEDIA, pages))
        assertEquals(2, ccStartPage(CcOpenOn.LAST, CcPage.CONNECTIVITY, pages))
        assertEquals("nothing used yet", 0, ccStartPage(CcOpenOn.LAST, null, pages))
        assertEquals("the page was switched off since", 0, ccStartPage(CcOpenOn.LAST, CcPage.MEDIA, ccPageSpecs(1, listOf(CcPage.CONNECTIVITY))))
        assertEquals("any Controls page is the first", 0, ccStartPage(CcOpenOn.LAST, CcPage.CONTROLS, ccPageSpecs(2, listOf(CcPage.MEDIA))))
    }

    @Test fun `only the one-shot tiles close Control Center, and only when asked`() {
        val on = CcOptions(closeAfterToggle = true)
        val off = CcOptions()
        val oneShot = setOf(CcControl.FLASHLIGHT, CcControl.CAMERA, CcControl.CALCULATOR, CcControl.SCREENSHOT, CcControl.SETTINGS, CcControl.LOCK, CcControl.WALLET, CcControl.NOTES)
        for (control in CcControl.entries) {
            assertFalse("${control.name} never closes it while the option is off", ccClosesAfterTap(control, off))
            assertEquals(control.name, control in oneShot, ccClosesAfterTap(control, on))
        }
        // The switches stay open.
        assertFalse(ccClosesAfterTap(CcControl.DND, on)); assertFalse(ccClosesAfterTap(CcControl.ROTATION, on))
    }

    @Test fun `an Edit controls request is good once, and only for a few seconds`() {
        CcEditRequest.take(now = 0L)
        assertFalse("nothing was asked", CcEditRequest.take(now = 100L))
        CcEditRequest.request(now = 1_000L)
        assertTrue(CcEditRequest.take(now = 1_500L))
        assertFalse("used up", CcEditRequest.take(now = 1_600L))
        CcEditRequest.request(now = 1_000L)
        assertFalse("too old: the panel never opened", CcEditRequest.take(now = 20_000L))
    }

    @Test fun `every row on the page can be found in Settings search while its gate is open`() {
        val titles = listOf(R.string.cc_media_page, R.string.cc_connectivity_page, R.string.cc_open_on, R.string.cc_slider_ticks,
            R.string.cc_close_after, R.string.edit_controls, R.string.cc_reset)
        val entries = SettingsEntries.filter { it.third == CustomizationPage.NOTIFICATIONS }.map { it.first }
        titles.forEach { assertTrue("a Settings search entry for ${EnglishStrings.get(it)}", it in entries) }
        assertEquals(titles.toSet(), CC_SETTINGS_V2_ROWS)
    }

    @Test fun `the gate is closed for everyone until 0_6_9 and the page keeps every old setting`() {
        assertEquals("0.6.9", FeatureGate.CC_SETTINGS_V2.opensIn)
        // The old rows are all still on the page (they moved into cards, none was dropped).
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val cards = java.io.File(root, "app/src/main/java/com/mccal/folio/ControlCenterSettings.kt").readText()
        for (name in listOf("control_center_size", "control_center_look", "cc_look_note", "unfolded_control_center_in_the_middle", "to_restyle_samsungs_own_pull_down_colors"))
            assertTrue("$name is still on the page", "R.string.$name" in cards)
    }
}
