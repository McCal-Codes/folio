package com.mccal.folio

import com.mccal.folio.market.DebVersion
import com.mccal.folio.market.InstalledPackage.Origin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketAutoUpdateTest {
    private val old = DebVersion.parse("1.0.0")!!
    private val newer = DebVersion.parse("1.1.0")!!
    private fun candidate(
        origin: Origin = Origin.FOLIO_SOURCE, enabled: Boolean = true, installedSource: String? = "https://a/", listingSource: String = "https://a/",
        installed: DebVersion = old, listing: DebVersion = newer, revoked: Boolean = false, impostor: Boolean = false, needs: Boolean = false, installable: Boolean = true,
        updatesBuiltIn: Boolean = false,
    ) = MarketAutoUpdate.isCandidate(origin, enabled, installedSource, installed, listingSource, listing, revoked, impostor, needs, installable, updatesBuiltIn)

    @Test fun `a newer version from the source that installed it is a candidate`() {
        assertTrue(candidate())
    }

    @Test fun `a version that is not newer is not`() {
        assertFalse(candidate(listing = old))
        assertFalse(candidate(installed = newer, listing = old))
    }

    @Test fun `only the source that installed it can update it`() {
        assertFalse(candidate(listingSource = "https://b/"))
        assertFalse(candidate(installedSource = null))
    }

    @Test fun `a file, a package that is off, a pulled or clashing listing and one needing another are left alone`() {
        assertFalse(candidate(origin = Origin.FILE))
        assertFalse(candidate(enabled = false))
        assertFalse(candidate(revoked = true))
        assertFalse(candidate(impostor = true))
        assertFalse(candidate(needs = true))
    }

    @Test fun `a listing with no size or checksum is never installed by itself`() {
        assertFalse(candidate(installable = false))
    }

    @Test fun `a built-in install follows a listing that stands in for it, and nothing else`() {
        // A built-in install has no source address; only Folio's own tweaks source standing in for it may update it.
        assertTrue(candidate(installedSource = null, listingSource = "https://a/", updatesBuiltIn = true))
        assertFalse(candidate(installedSource = null, listingSource = "https://a/"))
        // The flag doesn't excuse any other check.
        assertFalse(candidate(installedSource = null, updatesBuiltIn = true, revoked = true))
        assertFalse(candidate(installedSource = null, updatesBuiltIn = true, listing = old))
        assertFalse(candidate(installedSource = null, updatesBuiltIn = true, installable = false))
        assertFalse(candidate(installedSource = null, updatesBuiltIn = true, enabled = false))
        // A package already installed from some other source isn't taken over by the flag.
        assertFalse(candidate(installedSource = "https://other/", listingSource = "https://a/", updatesBuiltIn = true))
    }
}
