package com.mccal.folio

/**
 * How heavy the status rings are drawn. Stronger rings are thicker and have a clearer empty track, for people who
 * find the thin rings hard to tell apart at a glance. They are on when the person asks, and also while Android's own
 * Bold text is on, because someone who asked for heavier text wants heavier rings too.
 */
internal data class RingLook(val strokeScale: Float, val trackAlpha: Float) {
    companion object {
        val NORMAL = RingLook(1f, .22f)
        val STRONG = RingLook(1.45f, .40f)
        /** Used where the stroke is already wide (the Gauge), so it cannot grow into its neighbors. */
        const val GAUGE_SCALE = 1.25f

        /**
         * Bold text is on for a defined adjustment above zero. `Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED` is
         * `Int.MAX_VALUE`, which a device or preview can report when it doesn't know, and is not Bold text.
         */
        fun boldTextOn(fontWeightAdjustment: Int) = fontWeightAdjustment in 1 until android.content.res.Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED

        /** [fontWeightAdjustment] is `Configuration.fontWeightAdjustment`: above zero while Bold text is on. */
        fun of(strongRings: Boolean, fontWeightAdjustment: Int) = if (strongRings || boldTextOn(fontWeightAdjustment)) STRONG else NORMAL
    }
}
