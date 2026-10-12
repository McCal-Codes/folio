package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageAccessTest {
    @Test fun aPackageNeedsTheMostAnyOfItsFeaturesNeeds() {
        assertEquals(PackageAccess.STANDARD, packageAccess(emptySet()))
        assertEquals(PackageAccess.STANDARD, packageAccess(setOf(Capability.THEME, Capability.WALLPAPER)))
        assertEquals(PackageAccess.NOTIFICATIONS, packageAccess(setOf(Capability.THEME, Capability.TINT_MEDIA)))
    }

    @Test fun betterWithOnlyNamesAccessAboveWhatThePackageAlreadyNeeds() {
        assertEquals(PackageAccess.NOTIFICATIONS, packageBetterWith(setOf(Capability.APP_PANELS)))
        assertNull(packageBetterWith(setOf(Capability.APP_PANELS, Capability.NOTIFICATION_APP_ROW)))
        assertEquals(PackageAccess.ACCESSIBILITY, packageBetterWith(setOf(Capability.ISLAND_MESSAGES, Capability.TINT_MEDIA)))
        assertNull(packageBetterWith(setOf(Capability.THEME)))
    }

    @Test fun theBuiltInTweaksGetTheTiersTheirCodeNeeds() {
        val tier = TweakId.entries.associateWith { packageAccess(setOf(it.capability)) }
        assertEquals(PackageAccess.NOTIFICATIONS, tier[TweakId.NOTIFICATION_APP_ROW]) // Roll Call
        assertEquals(PackageAccess.NOTIFICATIONS, tier[TweakId.TINT_NOTIFICATIONS]) // Palette
        assertEquals(PackageAccess.NOTIFICATIONS, tier[TweakId.TINT_MEDIA]) // Afterglow
        assertEquals(PackageAccess.STANDARD, tier[TweakId.APP_PANELS]) // Cabinet works without it...
        assertEquals(PackageAccess.NOTIFICATIONS, packageBetterWith(setOf(TweakId.APP_PANELS.capability))) // ...and does more with it
        assertEquals(PackageAccess.STANDARD, tier[TweakId.DOCK_MAGNIFY])
        assertEquals(PackageAccess.STANDARD, tier[TweakId.PAGE_EFFECTS])
        assertEquals(PackageAccess.STANDARD, tier[TweakId.DUET])
    }

    @Test fun betterWithIsAlwaysMoreThanNeeds() {
        Capability.entries.filter { it.betterWith != null }.forEach {
            assertTrue("${it.id}: betterWith must be above needs", it.betterWith!! > it.needs)
        }
    }

    @Test fun nothingNeedsAccessibilityToday() {
        // Accessibility is only used by Folio's own features (the island and dock over other apps, the button bar, the
        // shade), none of which a package configures. A capability that needs it is a decision: change this test with it.
        assertEquals(emptyList<Capability>(), Capability.entries.filter { it.needs == PackageAccess.ACCESSIBILITY })
    }
}
