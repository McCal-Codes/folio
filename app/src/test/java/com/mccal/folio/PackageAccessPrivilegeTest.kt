package com.mccal.folio

import com.mccal.folio.market.PackageAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageAccessPrivilegeTest {
    @Test fun eachPackageAccessIsThePrivilegeTierOfTheSameName() {
        PackageAccess.entries.forEach { assertEquals(it.name, it.privilege.name) }
    }

    @Test fun packagesNeverReachPastTheTiersFolioUses() {
        // Shizuku, root and hooks are never a package tier (see PackageAccess).
        PackageAccess.entries.forEach { assertTrue("${it.name} maps past Accessibility", it.privilege.inUse) }
    }
}
