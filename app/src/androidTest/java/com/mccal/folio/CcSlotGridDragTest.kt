package com.mccal.folio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Control Center's editor drag, on the real grid with real touches (the pure rules are in CcDragTest): the held control stays under the finger,
 * a drop lands where the preview said, a drop that cannot work goes home, TalkBack's actions give the same results, and Reduce Motion keeps
 * everything still but the control in the finger.
 */
class CcSlotGridDragTest {
    @get:Rule val compose = createComposeRule()

    private val cell = 80.dp
    private val gap = 12.dp
    private val step = cell + gap
    private val changes = mutableListOf<List<CcSlot>>()

    private fun one(id: String, col: Int, row: Int) = CcSlot(id, col, row, 1, 1)

    /** Replaces the layout from outside the grid, as Undo does. */
    private var setSlots: ((List<CcSlot>) -> Unit)? = null

    /** The grid alone. */
    private fun host(initial: List<CcSlot>, reduceMotion: Boolean = false, rows: Int = 4) {
        compose.setContent {
            var slots by remember { mutableStateOf(initial) }
            setSlots = { slots = it }
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    CcSlotGrid(
                        slots, cols = 4, rows = rows, cell = cell, gap = gap, editing = true, raised = null,
                        removable = { true }, labelOf = { it },
                        onChange = { changes += it; slots = it }, onRemove = { id -> slots = slots.filter { it.id != id } },
                    ) { slot -> Box(Modifier.fillMaxSize().testTag("tile-${slot.id}")) }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The page being shown by [pagedHost]. */
    private var pageShown = 0

    /**
     * The grid as Control Center's editor shows it when the window holds [perPage] rows: one page at a time, the layout in whole-grid rows (so
     * what is seen is `changes.last()`), and a control held at a page's edge carried to the next page or the one before.
     */
    private fun pagedHost(initial: List<CcSlot>, perPage: Int = 3, startPage: Int = 0) {
        compose.setContent {
            var slots by remember { mutableStateOf(initial) }
            var page by remember { mutableStateOf(startPage) }
            pageShown = page
            var viewport by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
            val starts = ccPageStarts(slots, perPage, perPage * 2)
            fun endOf(i: Int) = starts.getOrNull(i + 1) ?: (starts[i] + perPage)
            val start = starts[page]
            val end = endOf(page)
            fun carry(id: String, to: Int, fromBottom: Boolean): Boolean {
                val next = ccCarry(slots, id, 4, starts[to], endOf(to), fromBottom) ?: return false
                changes += next; slots = next; page = to
                return true
            }
            CompositionLocalProvider(LocalReduceMotion provides false) {
                Box(Modifier.padding(16.dp).onGloballyPositioned { viewport = it }) {
                    CcSlotGrid(
                        ccPageSlots(slots, start, end), cols = 4, rows = end - start, cell = cell, gap = gap, editing = true, raised = null,
                        removable = { true }, labelOf = { it },
                        onChange = { next -> val merged = ccMergePage(slots, start, end, next); changes += merged; slots = merged },
                        onRemove = { id -> slots = slots.filter { it.id != id } },
                        viewport = { viewport },
                        carryNext = if (page < starts.lastIndex) { id -> carry(id, page + 1, false) } else null,
                        carryBack = if (page > 0) { id -> carry(id, page - 1, true) } else null,
                    ) { slot -> Box(Modifier.fillMaxSize().testTag("tile-${slot.id}")) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun centerOf(id: String): Pair<Dp, Dp> =
        compose.onNodeWithTag("tile-$id").getBoundsInRoot().let { (it.left + it.right) / 2 to (it.top + it.bottom) / 2 }

    private var held: String? = null

    /** Injected touches reach the UI thread a moment after the call returns and the test clock does not wait for them; this queues behind them. */
    private fun delivered() = compose.runOnUiThread { }

    // While a control is held the page runs a frame loop (for the carry at its edges), so Compose is never "idle" and waiting for it would
    // never end: the clock is driven by hand from the moment the finger goes down until it comes up.
    private fun hold(id: String) {
        held = id
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("tile-$id").performTouchInput { down(center) }
        // The press-and-hold timeout runs on the test clock, not on the injected event time.
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.advanceTimeByFrame()
    }

    /** A test that fails with a finger down must still let go, or the page keeps drawing and the test can never wind down. */
    @org.junit.After fun letGo() {
        held?.let { id -> runCatching { release(id) } }
        compose.mainClock.autoAdvance = true
    }

    /**
     * Moves the held finger by [dx] by [dy] (in dp) in one move, then lets the animations it caused finish unless [settle] is false. One move, not a
     * stream of small ones: the test harness hands a gesture that is still working on a move the next one and the gesture never sees it, which
     * would make the control fall short of the finger for a reason that is the harness's, not the control's (the finger was always where it was sent).
     */
    private fun moveFinger(id: String, dx: Dp, dy: Dp, settle: Boolean = true) {
        compose.onNodeWithTag("tile-$id").performTouchInput { moveBy(Offset(dx.toPx(), dy.toPx()), 16) }
        delivered()
        compose.mainClock.advanceTimeByFrame()
        if (settle) compose.mainClock.advanceTimeBy(600)
    }

    private fun release(id: String) {
        held = null
        compose.onNodeWithTag("tile-$id").performTouchInput { up() }
        delivered()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun within(expected: Dp, actual: Dp, by: Float = 1.2f) =
        assertTrue("expected about $expected, was $actual", kotlin.math.abs(expected.value - actual.value) <= by)

    @Test fun theHeldControlStaysUnderTheFingerAtEveryDistance() {
        host(listOf(one("a", 0, 0)))
        val (x0, y0) = centerOf("a")
        hold("a")
        // The lift grows the control around its middle; the pointer measures inside the grown control, so a drag that did not add that back
        // would fall 5% short of the finger (found on the emulator, 17 Nov 2026).
        moveFinger("a", step * 2, step)
        val (x1, y1) = centerOf("a")
        within(x0 + step * 2, x1)
        within(y0 + step, y1)
        release("a")
    }

    @Test fun letGoInAFreeCellMovesThereAndTheControlSettlesInIt() {
        host(listOf(one("a", 0, 0)))
        hold("a")
        moveFinger("a", step * 2, step)
        release("a")
        assertEquals(listOf(listOf(one("a", 2, 1))), changes)
        val (x, y) = centerOf("a")
        within(cell / 2 + step * 2 + 16.dp, x)
        within(cell / 2 + step + 16.dp, y)
    }

    @Test fun aControlDroppedOnANeighborTradesPlacesWithIt() {
        host(listOf(one("a", 0, 0), one("b", 1, 0)))
        hold("a")
        moveFinger("a", step, 0.dp)
        release("a")
        assertEquals(1, changes.size)
        assertEquals(setOf(one("a", 1, 0), one("b", 0, 0)), changes.last().toSet())
    }

    @Test fun aDragThatHasNotGoneHalfwayChangesNothing() {
        host(listOf(one("a", 0, 0), one("b", 1, 0)))
        hold("a")
        moveFinger("a", step * .3f, 0.dp)
        release("a")
        assertTrue(changes.isEmpty())
    }

    @Test fun aDragOffTheGridChangesNothingAndTheControlGoesHome() {
        host(listOf(one("a", 0, 0)))
        val (x0, y0) = centerOf("a")
        hold("a")
        moveFinger("a", -step * 4, -step * 4)
        release("a")
        assertTrue(changes.isEmpty())
        within(x0, centerOf("a").first)
        within(y0, centerOf("a").second)
    }

    @Test fun aCancelledTouchChangesNothingAndTheControlGoesHome() {
        host(listOf(one("a", 0, 0)))
        val (x0, y0) = centerOf("a")
        hold("a")
        moveFinger("a", step, step)
        held = null
        compose.onNodeWithTag("tile-a").performTouchInput { cancel() }
        delivered()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(changes.isEmpty())
        within(x0, centerOf("a").first)
        within(y0, centerOf("a").second)
    }

    @Test fun aNeighborSlidesIntoTheHeldControlsPlaceWhileItIsStillHeld() {
        host(listOf(one("a", 0, 0), one("b", 1, 0)))
        val (bx, _) = centerOf("b")
        hold("a")
        moveFinger("a", step, 0.dp)
        // Still held: b has already made room.
        within(bx - step, centerOf("b").first)
        assertTrue(changes.isEmpty())
        release("a")
    }

    private fun neighborAfterOneFrame(reduce: Boolean): Pair<Dp, Dp> {
        host(listOf(one("a", 0, 0), one("b", 1, 0)), reduceMotion = reduce)
        val (bx, _) = centerOf("b")
        hold("a")
        moveFinger("a", step, 0.dp, settle = false)
        compose.mainClock.advanceTimeByFrame()
        val now = centerOf("b").first
        release("a")
        return bx to now
    }

    @Test fun underReduceMotionTheNeighborJumpsAtOnce() {
        val (bx, now) = neighborAfterOneFrame(reduce = true)
        within(bx - step, now)
    }

    @Test fun withMotionTheNeighborSlidesRatherThanJumps() {
        val (bx, now) = neighborAfterOneFrame(reduce = false)
        assertTrue("b should still be on its way, was at $now (from $bx)", now > bx - step + 1.dp && now <= bx + 1.dp)
    }

    @Test fun talkBackMovesTradePlacesLikeADropAndSaySoWhenTheyCannot() {
        host(listOf(one("a", 0, 0), one("b", 1, 0)))
        val resources = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources
        fun act(id: String, label: Int): Boolean {
            val node = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNodes()
                .first { it.config[SemanticsActions.CustomActions].any { a -> a.label == "${resources.getString(label)}, $id" } }
            val action = node.config[SemanticsActions.CustomActions].first { it.label == "${resources.getString(label)}, $id" }
            var result = false
            compose.runOnUiThread { result = action.action() }
            compose.waitForIdle()
            return result
        }
        assertEquals(false, act("a", R.string.cc_move_left))
        assertEquals(false, act("a", R.string.cc_move_up))
        assertEquals(true, act("a", R.string.cc_move_right))
        assertEquals(setOf(one("a", 1, 0), one("b", 0, 0)), changes.last().toSet())
        assertEquals(true, act("a", R.string.cc_move_down))
        assertEquals(one("a", 1, 1), changes.last().first { it.id == "a" })
    }

    @Test fun eachControlSaysWhereItIsWhileEditing() {
        host(listOf(one("a", 2, 1)))
        val node = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).fetchSemanticsNodes().first()
        val said = node.config[SemanticsProperties.StateDescription]
        val resources = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources
        assertEquals(resources.getString(R.string.cc_tile_place, 2, 3, 1, 1), said)
    }

    @Test fun heldAtThePageTopForAMomentCarriesTheControlToTheNextPage() {
        pagedHost(listOf(one("a", 0, 0), one("b", 3, 3)))
        hold("a")
        // The control's middle is 40 dp down the page; 30 dp up puts the finger 10 dp from the page's top edge, inside its 36 dp edge.
        moveFinger("a", 0.dp, (-30).dp, settle = false)
        compose.mainClock.advanceTimeBy(900)
        assertEquals("a is on the next page, in its first free place", one("a", 0, 3), changes.last().first { it.id == "a" })
        assertEquals(1, pageShown)
        release("a")
        assertEquals("letting go afterwards changes nothing more", one("a", 0, 3), changes.last().first { it.id == "a" })
    }

    @Test fun heldAtThePageBottomForAMomentCarriesTheControlToThePageBefore() {
        pagedHost(listOf(one("a", 0, 3), one("b", 3, 0)), startPage = 1)
        hold("a")
        // Row 0 of this page, 40 dp down; 3 rows of 92 dp is the page, so 3 rows down puts the finger past its bottom edge.
        moveFinger("a", 0.dp, step * 3 - 40.dp, settle = false)
        compose.mainClock.advanceTimeBy(900)
        assertEquals("a is on the page before, at its end", one("a", 0, 2), changes.last().first { it.id == "a" })
        assertEquals(0, pageShown)
        release("a")
    }

    @Test fun passingTheEdgeWithoutStayingCarriesNothing() {
        pagedHost(listOf(one("a", 0, 0)))
        hold("a")
        moveFinger("a", 0.dp, (-30).dp, settle = false)
        compose.mainClock.advanceTimeBy(150)
        moveFinger("a", 0.dp, 30.dp, settle = false)
        compose.mainClock.advanceTimeBy(900)
        assertEquals(0, pageShown)
        assertTrue("nothing was committed while the finger was in motion", changes.isEmpty())
        release("a")
    }

    @Test fun aPageWithNoRoomLeavesTheControlWhereItIs() {
        // Page 2 (rows 3 to 5) is full: twelve one-cell controls.
        val full = (3 until 6).flatMap { r -> (0 until 4).map { c -> one("f$r$c", c, r) } }
        pagedHost(listOf(one("a", 0, 0)) + full)
        hold("a")
        moveFinger("a", 0.dp, (-30).dp, settle = false)
        compose.mainClock.advanceTimeBy(900)
        assertEquals(0, pageShown)
        assertTrue(changes.isEmpty())
        release("a")
        assertTrue("the control went home and nothing changed", changes.isEmpty())
    }

    @Test fun aSecondFingerCannotLiftAnotherControlWhileOneIsHeld() {
        host(listOf(one("a", 0, 0), one("b", 3, 0)))
        val (_, ay) = centerOf("a")
        val (bx, by) = centerOf("b")
        hold("a")
        // A second finger presses and holds another control for the long-press time; the first control stays the one in hand.
        compose.onNodeWithTag("tile-b").performTouchInput { down(1, center) }
        delivered()
        compose.mainClock.advanceTimeBy(900)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("tile-a").performTouchInput { updatePointerBy(0, Offset(0f, step.toPx())); move(16) }
        delivered()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600)
        within(ay + step, centerOf("a").second)
        within(by, centerOf("b").second)
        within(bx, centerOf("b").first)
        compose.onNodeWithTag("tile-b").performTouchInput { up(1) }
        delivered()
        release("a")
    }

    @Test fun aControlTakenOffUnderTheFingerLeavesNothingHeldBehind() {
        val start = listOf(one("a", 0, 0), one("b", 3, 3))
        host(start)
        val (x0, y0) = centerOf("a")
        hold("a")
        moveFinger("a", step * 2, 0.dp)
        // A second finger taps the held control's minus: it is gone while the first finger is still down.
        compose.onNodeWithTag("tile-a").performTouchInput { down(1, Offset(4.dp.toPx(), 4.dp.toPx())) }
        delivered()
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().performTouchInput { up(1) }
        delivered()
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().performTouchInput { up(0) }
        held = null
        delivered()
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        // Undo brings it back: it must be at home, not lifted and carried where the finger last was.
        compose.runOnUiThread { setSlots!!(start) }
        compose.waitForIdle()
        within(x0, centerOf("a").first)
        within(y0, centerOf("a").second)
        // And the next control can still be lifted and moved.
        changes.clear()
        hold("b")
        moveFinger("b", -step, 0.dp)
        release("b")
        assertEquals(one("b", 2, 3), changes.last().first { it.id == "b" })
    }
}
