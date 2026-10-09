package com.mccal.folio

import android.app.Activity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * The hinge as a reserved region, in window pixels. Present on a foldable's inner screen even when flat
 * ([active] false), which is enough to prefer even column counts; [active] only when partially folded.
 * [vertical] is a book-style fold; otherwise a laptop/tabletop fold.
 */
@Immutable
internal data class Hinge(val active: Boolean, val vertical: Boolean, val startPx: Int, val endPx: Int,
    /** Every hinge along the same axis, in order (a tri-fold has two); the first is [startPx]..[endPx]. */
    val spans: List<IntRange> = listOf(startPx..endPx))

internal val LocalHinge = staticCompositionLocalOf<Hinge?> { null }

@Composable
internal fun rememberHinge(activity: Activity): Hinge? {
    val info by remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
        .collectAsStateWithLifecycle(initialValue = null)
    val folds = info?.displayFeatures?.filterIsInstance<FoldingFeature>().orEmpty()
    val first = folds.firstOrNull() ?: return null
    val vertical = first.orientation == FoldingFeature.Orientation.VERTICAL
    val spans = folds.filter { it.orientation == first.orientation }
        .map { if (vertical) it.bounds.left..it.bounds.right else it.bounds.top..it.bounds.bottom }.sortedBy { it.first }
    // Avoid a hinge while it's bent, or when it splits the screen in two even flat (Android's isSeparating).
    val active = folds.any { it.state == FoldingFeature.State.HALF_OPENED || it.isSeparating }
    return Hinge(active, vertical, spans.first().first, spans.first().last, spans)
}

/** Grids on a screen with a hinge prefer an even number of columns, so no column sits on the fold. */
@Composable
internal fun evenColumnsOnHinge(columns: Int, min: Int): Int = evenColumns(columns, min, LocalHinge.current != null)

internal fun evenColumns(columns: Int, min: Int, hinge: Boolean): Int =
    if (hinge && columns % 2 == 1 && columns - 1 >= min) columns - 1 else columns

/**
 * The part of a window (along the hinge axis, in px) an overlay should use so it never sits on a hinge: the panels
 * between [hinges] ([extent] long in total). [trailing] picks the last panel (a book's trailing side, or a table's
 * bottom), otherwise the first. A panel narrower than a quarter of the window is skipped for the next best one.
 */
internal fun foldSafeSpan(hinges: List<IntRange>, extent: Int, trailing: Boolean): IntRange {
    val panels = mutableListOf<IntRange>()
    var start = 0
    for (h in hinges.sortedBy { it.first }) {
        if (h.first > start) panels += start..h.first
        start = maxOf(start, h.last)
    }
    if (extent > start) panels += start..extent
    if (panels.isEmpty()) return 0..extent
    val usable = panels.filter { it.last - it.first >= extent / 4 }.ifEmpty { listOf(panels.maxBy { it.last - it.first }) }
    return if (trailing) usable.last() else usable.first()
}

/** What an overlay is for, which decides where it goes when the device is partially folded. */
internal enum class FoldRole {
    /** Alerts and status: trailing half like a book (where they continue when closed), top half on a table (visible at a distance). */
    INFO,
    /** Sheets, menus and controls: trailing half like a book, bottom half on a table (a stable surface to tap). */
    CONTROLS,
}

/**
 * iPhone Duo-style displacement for overlays: flat, content uses the whole box; partially folded, it
 * springs into the region that suits its [role] instead of sitting on the curve. Continuous scrolling
 * content (lists, feeds) shouldn't use this; it adapts by scrolling.
 */
@Composable
internal fun FoldAvoidingBox(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.Center,
    role: FoldRole = FoldRole.CONTROLS, content: @Composable BoxScope.() -> Unit) {
    val hinge = LocalHinge.current?.takeIf { it.active }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gap = 12.dp
        val motion = FolioMotion.spring<androidx.compose.ui.unit.Dp>(.9f to 380f)
        // Window coordinates: close enough for these full-window overlays. Book folds use the trailing panel; on a
        // table, controls take the bottom panel and information the top one. Any number of hinges (tri-folds).
        val extentPx = with(density) { (if (hinge?.vertical == true) maxWidth else maxHeight).roundToPx() }
        val span = hinge?.let { foldSafeSpan(it.spans, extentPx, trailing = it.vertical || role == FoldRole.CONTROLS) }
        fun edge(px: Int, atWindowEdge: Boolean) = if (atWindowEdge) 0.dp else with(density) { px.toDp() } + gap
        val lead = span?.let { edge(it.first, it.first <= 0) } ?: 0.dp
        val trail = span?.let { edge(extentPx - it.last, it.last >= extentPx) } ?: 0.dp
        val start by animateDpAsState(if (hinge?.vertical == true) lead else 0.dp, motion, label = "fold start")
        val end by animateDpAsState(if (hinge?.vertical == true) trail else 0.dp, motion, label = "fold end")
        val top by animateDpAsState(if (hinge != null && !hinge.vertical) lead else 0.dp, motion, label = "fold top")
        val bottom by animateDpAsState(if (hinge != null && !hinge.vertical) trail else 0.dp, motion, label = "fold bottom")
        Box(Modifier.fillMaxSize().padding(start = start, end = end, top = top, bottom = bottom), contentAlignment = contentAlignment, content = content)
    }
}
