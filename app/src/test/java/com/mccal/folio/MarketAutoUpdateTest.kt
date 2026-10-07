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
    ) = MarketAutoUpdate.isCandidate(origin, enabled, installedSource, installed, listingSource, listing, revoked, impostor, needs, installable)

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
}
