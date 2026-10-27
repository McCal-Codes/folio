package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Advanced island ([IslandStyle]): the rules behind color, corner radius, animation speed, haptics and gestures. The
 * defaults are how the island always was, a custom color cannot make its text unreadable, a corner cannot go under the
 * camera hole's curve, no speed setting can make a change read as a flicker, and a card can always be put away.
 */
class IslandStyleTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    // ---- Defaults ---------------------------------------------------------------------------------------------------

    @Test fun `every option starts at what the island always was`() {
        val v = IslandStyle.Values()
        assertTrue(v.isDefault)
        assertEquals(black, v.background)
        assertEquals(IslandCorners.ROUND, v.corners)
        assertEquals(IslandSpeed.STANDARD, v.speed)
        assertEquals(IslandHaptics.STANDARD, v.haptics)
        assertEquals(IslandAction.EXPAND, v.gestures[IslandGesture.TAP])
        assertEquals(IslandAction.MOVE, v.gestures[IslandGesture.HOLD])
        assertEquals(IslandAction.DISMISS, v.gestures[IslandGesture.SWIPE_UP])
        assertEquals(IslandAction.NOTHING, v.gestures[IslandGesture.DOUBLE_TAP])
        assertTrue(IslandStyle.gesturesAreDefault(v.gestures))
    }

    @Test fun `an unknown saved key falls back to the default`() {
        assertEquals(IslandColor.BLACK, IslandColor.of("teal"))
        assertEquals(IslandCorners.ROUND, IslandCorners.of(null))
        assertEquals(IslandSpeed.STANDARD, IslandSpeed.of("warp"))
        assertEquals(IslandHaptics.STANDARD, IslandHaptics.of(""))
        assertNull(IslandAction.of("explode"))
    }

    // ---- Color ------------------------------------------------------------------------------------------------------

    @Test fun `the presets are solid and read well as they are`() {
        for (preset in IslandColor.entries.filter { it != IslandColor.CUSTOM }) {
            val argb = preset.argb!!
            assertEquals("${preset.key} is opaque", 0xFF, argb ushr 24)
            assertEquals("${preset.key} is not moved", argb, IslandStyle.readableBackground(argb))
            assertTrue("${preset.key} text reaches 4.5:1", IslandStyle.secondaryTextContrast(argb) >= FolioIsland.TEXT_MIN_CONTRAST)
            assertEquals(white, IslandStyle.inkFor(argb))
        }
        assertEquals(black, IslandColor.BLACK.argb)
    }

    @Test fun `ink flips by contrast`() {
        assertEquals(white, IslandStyle.inkFor(black))
        assertEquals(black, IslandStyle.inkFor(white))
        assertEquals(black, IslandStyle.inkFor(0xFFF2E6A0.toInt()))
        assertEquals(white, IslandStyle.inkFor(0xFF1D2B53.toInt()))
        assertEquals(21.0, IslandStyle.contrast(black, white), 0.001)
    }

    @Test fun `a light custom color keeps its look and takes black ink`() {
        val light = 0xFFF2E6A0.toInt()
        assertEquals(light, IslandStyle.readableBackground(light))
        assertTrue(IslandStyle.secondaryTextContrast(light) >= FolioIsland.TEXT_MIN_CONTRAST)
    }

    @Test fun `a mid-tone color that cannot be read is moved until it can`() {
        val gray = 0xFF787878.toInt()
        assertTrue("the gray fails to start", IslandStyle.secondaryTextContrast(gray) < FolioIsland.TEXT_MIN_CONTRAST)
        val fixed = IslandStyle.readableBackground(gray)
        assertNotEquals(gray, fixed)
        assertTrue(IslandStyle.secondaryTextContrast(fixed) >= FolioIsland.TEXT_MIN_CONTRAST)
        assertEquals(0xFF, fixed ushr 24)
        // Moved toward the end its ink comes from (black ink on this gray, so lighter), and no further than it has to be.
        assertEquals(black, IslandStyle.inkFor(gray))
        assertTrue(IslandStyle.luminance(fixed) > IslandStyle.luminance(gray))
        val stepBack = IslandStyle.readableBackground(fixed)
        assertEquals("moving it again changes nothing", fixed, stepBack)
    }

    @Test fun `every color ends readable and opaque`() {
        for (r in 0..255 step 51) for (g in 0..255 step 51) for (b in 0..255 step 51) {
            val argb = (0x80 shl 24) or (r shl 16) or (g shl 8) or b
            val out = IslandStyle.readableBackground(argb)
            assertEquals(0xFF, out ushr 24)
            assertTrue("#%02X%02X%02X".format(r, g, b), IslandStyle.secondaryTextContrast(out) >= FolioIsland.TEXT_MIN_CONTRAST)
        }
    }

    @Test fun `a custom color is drawn readable and says when it was moved`() {
        val moved = IslandStyle.Values(color = IslandColor.CUSTOM, custom = 0xFF787878.toInt())
        assertTrue(moved.nudged)
        assertNotEquals(0xFF787878.toInt(), moved.background)
        val fine = IslandStyle.Values(color = IslandColor.CUSTOM, custom = 0xFF1D2B53.toInt())
        assertFalse(fine.nudged)
        assertEquals(0xFF1D2B53.toInt(), fine.background)
        // Custom with nothing typed yet is the default black.
        assertEquals(black, IslandStyle.Values(color = IslandColor.CUSTOM).background)
    }

    @Test fun `hex codes parse to opaque colors`() {
        assertEquals(0xFF1D2B53.toInt(), IslandStyle.parseHex("1D2B53"))
        assertEquals(0xFF1D2B53.toInt(), IslandStyle.parseHex(" #1d2b53 "))
        assertEquals(0xFFAABBCC.toInt(), IslandStyle.parseHex("#ABC"))
        assertNull(IslandStyle.parseHex(""))
        assertNull(IslandStyle.parseHex("12345"))
        assertNull(IslandStyle.parseHex("GGGGGG"))
        assertNull(IslandStyle.parseHex("#1D2B53FF"))
        assertEquals("1D2B53", IslandStyle.toHex(0xFF1D2B53.toInt()))
    }

    // ---- Where the color applies ------------------------------------------------------------------------------------

    private val camera = intArrayOf(500, 20, 560, 80)

    @Test fun `the island stays black while it wraps a camera hole Android reports`() {
        assertTrue(IslandStyle.wrapsRealCamera(null, camera, reportedCutout = true, 1080, 2400))
        assertFalse(IslandStyle.colorApplies(IslandStyle.wrapsRealCamera(null, camera, true, 1080, 2400)))
    }

    @Test fun `the color applies when the island is moved off the camera`() {
        assertFalse(IslandStyle.wrapsRealCamera(IslandPosition(.5f, 200f), camera, true, 1080, 2400))
        assertTrue(IslandStyle.colorApplies(IslandStyle.wrapsRealCamera(IslandPosition(.5f, 200f), camera, true, 1080, 2400)))
    }

    @Test fun `the color applies when the camera is not a real cutout`() {
        // A camera under the display (the Fold's inner screen) is known to Folio but not reported as a cutout.
        assertFalse(IslandStyle.wrapsRealCamera(null, camera, reportedCutout = false, 1080, 2400))
        assertFalse(IslandStyle.wrapsRealCamera(null, null, reportedCutout = false, 1080, 2400))
    }

    @Test fun `a camera on a side edge is not wrapped by the pill`() {
        // Turned sideways the camera is on the left edge, so the island goes to the top center instead.
        assertFalse(IslandStyle.wrapsRealCamera(null, intArrayOf(10, 1000, 70, 1060), reportedCutout = true, 2400, 1080))
    }

    // ---- Corner radius ----------------------------------------------------------------------------------------------

    @Test fun `round is exactly the fully round pill`() {
        assertEquals(17f, IslandStyle.pillRadius(IslandCorners.ROUND, 34f, 12f))
        assertEquals(17f, IslandStyle.pillRadius(IslandCorners.ROUND, 34f, 0f))
        assertEquals(34f, IslandCorners.ROUND.card)
    }

    @Test fun `softer steps are less round, never rounder than the pill`() {
        val soft = IslandStyle.pillRadius(IslandCorners.SOFT, 34f, 0f)
        val squarer = IslandStyle.pillRadius(IslandCorners.SQUARER, 34f, 0f)
        assertTrue(squarer < soft && soft < 17f)
        assertTrue(IslandCorners.SQUARER.card < IslandCorners.SOFT.card && IslandCorners.SOFT.card < IslandCorners.ROUND.card)
    }

    @Test fun `no step is squarer than the camera hole's own curve`() {
        for (step in IslandCorners.entries) {
            assertTrue(IslandStyle.pillRadius(step, 34f, 12f) >= 12f)
            // A hole bigger than the pill's own curve cannot push the corner past fully round.
            assertTrue(IslandStyle.pillRadius(step, 34f, 40f) <= 17f)
        }
        assertEquals(12f, IslandStyle.pillRadius(IslandCorners.SQUARER, 34f, 12f))
    }

    // ---- Animation speed --------------------------------------------------------------------------------------------

    @Test fun `standard speed is exactly today's motion at every Animation Speed`() {
        for (global in MotionSpeed.entries.map { it.factor }) {
            assertEquals(1f, IslandStyle.speedMultiplier(IslandSpeed.STANDARD, global))
            assertEquals(FolioMotion.Appear, FolioMotion.scaled(FolioMotion.Appear, IslandStyle.speedMultiplier(IslandSpeed.STANDARD, global)))
        }
        assertEquals(220, IslandStyle.scaledMs(220, 1f))
        assertEquals(60, IslandStyle.scaledMs(60, 1f, 0))
    }

    @Test fun `slower is slower and faster is faster, never past the ceiling`() {
        for (global in MotionSpeed.entries.map { it.factor }) {
            val slower = IslandStyle.speedMultiplier(IslandSpeed.SLOWER, global)
            val faster = IslandStyle.speedMultiplier(IslandSpeed.FASTER, global)
            assertTrue(slower < 1f)
            assertTrue("faster is still faster at Animation Speed $global", faster > 1f)
            assertTrue("$global x $faster stays under the ceiling", faster * global <= maxOf(FolioMotion.ISLAND_FASTEST, global) + 0.0001f)
        }
        assertEquals(FolioMotion.ISLAND_FASTER, IslandStyle.speedMultiplier(IslandSpeed.FASTER, 1f))
    }

    @Test fun `a fade is never shortened below its floor`() {
        for (step in IslandSpeed.entries) for (global in MotionSpeed.entries.map { it.factor }) {
            val m = IslandStyle.speedMultiplier(step, global)
            for (ms in listOf(90, 150, 220)) assertTrue(IslandStyle.scaledMs(ms, m) >= FolioMotion.ISLAND_MIN_FADE_MS)
        }
        assertEquals(FolioMotion.ISLAND_MIN_FADE_MS, IslandStyle.scaledMs(90, 4f))
        assertTrue(IslandStyle.scaledMs(220, FolioMotion.ISLAND_SLOWER) > 220)
        // A duration already under the floor is never raised.
        assertEquals(40, IslandStyle.scaledMs(40, 1.5f))
    }

    // ---- Haptics ----------------------------------------------------------------------------------------------------

    @Test fun `system touch feedback off means no haptics at any setting`() {
        for (level in IslandHaptics.entries) assertEquals(IslandHaptics.OFF, IslandStyle.effectiveHaptics(level, systemTouchFeedbackOn = false))
        for (level in IslandHaptics.entries) assertEquals(level, IslandStyle.effectiveHaptics(level, systemTouchFeedbackOn = true))
    }

    // ---- Gestures ---------------------------------------------------------------------------------------------------

    @Test fun `only Hold may pick the island up`() {
        assertTrue(IslandAction.MOVE in IslandStyle.choices(IslandGesture.HOLD))
        for (g in listOf(IslandGesture.TAP, IslandGesture.SWIPE_UP, IslandGesture.DOUBLE_TAP)) assertFalse(IslandAction.MOVE in IslandStyle.choices(g))
        for (g in IslandGesture.entries) assertEquals(IslandAction.NOTHING, IslandStyle.choices(g).last())
    }

    @Test fun `saved gestures are completed and made valid`() {
        assertEquals(IslandStyle.defaultGestures, IslandStyle.resolveGestures(emptyMap()))
        // Move is not a thing a tap can do.
        assertEquals(IslandAction.EXPAND, IslandStyle.resolveGestures(mapOf(IslandGesture.TAP to IslandAction.MOVE))[IslandGesture.TAP])
        val set = IslandStyle.resolveGestures(mapOf(IslandGesture.DOUBLE_TAP to IslandAction.OPEN_APP))
        assertEquals(IslandAction.OPEN_APP, set[IslandGesture.DOUBLE_TAP])
        assertFalse(IslandStyle.gesturesAreDefault(set))
    }

    @Test fun `a card can always be put away`() {
        // Nothing left on Dismiss in a saved set puts Swipe up back on it.
        val none = IslandStyle.resolveGestures(mapOf(IslandGesture.SWIPE_UP to IslandAction.NOTHING))
        assertEquals(IslandAction.DISMISS, none[IslandGesture.SWIPE_UP])
        // The only Dismiss cannot be changed away, but can once another gesture has it.
        val current = IslandStyle.defaultGestures
        assertFalse(IslandStyle.canSet(current, IslandGesture.SWIPE_UP, IslandAction.NOTHING))
        assertFalse(IslandStyle.canSet(current, IslandGesture.SWIPE_UP, IslandAction.EXPAND))
        assertTrue(IslandStyle.canSet(current, IslandGesture.DOUBLE_TAP, IslandAction.DISMISS))
        val moved = current + (IslandGesture.DOUBLE_TAP to IslandAction.DISMISS)
        assertTrue(IslandStyle.canSet(moved, IslandGesture.SWIPE_UP, IslandAction.NOTHING))
        // Setting a gesture to what it already is stays allowed.
        assertTrue(IslandStyle.canSet(current, IslandGesture.SWIPE_UP, IslandAction.DISMISS))
        // An action a gesture may not have is refused.
        assertFalse(IslandStyle.canSet(current, IslandGesture.TAP, IslandAction.MOVE))
    }

    @Test fun `Hold keeps picking the island up unless it is set to something else`() {
        assertEquals(IslandAction.MOVE, IslandStyle.defaultGestures[IslandGesture.HOLD])
        val changed = IslandStyle.resolveGestures(mapOf(IslandGesture.HOLD to IslandAction.OPEN_APP))
        assertEquals(IslandAction.OPEN_APP, changed[IslandGesture.HOLD])
    }

    @Test fun `over another app Expand opens the app, since there is no card`() {
        assertEquals(IslandAction.OPEN_APP, IslandStyle.overlayAction(IslandAction.EXPAND))
        assertEquals(IslandAction.OPEN_APP, IslandStyle.overlayAction(IslandAction.OPEN_APP))
        assertEquals(IslandAction.DISMISS, IslandStyle.overlayAction(IslandAction.DISMISS))
        assertEquals(IslandAction.NOTHING, IslandStyle.overlayAction(IslandAction.NOTHING))
    }
}
