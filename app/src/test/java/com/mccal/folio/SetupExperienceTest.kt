package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupExperienceTest {
    @Test fun freshInstallShowsSetup() {
        assertEquals(SetupEntryDecision.SHOW,
            setupEntryDecision(finished = false, started = false, hadLauncherState = false))
    }

    @Test fun existingInstallStaysSilentWithoutNeedingSetupState() {
        assertEquals(SetupEntryDecision.EXISTING_INSTALL,
            setupEntryDecision(finished = false, started = false, hadLauncherState = true))
    }

    @Test fun startedFreshSetupSurvivesLauncherStateCreationAndRecreation() {
        assertEquals(SetupEntryDecision.SHOW,
            setupEntryDecision(finished = false, started = true, hadLauncherState = true))
    }

    @Test fun finishedSetupStaysDismissedAfterRecreation() {
        assertEquals(SetupEntryDecision.ALREADY_FINISHED,
            setupEntryDecision(finished = true, started = true, hadLauncherState = false))
        assertEquals(SetupEntryDecision.ALREADY_FINISHED,
            setupEntryDecision(finished = true, started = true, hadLauncherState = true))
    }

    // BI-6: leaving the Welcome with a Home intent is "Skip for now". The next recreation (a font size change) must not bring it back.
    @Test fun leavingSetupWithHomeDefersItAcrossRecreation() {
        assertEquals(SetupEntryDecision.DEFERRED,
            setupEntryDecision(finished = false, started = true, hadLauncherState = true, deferred = true))
        assertEquals(SetupEntryDecision.DEFERRED,
            setupEntryDecision(finished = false, started = true, hadLauncherState = false, deferred = true))
    }

    @Test fun deferringChangesNothingOnFirstLaunchOrAfterFinish() {
        assertEquals(SetupEntryDecision.SHOW, setupEntryDecision(finished = false, started = false, hadLauncherState = false, deferred = false))
        assertEquals(SetupEntryDecision.SHOW, setupEntryDecision(finished = false, started = true, hadLauncherState = true, deferred = false))
        assertEquals(SetupEntryDecision.ALREADY_FINISHED, setupEntryDecision(finished = true, started = true, hadLauncherState = true, deferred = true))
    }

    @Test fun onlyAnUnfinishedWelcomeThatWasShowingIsDeferredByHome() {
        assertEquals(true, setupDeferredByHome(welcomeShowing = true, finished = false))
        // Home with no Welcome up, or a replay from Settings on a phone that already finished setup, leaves the saved state alone.
        assertEquals(false, setupDeferredByHome(welcomeShowing = false, finished = false))
        assertEquals(false, setupDeferredByHome(welcomeShowing = true, finished = true))
    }
}
