package com.mccal.folio

import com.mccal.folio.duet.DuetOptions
import com.mccal.folio.duet.DuetStyles
import com.mccal.folio.market.TweakOptions
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Duet, the fold animation as a tweak: a save from before Duet keeps the fold it had until someone picks another look. */
class DuetTest {
    @Test fun `the default is iPhone Duo, with Apple's numbers`() {
        assertEquals(DuetStyles.IPHONE, DuetOptions().resolved())
        assertEquals(DuetStyles.IPHONE, LauncherState().duet.resolved())
        assertEquals("a save with no style gets the default", DuetStyles.IPHONE, DuetOptions.fromJson(JSONObject("{}")).look)
        with(DuetStyles.IPHONE) {
            // chuspeeism/iphone-duo: 72 px at the edge (Folio's 4.5% of the width), 2x shade, straight-on projection, full
            // tilt, and a hard edge at the hinge because the camera half never moves.
            assertEquals(listOf(1f, 1f, 1f, 0f, 0f), listOf(frost, darkening, perspective, blurFloor, softEdge))
            assertTrue(classic)
            assertEquals("starts where the Fold8 lights its inner panel, ~125 degrees", .61f, startAt, 1e-6f)
        }
    }

    @Test fun `a save from before Duet keeps the Duo fold, and a new save keeps its choice`() {
        val before = JSONObject().put("foldEffect", true)
        assertEquals("duo", decodeLauncherState(before.toString(), legacyRaw = null).duet.style)
        val after = JSONObject().put("foldEffect", true).put("duet", DuetOptions(style = "deep").toJson())
        assertEquals("deep", decodeLauncherState(after.toString(), legacyRaw = null).duet.style)
        // Nothing saved at all is a new install, which starts on the default look.
        assertEquals(DuetStyles.IPHONE.id, decodeLauncherState("{}", legacyRaw = null).duet.style)
    }

    @Test fun `Duo is still Folio's earlier fold, number for number`() {
        with(DuetStyles.DUO) { assertEquals(listOf(1f, 1f, 0f, 0f, 0f, 1f), listOf(frost, darkening, perspective, blurFloor, softEdge, startAt)) }
        assertTrue("Duo keeps its hard edge at the fold", !DuetStyles.DUO.classic)
    }

    @Test fun `sliders scale the style, so switching style keeps the adjustment`() {
        val mine = DuetOptions(style = "deep", frost = .5f, perspective = 5f)
        assertEquals(.5f, mine.resolved().frost)
        assertEquals("perspective can't pass a full tilt", 1f, mine.resolved().perspective)
        assertEquals(.5f * DuetStyles.SUBTLE.frost, mine.copy(style = "subtle").resolved().frost)
    }

    @Test fun `saved options survive a round trip, and damaged ones fall back instead of throwing`() {
        val mine = DuetOptions(style = "minimal", frost = .25f, darkening = 1.5f, perspective = .5f)
        assertEquals(mine, DuetOptions.fromJson(mine.toJson()))
        assertEquals(DuetOptions(), DuetOptions.fromJson(null))
        val damaged = DuetOptions.fromJson(JSONObject("""{"style":"nope","frost":99,"darkening":"x","perspective":-4}"""))
        assertEquals(DuetOptions(style = "iphone", frost = 2f, darkening = 1f, perspective = 0f), damaged)
    }

    @Test fun `the Market, the picker and the renderer list the same styles`() {
        val market = (TweakOptions.DUET.getValue("style") as TweakOptions.Choice).values
        assertEquals(DuetStyles.all.map { it.id }, market)
        for ((key, app) in mapOf("frost" to DuetOptions.FROST, "darkening" to DuetOptions.DARKENING, "perspective" to DuetOptions.PERSPECTIVE)) {
            val range = (TweakOptions.DUET.getValue(key) as TweakOptions.Number).range
            assertEquals(key, range.start, app.start.toDouble(), 1e-6)
            assertEquals(key, range.endInclusive, app.endInclusive.toDouble(), 1e-6)
        }
    }

    @Test fun `Duet is a tweak, and a save from before it keeps the animation exactly as it was`() {
        val duet = TweakFeatures.single { it.id == DUET_ID }
        assertTrue(duet.default)
        assertTrue("the fold animation was on by default, so it still is", DUET_ID in decode("{}"))
        assertTrue(DUET_ID in decode("""{"foldEffect":true,"installedTweaks":["appPanels"]}"""))
        assertTrue("off stays off, and not installed", DUET_ID !in decode("""{"foldEffect":false,"installedTweaks":[]}"""))
    }

    @Test fun `once Duet has been saved, removing it sticks`() {
        assertTrue(DUET_ID !in decode("""{"foldEffect":true,"installedTweaks":[],"duet":{"style":"duo"}}"""))
    }

    @Test fun `Use On turns Duet off for one screen, and Back from Fold & Displays returns there`() {
        val scopes = FeatureScopes.set(emptyMap(), DUET_ID, FolioScreen.COVER, ScopeValue.OFF)
        assertTrue(!FeatureScopes.on(scopes, DUET_ID, true, FolioScreen.COVER))
        assertTrue(FeatureScopes.on(scopes, DUET_ID, true, FolioScreen.INNER))
        assertEquals(CustomizationPage.FOLD, CustomizationPage.FOLD_TWEAK.parent)
        val main = java.io.File("src/main/java/com/mccal/folio/MainActivity.kt").readText()
        assertTrue("the fold animation should ask Use On", "FeatureScopes.on(state.featureScopes, DUET_ID" in main)
    }

    @Test fun `Plays When picks which way the fold animates, and survives a save`() {
        val open = com.mccal.folio.duet.DuetDirection.OPENING
        assertTrue(open.allows(true)); assertTrue(!open.allows(false))
        assertTrue(com.mccal.folio.duet.DuetDirection.CLOSING.allows(false))
        assertTrue(com.mccal.folio.duet.DuetDirection.BOTH.allows(true) && com.mccal.folio.duet.DuetDirection.BOTH.allows(false))
        val mine = DuetOptions(direction = "closing")
        assertEquals(mine, DuetOptions.fromJson(mine.toJson()))
        assertEquals("both", DuetOptions.fromJson(JSONObject("""{"direction":"sideways"}""")).direction)
        val market = (TweakOptions.DUET.getValue("direction") as TweakOptions.Choice).values
        assertEquals(com.mccal.folio.duet.DuetDirection.entries.map { it.id }, market)
    }

    @Test fun `Classic Glass is Duo Fold Live's look, and its tilt follows the Tilt slider`() {
        val classic = DuetOptions(style = "classic", perspective = .5f).resolved()
        assertTrue(classic.classic)
        assertEquals(.5f, classic.perspective)
        assertEquals(1.25f, classic.frost)
    }

    private fun decode(raw: String) = decodeLauncherState(raw, legacyRaw = null).installedTweaks
}
