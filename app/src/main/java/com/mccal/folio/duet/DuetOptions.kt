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
)

object DuetStyles {
    /**
     * Apple's iPhone Duo, 1:1 with chuspeeism/iphone-duo's model of Apple's own device: only the cover half moves,
     * seen from a fixed eye straight on (Classic Glass's projection), the 72 px blur and 2x shade, a hard edge at the
     * hinge because the camera half never moves, and no screenshot, because Folio's inner right page is the cover's
     * page. It starts at the frost the real hinge angle gives when the inner panel lights. The default.
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

    val all = listOf(IPHONE, DUO, CLASSIC, DEEP, SUBTLE, MINIMAL)

    fun from(id: String?): DuetStyle = all.firstOrNull { it.id == id } ?: IPHONE

    /** With Reduce Motion on, any style becomes a shade: its darkness stays, frost, tilt and soft edge go. */
    fun reducedMotion(style: DuetStyle) = style.copy(frost = 0f, perspective = 0f, blurFloor = 0f, softEdge = 0f, classic = false)
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
    val style: String = DuetStyles.IPHONE.id,
    val frost: Float = 1f,
    val darkening: Float = 1f,
    val perspective: Float = 1f,
    val direction: String = DuetDirection.BOTH.id,
) {
    val look: DuetStyle get() = DuetStyles.from(style)
    val plays: DuetDirection get() = DuetDirection.from(direction)

    /** The numbers the shader draws with. */
    fun resolved(): DuetStyle = look.let {
        it.copy(frost = it.frost * frost, darkening = it.darkening * darkening, perspective = (it.perspective * perspective).coerceIn(0f, 1f))
    }

    fun toJson(): JSONObject = JSONObject().put("style", style)
        .put("frost", frost.toDouble()).put("darkening", darkening.toDouble()).put("perspective", perspective.toDouble())
        .put("direction", direction)

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
            )
        }
    }
}
