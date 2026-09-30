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
     * Positive for a page to the right, negative for one to the left, which turns each page's free edge away from
     * the viewer and makes the cube read as a solid seen from outside. Android puts the camera on the layer's pivot,
     * so a face turned toward the camera is flung outward from its hinge, and one turned away pulls back toward it.
     * This was the other way round until 2026-09-25, and a recording of a swipe on the Fold8's cover screen showed
     * both halves of that: the leaving page swung its clock out at the viewer, huge, and the arriving page was
     * thrown off the right edge of the screen and never seen turning in at all.
     *
     * The clamp is not cosmetic: a page two positions out would otherwise reach 180 degrees and come back into view
     * mirrored, and pages that far out are kept composed on purpose ([PRF-9]).
     */
    fun rotationY(position: Float): Float = spec?.rotationY(position) ?: 0f

    /** Uniform scale at [position]. Always 1 for a settled page, so Home is untouched at rest ([DYN-3]). */
    fun scale(position: Float): Float = spec?.scale(position) ?: 1f

    /**
     * Where the vertical axis runs through the page, 0 at its left edge and 1 at its right.
     *
     * The cube pivots on the seam: a page to the right turns about its left edge, a page to the left about its right
     * edge, so the two edges that meet stay met and the pages never cross. Everything else turns about its middle.
     */
    fun pivotX(position: Float): Float = spec?.pivotX(position) ?: .5f

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
    val cameraWidths: Float get() = spec?.cameraWidths ?: 1f

    companion object {
        /** A full quarter turn, so a page is exactly edge-on when it is exactly one page out: the faces of a box. */
        internal const val CUBE_DEGREES = 90f

        /** Enough turn to read as depth, little enough that icon labels stay legible all the way through a swipe. */
        internal const val CAROUSEL_DEGREES = 28f

        /** A leaving page ends at 80% of its size. Deeper and the gap between pages starts to look like a mistake. */
        internal const val CAROUSEL_SHRINK = .2f

        /** The saved value, or [NONE] for a save from before Page Effects and for anything unrecognised. */
        fun of(name: String?): PageEffect = entries.firstOrNull { it.name == name } ?: NONE

        /** What Flipbook's page offers: every effect but [NONE], which is Flipbook switched off. */
        internal val CHOICES: List<PageEffect> = entries.filter { it != NONE }
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
    pageEffect(effect.spec, pager, page)

/** The same, for any spec: a built-in effect or one a package described. Null draws nothing extra. */
internal fun Modifier.pageEffect(effect: PageEffectSpec?, pager: PagerState, page: Int): Modifier =
    if (effect == null) this else graphicsLayer {
        val position = pager.pagePosition(page)
        transformOrigin = TransformOrigin(effect.pivotX(position), .5f)
        rotationY = effect.rotationY(position)
        scaleX = effect.scale(position)
        scaleY = scaleX
        cameraDistance = effect.cameraDistance(size.width)
    }

/**
 * A page effect as data: the four numbers the engine turns into a layer transform (ADR 0008: the engine lives in
 * Folio, the effect can come from a package). Built-in effects are specs too, so a packaged one is drawn by exactly
 * the same code.
 *
 * Nothing a package says is trusted: [of] clamps every number to the range the engine is known to draw well. Turning
 * past a quarter turn would show a page's back, shrinking past a third makes Home read as a thumbnail, and a camera
 * nearer than 1.5 page widths tears the perspective ([PageEffect.cameraWidths]).
 */
data class PageEffectSpec(
    /** Degrees around the vertical axis at one full page out. */
    val maxRotation: Float,
    /** Where a turning page pivots: on the edge it shares with its neighbor, or through its middle. */
    val pivot: Pivot,
    /** How much smaller a page is at one full page out, 0 for not at all. */
    val shrink: Float,
    /** Camera distance in page widths. */
    val cameraWidths: Float,
) {
    enum class Pivot { SEAM, CENTER }

    fun rotationY(position: Float): Float = maxRotation * position.coerceIn(-1f, 1f)
    fun scale(position: Float): Float = 1f - shrink * abs(position).coerceAtMost(1f)
    fun pivotX(position: Float): Float = when (pivot) {
        Pivot.CENTER -> .5f
        Pivot.SEAM -> if (position > 0f) 0f else 1f
    }

    /**
     * The layer's `cameraDistance` for a page [widthPx] pixels wide: [cameraWidths] page widths.
     *
     * Compose hands `cameraDistance` to `RenderNode` unchanged, `RenderNode` reads it as inches, and Skia's 3D camera
     * turns inches into pixels at 72 per inch, whatever the screen's density ([POINTS_PER_INCH]). Until 29 Sep 2026
     * this divided by the screen's dpi instead, which put the camera 72/420 as far away as meant on the Fold8: Cube's
     * camera sat a third of a page from the seam and a page shrank to a sliver a third of the way through a swipe.
     */
    fun cameraDistance(widthPx: Float): Float = cameraWidths * widthPx / POINTS_PER_INCH

    companion object {
        /** Skia's `Sk3DView::setCameraLocation` works in points: an inch of camera distance is 72 pixels. */
        const val POINTS_PER_INCH = 72f
        const val MAX_ROTATION = 90f
        const val MAX_SHRINK = .3f
        const val MIN_CAMERA = 1.5f
        const val MAX_CAMERA = 4f

        /** A spec from untrusted numbers (a package's manifest), each held to what the engine draws well. */
        fun of(maxRotation: Float, pivot: Pivot, shrink: Float, cameraWidths: Float) = PageEffectSpec(
            maxRotation.takeIf { it.isFinite() }?.coerceIn(-MAX_ROTATION, MAX_ROTATION) ?: 0f,
            pivot,
            shrink.takeIf { it.isFinite() }?.coerceIn(0f, MAX_SHRINK) ?: 0f,
            cameraWidths.takeIf { it.isFinite() }?.coerceIn(MIN_CAMERA, MAX_CAMERA) ?: 3f,
        )
    }
}

/** The built-in effects as specs. [PageEffect.NONE] has none: it adds no layer at all. */
internal val PageEffect.spec: PageEffectSpec? get() = when (this) {
    PageEffect.NONE -> null
    PageEffect.CUBE -> PageEffectSpec(PageEffect.CUBE_DEGREES, PageEffectSpec.Pivot.SEAM, 0f, 2f)
    PageEffect.CAROUSEL -> PageEffectSpec(PageEffect.CAROUSEL_DEGREES, PageEffectSpec.Pivot.CENTER, PageEffect.CAROUSEL_SHRINK, 3f)
}

/** A page effect a Market package installed: its package id, the name the picker shows, and its clamped spec. */
data class PackagedPageEffect(val id: String, val name: String, val spec: PageEffectSpec) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().put("id", id).put("name", name)
        .put("maxRotation", spec.maxRotation.toDouble()).put("pivot", spec.pivot.name)
        .put("shrink", spec.shrink.toDouble()).put("cameraWidths", spec.cameraWidths.toDouble())

    companion object {
        /** From a package's untrusted numbers: [PageEffectSpec.of] clamps them. */
        fun of(id: String, name: String, maxRotation: Float, pivot: String, shrink: Float, cameraWidths: Float) =
            PackagedPageEffect(id, name, PageEffectSpec.of(maxRotation, if (pivot.equals("seam", true)) PageEffectSpec.Pivot.SEAM
                else PageEffectSpec.Pivot.CENTER, shrink, cameraWidths))

        fun from(json: org.json.JSONObject?): PackagedPageEffect? {
            json ?: return null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            return of(id, json.optString("name", id), json.optDouble("maxRotation", 0.0).toFloat(), json.optString("pivot"),
                json.optDouble("shrink", 0.0).toFloat(), json.optDouble("cameraWidths", 3.0).toFloat())
        }
    }
}
