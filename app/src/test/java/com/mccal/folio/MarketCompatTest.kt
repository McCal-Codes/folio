package com.mccal.folio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mccal.folio.market.CompatCheck
import com.mccal.folio.market.CompatState
import com.mccal.folio.market.DebVersion
import com.mccal.folio.market.FolioMinor
import com.mccal.folio.market.FolioVersion
import com.mccal.folio.market.InstalledPackage
import com.mccal.folio.market.PackageRelation
import com.mccal.folio.market.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the Compatibility card and the list rows say, from the checks the installer refuses on. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class MarketCompatTest {
    @get:Rule val compose = createComposeRule()

    private val have = FolioVersion(0, 6, 8)

    @Test fun `a package that works says so, with the version it was checked against`() {
        val lines = compatLines(listOf(CompatCheck.Release(FolioVersion(0, 6, 6), have), CompatCheck.Screens(setOf(Screen.COVER, Screen.INNER), setOf(Screen.COVER, Screen.INNER))))
        assertEquals(listOf(R.string.compat_works, R.string.compat_screens_both), lines.map { it.title })
        assertEquals(listOf("0.6.8"), lines.first().args)
        assertNull("nothing in the way, nothing worth a note", compatSummary(lines))
        assertNull(compatBlocked(lines))
    }

    @Test fun `a package that needs a later Folio is blocked and says which`() {
        val lines = compatLines(listOf(CompatCheck.Release(FolioVersion(0, 8, 0), have)))
        assertEquals(CompatState.BLOCKED, lines.single().state)
        assertEquals(R.string.compat_needs_folio, lines.single().title)
        assertEquals(listOf("0.8.0", "0.6.8"), lines.single().args)
        assertEquals(lines.single(), compatBlocked(lines))
    }

    @Test fun `a screen the package skips is a note, never a block`() {
        val both = setOf(Screen.COVER, Screen.INNER)
        val inner = compatLines(listOf(CompatCheck.Screens(setOf(Screen.INNER), both))).single()
        val cover = compatLines(listOf(CompatCheck.Screens(setOf(Screen.COVER), both))).single()
        assertEquals(CompatState.NOTE to R.string.compat_screen_inner_only, inner.state to inner.title)
        assertEquals(CompatState.NOTE to R.string.compat_screen_cover_only, cover.state to cover.title)
        assertNull(compatBlocked(listOf(inner, cover)))
        assertEquals(inner, compatSummary(listOf(inner)))
    }

    @Test fun `a blocking line is what a row says, ahead of a note`() {
        val note = CompatLine(CompatState.NOTE, R.string.compat_screen_inner_only, R.string.compat_screen_inner_only_detail)
        val stop = CompatLine(CompatState.BLOCKED, R.string.compat_needs_folio, R.string.compat_needs_folio_detail, listOf("0.8.0", "0.6.8"))
        assertEquals(stop, compatSummary(listOf(note, stop)))
    }

    @Test fun `dependencies and conflicts name what they are about, and the tweak host is left to its own note`() {
        val need = PackageRelation.parse("dev.host.base")!!
        val missing = compatLines(listOf(CompatCheck.Needs(listOf(need), listOf(need)))).single()
        assertEquals(CompatState.BLOCKED to listOf("dev.host.base"), missing.state to missing.args)
        val present = compatLines(listOf(CompatCheck.Needs(listOf(need), emptyList()))).single()
        assertEquals(CompatState.OK, present.state)
        val other = InstalledPackage("dev.other", DebVersion.parse("1.0")!!, "Other", InstalledPackage.Origin.FOLIO_SOURCE, null, 0L, emptyList())
        assertEquals(listOf("Other"), compatLines(listOf(CompatCheck.Replaces(other))).single().args)
        assertTrue(compatLines(listOf(CompatCheck.Hosts(listOf("pageEffects"), listOf("pageEffects")))).isEmpty())
    }

    @Test fun `ADR 0013 lines, where Android blocks, an untried Folio is a note and breaks names what to update`() {
        val outside = compatLines(listOf(CompatCheck.Android(33, 36, 37))).single()
        assertEquals(CompatState.BLOCKED to R.string.compat_android_between, outside.state to outside.title)
        assertEquals(listOf("13", "16"), outside.args)
        assertEquals(listOf("17"), outside.detailArgs)
        assertEquals(R.string.compat_android_ok_detail, compatLines(listOf(CompatCheck.Android(33, null, 37))).single().detail)
        assertTrue("no Android given, nothing said", compatLines(listOf(CompatCheck.Android(33, null, null))).isEmpty())

        val untried = compatLines(listOf(CompatCheck.FolioRange(FolioMinor(0, 7), FolioVersion(0, 8, 1)))).single()
        assertEquals(CompatState.NOTE to listOf("0.8"), untried.state to untried.args)
        assertEquals(listOf("0.7"), untried.detailArgs)
        assertTrue("inside the range it says nothing more", compatLines(listOf(CompatCheck.FolioRange(FolioMinor(0, 7), FolioVersion(0, 7, 3)))).isEmpty())

        val harbor = InstalledPackage("com.mccal.folio.harborline", DebVersion.parse("1.0.0")!!, "Harborline", InstalledPackage.Origin.FOLIO_SOURCE, null, 0L, emptyList())
        val breaks = compatLines(listOf(CompatCheck.Breaks(listOf(PackageRelation.parse("com.mccal.folio.harborline (<< 1.1)")!!), listOf(harbor)))).single()
        assertEquals(CompatState.BLOCKED to listOf("Harborline", "1.0.0"), breaks.state to breaks.args)
        assertEquals(listOf("Harborline"), breaks.detailArgs)
    }

    @Test fun `the Android line reads as words on the card`() {
        compose.setContent { CompatibilityCard(compatLines(listOf(CompatCheck.Android(33, 36, 37)))) }
        compose.onNodeWithText("Android 13 to 16").assertIsDisplayed()
        compose.onNodeWithText("This phone has Android 17. No update to Folio can change that.").assertIsDisplayed()
    }

    @Test fun `the card shows each line's title and its reason`() {
        val lines = compatLines(listOf(CompatCheck.Release(FolioVersion(0, 8, 0), have), CompatCheck.Screens(setOf(Screen.INNER), setOf(Screen.COVER, Screen.INNER))))
        compose.setContent { CompatibilityCard(lines) }
        compose.onNodeWithText("Needs Folio 0.8.0").assertIsDisplayed()
        compose.onNodeWithText("This phone has Folio 0.6.8. Update Folio, then it can be installed.").assertIsDisplayed()
        compose.onNodeWithText("Inner screen only").assertIsDisplayed()
    }
}
