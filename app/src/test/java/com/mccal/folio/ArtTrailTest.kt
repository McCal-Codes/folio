package com.mccal.folio

import com.mccal.folio.market.DebVersion
import com.mccal.folio.market.InstallResult
import com.mccal.folio.market.InstalledPackage
import com.mccal.folio.market.PackageInstaller
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a bug report says about wallpapers and the Market.
 *
 * The installer shows people one general message when a package can't be applied, so the trail is the only place the
 * real reason survives. These check that it does, and that the lines carry ids and sizes rather than anything a
 * package contains.
 */
class ArtTrailTest {
    private fun last() = Diagnostics.trailText().lines().last()

    private fun pkg(version: String) = InstalledPackage(
        id = "dev.example.hills", version = DebVersion.parse(version)!!, name = "Green Hills",
        origin = InstalledPackage.Origin.FOLIO_SOURCE, sourceUrl = null, installedAt = 0, snapshots = emptyList(),
    )

    @Test fun `a refused wallpaper keeps its reason`() {
        Diagnostics.artRefused("dev.example.hills", "that wallpaper's picture is too large to show (6000x6000)")
        assertTrue(last(), last().endsWith("Wallpaper dev.example.hills refused: that wallpaper's picture is too large to show (6000x6000)"))
    }

    @Test fun `an update says what it replaced`() {
        Diagnostics.marketGot("dev.example.hills", InstallResult.Installed(pkg("2.0"), pkg("1.0"), emptyList()))
        assertTrue(last(), last().endsWith("Market get dev.example.hills: installed 2.0 over 1.0"))
    }

    @Test fun `a failure says which check stopped it`() {
        Diagnostics.marketGot(null, InstallResult.Failed(InstallResult.Reason.APPLY, "Folio couldn't apply that package"))
        assertTrue(last(), last().endsWith("Market get from a file: failed, APPLY: Folio couldn't apply that package"))
    }

    @Test fun `whether the user's background was kept is said plainly`() {
        Diagnostics.artOn("dev.example.hills", fresh = false, shown = false)
        assertTrue(last(), last().endsWith("Wallpaper dev.example.hills on (again), user's choice kept"))
        Diagnostics.artOff("dev.example.hills", putBack = true)
        assertTrue(last(), last().endsWith("Wallpaper dev.example.hills off, earlier background put back"))
    }

    @Test fun `a backup restore counts, and names what failed`() {
        Diagnostics.marketRestored(PackageInstaller.Restore(on = listOf(pkg("1.0")), off = emptyList(), failed = listOf(pkg("1.0"))))
        assertTrue(last(), last().endsWith("Market backup: 1 on, 0 off, 1 failed (dev.example.hills)"))
    }

    @Test fun `nothing cleared says nothing`() {
        Diagnostics.event("marker")
        Diagnostics.artPruned(emptyList())
        assertTrue(last(), last().endsWith("marker"))
    }
}
