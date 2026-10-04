package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartingPointTest {
    @Test fun `iPhone is Folio's own panels with the usual icons`() {
        val s = LauncherState().withStartingPoint(StartingPoint.IPHONE)
        assertEquals(ExperienceProfile.FOLIO, s.profile())
        assertEquals(IconShape.DEFAULT, s.iconShape)
        assertEquals(StartingPoint.IPHONE, s.startingPoint())
    }

    @Test fun `Android is Android's own panels with round icons`() {
        val s = LauncherState().withStartingPoint(StartingPoint.ANDROID)
        assertEquals(ExperienceProfile.ANDROID_STYLE, s.profile())
        assertEquals(IconShape.CIRCLE, s.iconShape)
        assertEquals(StartingPoint.ANDROID, s.startingPoint())
    }

    @Test fun `switching back and forth leaves nothing behind`() {
        val base = LauncherState()
        val there = base.withStartingPoint(StartingPoint.ANDROID).withStartingPoint(StartingPoint.IPHONE)
        assertEquals(base.withStartingPoint(StartingPoint.IPHONE), there)
    }

    @Test fun `a starting point changes only its own settings`() {
        val base = LauncherState(labels = false, iconShape = IconShape.SQUIRCLE)
        val s = base.withStartingPoint(StartingPoint.ANDROID)
        assertEquals(false, s.labels)
        assertEquals(base.dock, s.dock)
        assertEquals(base.homeAppRows, s.homeAppRows)
    }

    @Test fun `settings changed afterwards are the person's own mix`() {
        val s = LauncherState().withStartingPoint(StartingPoint.ANDROID).copy(iconShape = IconShape.SQUIRCLE)
        assertNull(s.startingPoint())
    }

    @Test fun `navigation tips follow how Android is set up`() {
        assertEquals(NavigationTip.GESTURES, NavigationTip.of(true))
        assertEquals(NavigationTip.BUTTONS, NavigationTip.of(false))
    }
}
