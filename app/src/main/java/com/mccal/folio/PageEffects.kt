package com.mccal.folio

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import org.json.JSONArray
import org.json.JSONObject
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

    /**
     * Cube seen from inside: pages hinge on the same seam, but toward you, like the walls of a room. The idea is
     * Cylinder's Cube (inside), by Reed Weichler, re-created here as numbers; Cylinder has no license, so no code.
     *
     * Its free edge comes toward the camera, which is how a face looked huge before the camera fix of 29 Sep 2026
     * ([PageEffectSpec.cameraDistance]). With the camera two page widths away, an edge reaches twice its size only
     * edge-on, a full page out, where it has left the screen.
     */
    INSIDE_CUBE(R.string.inside_cube),

    /** Pages turn a little and step back as they leave, the way a row of cards passing a window would. */
    CAROUSEL(R.string.carousel),

    /**
     * Pages step back as they leave, without turning: Page Squeeze by Beta382 for Cylinder, re-created as numbers.
     * With no turn, Android draws it as a flat scale with no 3D camera at all.
     */
    STACK(R.string.stack);

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
     *
     * [INSIDE_CUBE] is the cube with the sign flipped, so its free edges turn toward the viewer instead.
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

        /**
         * A leaving page ends at three quarters of its size, deeper than Carousel's: with no turn, stepping back is all
         * that shows Stack's depth. The number shown in the Mockup Lab.
         */
        internal const val STACK_SHRINK = .25f

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
        // The pager's own page step, read only by an effect that shifts a page sideways, so every other effect reads
        // exactly the state it read before channels existed.
        val travel = if (effect.shiftsSideways) pager.layoutInfo.let { it.pageSize + it.pageSpacing }.toFloat() else 0f
        applyPageEffect(effect, position, size.width, size.height, travel)
    }

/**
 * Sets one page's layer for [effect] at [position]: the whole of what Flipbook draws, as a pure function of its
 * inputs so a test can hold it to the numbers ([DYN-20]). [widthPx] and [heightPx] are the layer's size, [travelPx]
 * how far the pager moves a page for one position (only a sideways shift reads it).
 *
 * Transforms only, on the layer the page already has: no clip, no shadow, no blur, no offscreen buffer and nothing
 * that fades. With no channels the four properties it sets are the four it always set, computed the same way, and the
 * three channels adds (rotationZ, translationX, translationY) stay at the layer's own resting values.
 */
internal fun GraphicsLayerScope.applyPageEffect(
    effect: PageEffectSpec, position: Float, widthPx: Float, heightPx: Float, travelPx: Float,
) {
    transformOrigin = TransformOrigin(effect.pivotX(position), effect.pivotY)
    rotationY = effect.rotationY(position)
    scaleX = effect.scale(position)
    scaleY = scaleX
    cameraDistance = effect.cameraDistance(widthPx)
    if (effect.channels != null) {
        rotationZ = effect.rotationZ(position)
        translationX = effect.translateX(position) * travelPx
        translationY = effect.translateY(position) * heightPx
    }
}

/**
 * A page effect as data: the four numbers the engine turns into a layer transform (ADR 0008: the engine lives in
 * Folio, the effect can come from a package). Built-in effects are specs too, so a packaged one is drawn by exactly
 * the same code.
 *
 * Nothing a package says is trusted: [of] clamps every number to the range the engine is known to draw well. Turning
 * past a quarter turn would show a page's back, shrinking past a third makes Home read as a thumbnail, and a camera
 * nearer than 1.5 page widths tears the perspective ([PageEffect.cameraWidths]).
 *
 * [channels] (format 0.7.0) refine the four numbers with curves and more ways to move. Without them a spec draws
 * exactly what it drew before they existed; with them, each channel replaces the one thing it names and every
 * channel left out still comes from the four numbers.
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
    /** Curves and extra movement, already clamped and baked; null for a v1 effect. */
    val channels: PageEffectChannels? = null,
) {
    enum class Pivot { SEAM, CENTER }

    fun rotationY(position: Float): Float =
        channels?.rotationY?.let { PageEffectChannels.odd(it, position) } ?: (maxRotation * position.coerceIn(-1f, 1f))

    fun scale(position: Float): Float =
        channels?.scale?.let { PageEffectChannels.even(it, position) } ?: (1f - shrink * abs(position).coerceAtMost(1f))

    fun pivotX(position: Float): Float {
        val share = channels?.pivotX ?: return when (pivot) {
            Pivot.CENTER -> .5f
            Pivot.SEAM -> if (position > 0f) 0f else 1f
        }
        // A share of the way from the seam to the outer edge: a page on the right has its seam on the left.
        return if (position > 0f) share else 1f - share
    }

    /** Where the turn's axis crosses the page vertically, in page heights from the top: the middle unless a channel says. */
    val pivotY: Float get() = channels?.pivotY ?: .5f

    /** Degrees in the page's own plane, positive clockwise for a page on the right. 0 without a channel. */
    fun rotationZ(position: Float): Float = channels?.rotationZ?.let { PageEffectChannels.odd(it, position) } ?: 0f

    /** A sideways shift as a share of the page's travel, outward for a page on the right when positive. */
    fun translateX(position: Float): Float = channels?.translateX?.let { PageEffectChannels.odd(it, position) } ?: 0f

    /** A drop in page heights, the same on both sides. */
    fun translateY(position: Float): Float = channels?.translateY?.let { PageEffectChannels.even(it, position) } ?: 0f

    /** True when drawing needs the pager's page step, so nothing else reads it. */
    val shiftsSideways: Boolean get() = channels?.translateX != null

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

        /**
         * A spec from untrusted numbers (a package's manifest), each held to what the engine draws well. [channels]
         * come already clamped and baked ([PageEffectChannels.from]).
         */
        fun of(maxRotation: Float, pivot: Pivot, shrink: Float, cameraWidths: Float, channels: PageEffectChannels? = null) =
            PageEffectSpec(
                maxRotation.takeIf { it.isFinite() }?.coerceIn(-MAX_ROTATION, MAX_ROTATION) ?: 0f,
                pivot,
                shrink.takeIf { it.isFinite() }?.coerceIn(0f, MAX_SHRINK) ?: 0f,
                cameraWidths.takeIf { it.isFinite() }?.coerceIn(MIN_CAMERA, MAX_CAMERA) ?: 3f,
                channels,
            )
    }
}

/**
 * Page effect channels (format 0.7.0): each one a curve over how far out a page is, so an effect can wait, ease, or
 * do something a straight line from the four v1 numbers cannot (a drop, a spin in the page's own plane, a door's
 * outer-edge hinge).
 *
 * A curve has up to [MAX_KEYS] keys, each a value at a distance 0 to 1 pages out, and one [Ease] between every pair.
 * Past the last key a curve holds its last value, and past a full page out it holds the value at one page, as the v1
 * numbers do. At 0 every curve is the channel's resting value whatever the keys say, so a settled page is untouched
 * ([DYN-3]).
 *
 * The turns and the sideways shift are odd (a page on the left does the opposite of one on the right, so a cube's
 * edges stay met); the drop and the scale are even (both sides step back the same way).
 *
 * Nothing here is trusted: [from] drops what it cannot read and clamps every value to [Channel]'s range, and each
 * curve is baked once into [SAMPLES] floats. Drawing reads two of them and blends, with no allocation, no search and
 * no easing maths in the layer block.
 */
data class PageEffectChannels(
    val rotationYCurve: Curve? = null,
    val rotationZCurve: Curve? = null,
    val translateXCurve: Curve? = null,
    val translateYCurve: Curve? = null,
    val scaleCurve: Curve? = null,
    /** A share of the way from the seam edge (0) to the outer edge (1); null leaves the v1 pivot. */
    val pivotX: Float? = null,
    /** Page heights from the top, 0 to [MAX_PIVOT_Y]; null is the middle. */
    val pivotY: Float? = null,
) {
    /** The baked tables the engine reads. Not part of equality: they are a pure function of the curves. */
    internal val rotationY: FloatArray? = rotationYCurve?.bake(Channel.ROTATION_Y)
    internal val rotationZ: FloatArray? = rotationZCurve?.bake(Channel.ROTATION_Z)
    internal val translateX: FloatArray? = translateXCurve?.bake(Channel.TRANSLATE_X)
    internal val translateY: FloatArray? = translateYCurve?.bake(Channel.TRANSLATE_Y)
    internal val scale: FloatArray? = scaleCurve?.bake(Channel.SCALE)

    /**
     * How the value moves between two keys: the quadratic eases, so the Mockup Lab can draw the same curve in a line of
     * JavaScript. One ease per curve, applied to every span between its keys.
     */
    enum class Ease(val json: String) {
        LINEAR("linear"), IN("in"), OUT("out"), IN_OUT("inOut");

        fun apply(t: Float): Float = when (this) {
            LINEAR -> t
            IN -> t * t
            OUT -> 1f - (1f - t) * (1f - t)
            IN_OUT -> if (t < .5f) 2f * t * t else 1f - 2f * (1f - t) * (1f - t)
        }

        companion object {
            fun of(name: String?): Ease? = entries.firstOrNull { it.json == name }
        }
    }

    /** One channel's name in effect.json, its range, its resting value, and whether a page on the left mirrors it. */
    enum class Channel(val json: String, val min: Float, val max: Float, val identity: Float, val odd: Boolean) {
        ROTATION_Y("rotationY", -PageEffectSpec.MAX_ROTATION, PageEffectSpec.MAX_ROTATION, 0f, true),
        ROTATION_Z("rotationZ", -45f, 45f, 0f, true),
        TRANSLATE_X("translateX", -1f, 1f, 0f, true),
        TRANSLATE_Y("translateY", 0f, .25f, 0f, false),
        SCALE("scale", .6f, 1f, 1f, false),
    }

    /** A value at [at] pages out, 0 to 1. */
    data class Key(val at: Float, val value: Float)

    /** Keys sorted by [Key.at], each [at][Key.at] once, at most [MAX_KEYS], values already clamped. */
    data class Curve(val keys: List<Key>, val ease: Ease = Ease.LINEAR) {
        /** The value at [x] (0 to 1), with the resting value forced at 0. */
        fun valueAt(channel: Channel, x: Float): Float {
            var fromAt = 0f
            var fromValue = channel.identity
            for (key in keys) {
                if (key.at <= 0f) continue // identity at 0, whatever the key says
                if (x <= key.at) {
                    val t = ease.apply((x - fromAt) / (key.at - fromAt))
                    return fromValue + (key.value - fromValue) * t
                }
                fromAt = key.at
                fromValue = key.value
            }
            return fromValue
        }

        fun bake(channel: Channel): FloatArray =
            FloatArray(SAMPLES) { i -> if (i == 0) channel.identity else valueAt(channel, i / (SAMPLES - 1f)) }

        fun toJson(): JSONObject = JSONObject().put("ease", ease.json)
            .put("keys", JSONArray().apply { keys.forEach { put(JSONObject().put("at", it.at.toDouble()).put("value", it.value.toDouble())) } })

        companion object {
            /**
             * A curve from untrusted JSON: `{"keys": [{"at": 0.1, "value": 0}, ...], "ease": "inOut"}`. A key
             * without two finite numbers is dropped, [at][Key.at] is held to 0..1, a repeated [at][Key.at] keeps its
             * first value, values are clamped to [channel]'s range, and only the first [MAX_KEYS] count. No usable
             * key, no curve.
             */
            fun from(json: JSONObject?, channel: Channel): Curve? {
                val array = json?.optJSONArray("keys") ?: return null
                val keys = (0 until array.length()).mapNotNull { i ->
                    val key = array.optJSONObject(i) ?: return@mapNotNull null
                    val at = key.optDouble("at", Double.NaN).toFloat()
                    val value = key.optDouble("value", Double.NaN).toFloat()
                    if (!at.isFinite() || !value.isFinite()) null
                    else Key(at.coerceIn(0f, 1f), value.coerceIn(channel.min, channel.max))
                }.take(MAX_KEYS).sortedBy { it.at }.distinctBy { it.at }
                if (keys.isEmpty()) return null
                return Curve(keys, Ease.of(json.optString("ease")) ?: Ease.LINEAR)
            }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        rotationYCurve?.let { put(Channel.ROTATION_Y.json, it.toJson()) }
        rotationZCurve?.let { put(Channel.ROTATION_Z.json, it.toJson()) }
        translateXCurve?.let { put(Channel.TRANSLATE_X.json, it.toJson()) }
        translateYCurve?.let { put(Channel.TRANSLATE_Y.json, it.toJson()) }
        scaleCurve?.let { put(Channel.SCALE.json, it.toJson()) }
        pivotX?.let { put("pivotX", it.toDouble()) }
        pivotY?.let { put("pivotY", it.toDouble()) }
    }

    companion object {
        /** Keys per curve. Enough for a pause, an ease out and a settle; few enough to read at a glance. */
        const val MAX_KEYS = 8

        /** Baked samples per curve, 0 to 1 pages out in steps of 1/32. */
        const val SAMPLES = 33

        /** A hub below the page, as Wheel needs, but no further than a page below it. */
        const val MAX_PIVOT_Y = 2f

        /**
         * Channels from untrusted JSON (an effect.json's `channels`, or Folio's own saved copy). Anything unreadable
         * is left out rather than refused: the market module has already refused a package whose channels break the
         * format, so what reaches here is held to the engine's ranges, not judged. Null when nothing usable is left.
         */
        fun from(json: JSONObject?): PageEffectChannels? {
            json ?: return null
            fun curve(channel: Channel) = Curve.from(json.optJSONObject(channel.json), channel)
            val channels = PageEffectChannels(
                rotationYCurve = curve(Channel.ROTATION_Y),
                rotationZCurve = curve(Channel.ROTATION_Z),
                translateXCurve = curve(Channel.TRANSLATE_X),
                translateYCurve = curve(Channel.TRANSLATE_Y),
                scaleCurve = curve(Channel.SCALE),
                pivotX = pivotShare(json.opt("pivotX")),
                pivotY = json.optDouble("pivotY", Double.NaN).toFloat().takeIf { it.isFinite() }?.coerceIn(0f, MAX_PIVOT_Y),
            )
            return channels.takeIf { it != PageEffectChannels() }
        }

        /** `seam`, `center`, `outer`, or a share 0 to 1 from the seam edge to the outer edge. */
        internal fun pivotShare(value: Any?): Float? = when (value) {
            "seam" -> 0f
            "center" -> .5f
            "outer" -> 1f
            is Number -> value.toFloat().takeIf { it.isFinite() }?.coerceIn(0f, 1f)
            else -> null
        }

        /** The table's value at |[position]|, held at one page out, with the sign of [position]. */
        internal fun odd(table: FloatArray, position: Float): Float {
            val value = sample(table, abs(position))
            return if (position < 0f) -value else value
        }

        /** The table's value at |[position]|, held at one page out, the same on both sides. */
        internal fun even(table: FloatArray, position: Float): Float = sample(table, abs(position))

        private fun sample(table: FloatArray, x: Float): Float {
            val scaled = x.coerceIn(0f, 1f) * (SAMPLES - 1)
            val index = scaled.toInt()
            if (index >= SAMPLES - 1) return table[SAMPLES - 1]
            val from = table[index]
            return from + (table[index + 1] - from) * (scaled - index)
        }
    }
}

/** The built-in effects as specs. [PageEffect.NONE] has none: it adds no layer at all. */
internal val PageEffect.spec: PageEffectSpec? get() = when (this) {
    PageEffect.NONE -> null
    PageEffect.CUBE -> PageEffectSpec(PageEffect.CUBE_DEGREES, PageEffectSpec.Pivot.SEAM, 0f, 2f)
    PageEffect.INSIDE_CUBE -> PageEffectSpec(-PageEffect.CUBE_DEGREES, PageEffectSpec.Pivot.SEAM, 0f, 2f)
    PageEffect.CAROUSEL -> PageEffectSpec(PageEffect.CAROUSEL_DEGREES, PageEffectSpec.Pivot.CENTER, PageEffect.CAROUSEL_SHRINK, 3f)
    PageEffect.STACK -> PageEffectSpec(0f, PageEffectSpec.Pivot.CENTER, PageEffect.STACK_SHRINK, 3f)
}

/** A page effect a Market package installed: its package id, the name the picker shows, and its clamped spec. */
data class PackagedPageEffect(val id: String, val name: String, val spec: PageEffectSpec) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("maxRotation", spec.maxRotation.toDouble()).put("pivot", spec.pivot.name)
        .put("shrink", spec.shrink.toDouble()).put("cameraWidths", spec.cameraWidths.toDouble())
        .apply { spec.channels?.let { put("channels", it.toJson()) } }

    companion object {
        /** From a package's untrusted numbers: [PageEffectSpec.of] and [PageEffectChannels.from] clamp them. */
        fun of(
            id: String, name: String, maxRotation: Float, pivot: String, shrink: Float, cameraWidths: Float,
            channels: JSONObject? = null,
        ) = PackagedPageEffect(id, name, PageEffectSpec.of(maxRotation, if (pivot.equals("seam", true)) PageEffectSpec.Pivot.SEAM
            else PageEffectSpec.Pivot.CENTER, shrink, cameraWidths, PageEffectChannels.from(channels)))

        fun from(json: JSONObject?): PackagedPageEffect? {
            json ?: return null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            return of(id, json.optString("name", id), json.optDouble("maxRotation", 0.0).toFloat(), json.optString("pivot"),
                json.optDouble("shrink", 0.0).toFloat(), json.optDouble("cameraWidths", 3.0).toFloat(),
                json.optJSONObject("channels"))
        }
    }
}
