package com.mccal.folio

import androidx.compose.ui.Alignment

/** Which corner of an icon its notification badge sits over. */
internal enum class BadgeCorner(val alignment: Alignment) { TOP_END(Alignment.TopEnd), TOP_START(Alignment.TopStart) }

/**
 * Where an icon's badge goes: over [corner], hanging a third of its height past the icon's edges like iOS, but never
 * reaching further right than [endLimit] dp from the icon's left edge (something else owns the space past it, like
 * the "..." button on an app inside an open folder). Values are in dp, plain floats so the math is testable.
 */
internal data class BadgePlacement(val corner: BadgeCorner = BadgeCorner.TOP_END, val endLimit: Float = Float.POSITIVE_INFINITY) {
    /** How far the badge moves sideways from its corner: outwards by a third of its height, pulled back to [endLimit]. */
    fun offsetX(iconWidth: Float, badgeHeight: Float): Float = when (corner) {
        BadgeCorner.TOP_START -> -OVERHANG * badgeHeight
        BadgeCorner.TOP_END -> minOf(OVERHANG * badgeHeight, endLimit - iconWidth)
    }

    /** How far the badge moves up from its corner. */
    fun offsetY(badgeHeight: Float): Float = -OVERHANG * badgeHeight

    companion object {
        const val OVERHANG = .32f
        val DEFAULT = BadgePlacement()
    }
}
