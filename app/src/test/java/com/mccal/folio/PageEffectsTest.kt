package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Page Effects: the maths each effect turns a page position into, that None is the default everywhere, and that the
 * feature is switched off at its entry point rather than only hidden ([PageEffect], [FeatureGate.PAGE_EFFECTS]).
 */
class PageEffectsTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
    private fun decode(json: String) = decodeLauncherState(json, legacyRaw = null)
    private fun source(name: String) = java.io.File(root, "app/src/main/java/com/mccal/folio/$name").readText()

    private val empty get() = JSONArray().apply { repeat(HOME_CELLS) { put(JSONObject.NULL) } }
    private fun saved(vararg extra: Pair<String, Any>) = JSONObject().put("schema", STATE_SCHEMA)
        .put("pinned", JSONArray()).put("homeSlots", empty).put("leadingSlots", empty)
        .put("dock", JSONArray()).put("widgets", JSONArray()).put("folders", JSONArray())
        .apply { extra.forEach { (key, value) -> put(key, value) } }

    @Test fun `a new install swipes flat`() {
        assertEquals(PageEffect.NONE, LauncherState().pageEffect)
        assertEquals(PageEffect.NONE, decode("{}").pageEffect)
    }

    @Test fun `a save from before Page Effects, and a name Folio does not know, both read as None`() {
        assertEquals(PageEffect.NONE, decode(saved().toString()).pageEffect)
        assertEquals(PageEffect.NONE, decode(saved("pageEffect" to "BARREL").toString()).pageEffect)
        assertEquals(PageEffect.NONE, decode(saved("pageEffect" to "").toString()).pageEffect)
    }

    @Test fun `a chosen effect survives a restart`() {
        assertEquals(PageEffect.CUBE, decode(saved("pageEffect" to "CUBE").toString()).pageEffect)
        assertEquals(PageEffect.CAROUSEL, decode(saved("pageEffect" to "CAROUSEL").toString()).pageEffect)
        assertTrue("the choice has to be written back out too", "\"pageEffect\", s.pageEffect.name" in source("LauncherModel.kt"))
    }

    @Test fun `a settled page is untouched by every effect, so Home is the same with the swipe at rest`() {
        for (effect in PageEffect.entries) {
            assertEquals("${effect.name} turns a settled page", 0f, effect.rotationY(0f), 0f)
            assertEquals("${effect.name} resizes a settled page", 1f, effect.scale(0f), 0f)
        }
    }

    @Test fun `None does nothing at any position`() {
        for (position in listOf(-2f, -1f, -.5f, 0f, .37f, 1f, 2f)) {
            assertEquals(0f, PageEffect.NONE.rotationY(position), 0f)
            assertEquals(1f, PageEffect.NONE.scale(position), 0f)
            assertEquals(.5f, PageEffect.NONE.pivotX(position), 0f)
        }
    }

    @Test fun `the cube pivots on the edge the two pages share`() {
        // A page still to the right turns about its left edge, one already passed about its right edge, so the edges
        // that meet in the middle of the screen stay met and the two pages never cross.
        assertEquals(0f, PageEffect.CUBE.pivotX(.4f), 0f)
        assertEquals(0f, PageEffect.CUBE.pivotX(1f), 0f)
        assertEquals(1f, PageEffect.CUBE.pivotX(-.4f), 0f)
        assertEquals(1f, PageEffect.CUBE.pivotX(-1f), 0f)
    }

    @Test fun `the cube is a quarter turn at one page out, and turns the free edge away`() {
        assertEquals(-90f, PageEffect.CUBE.rotationY(1f), 0f)
        assertEquals(90f, PageEffect.CUBE.rotationY(-1f), 0f)
        // Half way through a swipe, half way through the turn.
        assertEquals(-45f, PageEffect.CUBE.rotationY(.5f), 0f)
        // Negative degrees push the free edge away from the viewer: that is what makes it a solid rather than a box
        // seen from inside. The page to the right turns about its left edge, so its right half is the half that goes.
        assertTrue(PageEffect.CUBE.rotationY(.5f) < 0f)
    }

    @Test fun `no effect ever mirrors a page that is kept composed further out`() {
        // Adjacent pages stay attached on purpose (PRF-9), so a page can sit two positions out. Past a quarter turn
        // a page would come back into view mirrored, which is why every effect clamps.
        for (effect in PageEffect.entries) for (position in listOf(-3f, -2f, -1.2f, 1.2f, 2f, 3f)) {
            assertTrue("${effect.name} at $position turns past edge-on", abs(effect.rotationY(position)) <= 90f)
            assertTrue("${effect.name} at $position turns a page inside out", effect.scale(position) > 0f)
        }
        assertEquals(PageEffect.CUBE.rotationY(1f), PageEffect.CUBE.rotationY(4f), 0f)
        assertEquals(PageEffect.CAROUSEL.scale(1f), PageEffect.CAROUSEL.scale(4f), 0f)
    }

    @Test fun `the carousel turns about the middle, and steps back the same amount either way`() {
        assertEquals(.5f, PageEffect.CAROUSEL.pivotX(-.6f), 0f)
        assertEquals(.5f, PageEffect.CAROUSEL.pivotX(.6f), 0f)
        assertEquals(PageEffect.CAROUSEL.scale(-.6f), PageEffect.CAROUSEL.scale(.6f), 0f)
        assertEquals(.8f, PageEffect.CAROUSEL.scale(1f), 1e-6f)
        // It shrinks the whole way out rather than jumping at the end, and stays readable: a gentler turn than the cube.
        assertTrue(PageEffect.CAROUSEL.scale(.25f) > PageEffect.CAROUSEL.scale(.75f))
        assertTrue(abs(PageEffect.CAROUSEL.rotationY(1f)) < abs(PageEffect.CUBE.rotationY(1f)))
    }

    @Test fun `every turning effect keeps the camera far enough away to avoid a sheared rectangle`() {
        // Android's own advice for a rotation about Y: keep the camera further off than the page is wide. Under about
        // 1.5 widths a quarter turn tears, over about 4 the perspective flattens out and the effect looks like a shear.
        for (effect in PageEffect.entries - PageEffect.NONE) {
            assertTrue("${effect.name} camera at ${effect.cameraWidths} widths", effect.cameraWidths in 1.5f..4f)
        }
        // The cube turns all the way to edge-on, so it needs the stronger perspective of the two.
        assertTrue(PageEffect.CUBE.cameraWidths < PageEffect.CAROUSEL.cameraWidths)
    }

    @Test fun `Safe Mode swipes flat, whatever was chosen`() {
        val chosen = LauncherState(pageEffect = PageEffect.CUBE)
        assertEquals("Safe Mode is off: the choice stands", PageEffect.CUBE, SafeMode.effective(chosen).pageEffect)
        // Safe Mode only turns itself on after two quick crashes, so what it does is read from the copy it makes:
        // a phone that Folio has just crashed on twice should not be turning pages in 3D while you fix it.
        assertTrue("Safe Mode should turn Page Effects off", "pageEffect = PageEffect.NONE" in source("CrashLog.kt"))
    }

    @Test fun `Home asks the gate and Reduce Motion before any of this runs`() {
        val screen = source("LauncherScreen.kt")
        // REL-4a: the gate is asked where the work starts, not only where the setting is drawn.
        assertTrue("the effect itself should be gated", "FeatureGate.PAGE_EFFECTS.isOpen" in screen)
        // DYN-11 / A11Y-14: spatial motion goes away with Reduce Motion on, and it is read through the composition local.
        assertTrue("Reduce Motion should switch it off", "!LocalReduceMotion.current" in screen)
        assertTrue("with the gate shut or Reduce Motion on it has to fall back to None", "else PageEffect.NONE" in screen)
        // A drop lands by coordinates, so nothing may transform a page while an icon or widget is being moved.
        assertTrue("moving an icon has to switch it off", "!drag.active && !resize.active" in screen)
        assertTrue("the transform belongs on Home's pages", ".pageEffect(pageEffect, nativePager, physicalPage)" in screen)

        // Turning it off is not a hidden identity transform: with None there is no layer and no pager read at all.
        val effects = source("PageEffects.kt")
        assertTrue("None should add no layer", "if (effect == PageEffect.NONE) this else graphicsLayer" in effects)
        // PRF-7 / DYN-16: the pager is read in the layer block, so a swipe recomposes nothing.
        assertTrue("the position has to be read inside the layer block", "pager.pagePosition(page)" in effects)
        assertTrue("no alpha: it would cost an offscreen buffer per page", "alpha" !in effects.substringAfter("internal fun Modifier.pageEffect"))
    }

    @Test fun `every page in the pager turns, including the two either side of Home`() {
        // A cube whose neighbour slides in flat is not a cube, and the swipe from the last Home page to the App
        // Library is the commonest one there is. So all three branches of the pager's page lambda carry the effect.
        val screen = source("LauncherScreen.kt")
        val calls = Regex("""\.pageEffect\(pageEffect, nativePager, physicalPage\)""").findAll(screen).count()
        assertEquals("Discover, the App Library and Home should each turn", 3, calls)
        assertTrue("the page on Home's left should turn",
            ".fillMaxSize().pageEffect(pageEffect, nativePager, physicalPage))" in screen)
        // Outside the library's own layer, so the page turns as a whole and the two stack rather than fight.
        assertTrue("the library's effect belongs outside its libraryBack layer",
            screen.substringAfter("onActions = { overlays.menu = it.id }, modifier = Modifier.fillMaxSize()")
                .substringBefore("libraryBack").contains(".pageEffect("))
        // The layer wraps the pane, not the full-width Row: a cube pivoting on the Row would hinge on the window
        // edge whenever the grid is centred and narrower than the window.
        assertTrue("Home's effect should wrap the pane, not the Row",
            "Box(Modifier.pageEffect(pageEffect, nativePager, physicalPage))" in screen)
        assertTrue("the Row should carry no layer of its own",
            """Row(Modifier.fillMaxSize().testTag("home-surface")""" in screen)
    }

    @Test fun `the setting is only offered where the gate is open, and None is one of the choices`() {
        val sheet = source("CustomizationSheet.kt")
        assertTrue("Settings should ask the gate", "FeatureGate.PAGE_EFFECTS.isOpen(gestureContext)" in sheet)
        assertTrue("the row should be behind the gate", "if (pageEffectsOpen) {" in sheet)
        assertTrue(PageEffect.NONE in PageEffect.entries)
        // Every choice needs a name people can read, and no two effects share one.
        val labels = PageEffect.entries.map { it.label }
        assertEquals("two effects share a label", labels.size, labels.toSet().size)
        for (effect in PageEffect.entries) assertNotEquals("${effect.name} has no label", 0, effect.label)
    }
}
