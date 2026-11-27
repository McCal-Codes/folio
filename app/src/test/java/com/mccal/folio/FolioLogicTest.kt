package com.mccal.folio

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightMathTest {
    @Test fun precedenceAndParentheses() {
        assertEquals("14", evaluateMath("2+3*4"))
        assertEquals("20", evaluateMath("(2+3)*4"))
        assertEquals("42", evaluateMath("12*(3+4)/2"))
    }

    @Test fun powerIsRightAssociativeAndUnaryMinusWorks() {
        assertEquals("512", evaluateMath("2^3^2"))
        assertEquals("-6", evaluateMath("-2*3"))
    }

    @Test fun decimalsUseDotsRegardlessOfLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("0.5", evaluateMath("1/2"))
        } finally { java.util.Locale.setDefault(previous) }
    }

    @Test fun phoneNumbersDatesAndWordsAreNotMath() {
        assertNull(evaluateMath("555-1234"))
        assertNull(evaluateMath("2026-09-12"))
        assertNull(evaluateMath("gmail"))
        assertNull(evaluateMath("12"))
        assertNull(evaluateMath("1/0"))
        assertNull(evaluateMath("(2+3"))
    }
}

class SpotlightRankingTest {
    private val labels = listOf("Google Maps", "Maps", "Gmail", "Messages", "Amazon Music", "Samsung Notes")
    private fun rank(q: String) = rankByLabel(labels, q) { it }

    @Test fun exactThenPrefixThenWordStartThenSubstring() {
        assertEquals("Maps", rank("maps").first())               // exact
        assertEquals(listOf("Maps", "Google Maps"), rank("maps"))  // then word start
        assertEquals("Gmail", rank("gma").first())               // prefix
        assertEquals(listOf("Amazon Music"), rank("azon"))         // substring
    }

    @Test fun initialsMatch() {
        assertEquals("Google Maps", rank("gm").first { it == "Google Maps" })
        assertTrue(rank("sn").contains("Samsung Notes"))
    }

    @Test fun shorterLabelWinsTies() {
        assertEquals(listOf("Maps", "Messages"), rankByLabel(listOf("Messages", "Maps"), "m") { it })
    }

    @Test fun fuzzyLettersInOrderMatchLast() {
        assertEquals("Samsung Notes", rankByLabel(labels, "smng") { it }.first())
    }

    @Test fun frecencyBreaksTiesWithinATier() {
        val boosted = rankByLabel(listOf("Messages", "Maps"), "m", boost = { if (it == "Messages") 3.0 else 0.0 }) { it }
        assertEquals(listOf("Messages", "Maps"), boosted)
        val plain = rankByLabel(listOf("Messages", "Maps"), "m") { it }
        assertEquals(listOf("Maps", "Messages"), plain)
    }

    @Test fun frecencyHalvesEveryWeek() {
        val week = 7L * 24 * 60 * 60 * 1000
        val now = 10 * week
        val scores = RecentApps.frecencyOf(listOf("a|$now", "a|${now - week}", "b|${now - 2 * week}", "garbage"), now)
        assertEquals(1.5, scores.getValue("a"), 1e-9)
        assertEquals(0.25, scores.getValue("b"), 1e-9)
    }

    @Test fun noMatchGivesNothing() {
        assertTrue(rank("zzz").isEmpty())
    }
}

class ShadeZonesTest {
    @Test fun topLeftTopRightAndLower() {
        assertEquals(ShadePanel.NOTIFICATIONS, shadePanelForStart(10f, 10f, 400f, 120f))
        assertEquals(ShadePanel.QUICK_SETTINGS, shadePanelForStart(200f, 10f, 400f, 120f))
        assertEquals(ShadePanel.SEARCH, shadePanelForStart(10f, 120f, 400f, 120f))
    }
}

class StatusStyleJsonTest {
    @Test fun roundTrip() {
        val style = StatusStyle(showTime = false, showDate = true, showBatteryPercent = false, glyph = StatusGlyph.ICONS,
            colorfulBattery = false, chargingBolt = false, railGlass = .5f, showSilent = false, background = false, spacing = 9f)
        assertEquals(style, StatusStyle.fromJson(style.toJson()))
        assertEquals(StatusStyle(spacing = StatusStyle.COMPACT_SPACING), StatusStyle.fromJson(StatusStyle(spacing = 0f).toJson()))
        for (glyph in StatusGlyph.entries) assertEquals(glyph, StatusStyle.fromJson(StatusStyle(glyph = glyph).toJson()).glyph)
    }

    @Test fun savedSpacingChoicesKeepTheirLook() {
        // Before the Status spacing slider: Standard or Compact.
        assertEquals(StatusStyle.COMPACT_SPACING, StatusStyle.fromJson(org.json.JSONObject().put("compactSpacing", true)).spacing)
        assertEquals(StatusStyle.STANDARD_SPACING, StatusStyle.fromJson(org.json.JSONObject().put("compactSpacing", false)).spacing)
        assertEquals(StatusStyle.STANDARD_SPACING, StatusStyle.fromJson(org.json.JSONObject()).spacing)
        assertEquals(true, StatusStyle(spacing = StatusStyle.COMPACT_SPACING).tight)
        assertEquals(false, StatusStyle().tight)
        assertEquals(16f, StatusStyle.fromJson(org.json.JSONObject().put("spacing", 99.0)).spacing)
    }

    @Test fun olderSavedStylesShowTheSilentIcon() {
        assertEquals(true, StatusStyle.fromJson(org.json.JSONObject().put("glyph", "RING")).showSilent)
    }

    @Test fun unknownGlyphAndOutOfRangeFrostFallBack() {
        val json = org.json.JSONObject().put("glyph", "SPARKLES").put("railGlass", 3.0)
        val style = StatusStyle.fromJson(json)
        assertEquals(StatusGlyph.GAUGE, style.glyph)
        assertEquals(1f, style.railGlass)
        assertEquals(StatusStyle(), StatusStyle.fromJson(null))
    }
}

class PagesTest {
    private fun layout(slots: Int, minPages: Int) =
        HomeLayout(List(slots) { "app$it" }, List(4) { null }, minPages = minPages)

    @Test fun explicitPagesAddToContentPages() {
        assertEquals(1, layout(HOME_CELLS, minPages = 1).pageCount)
        assertEquals(3, layout(HOME_CELLS, minPages = 3).pageCount)
        assertEquals(1, layout(HOME_CELLS, minPages = 3).contentPageCount)
    }

    @Test fun contentBeyondExplicitPagesStillCounts() {
        assertEquals(2, layout(HOME_CELLS + 1, minPages = 1).pageCount)
    }
}

class IslandGeometryTest {
    @Test fun pillIsCenteredOnTheCameraAndCappedByTheNarrowSide() {
        // 1000px wide window, camera 40px wide near the right edge, density 2.
        val g = islandGeometry(intArrayOf(900, 20, 940, 60), 1000, 2f)
        assertEquals(920f, g.centerXPx)
        assertEquals(20f, g.camW)
        // Room on the narrow (right) side: (1000-920)/2 - 12 = 28dp → max width 56dp.
        assertEquals(56f, g.maxW)
        val event = IslandContent.Event(IslandEvent.Silent(true))
        assertEquals(56f, g.widthFor(event))
        // Camera spans 10..30dp: pill starts 8dp from the edge and still wraps the camera.
        assertEquals(8f, g.top)
        assertEquals(34f, g.pillH)
        assertTrue(g.top <= 10f && g.top + g.pillH >= 30f)
    }

    @Test fun noCutoutCentersOnTheWindow() {
        // A camera closer to the top than the usual gap is still covered: the island starts above it.
        val high = islandGeometry(intArrayOf(900, 6, 940, 46), 2000, 2f)
        assertTrue(high.top < 3f)
        assertTrue(high.top + high.pillH >= 23f + 5f - .01f)
        val low = islandGeometry(intArrayOf(900, 40, 940, 80), 2000, 2f)
        assertEquals(15f, low.top)
        val g = islandGeometry(null as IntArray?, 800, 2f)
        assertEquals(400f, g.centerXPx)
        assertEquals(islandWantWidth(IslandContent.Event(IslandEvent.Focus(true)), 0.dp).value, 190f)
    }
}

class CameraAreaTest {
    @Test fun rotatesTheHiddenCamera() {
        // Pure math check without android.graphics.Rect: rotate corner points like CameraArea.rotate does.
        val w = 2448; val h = 1848
        val left = 1823; val top = 18; val right = 1901; val bottom = 96
        // ROTATION_90: (x, y) -> (y, w - x)
        assertEquals(listOf(18, 2448 - 1901, 96, 2448 - 1823), listOf(top, w - right, bottom, w - left))
        // ROTATION_180 keeps the camera's size
        assertEquals(78, (w - left) - (w - right)); assertEquals(78, (h - top) - (h - bottom))
    }
}

class BadgeAccentTest {
    private fun icon(vararg colors: Int) = IntArray(576) { colors[it % colors.size] }

    @Test fun picksTheMainColor() {
        val blue = 0xFF1E88E5.toInt(); val white = 0xFFFFFFFF.toInt()
        val c = dominantAccent(icon(blue, blue, blue, white))!!
        assertEquals(0x1E, c shr 16 and 255); assertEquals(0xE5, c and 255)
    }

    @Test fun grayOrTransparentIconsHaveNoAccent() {
        assertNull(dominantAccent(icon(0xFF808080.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())))
        assertNull(dominantAccent(icon(0x00FF0000)))
    }
}

class WidgetStacksTest {
    @Test fun cardsListPrimaryFirst() {
        assertEquals(listOf(5, 7, 9), WidgetStacks.cards(5, listOf(7, 9)))
        assertEquals(listOf(5), WidgetStacks.cards(5, null))
    }

    @Test fun retainsOnlyStacksWhosePlacementExists() {
        val stacks = mapOf(0 to listOf(11, CLOCK_WIDGET), 3 to listOf(12))
        assertEquals(setOf(11), WidgetStacks.retained(stacks, setOf(0, 1)))
        assertEquals(setOf(11, 12), WidgetStacks.retained(stacks, setOf(0, 3)))
    }

    @Test fun pruneDropsMissingAndEmpty() {
        assertEquals(mapOf(0 to listOf(4)), WidgetStacks.prune(mapOf(0 to listOf(4), 1 to emptyList(), 2 to listOf(8)), setOf(0, 1)))
    }

    @Test fun addIgnoresDuplicates() {
        assertEquals(listOf(7), WidgetStacks.add(listOf(7), 5, 7))
        assertEquals(listOf(7), WidgetStacks.add(null, 5, 7).let { WidgetStacks.add(it, 5, 5) })
    }

    @Test fun removePromotesNextCardWhenPrimaryGoes() {
        assertEquals(5 to listOf(9), WidgetStacks.remove(5, listOf(7, 9), 7))
        assertEquals(7 to listOf(9), WidgetStacks.remove(5, listOf(7, 9), 5))
        assertNull(WidgetStacks.remove(5, emptyList(), 5))
        assertNull(WidgetStacks.remove(5, listOf(7), 42))
    }

    @Test fun showFirstReorders() {
        assertEquals(9 to listOf(5, 7), WidgetStacks.showFirst(5, listOf(7, 9), 9))
        assertNull(WidgetStacks.showFirst(5, listOf(7), 5))
    }
}

class TodayViewTest {
    private fun w(id: Int, size: TodaySize) = TodayWidget(id, size)

    @Test fun rowsPackSmallWidgetsInPairs() {
        val rows = todayRows(listOf(w(1, TodaySize.SMALL), w(2, TodaySize.SMALL), w(3, TodaySize.MEDIUM), w(4, TodaySize.SMALL)))
        assertEquals(listOf(listOf(1, 2), listOf(3), listOf(4)), rows.map { r -> r.map { it.id } })
    }

    @Test fun largeNeverSharesARow() {
        val rows = todayRows(listOf(w(1, TodaySize.SMALL), w(2, TodaySize.LARGE), w(3, TodaySize.SMALL)))
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), rows.map { r -> r.map { it.id } })
    }

    @Test fun sizeFromHomeFootprint() {
        assertEquals(TodaySize.SMALL, TodaySize.forSpan(2, 2))
        assertEquals(TodaySize.MEDIUM, TodaySize.forSpan(4, 2))
        assertEquals(TodaySize.LARGE, TodaySize.forSpan(4, 4))
    }

    @Test fun todaySizeFollowsTheSizePickedInThePicker() {
        val limits = WidgetSpanConstraints(preferred = WidgetSpan(2, 2), minimum = WidgetSpan(2, 2), maximum = WidgetSpan(4, 4),
            canResizeHorizontally = true, canResizeVertically = true)
        assertEquals(TodaySize.MEDIUM, TodaySize.forChoice(WidgetSpan(4, 2), limits, TodaySize.MEDIUM))
        assertEquals(TodaySize.LARGE, TodaySize.forChoice(WidgetSpan(4, 4), limits, TodaySize.MEDIUM))
        assertEquals(TodaySize.SMALL, TodaySize.forChoice(WidgetSpan(2, 2), limits, TodaySize.MEDIUM))
        // Nothing picked: the widget's own preferred size, as before.
        assertEquals(TodaySize.SMALL, TodaySize.forChoice(null, limits, TodaySize.MEDIUM))
        // A size the widget cannot take falls back to its preferred one.
        val fixed = limits.copy(maximum = WidgetSpan(2, 2))
        assertEquals(TodaySize.SMALL, TodaySize.forChoice(WidgetSpan(4, 4), fixed, TodaySize.MEDIUM))
        // Nothing known about the widget: the picked size if there is one, else the fallback.
        assertEquals(TodaySize.LARGE, TodaySize.forChoice(WidgetSpan(4, 4), null, TodaySize.SMALL))
        assertEquals(TodaySize.SMALL, TodaySize.forChoice(null, null, TodaySize.SMALL))
    }

    @Test fun editsKeepOrderAndIgnoreDuplicates() {
        val list = listOf(w(1, TodaySize.SMALL), w(2, TodaySize.SMALL), w(3, TodaySize.MEDIUM))
        assertEquals(list, TodayWidgets.add(list, w(2, TodaySize.LARGE)))
        assertEquals(listOf(2, 1, 3), TodayWidgets.move(list, 1, 1).map { it.id })
        assertEquals(list, TodayWidgets.move(list, 1, -1))
        assertEquals(setOf(1, 2, 3), TodayWidgets.retained(list + w(CLOCK_WIDGET, TodaySize.SMALL)))
    }
}

class SoftBadgeTest {
    private fun hsv(argb: Int): Pair<Float, Float> {
        val r = (argb shr 16 and 255) / 255f; val g = (argb shr 8 and 255) / 255f; val b = (argb and 255) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        return (if (max == 0f) 0f else (max - min) / max) to max
    }

    @Test fun softColorsArePastel() {
        val icon = IntArray(576) { if (it % 3 == 0) 0xFF1E88E5.toInt() else if (it % 3 == 1) 0xFFE53935.toInt() else 0xFFFFFFFF.toInt() }
        val (s, v) = hsv(softBadgeColor(icon)!!)
        assertTrue(s <= .41f); assertTrue(v >= .89f)
    }

    @Test fun softenKeepsHueFamily() {
        val soft = softened(0xFF0000FF.toInt())
        assertTrue((soft and 255) > (soft shr 16 and 255)) // still bluer than red
        assertNull(softBadgeColor(IntArray(576) { 0xFF808080.toInt() }))
    }
}

class PageBoundsTest {
    @Test fun minPageBlocksSwipingPastIt() {
        assertEquals(1f, boundedPagePosition(0.4f, 1, 5, minPage = 1))
        assertEquals(0.4f, boundedPagePosition(0.4f, 1, 5))
        assertEquals(1, releasePage(0.6f, 1, 5, velocity = 9999f, threshold = 250f, minPage = 1))
    }
}

class HomeInkTest {
    @Test fun settingOverridesWallpaper() {
        assertTrue(homeInkFor("AUTO", wallpaperPrefersDarkText = true).dark)
        assertTrue(!homeInkFor("AUTO", wallpaperPrefersDarkText = false).dark)
        assertTrue(homeInkFor("DARK", wallpaperPrefersDarkText = false).dark)
        assertTrue(!homeInkFor("LIGHT", wallpaperPrefersDarkText = true).dark)
    }
}

class FeatureScopesTest {
    @Test fun overrideWinsOtherwiseGlobal() {
        var scopes = emptyMap<String, Map<String, String>>()
        assertTrue(FeatureScopes.on(scopes, "x", global = true, FolioScreen.COVER))
        scopes = FeatureScopes.set(scopes, "x", FolioScreen.COVER, ScopeValue.OFF)
        assertTrue(!FeatureScopes.on(scopes, "x", global = true, FolioScreen.COVER))
        assertTrue(FeatureScopes.on(scopes, "x", global = true, FolioScreen.INNER))
        scopes = FeatureScopes.set(scopes, "x", FolioScreen.INNER, ScopeValue.ON)
        assertTrue(FeatureScopes.on(scopes, "x", global = false, FolioScreen.INNER))
    }

    @Test fun defaultRemovesOverride() {
        val scopes = FeatureScopes.set(FeatureScopes.set(emptyMap(), "x", FolioScreen.COVER, ScopeValue.ON), "x", FolioScreen.COVER, ScopeValue.DEFAULT)
        assertEquals(emptyMap<String, Map<String, String>>(), scopes)
        assertEquals(ScopeValue.DEFAULT, FeatureScopes.value(scopes, "x", FolioScreen.COVER))
    }
}

class SettingsSearchTest {
    @Test fun matchesAllWordsInTitleOrKeywords() {
        assertTrue(settingsMatches("dock size", "Grid, icon size & dock", "layout"))
        assertTrue(settingsMatches("CHATGPT", "Side Key", "assistant chatgpt"))
        assertTrue(!settingsMatches("wallet blur", "Side Key", "wallet"))
        assertTrue(!settingsMatches("   ", "Anything", ""))
    }
}
