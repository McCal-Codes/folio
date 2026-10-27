package com.mccal.folio

/**
 * The Music widget's room arithmetic, apart from Compose so it can be tested: what the Strip keeps when it is narrow, and how tall the
 * text rows of the Card and Poster are at the person's font size. Sizes are dp; text is the FolioType token in sp, turned into dp by
 * the caller's density (so Display size and font scale both count, not a fixed number standing in for a row).
 */

/** The fewest columns the widget can be placed at: the Strip needs 230 dp to show its art, title and both controls, and two columns of a phone are about 165. */
internal const val MUSIC_MIN_COLUMNS = 3

/** A text row is this many times its size tall (Material's body lines run 1.3 to 1.4). */
internal const val MUSIC_LINE = 1.35f

/** Strip: padding at both sides, the art and its gap, a touch box per control, and the least the title may be left. */
private const val STRIP_PADDING_DP = 20f
private const val STRIP_ART_DP = 40f
private const val STRIP_ART_GAP_DP = 10f
private const val STRIP_TITLE_MIN_DP = 64f

/** What the Strip draws at a width: the art, the play button, the next button. The title is always there. */
internal data class MusicStripPlan(val art: Boolean, val play: Boolean, val next: Boolean)

/**
 * The Strip is a row, and its title takes what the rest leaves. When there is not room for [STRIP_TITLE_MIN_DP] beside everything,
 * the next button goes first, then the art; play and pause stay, because a widget that cannot pause is worse than one without a cover.
 */
internal fun musicStripPlan(widthDp: Float): MusicStripPlan {
    val touch = FolioTouch.MIN.toFloat()
    val base = STRIP_PADDING_DP + STRIP_TITLE_MIN_DP + touch
    val withArt = base + STRIP_ART_DP + STRIP_ART_GAP_DP
    return MusicStripPlan(art = widthDp >= withArt, play = true, next = widthDp >= withArt + touch)
}

/** The height of one line of [sizeSp] text in dp; [spToDp] is the density's own conversion, which includes the font scale. */
internal fun musicLineDp(sizeSp: Int, spToDp: (Float) -> Float): Float = spToDp(sizeSp * MUSIC_LINE)

/** The scrub line, its air, and one row of times. */
internal fun musicScrubberDp(spToDp: (Float) -> Float): Float = 7f + musicLineDp(FolioType.GROUP_LABEL, spToDp)

/** What the Card or Poster draws, and how tall the art is, for a height. */
internal data class MusicCardPlan(val artDp: Float, val subtitle: Boolean, val scrubber: Boolean)

internal const val MUSIC_ART_MIN_DP = 32f

/**
 * Everything but the art has a height: the title, the artist, the scrubber, the gaps ([fixedDp]) and the controls ([controlsDp]). The art gets what is
 * left. Under the least art there is room for, the scrubber goes, then the artist, so the title and controls always fit at any font size.
 * The art is then kept between the minimum and [maxArtDp].
 */
internal fun musicCardPlan(heightDp: Float, maxArtDp: Float, titleSp: Int, subtitleSp: Int, hasSubtitle: Boolean, hasScrubber: Boolean,
    fixedDp: Float, controlsDp: Float, spToDp: (Float) -> Float): MusicCardPlan {
    var subtitle = hasSubtitle
    var scrubber = hasScrubber
    fun left() = heightDp - controlsDp - fixedDp - musicLineDp(titleSp, spToDp) -
        (if (subtitle) musicLineDp(subtitleSp, spToDp) else 0f) - (if (scrubber) musicScrubberDp(spToDp) else 0f)
    if (left() < MUSIC_ART_MIN_DP && scrubber) scrubber = false
    if (left() < MUSIC_ART_MIN_DP && subtitle) subtitle = false
    return MusicCardPlan(left().coerceIn(MUSIC_ART_MIN_DP, maxOf(MUSIC_ART_MIN_DP, maxArtDp)), subtitle, scrubber)
}
