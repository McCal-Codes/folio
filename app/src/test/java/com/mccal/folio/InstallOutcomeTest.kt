package com.mccal.folio

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallOutcomeTest {
    @Test fun cancellingTheAndroidPromptIsNotAFailure() {
        assertEquals(InstallOutcome.CANCELLED, installOutcome(PackageInstaller.STATUS_FAILURE_ABORTED))
    }

    @Test fun theOtherStatusesKeepTheirMeaning() {
        assertEquals(InstallOutcome.NEEDS_CONFIRM, installOutcome(PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertEquals(InstallOutcome.SUCCESS, installOutcome(PackageInstaller.STATUS_SUCCESS))
        for (failure in listOf(PackageInstaller.STATUS_FAILURE, PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_STORAGE)) {
            assertEquals(InstallOutcome.FAILED, installOutcome(failure))
        }
    }

    @Test fun aLinkAddsACodeWhenNothingIsHeld() = assertTrue(canRedeemFromLink(null, false, "AAA"))
    @Test fun aLinkMaySayTheSameCodeAgain() = assertTrue(canRedeemFromLink("AAA", true, "AAA"))
    @Test fun aLinkCannotReplaceACodeThatStillWorks() = assertFalse(canRedeemFromLink("AAA", true, "BBB"))
    @Test fun aLinkCanReplaceACodeThatHasRunOut() = assertTrue(canRedeemFromLink("AAA", false, "BBB"))
}
