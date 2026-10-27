package com.mccal.folio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Where the 21 Oct regrouping put things (docs/audits/2026-10-20-settings-redundancy.md): the sheet's source is the spec. */
class SettingsRegroupTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val sheet = File(root, "app/src/main/java/com/mccal/folio/CustomizationSheet.kt").readText()

    private fun between(start: String, end: String): String {
        val a = sheet.indexOf(start); require(a >= 0) { "missing: $start" }
        val b = sheet.indexOf(end, a + start.length); require(b >= 0) { "missing: $end" }
        return sheet.substring(a, b)
    }

    @Test fun reduceTransparencySitsBesideReduceMotionInAccessibility() {
        val card = between("CustomizationPage.ACCESSIBILITY -> {", "R.string.big_buttons")
        val motion = card.indexOf("R.string.reduce_motion)")
        val transparency = card.indexOf("R.string.reduce_transparency)")
        assertTrue("Reduce Motion is in the first Accessibility card", motion >= 0)
        assertTrue("Reduce Transparency follows it in the same card", transparency > motion)
    }

    @Test fun theGlassPageNoLongerHasItsOwnReduceTransparencySwitch() {
        val glass = sheet.substring(sheet.indexOf("private fun GlassCardSettings"))
        assertFalse(glass.substringBefore("A live sample of the badge settings").contains("SettingsSwitch(stringResource(R.string.reduce_transparency)"))
    }

    @Test fun searchForReduceTransparencyLandsOnAccessibility() {
        assertTrue(sheet.contains("R.string.reduce_transparency to CustomizationPage.ACCESSIBILITY"))
    }

    @Test fun theDockHasOneCardHoldingItsPlacementWidthSpacingAlignAndHeight() {
        val card = between("SettingsCard(stringResource(R.string.dock)) {", "R.string.reset_this_layout")
        listOf("dock-placement", "R.string.width", "R.string.space_between_dock_apps", "R.string.align_dock_with_app_rows", "R.string.dock_height")
            .forEach { assertTrue("$it belongs in the Dock card", card.contains(it)) }
    }

    @Test fun thePositionCardKeepsOnlyAppsAndStatus() {
        val card = between("SettingsCard(stringResource(R.string.position)) {", "SettingsCard(stringResource(R.string.full_width_home))")
        assertFalse(card.contains("dock-placement"))
        assertTrue(card.contains("page-position") && card.contains("status-position"))
    }

    @Test fun theDockRowIsNamedPlacementSoItDoesNotRepeatThePositionCard() {
        val card = between("SettingsCard(stringResource(R.string.dock)) {", "R.string.reset_this_layout")
        assertTrue(card.contains("IosMenuRow(stringResource(R.string.placement)"))
        assertFalse(card.contains("IosMenuRow(stringResource(R.string.position)"))
        assertTrue(sheet.contains("R.string.placement to CustomizationPage.HOME"))
    }

    @Test fun searchForSideBarLandsOnTheStatusPageWhereItsCardIs() {
        assertTrue(sheet.contains("if (page == CustomizationPage.STATUS) SettingsCard(stringResource(R.string.side_rail)) {"))
        assertTrue(sheet.contains("R.string.side_rail to CustomizationPage.STATUS"))
        assertFalse(sheet.contains("R.string.side_rail to CustomizationPage.HOME"))
    }
}
