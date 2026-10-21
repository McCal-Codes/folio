package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandStatusTest {
    private val ok = IslandStatusInputs(safeMode = false, everywhere = true, gesturesService = true, notificationAccess = true,
        doNotDisturb = false, avoidDouble = false)

    @Test fun everythingOnAndNothingHoldingItBackIsReady() {
        assertEquals(IslandStatus(IslandReason.READY), islandStatus(ok))
    }

    @Test fun safeModeComesFirstOfAll() {
        val worst = ok.copy(safeMode = true, everywhere = false, gesturesService = false, notificationAccess = false, doNotDisturb = true)
        assertEquals(IslandReason.SAFE_MODE, islandStatus(worst).headline)
    }

    @Test fun theSwitchThenTheServiceThenTheAccessAreCheckedInThatOrder() {
        assertEquals(IslandReason.IN_EVERY_APP_OFF, islandStatus(ok.copy(everywhere = false, gesturesService = false)).headline)
        assertEquals(IslandReason.GESTURES_SERVICE_OFF, islandStatus(ok.copy(gesturesService = false, notificationAccess = false)).headline)
        assertEquals(IslandReason.NOTIFICATION_ACCESS_OFF, islandStatus(ok.copy(notificationAccess = false, doNotDisturb = true)).headline)
    }

    @Test fun doNotDisturbAloneQuietsMessagePopUps() {
        assertEquals(IslandStatus(IslandReason.DO_NOT_DISTURB), islandStatus(ok.copy(doNotDisturb = true)))
    }

    @Test fun avoidDoubleAloneSaysAndroidIsShowingThem() {
        assertEquals(IslandStatus(IslandReason.ANDROID_POPUP), islandStatus(ok.copy(avoidDouble = true)))
    }

    @Test fun bothAtOnceKeepsDoNotDisturbAsTheHeadlineAndNotesTheOther() {
        assertEquals(IslandStatus(IslandReason.DO_NOT_DISTURB, listOf(IslandReason.ANDROID_POPUP)), islandStatus(ok.copy(doNotDisturb = true, avoidDouble = true)))
    }

    @Test fun onlyReasonsThePersonCanActOnGetAButton() {
        assertTrue(IslandReason.DO_NOT_DISTURB.hasAction() && IslandReason.ANDROID_POPUP.hasAction())
        assertTrue(IslandReason.GESTURES_SERVICE_OFF.hasAction() && IslandReason.NOTIFICATION_ACCESS_OFF.hasAction())
        assertFalse(IslandReason.READY.hasAction() || IslandReason.SAFE_MODE.hasAction() || IslandReason.IN_EVERY_APP_OFF.hasAction())
    }

    @Test fun levelsMatchHowSeriousEachReasonIs() {
        assertEquals(IslandLevel.GOOD, IslandReason.READY.level())
        assertEquals(IslandLevel.WARNING, IslandReason.DO_NOT_DISTURB.level())
        assertEquals(IslandLevel.PROBLEM, IslandReason.GESTURES_SERVICE_OFF.level())
        assertEquals(IslandLevel.INFO, IslandReason.ANDROID_POPUP.level())
    }

    @Test fun androidTakingOverIsOnlyReportedWhenSomethingWouldHaveShown() {
        // Messages off and other alerts off (the defaults): Don't double up has nothing to take over.
        assertEquals(IslandStatus(IslandReason.READY_LIVE_ONLY), islandStatus(ok.copy(avoidDouble = true, popUpsOn = false)))
        // Do Not Disturb has no message pop-ups to limit when they are off, so the card must not mention it.
        assertEquals(IslandStatus(IslandReason.READY_LIVE_ONLY), islandStatus(ok.copy(doNotDisturb = true, avoidDouble = true, popUpsOn = false)))
        assertEquals(IslandStatus(IslandReason.ANDROID_POPUP), islandStatus(ok.copy(avoidDouble = true, popUpsOn = true)))
    }

    @Test fun readyOnlyMentionsMessagePopUpsWhenTheyAreOn() {
        assertEquals(IslandStatus(IslandReason.READY), islandStatus(ok.copy(popUpsOn = true)))
        assertEquals(IslandStatus(IslandReason.READY_LIVE_ONLY), islandStatus(ok.copy(popUpsOn = false)))
        assertEquals(IslandLevel.GOOD, IslandReason.READY_LIVE_ONLY.level())
        assertFalse(IslandReason.READY_LIVE_ONLY.hasAction())
    }

    @Test fun musicAndLiveProgressOffSaysNothingWillShow() {
        assertEquals(IslandStatus(IslandReason.ISLAND_OFF), islandStatus(ok.copy(islandOn = false)))
        assertEquals(IslandStatus(IslandReason.ISLAND_OFF), islandStatus(ok.copy(islandOn = false, doNotDisturb = true, avoidDouble = true)))
        // The switch for showing over other apps still comes first.
        assertEquals(IslandReason.IN_EVERY_APP_OFF, islandStatus(ok.copy(islandOn = false, everywhere = false)).headline)
        assertEquals(IslandLevel.OFF, IslandReason.ISLAND_OFF.level())
        assertFalse(IslandReason.ISLAND_OFF.hasAction())
    }
}
