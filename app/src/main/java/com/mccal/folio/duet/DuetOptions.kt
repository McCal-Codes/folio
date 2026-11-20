package com.mccal.folio.duet

import org.json.JSONObject

/**
 * One look for Duet, as numbers over the one fold shader ([DuetShader]). A new look is one entry in [DuetStyles]:
 * the picker, the renderer and the package validator all read that list, so they can't disagree about what exists
 * (the catalog idea is from FoldFX by Gary Keeler, MIT; see third_party/duet/PROVENANCE.md).
 */
data class DuetStyle(
    val id: String,
    /** What the picker calls it. */
    @androidx.annotation.StringRes val name: Int,
    /** Blur strength, 1 = Folio's original fold. */
    val frost: Float,
    /** How dark the moving half gets toward its outer edge, 1 = original. */
    val darkening: Float,
    /** 0 = flat glass (original); above 0 the moving half tilts toward you in perspective (hingewave's model). */
    val perspective: Float,
    /** Share of the blur already there at the hinge, so the half frosts as one pane (hingewave's `blurFloor`). */
    val blurFloor: Float,
    /**
     * Duo Fold Live's Classic Glass: iphone-duo's eye at a fixed distance looking at the folding strip, instead of
     * hingewave's tilted pane. [perspective] then scales its tilt.
     */
    val classic: Boolean = false,
    /**
     * How far past the hinge the frost carries into the still half, as a share of the whole screen (Duo Fold Live's
     * seam blend, 7% by default there), so the frost fades out instead of stopping at a hard line. 0 = hard edge.
     */
    val softEdge: Float = 0f,
    /**
     * How frosted the open screen is when its panel lights, 1 = fully. Apple's frost follows the hinge; the Fold8 lights
     * its inner panel at about 125 degrees, where Apple's would be about 61%, so iPhone Duo starts there.
     */
    val startAt: Float = 1f,
    /**
     * Duo Clip: the moving half blurs at once and shades from its outer edge inward, each on its own curve of the fold
     * ([DuetClip]); the open half stays sharp and bright and the whole screen does not dim. The cover is the same look with the whole screen as the moving half.
     * [frost] and [darkening] still scale it. The other looks ignore this.
     */
    val clip: Boolean = false,
)

/**
 * Duo Clip's curves, measured from the 16 Nov 2026 Duo clip (4K, 60 fps) and not just read by eye. All run on how far the
 * fold has gone, 0 flat to 1 closed, and the shade also on how far a point is from the hinge, 0 at the hinge to 1 at the
 * outer edge. Frame by frame (closing, from flat):
 *  - Blur: the half is sharp, then within one frame (17 ms) it is blurred about half as hard as it will get, and it is at its
 *    full blur 0.2 s later while the half has barely lifted. Opening is the same backward: heavy blur until the last few
 *    degrees, then sharp. Here it is a smooth rise (half by about 7 degrees, full by about 22): a hard step would flicker if
 *    the hinge hovers at the threshold, and on the Fold8 it read as a snap.
 *  - Shade: starts about 0.15 s after the blur and runs over about 0.5 s. The outer edge goes dark first (down to about a
 *    fifth of its light by 26 degrees) while the strip next to the hinge keeps its light until about 50 degrees, and the half
 *    is black when closed. The half that stays open never changes, and nothing else on the screen dims.
 * The shader in [DuetShader] repeats this arithmetic; DuetClipTest keeps the two honest.
 */
object DuetClip {
    /** The fold's own strength, an ease over the hinge angle held a little short of full, as FoldTransition makes it. */
    const val HOLD = .9f
    /** The blur steps up to [BLUR_JUMP] of its full strength between these two folds, then rises to full by [BLUR_FULL_AT]. */
    const val BLUR_FROM = 0f
    const val BLUR_JUMP_AT = .04f
    const val BLUR_JUMP = .5f
    const val BLUR_FULL_AT = .12f
    /** The full blur's Gaussian sigma, as a share of the screen's width: the half goes to shapes, as the clip's does. */
    const val BLUR_SIGMA = .031f
    /** How far across the hinge the blurred half fades out, as a share of the screen's length, so there is no hard line. */
    const val HINGE_SOFT = .02f
    /**
     * The outer edge's shade is 1 - e^(-fold / SHADE_TAU): about 0.4 at 6 degrees, 0.5 at 11, 0.7 at 26 and 0.8 at 40, close to what
     * the clip shows. A point [SHADE_REACH] of the way in is as dark as the outer edge's shade raised to this power (the middle of
     * the half is about half as dark as its edge).
     */
    const val SHADE_TAU = .125f
    const val SHADE_REACH = .7f
    /** The hinge strip waits, then follows the rest of the half down to black. */
    const val SHADE_ALL_FROM = .22f
    const val SHADE_ALL_TO = .5f
    /** Up to this share of the half's width next to the hinge is spared the early shade. */
    const val HINGE_SPARED = .12f
    /** The shade stops a little short of black, as the clip does. */
    const val SHADE_PEAK = .97f
    /**
     * The frosted reflection: with the half most of the way over and dark, the clip shows a mirror image of the lit half on it,
     * as glass does. It comes in from [REFLECT_IN_FROM] to [REFLECT_IN_TO] of the fold, is at [REFLECT_PEAK] of the lit half's
     * light between that and [REFLECT_OUT_FROM], and is gone by [REFLECT_OUT_TO] as the half closes. It is strongest at the
     * hinge and fades out toward the outer edge.
     */
    const val REFLECT_IN_FROM = .2f
    const val REFLECT_IN_TO = .32f
    const val REFLECT_OUT_FROM = .42f
    const val REFLECT_OUT_TO = .58f
    const val REFLECT_PEAK = .42f
    const val REFLECT_REACH = 1.4f

    private fun smooth(from: Float, to: Float, x: Float): Float {
        val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** How far the fold has gone, given the strength [m] the effect is driven with (the ease taken back out). */
    fun progress(m: Float): Float {
        val x = (m / HOLD).coerceIn(0f, 1f)
        // acos(1 - 2x) = 2 asin(sqrt x), which stays exact for the very small x of a fold that has only just begun.
        return (2.0 * kotlin.math.asin(kotlin.math.sqrt(x.toDouble())) / Math.PI).toFloat()
    }

    /** Share of the blur at [progress]: 0 flat, about half a few degrees in, 1 from about 18 degrees on. */
    fun blur(progress: Float): Float =
        BLUR_JUMP * smooth(BLUR_FROM, BLUR_JUMP_AT, progress) + (1f - BLUR_JUMP) * smooth(BLUR_JUMP_AT, BLUR_FULL_AT, progress)

    /** How much of the mirrored lit half shows at [progress], for a point [edge] of the way from the hinge (0) to the outer edge (1). */
    fun reflect(progress: Float, edge: Float): Float {
        val window = smooth(REFLECT_IN_FROM, REFLECT_IN_TO, progress) * (1f - smooth(REFLECT_OUT_FROM, REFLECT_OUT_TO, progress))
        return REFLECT_PEAK * window * Math.pow((1f - edge.coerceIn(0f, 1f)).toDouble(), REFLECT_REACH.toDouble()).toFloat()
    }

    /** Share of the shade at [progress] for a point [edge] of the way from the hinge (0) to the outer edge (1). */
    fun shade(progress: Float, edge: Float): Float {
        val away = ((edge - HINGE_SPARED) / (1f - HINGE_SPARED)).coerceIn(0f, 1f)
        val early = (1.0 - Math.exp(-progress / SHADE_TAU.toDouble())).toFloat() * Math.pow(away.toDouble(), SHADE_REACH.toDouble()).toFloat()
        val late = smooth(SHADE_ALL_FROM, SHADE_ALL_TO, progress)
        return maxOf(early, late) * SHADE_PEAK
    }
}

object DuetStyles {
    /**
     * Apple's iPhone Duo, 1:1 with chuspeeism/iphone-duo's model of Apple's own device: only the cover half moves,
     * seen from a fixed eye straight on (Classic Glass's projection), the 72 px blur and 2x shade, a hard edge at the
     * hinge because the camera half never moves, and no screenshot, because Folio's inner right page is the cover's
     * page. It starts at the frost the real hinge angle gives when the inner panel lights.
     */
    val IPHONE = DuetStyle("iphone", com.mccal.folio.R.string.duet_style_iphone, frost = 1f, darkening = 1f, perspective = 1f,
        blurFloor = 0f, classic = true, softEdge = 0f, startAt = .61f)
    /** Folio's fold as it looked before iPhone Duo became the default (28 Sep 2026). */
    val DUO = DuetStyle("duo", com.mccal.folio.R.string.duet_style_duo, frost = 1f, darkening = 1f, perspective = 0f, blurFloor = 0f)
    /** The moving half tilts toward you and frosts as one pane: hingewave's tuned defaults. */
    val DEEP = DuetStyle("deep", com.mccal.folio.R.string.duet_style_deep, frost = 1f, darkening = 1f, perspective = .75f, blurFloor = .3f,
        softEdge = .07f)
    val SUBTLE = DuetStyle("subtle", com.mccal.folio.R.string.duet_style_subtle, frost = .55f, darkening = .5f, perspective = 0f, blurFloor = 0f,
        softEdge = .07f)
    /** No frost at all, just the shade. The cheapest look, and the same on every Android version. */
    val MINIMAL = DuetStyle("minimal", com.mccal.folio.R.string.duet_style_minimal, frost = 0f, darkening = .8f, perspective = 0f, blurFloor = 0f)
    /** Duo Fold Live's Classic Glass, with its 1.25× blur on the inner screen. */
    val CLASSIC = DuetStyle("classic", com.mccal.folio.R.string.duet_style_classic, frost = 1.25f, darkening = 1f, perspective = 1f,
        blurFloor = 0f, classic = true, softEdge = .07f)

    /**
     * The look of the Duo clip (see [DuetClip]): only the moving half changes, blurred and shaded evenly, and the open
     * half and the screen as a whole stay as they are. It starts where the Fold8 lights its inner panel, about 125 degrees,
     * which is 30% of the way folded: full blur and about two fifths of the shade. The default.
     */
    val CLIP = DuetStyle("clip", com.mccal.folio.R.string.duet_style_clip, frost = 1f, darkening = 1f, perspective = 1f, blurFloor = 0f,
        startAt = .19f, clip = true)

    val all = listOf(CLIP, IPHONE, DUO, CLASSIC, DEEP, SUBTLE, MINIMAL)

    /** What a new install and an unreadable save start on. */
    val DEFAULT = CLIP

    fun from(id: String?): DuetStyle = all.firstOrNull { it.id == id } ?: DEFAULT

    /** With Reduce Motion on, any style becomes a shade: its darkness stays, frost, tilt and soft edge go. */
    fun reducedMotion(style: DuetStyle) = style.copy(frost = 0f, perspective = 0f, blurFloor = 0f, softEdge = 0f, classic = false, clip = false)
}

/** Which way the animation plays (Duo Fold Live's Fold-Only and Unfold-Only modes). */
enum class DuetDirection(val id: String, @androidx.annotation.StringRes val label: Int) {
    BOTH("both", com.mccal.folio.R.string.duet_direction_both),
    OPENING("opening", com.mccal.folio.R.string.duet_direction_opening),
    CLOSING("closing", com.mccal.folio.R.string.duet_direction_closing);

    fun allows(opening: Boolean) = when (this) { BOTH -> true; OPENING -> opening; CLOSING -> !opening }

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: BOTH
    }
}

/**
 * What a person (or a package) has chosen for Duet. The sliders are multipliers on the style's own numbers, so 1 is
 * "as the style comes" and switching style keeps a person's adjustment. Intensity and the snapshot style stay where
 * they always were (`foldIntensity`, `foldSnapshot`) so older backups keep working.
 */
data class DuetOptions(
    val style: String = DuetStyles.DEFAULT.id,
    val frost: Float = 1f,
    val darkening: Float = 1f,
    val perspective: Float = 1f,
    val direction: String = DuetDirection.BOTH.id,
    /** The Crackgate joke (a pretend hinge fault drawn over the screen): off to start with, and only reachable while its feature gate is open. */
    val crackgate: Boolean = false,
    /** Whether the joke makes a few slow changes after it appears; on to start with. */
    val crackgateGlitchy: Boolean = true,
) {
    val look: DuetStyle get() = DuetStyles.from(style)
    val plays: DuetDirection get() = DuetDirection.from(direction)

    /** The numbers the shader draws with. */
    fun resolved(): DuetStyle = look.let {
        it.copy(frost = it.frost * frost, darkening = it.darkening * darkening, perspective = (it.perspective * perspective).coerceIn(0f, 1f))
    }

    fun toJson(): JSONObject = JSONObject().put("style", style)
        .put("frost", frost.toDouble()).put("darkening", darkening.toDouble()).put("perspective", perspective.toDouble())
        .put("direction", direction).put("crackgate", crackgate).put("crackgateGlitchy", crackgateGlitchy)

    companion object {
        val FROST = 0f..2f
        val DARKENING = 0f..2f
        val PERSPECTIVE = 0f..1.33f

        /** Reads saved options; anything missing or out of range falls back to the default, never throws. */
        fun fromJson(json: JSONObject?): DuetOptions {
            if (json == null) return DuetOptions()
            fun f(key: String, range: ClosedFloatingPointRange<Float>) =
                json.optDouble(key, 1.0).toFloat().takeIf { !it.isNaN() }?.coerceIn(range) ?: 1f
            return DuetOptions(
                style = DuetStyles.from(json.optString("style")).id,
                frost = f("frost", FROST), darkening = f("darkening", DARKENING), perspective = f("perspective", PERSPECTIVE),
                direction = DuetDirection.from(json.optString("direction")).id,
                crackgate = json.optBoolean("crackgate", false), crackgateGlitchy = json.optBoolean("crackgateGlitchy", true),
            )
        }
    }
}
