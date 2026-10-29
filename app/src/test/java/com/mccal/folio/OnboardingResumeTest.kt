package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** Setup resumes where it was left, including after making Folio the Home app takes the Home page out of the list. */
class OnboardingResumeTest {
    @Test fun `a saved page that is still there is where setup resumes`() {
        assertEquals(2, onboardingResumeIndex("apps", listOf("welcome", "feel", "apps", "home", "done")))
        assertEquals(3, onboardingResumeIndex("home", listOf("welcome", "feel", "apps", "home", "done")))
    }

    @Test fun `after Folio becomes the Home app setup carries on at the page after the Home page`() {
        assertEquals(3, onboardingResumeIndex("home", listOf("welcome", "feel", "apps", "adv", "done")))
        assertEquals(2, onboardingResumeIndex("home", listOf("welcome", "feel", "done")))
        assertEquals(3, onboardingResumeIndex("home", listOf("welcome", "feel", "nav", "done")))
    }

    @Test fun `with nothing saved, or a page that is gone for another reason, setup starts at the first page`() {
        assertEquals(0, onboardingResumeIndex(null, listOf("welcome", "feel", "done")))
        assertEquals(0, onboardingResumeIndex("apps", listOf("welcome", "feel", "done"))) // the list may not have loaded yet
        assertEquals(0, onboardingResumeIndex("somethingOlder", listOf("welcome", "feel", "done")))
    }

    @Test fun `if the Home page was the last one left the last page is used`() {
        assertEquals(0, onboardingResumeIndex("home", listOf("welcome")))
    }
}
