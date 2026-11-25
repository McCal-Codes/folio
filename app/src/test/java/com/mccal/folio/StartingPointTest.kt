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

    @Test fun `the Android look turns the iPhone-style touches off`() {
        val s = LauncherState().withStartingPoint(StartingPoint.ANDROID)
        assertEquals(false, s.island)
        assertEquals(false, s.folioPanels)
        assertEquals("NOTIFICATIONS", s.swipeDownHome)
        assertEquals(HomeStrip.DOTS, s.homeStrip)
        assertEquals(StatusGlyph.ICONS, s.statusStyle.glyph)
        assertEquals(false, s.statusStyle.colorfulBattery)
        assertEquals(false, s.statusStyle.background)
        assertEquals(0f, s.statusStyle.railGlass)
        assertEquals(0f, s.glassOutline)
        assertEquals(BadgeColor.APP, s.badgeColor)
        assertEquals(FolderBackground.SOLID, s.folderBackground)
        assertEquals("DISCOVER", s.leftPage)
    }

    @Test fun `both looks keep the fold effect`() {
        val on = LauncherState(installedTweaks = setOf(DUET_ID), foldEffect = true)
        for (point in StartingPoint.entries) {
            val s = on.withStartingPoint(point)
            assertEquals(true, s.foldEffect)
            assertEquals(setOf(DUET_ID), s.installedTweaks)
            assertEquals(on.duet, s.duet)
            assertEquals(on.featureScopes, s.featureScopes)
        }
        // Nor does either look turn it back on for someone who switched it off.
        assertEquals(false, LauncherState(foldEffect = false).withStartingPoint(StartingPoint.ANDROID).foldEffect)
    }

    @Test fun `Folio look puts back the person's own look settings`() {
        val mine = LauncherState(homeStrip = HomeStrip.NOTHING, badgeColor = BadgeColor.PURPLE, glassOutline = .4f, leftPage = "NONE",
            statusStyle = StatusStyle(glyph = StatusGlyph.RINGS, railGlass = .5f))
        val saved = LookSettings.of(mine)
        val back = mine.withStartingPoint(StartingPoint.ANDROID).withStartingPoint(StartingPoint.IPHONE, saved)
        assertEquals(saved, LookSettings.of(back))
        assertEquals(ExperienceProfile.FOLIO, back.profile())
        assertEquals(StartingPoint.IPHONE, back.startingPoint())
    }

    @Test fun `without a saved copy Folio look brings Folio's own values`() {
        val back = LauncherState().withStartingPoint(StartingPoint.ANDROID).withStartingPoint(StartingPoint.IPHONE, null)
        assertEquals(LookSettings.FOLIO, LookSettings.of(back))
        assertEquals(LauncherState(), back)
    }

    @Test fun `a look setting changed while on the Android look stays the person's`() {
        val tuned = LauncherState().withStartingPoint(StartingPoint.ANDROID).copy(homeStrip = HomeStrip.SEARCH, badgeColor = BadgeColor.GREEN)
        val back = tuned.withStartingPoint(StartingPoint.IPHONE, LookSettings.of(LauncherState(homeStrip = HomeStrip.NOTHING)))
        assertEquals(HomeStrip.SEARCH, back.homeStrip)
        assertEquals(BadgeColor.GREEN, back.badgeColor)
        assertEquals(FolderBackground.GLASS, back.folderBackground)
        // Still the Android look until the person picks Folio: a tweak doesn't take the check away.
        assertEquals(StartingPoint.ANDROID, tuned.startingPoint())
    }

    @Test fun `picking Folio look while already on it keeps hand-set look settings`() {
        val mine = LauncherState(homeStrip = HomeStrip.DOTS, badgeColor = BadgeColor.APP)
        assertEquals(mine, mine.withStartingPoint(StartingPoint.IPHONE, null))
    }

    @Test fun `the Android look leaves Home and the rest of Appearance alone`() {
        val base = LauncherState(labels = false, iconStyle = IconStyle.DARK, widgetGlass = .6f, statusStyle = StatusStyle(showDate = false))
        val s = base.withStartingPoint(StartingPoint.ANDROID)
        assertEquals(base.layout, s.layout)
        assertEquals(IconStyle.DARK, s.iconStyle)
        assertEquals(.6f, s.widgetGlass)
        assertEquals(false, s.statusStyle.showDate)
        assertEquals(false, s.labels)
    }

    @Test fun `a saved copy reads back exactly and a damaged one is not used`() {
        val saved = LookSettings.of(LauncherState(homeStrip = HomeStrip.NOTHING, leftPage = "NONE"))
        assertEquals(saved, LookSettings.parse(saved.toJson().toString()))
        assertNull(LookSettings.parse(saved.toJson().apply { remove("leftPage") }.toString()))
        assertNull(LookSettings.parse("not json"))
        assertNull(LookSettings.parse(null))
    }

    @Test fun `navigation tips follow how Android is set up`() {
        assertEquals(NavigationTip.GESTURES, NavigationTip.of(true))
        assertEquals(NavigationTip.BUTTONS, NavigationTip.of(false))
    }
}
