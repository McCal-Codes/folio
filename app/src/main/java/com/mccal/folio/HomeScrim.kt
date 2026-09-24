package com.mccal.folio

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache

/**
 * iOS's legibility gradient: a soft darkening at the top and bottom of Home, over the background and under everything
 * Folio draws, so white text still reads on a pale wallpaper (A11Y-9, DES-16). iOS does this rather than only shipping
 * dark wallpapers, and Folio is about to ship pale ones (dawn beaches, cream paintings) that white text disappears
 * into.
 *
 * Both ends, and only the ends, because of what Home really draws:
 *
 * - The top holds the clock and date. The Side Bar's own text sits on frosted glass, but not with the capsule turned
 *   off (`StatusStyle.background`), and the Clock and Date widgets draw straight on the wallpaper with no shadow at
 *   all ([HomeWidgets.kt]).
 * - The bottom holds the page dots ([HomeInk.faint], white at .4), the Discover and App Library buttons (white at .6
 *   and .65) and the "3 / 7" page counter. A11Y-9 names white at .55 over a bright wallpaper as the case that needs a
 *   scrim, and these are below it.
 * - The middle is the grid, and its app labels already carry [HomeInk.labelShadow] per glyph (black at .55, 3px
 *   blur), so they need no wash. That middle third is also the part of a wallpaper worth looking at, so it is left
 *   alone.
 *
 * The falloff is a raised cosine rather than a straight line: smooth at both ends, so there is no edge where the
 * gradient stops, and it keeps most of its strength over the outer half of each band where the text actually is.
 */
@Immutable
internal data class HomeScrim(
    /** Darkness at the very top edge, 0 for none. */
    val top: Float = 0f,
    /** Darkness at the very bottom edge, 0 for none. */
    val bottom: Float = 0f,
) {
    val draws: Boolean get() = top > 0f || bottom > 0f

    internal companion object {
        /**
         * How tall each band is, as a fraction of the window. A third each, so the middle third is untouched; the
         * cosine falloff has already dropped under a tenth of the peak by four fifths of the way in, so what you
         * can actually see is about the outer quarter at each end. A fraction, not a dp height, so it holds on a
         * cover strip, a phone, an unfolded screen and a small window alike (ADP-1).
         */
        const val BAND = .34f

        /**
         * Darkest values, at the very edges. Deliberately no stronger than the .3 black that
         * "Dark appearance dims wallpaper" has shipped over the whole screen since 0.6.x: whatever else is true, a
         * gradient that peaks below that only at the two edges and reaches nothing across the middle darkens Home
         * strictly less than a setting Folio already calls a dim rather than a filter.
         *
         * The bottom is the stronger of the two because the weakest text is there: the page dots and the two round
         * controls beside them have no shadow and no glass behind them.
         *
         * Both were then settled by the numbers: over #D8CEB6, the palest colour Folio itself ships (the sand at the
         * foot of the dunes), they take white to 3.1:1 at the top edge and 3.3:1 at the bottom, so A11Y-9's 3:1 for
         * large text, icons and control boundaries is met. 4.5:1 for body-size white text over that same sand would
         * take about .43, which is a filter and not a scrim; that case belongs to Automatic dark text instead, and
         * [HomeScrimTest] holds both facts.
         */
        const val TOP = .29f
        const val BOTTOM = .32f

        /** Stops along each band. Seven samples of the cosine; between them a straight line is off by under .01. */
        const val STOPS = 7

        val None = HomeScrim()

        /**
         * The scrim for Home right now.
         *
         * [darkText] is "Text on Home" as it resolved (set by hand, or picked from the wallpaper): with dark ink on
         * Home a dark scrim would take contrast away rather than add it, so there is no scrim at all. The two settings
         * are the two halves of one job and never both fire: a pale wallpaper that reports
         * `HINT_SUPPORTS_DARK_TEXT` gets dark text, and a pale wallpaper that doesn't (or a Light choice made by hand)
         * gets the scrim.
         *
         * [dim] is the black that "Dark appearance dims wallpaper" is already drawing over the same pixels, so the
         * scrim gives way to it instead of darkening twice: see [under].
         */
        fun of(on: Boolean, darkText: Boolean, dim: Float): HomeScrim =
            if (!on || darkText) None else HomeScrim(under(TOP, dim), under(BOTTOM, dim))

        /**
         * How dark the scrim has to be to reach [target] over a wallpaper that [dim] has already darkened, so the two
         * together land on [target] rather than on the sum of both.
         *
         * Two blacks at a and b compose to `a + b - ab`, so solving that for b gives `(target - dim) / (1 - dim)`. At
         * the dim's full .3 both of Folio's targets are already met and the scrim is nothing.
         */
        fun under(target: Float, dim: Float): Float {
            val already = dim.coerceIn(0f, 1f)
            if (already >= 1f) return 0f
            return ((target - already) / (1f - already)).coerceIn(0f, 1f)
        }

        /** [peak] darkness [fraction] of the way through a band: a raised cosine, [peak] at 0 and nothing at 1. */
        fun alphaAt(peak: Float, fraction: Float): Float {
            val t = fraction.coerceIn(0f, 1f)
            return peak * (.5f + .5f * kotlin.math.cos(Math.PI.toFloat() * t))
        }

        /** The band's colours, edge first. Black only: a tinted scrim would cast the wallpaper's colour. */
        fun ramp(peak: Float): List<Color> =
            List(STOPS) { Color.Black.copy(alpha = alphaAt(peak, it / (STOPS - 1f))) }
    }
}

/**
 * Draws [scrim] over whatever this modifier's content drew.
 *
 * Put it after the `graphicsLayer` that caches the background and the scrim goes into that same layer, so it is
 * rasterised once with the dunes or the photo and costs nothing per frame (PRF-7). Used on its own, over Android's
 * wallpaper, it is two alpha-blended gradient bands of fill a frame and no new layer: no blur, no `RenderEffect`, no
 * offscreen buffer.
 *
 * The brushes are built in [drawWithCache], so a swipe does not allocate.
 */
internal fun Modifier.homeScrim(scrim: HomeScrim): Modifier =
    if (!scrim.draws) this else this.drawWithCache {
        val band = size.height * HomeScrim.BAND
        val top = if (scrim.top > 0f && band > 0f) Brush.verticalGradient(HomeScrim.ramp(scrim.top), 0f, band) else null
        val bottom = if (scrim.bottom > 0f && band > 0f)
            Brush.verticalGradient(HomeScrim.ramp(scrim.bottom).reversed(), size.height - band, size.height) else null
        val strip = Size(size.width, band)
        onDrawWithContent {
            drawContent()
            top?.let { drawRect(it, Offset.Zero, strip) }
            bottom?.let { drawRect(it, Offset(0f, size.height - band), strip) }
        }
    }
