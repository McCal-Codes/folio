package com.mccal.folio

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * A Big Clock's own look, kept separately from the folder-style `folderColors`/`folderSizes` maps since more than
 * one Big Clock can be on Home (so this is keyed by [WidgetPlacement.slot], not a shared id). `mode` is one of
 * AUTO (today's whole-wallpaper ink, unchanged), WALLPAPER (a vivid color sampled from the picture under the
 * clock), WHITE (forced), or CUSTOM (one of the picture's own readable colors, picked by [customIndex]).
 */
data class BigClockStyle(val mode: String = "AUTO", val customIndex: Int = 0, val weight: Int = 600)

internal fun BigClockStyle?.orDefault() = this ?: BigClockStyle()

/** The real styles (by widget slot) and a setter, provided once where `model`/`state` are in scope (LauncherScreen).
 * A preview context that provides no value (Settings' widget preview, Today View) reads an empty map and a no-op
 * setter, the same fallback shape `LocalHomeApps` already uses. */
internal class BigClockStyles(val bySlot: Map<Int, BigClockStyle>, val set: (Int, BigClockStyle?) -> Unit)
// compositionLocalOf, not static: a static local recomposes everything under its provider when the value changes,
// and this changes on every tick of the weight slider - only the clocks that read it should redraw.
internal val LocalBigClockStyles = androidx.compose.runtime.compositionLocalOf { BigClockStyles(emptyMap()) { _, _ -> } }

// ---- contrast math, ported from the Mockup Lab's adaptive-clock.js (same WCAG relative-luminance formula) ----
private fun linearize(channel: Float): Float {
    val c = channel / 255f
    return if (c <= .03928f) c / 12.92f else ((c + .055f) / 1.055f).toDouble().pow(2.4).toFloat()
}
private fun luminance(r: Float, g: Float, b: Float) = .2126f * linearize(r) + .7152f * linearize(g) + .0722f * linearize(b)
internal fun contrastRatio(a: Float, b: Float) = (max(a, b) + .05f) / (min(a, b) + .05f)

internal data class RegionStats(val mean: Float, val p15: Float, val p85: Float, val r: Float, val g: Float, val b: Float)
internal data class ClockInkSample(val under: RegionStats, val whole: RegionStats, val suggestions: List<Int>)

/** The worst case for an ink: a light ink fights the brightest 15% behind it, a dark one the darkest 15%. */
private fun worstCase(inkLuminance: Float, region: RegionStats) =
    min(contrastRatio(inkLuminance, if (inkLuminance > .18f) region.p85 else region.p15), contrastRatio(inkLuminance, region.mean))

/** How a white or dark ink would read against a sampled region: whichever holds more contrast wins. */
internal fun pickInk(region: RegionStats): Triple<Boolean, Float, Float> {
    val white = contrastRatio(1f, region.p85)
    val dark = contrastRatio(.006f, region.p15)
    return Triple(dark > white, max(white, dark), white)
}

/**
 * Downsamples [bitmap] to a small grid once (cheap, same idea as the lab's canvas-based sampler) and reads back
 * stats for the fraction of it under [box] (0..1 of the whole picture) and for the whole picture - plus five
 * suggested colors, the most common vivid-enough buckets in the whole picture, each already bumped toward
 * [vividTint]'s saturation/brightness floor.
 */
internal fun sampleClockRegion(bitmap: Bitmap, box: Rect): ClockInkSample {
    val cw = 72; val ch = 128
    val small = Bitmap.createScaledBitmap(bitmap, cw, ch, true)
    val pixels = IntArray(cw * ch)
    small.getPixels(pixels, 0, cw, 0, 0, cw, ch)
    if (small !== bitmap) small.recycle()

    fun stats(x0: Int, y0: Int, x1: Int, y1: Int): RegionStats {
        var sumR = 0f; var sumG = 0f; var sumB = 0f; var n = 0
        val lums = ArrayList<Float>((x1 - x0).coerceAtLeast(1) * (y1 - y0).coerceAtLeast(1))
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = pixels[y * cw + x]
            val r = (p shr 16 and 0xFF).toFloat(); val g = (p shr 8 and 0xFF).toFloat(); val b = (p and 0xFF).toFloat()
            sumR += r; sumG += g; sumB += b; n++
            lums += luminance(r, g, b)
        }
        if (n == 0) return RegionStats(0f, 0f, 0f, 255f, 255f, 255f)
        lums.sort()
        return RegionStats(lums.sum() / n, lums[(n * .15f).toInt().coerceIn(0, n - 1)], lums[(n * .85f).toInt().coerceIn(0, n - 1)], sumR / n, sumG / n, sumB / n)
    }
    val bx0 = (box.left * cw).toInt().coerceIn(0, cw - 1); val bx1 = (box.right * cw).toInt().coerceIn(bx0 + 1, cw)
    val by0 = (box.top * ch).toInt().coerceIn(0, ch - 1); val by1 = (box.bottom * ch).toInt().coerceIn(by0 + 1, ch)
    val under = stats(bx0, by0, bx1, by1)
    val whole = stats(0, 0, cw, ch)

    // Suggestions: coarse color buckets over the whole picture, most common first, skipping near-grey/near-black.
    val buckets = HashMap<Int, IntArray>() // key -> [n, r, g, b]
    for (p in pixels) {
        val r = p shr 16 and 0xFF; val g = p shr 8 and 0xFF; val b = p and 0xFF
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        if (mx < 60 || (mx - mn) < 28) continue
        val key = (r shr 6) * 100 + (g shr 6) * 10 + (b shr 6)
        val e = buckets.getOrPut(key) { IntArray(4) }
        e[0]++; e[1] += r; e[2] += g; e[3] += b
    }
    val suggestions = buckets.values.sortedByDescending { it[0] }.take(5)
        .map { vividTint(android.graphics.Color.rgb(it[1] / it[0], it[2] / it[0], it[3] / it[0])) }
    return ClockInkSample(under, whole, suggestions)
}

/** Colors from [suggestions] that still hold WCAG AA (4.5:1) under the clock, in the picture's own ranked order. */
internal fun ClockInkSample.readableSuggestions(): List<Int> = suggestions.filter { argb ->
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(argb, hsv)
    worstCase(luminance((argb shr 16 and 0xFF).toFloat(), (argb shr 8 and 0xFF).toFloat(), (argb and 0xFF).toFloat()), under) >= 4.5f
}

/** What `BigClockCard` should actually draw: an ink (and, with a shade, a soft dark halo so it stays readable). */
internal data class ClockInkResult(val color: Color, val shade: Boolean, val ratio: Float)

internal fun resolveClockInk(style: BigClockStyle, sample: ClockInkSample?, fallbackDark: Boolean): ClockInkResult {
    if (sample == null) return ClockInkResult(if (fallbackDark) FolioColors.SecondaryBackground else Color.White, false, 21f)
    when (style.mode) {
        "WHITE" -> { /* always offered, no sampling needed for the color itself */ }
        "WALLPAPER" -> {
            val vivid = vividTint(android.graphics.Color.rgb(sample.under.r.toInt(), sample.under.g.toInt(), sample.under.b.toInt()))
            val ratio = worstCase(luminance((vivid shr 16 and 0xFF).toFloat(), (vivid shr 8 and 0xFF).toFloat(), (vivid and 0xFF).toFloat()), sample.under)
            if (ratio >= 4.5f) return ClockInkResult(Color(vivid), false, ratio)
            // Falls back to Automatic below when the wallpaper color would be too faint here.
        }
        "CUSTOM" -> {
            val readable = sample.readableSuggestions()
            val argb = readable.getOrNull(style.customIndex) ?: readable.firstOrNull()
            if (argb != null) {
                val ratio = worstCase(luminance((argb shr 16 and 0xFF).toFloat(), (argb shr 8 and 0xFF).toFloat(), (argb and 0xFF).toFloat()), sample.under)
                return ClockInkResult(Color(argb), false, ratio)
            }
        }
    }
    if (style.mode == "WHITE") {
        val ratio = contrastRatio(1f, sample.under.p85)
        if (ratio >= 4.5f) return ClockInkResult(Color.White, false, ratio)
        return ClockInkResult(Color.White, true, contrastRatio(1f, sample.under.p85 * .6f.toDouble().pow(2.2).toFloat()))
    }
    // Automatic (and Wallpaper/Custom falling back to it): whichever ink holds more contrast under the clock.
    val (dark, ratio, _) = pickInk(sample.under)
    if (ratio >= 4.5f) return ClockInkResult(if (dark) FolioColors.SecondaryBackground else Color.White, false, ratio)
    // Neither ink reaches 4.5:1 over a busy picture: a soft shade goes behind the digits, white ink over it.
    return ClockInkResult(Color.White, true, contrastRatio(1f, sample.under.p85 * .6f.toDouble().pow(2.2).toFloat()))
}

/**
 * Samples the picture under [box] (fractions of the whole background, 0..1) whenever the background or the box
 * changes. Only runs when Folio's own picture is behind Home - Android gives no API to read the system
 * wallpaper's pixels, so Wallpaper/Custom modes are unavailable then and this returns null.
 */
@Composable
internal fun rememberClockInkSample(box: Rect, enabled: Boolean): ClockInkSample? {
    val context = LocalContext.current
    val revision = LauncherBackgroundCache.revision.intValue
    var sample by remember { mutableStateOf<ClockInkSample?>(null) }
    LaunchedEffect(box, revision, enabled) {
        if (!enabled || box.isEmpty) { sample = null; return@LaunchedEffect }
        sample = withContext(Dispatchers.Default) {
            val bitmap = loadLauncherBackground(context) ?: return@withContext null
            runCatching { sampleClockRegion(bitmap, box) }.getOrNull()
        }
    }
    return sample
}

/**
 * Color and weight for a Big Clock, opened from its "Customize" row. The picker doesn't know exactly where on
 * screen the clock sits (that's `BigClockCard`'s own live measurement), so its Custom suggestions are read from
 * the whole picture rather than the precise patch under the clock - a close approximation of what the clock
 * itself will actually draw, not a second copy of its exact math.
 */
@Composable
internal fun ClockStyleSheet(
    placement: WidgetPlacement?, style: BigClockStyle?, onStyle: (BigClockStyle?) -> Unit,
    systemWallpaper: Boolean, onClose: () -> Unit,
) {
    val context = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { HomePeek.value = true; onDispose { HomePeek.value = false } }
    var s by remember(placement?.slot) { mutableStateOf(style.orDefault()) }
    fun push(next: BigClockStyle) { s = next; onStyle(next) }
    val revision = LauncherBackgroundCache.revision.intValue
    var sample by remember { mutableStateOf<ClockInkSample?>(null) }
    LaunchedEffect(revision, systemWallpaper) {
        sample = if (systemWallpaper) null else withContext(Dispatchers.Default) {
            loadLauncherBackground(context)?.let { runCatching { sampleClockRegion(it, Rect(0f, 0f, 1f, 1f)) }.getOrNull() }
        }
    }
    val readable = remember(sample) { sample?.readableSuggestions() ?: emptyList() }
    Column(Modifier.fillMaxWidth().padding(horizontal = FolioSpace.XL.dp, vertical = FolioSpace.TINY.dp),
        verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.clock_style), Modifier.weight(1f), color = Color.White, fontSize = FolioType.TITLE.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.size(FolioTouch.MIN.dp).clickable(onClickLabel = stringResource(R.string.close), onClick = onClose),
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        SheetGroupLabel(stringResource(R.string.color))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            IosChip(selected = s.mode == "AUTO", onClick = { push(s.copy(mode = "AUTO")) }, label = { Text(stringResource(R.string.automatic)) })
            IosChip(selected = s.mode == "WALLPAPER", onClick = { if (!systemWallpaper) push(s.copy(mode = "WALLPAPER")) },
                label = { Text(stringResource(R.string.wallpaper), color = if (systemWallpaper) Color.White.copy(alpha = .3f) else Color.Unspecified) })
            IosChip(selected = s.mode == "WHITE", onClick = { push(s.copy(mode = "WHITE")) }, label = { Text(stringResource(R.string.white)) })
            IosChip(selected = s.mode == "CUSTOM", onClick = { if (!systemWallpaper) push(s.copy(mode = "CUSTOM")) },
                label = { Text(stringResource(R.string.custom), color = if (systemWallpaper) Color.White.copy(alpha = .3f) else Color.Unspecified) })
        }
        if (s.mode == "CUSTOM" && !systemWallpaper) {
            Text(stringResource(R.string.suggested_from_the_picture), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
                readable.forEachIndexed { i, argb ->
                    val selected = s.customIndex == i
                    Box(Modifier.size(FolioTouch.MIN.dp).clickable { push(s.copy(customIndex = i)) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(argb))
                            .then(if (selected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier))
                    }
                }
            }
        }
        if (systemWallpaper) Text(stringResource(R.string.wallpaper_from_folios_own_pictures_only), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
        SheetGroupLabel(stringResource(R.string.weight))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(value = s.weight.toFloat(), onValueChange = { push(s.copy(weight = it.toInt())) },
                valueRange = 100f..900f, steps = 15, modifier = Modifier.weight(1f))
            Text(s.weight.toString(), color = Color.White, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
        }
        Text(stringResource(R.string.match_picture), color = Color.White, fontSize = FolioType.FOOTNOTE.sp)
        Text(stringResource(R.string.automatic_reads_the_picture_under_the_clock), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
        Spacer(Modifier.height(16.dp))
    }
}
