package com.mccal.folio

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/**
 * Page Effects: how a Home page moves while you swipe to the next one. The idea is Barrel's, re-created from scratch.
 *
 * [NONE] is the default and stays the default. Home's page swipe is Folio's most expensive journey (J2 in
 * [docs/standards/performance.md]), a swipe on the inner screen already drops more frames than One UI Home does, and
 * every one of these effects spends GPU time that path does not have to spare. So this is something you turn on,
 * having seen what it costs on your phone, and never something Folio decides for you.
 *
 * Each effect is a pure function of one number: where the page sits relative to the viewport, in page widths
 * ([pagePosition]). Nought is settled and filling the screen, +1 is one page to the right, -1 one page to the left.
 * That keeps the whole feature testable ([DYN-20]) and, more importantly, keeps it out of composition: [pageEffect]
 * reads the pager inside `graphicsLayer { }`, so a swipe invalidates one layer per page rather than recomposing Home
 * ([PRF-7], [DYN-16]).
 *
 * What these deliberately do not do:
 * - **No alpha.** A `RenderNode` with alpha below 1 and overlapping content draws into an offscreen buffer, which is
 *   a whole extra page-sized surface per page, per frame.
 * - **No clip, no shadow, no blur, no `CompositingStrategy.Offscreen`.** Every effect here is a transform matrix on a
 *   layer Compose already has, so the cost is fill rate and a non-axis-aligned blit, nothing else.
 * - **No allocation in the layer block.** The three values come back as floats, and `TransformOrigin` is a value
 *   class over a `Long`.
 */
enum class PageEffect(@androidx.annotation.StringRes val label: Int) {
    /** Pages slide flat, exactly as they always have. The default, and what Reduce Motion and Safe Mode fall back to. */
    NONE(R.string.none),

    /**
     * Pages hinge like the faces of a box: each one pivots on the edge it shares with its neighbour and turns away
     * until it is edge-on at a full page out.
     */
    CUBE(R.string.cube),

    /** Pages turn a little and step back as they leave, the way a row of cards passing a window would. */
    CAROUSEL(R.string.carousel);

    /**
     * Degrees around the vertical axis at [position].
     *
     * Negative turns the free edge away from the viewer, which is what makes the cube read as a solid seen from
     * outside rather than as the inside of a box.
     *
     * The clamp is not cosmetic: a page two positions out would otherwise reach 180 degrees and come back into view
     * mirrored, and pages that far out are kept composed on purpose ([PRF-9]).
     */
    fun rotationY(position: Float): Float = when (this) {
        NONE -> 0f
        CUBE -> -CUBE_DEGREES * position.coerceIn(-1f, 1f)
        CAROUSEL -> -CAROUSEL_DEGREES * position.coerceIn(-1f, 1f)
    }

    /** Uniform scale at [position]. Always 1 for a settled page, so Home is untouched at rest ([DYN-3]). */
    fun scale(position: Float): Float = when (this) {
        NONE, CUBE -> 1f
        CAROUSEL -> 1f - CAROUSEL_SHRINK * abs(position).coerceAtMost(1f)
    }

    /**
     * Where the vertical axis runs through the page, 0 at its left edge and 1 at its right.
     *
     * The cube pivots on the seam: a page to the right turns about its left edge, a page to the left about its right
     * edge, so the two edges that meet stay met and the pages never cross. Everything else turns about its middle.
     */
    fun pivotX(position: Float): Float = when (this) {
        NONE, CAROUSEL -> .5f
        CUBE -> if (position > 0f) 0f else 1f
    }

    /**
     * How far the camera sits from the page, as a multiple of the page's own width.
     *
     * Android's own advice for a rotation about Y is to keep the camera further away than the view is wide, or the
     * perspective tears. Below about 1.5 widths a 90 degree hinge distorts into a wedge; above about 4 it flattens
     * into the sheared rectangle that gives these effects away. The cube needs the stronger perspective because it
     * turns all the way to edge-on; the carousel barely turns, so it gets a calmer camera.
     *
     * Measuring it in page widths rather than in a fixed distance is what keeps the effect looking the same on the
     * cover screen and on the inner one, which are nearly two to one in width ([ADP-1]: the window decides, not the
     * model name).
     */
    val cameraWidths: Float get() = when (this) {
        NONE -> 1f
        CUBE -> 2f
        CAROUSEL -> 3f
    }

    companion object {
        /** A full quarter turn, so a page is exactly edge-on when it is exactly one page out: the faces of a box. */
        private const val CUBE_DEGREES = 90f

        /** Enough turn to read as depth, little enough that icon labels stay legible all the way through a swipe. */
        private const val CAROUSEL_DEGREES = 28f

        /** A leaving page ends at 80% of its size. Deeper and the gap between pages starts to look like a mistake. */
        private const val CAROUSEL_SHRINK = .2f

        /** The saved value, or [NONE] for a save from before Page Effects and for anything unrecognised. */
        fun of(name: String?): PageEffect = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * Where [page] sits relative to the viewport, in page widths: 0 settled, +1 one page to the right, -1 one to the left.
 *
 * Both reads are snapshot state, so call this from a layer or draw block and not from composition.
 */
internal fun PagerState.pagePosition(page: Int): Float = page - (currentPage + currentPageOffsetFraction)

/**
 * Applies [effect] to one Home page of [pager].
 *
 * With [PageEffect.NONE] this adds nothing at all: no layer, no state read, not one instruction per frame. That is
 * the point of returning `this` rather than an identity transform, and it is where the feature is switched off for
 * Reduce Motion, for Safe Mode, and for a phone the gate is still shut on ([REL-4a]).
 *
 * It transforms how a page is drawn and never where it is: the pager still measures, places, snaps and reports the
 * same pages at the same positions. The caller keeps it off while an icon is being dragged, because a drop lands by
 * coordinates and a layer transform moves the coordinates under the finger.
 */
internal fun Modifier.pageEffect(effect: PageEffect, pager: PagerState, page: Int): Modifier =
    if (effect == PageEffect.NONE) this else graphicsLayer {
        val position = pager.pagePosition(page)
        transformOrigin = TransformOrigin(effect.pivotX(position), .5f)
        rotationY = effect.rotationY(position)
        scaleX = effect.scale(position)
        scaleY = scaleX
        // Compose measures cameraDistance in inches: it hands the number to RenderNode, which multiplies by the
        // display's dpi (the View path does the same division in reverse). density * 160 is densityDpi exactly, so
        // this is the page's width in inches times the multiplier the effect asked for.
        cameraDistance = effect.cameraWidths * size.width / (density * 160f)
    }
