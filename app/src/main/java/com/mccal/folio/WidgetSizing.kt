package com.mccal.folio

import kotlin.math.ceil

data class WidgetSpan(val width: Int, val height: Int)

data class WidgetGridSizing(
    val columns: Int,
    val rows: Int,
    /** Distance between adjacent grid lines. A widget's content is span * pitch - gap. */
    val cellWidthDp: Float,
    /** Conservative pitch when the UI has rows of different heights. */
    val cellHeightDp: Float,
    /** Largest row pitch, used to keep a provider maximum valid across mixed-height rows. */
    val maximumCellHeightDp: Float = cellHeightDp,
    val horizontalGapDp: Float = 0f,
    val verticalGapDp: Float = 0f,
    /** Exact pitches used to publish the content rectangle for an anchored placement. */
    val topRowHeightDp: Float = cellHeightDp,
    val appRowHeightDp: Float = cellHeightDp,
)

data class WidgetContentSize(val widthDp: Float, val heightDp: Float)

fun WidgetGridSizing.contentSize(column: Int, row: Int, spanX: Int, spanY: Int): WidgetContentSize {
    require(column >= 0 && row >= 0 && spanX > 0 && spanY > 0)
    fun rowTop(value: Int) = if (value <= 2) value * topRowHeightDp
        else 2 * topRowHeightDp + (value - 2) * appRowHeightDp
    return WidgetContentSize(
        (spanX * cellWidthDp - horizontalGapDp).coerceAtLeast(1f),
        (rowTop(row + spanY) - rowTop(row) - verticalGapDp).coerceAtLeast(1f),
    )
}

data class WidgetProviderSizing(
    val minWidthDp: Float,
    val minHeightDp: Float,
    val minResizeWidthDp: Float = 0f,
    val minResizeHeightDp: Float = 0f,
    val maxResizeWidthDp: Float = 0f,
    val maxResizeHeightDp: Float = 0f,
    val targetCellWidth: Int = 0,
    val targetCellHeight: Int = 0,
    val horizontalPaddingDp: Float = 0f,
    val verticalPaddingDp: Float = 0f,
    val resizeMode: Int = RESIZE_NONE,
) {
    val canResizeHorizontally get() = resizeMode and RESIZE_HORIZONTAL != 0
    val canResizeVertically get() = resizeMode and RESIZE_VERTICAL != 0

    companion object {
        const val RESIZE_NONE = 0
        const val RESIZE_HORIZONTAL = 1
        const val RESIZE_VERTICAL = 2
    }
}

data class WidgetSpanConstraints(
    val preferred: WidgetSpan,
    val minimum: WidgetSpan,
    val maximum: WidgetSpan,
    val canResizeHorizontally: Boolean,
    val canResizeVertically: Boolean,
    val minimumFitsGrid: Boolean = true,
)

/** Derives bounded spans; minimumFitsGrid reports when provider minima exceed the grid. */
fun widgetSpanConstraints(provider: WidgetProviderSizing, grid: WidgetGridSizing): WidgetSpanConstraints? {
    require(grid.columns > 0 && grid.rows > 0)
    require(grid.cellWidthDp > 0f && grid.cellHeightDp > 0f && grid.maximumCellHeightDp > 0f)
    require(grid.horizontalGapDp >= 0f && grid.verticalGapDp >= 0f)

    require(provider.horizontalPaddingDp >= 0f && provider.verticalPaddingDp >= 0f)
    fun widthSpan(size: Float) = spanForSize(size + provider.horizontalPaddingDp, grid.cellWidthDp, grid.horizontalGapDp)
    fun heightSpan(size: Float, pitch: Float = grid.cellHeightDp) =
        spanForSize(size + provider.verticalPaddingDp, pitch, grid.verticalGapDp)
    val legacyWidth = widthSpan(provider.minWidthDp)
    val legacyHeight = heightSpan(provider.minHeightDp)

    val resizeMinWidth = widthSpan(provider.minResizeWidthDp)
    val resizeMinHeight = heightSpan(provider.minResizeHeightDp)
    val declaredMaxWidth = widthSpan(provider.maxResizeWidthDp)
    val declaredMaxHeight = heightSpan(provider.maxResizeHeightDp, grid.maximumCellHeightDp)
    val maxWidth = (if (provider.maxResizeWidthDp > 0f) declaredMaxWidth else grid.columns)
        .coerceAtLeast(resizeMinWidth)
    val maxHeight = (if (provider.maxResizeHeightDp > 0f) declaredMaxHeight else grid.rows)
        .coerceAtLeast(resizeMinHeight)
    val targetPairValid = provider.targetCellWidth in resizeMinWidth..maxWidth &&
        provider.targetCellHeight in resizeMinHeight..maxHeight
    val preferredWidth = if (targetPairValid) provider.targetCellWidth else legacyWidth
    val preferredHeight = if (targetPairValid) provider.targetCellHeight else legacyHeight
    val minWidth = minOf(preferredWidth, resizeMinWidth)
    val minHeight = minOf(preferredHeight, resizeMinHeight)
    val effectiveMinWidth = if (provider.canResizeHorizontally) minWidth else preferredWidth
    val effectiveMinHeight = if (provider.canResizeVertically) minHeight else preferredHeight
    val effectiveMaxWidth = if (provider.canResizeHorizontally) maxWidth else preferredWidth
    val effectiveMaxHeight = if (provider.canResizeVertically) maxHeight else preferredHeight
    val boundedMinWidth = effectiveMinWidth.coerceIn(1, grid.columns)
    val boundedMinHeight = effectiveMinHeight.coerceIn(1, grid.rows)
    val boundedMaxWidth = effectiveMaxWidth.coerceIn(boundedMinWidth, grid.columns)
    val boundedMaxHeight = effectiveMaxHeight.coerceIn(boundedMinHeight, grid.rows)

    return WidgetSpanConstraints(
        preferred = WidgetSpan(
            preferredWidth.coerceIn(boundedMinWidth, boundedMaxWidth),
            preferredHeight.coerceIn(boundedMinHeight, boundedMaxHeight),
        ),
        minimum = WidgetSpan(boundedMinWidth, boundedMinHeight),
        maximum = WidgetSpan(boundedMaxWidth, boundedMaxHeight),
        canResizeHorizontally = provider.canResizeHorizontally,
        canResizeVertically = provider.canResizeVertically,
        minimumFitsGrid = effectiveMinWidth <= grid.columns && effectiveMinHeight <= grid.rows,
    )
}

private fun spanForSize(sizeDp: Float, cellDp: Float, gapDp: Float): Int =
    ceil(((sizeDp.coerceAtLeast(0f) + gapDp) / cellDp).toDouble()).toInt().coerceAtLeast(1)

/**
 * Size limits for Folio's own widgets, which have no provider to ask. Suggestions resizes freely, from one cell to the
 * whole 4 x 4 grid; the others keep the defaults (null).
 */
fun builtinWidgetConstraints(id: Int): WidgetSpanConstraints? = when (id) {
    SUGGESTIONS_WIDGET -> WidgetSpanConstraints(
        preferred = WidgetSpan(2, 2), minimum = WidgetSpan(1, 1), maximum = WidgetSpan(GRID_COLUMNS, 4),
        canResizeHorizontally = true, canResizeVertically = true)
    else -> null
}
