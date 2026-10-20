package com.mccal.folio

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
 * A Big Clock's own look, kept per widget slot (more than one Big Clock can be on Home, so it is not keyed by the
 * shared widget id). Nothing here is stored until the clock is customized, and the defaults draw exactly what the
 * clock drew before, so an untouched clock never changes.
 *
 * `mode`: AUTO (white or dark, whichever holds more contrast under the clock), WALLPAPER (a vivid tint of the
 * picture under it), WHITE, or CUSTOM (one of the picture's readable colors, [customIndex]). `face`: SANS, ROUNDED,
 * SERIF, MONO (Android's own fonts). `shadow`: OFF, SOFT, GLOW. `date`: LONG, SHORT, OFF. `align`: LEFT, CENTER, RIGHT.
 * `stacked`: hours over minutes. `hours`: SYSTEM (follow the phone), 12 or 24; `ampm`: show AM/PM in 12-hour (off unless asked for, so an untouched clock reads as it always did).
 */
data class BigClockStyle(
    val mode: String = "AUTO", val customIndex: Int = 0, val weight: Int = 600,
    val size: Float = 1f, val face: String = "SANS", val shadow: String = "SOFT",
    val date: String = "LONG", val showNext: Boolean = true, val align: String = "CENTER",
    val stacked: Boolean = false, val hours: String = "SYSTEM", val ampm: Boolean = false,
)

internal fun BigClockStyle?.orDefault() = this ?: BigClockStyle()

/** The real styles (by widget slot) and a setter, provided once where `model`/`state` are in scope (LauncherScreen).
 * A preview that provides no value (Settings' Home preview, Today View) reads an empty map and a no-op setter. */
internal class BigClockStyles(val bySlot: Map<Int, BigClockStyle>, val set: (Int, BigClockStyle?) -> Unit)
// compositionLocalOf, not static: a static local recomposes everything under its provider when the value changes,
// and this changes on every tick of the weight slider - only the clocks that read it should redraw.
internal val LocalBigClockStyles = androidx.compose.runtime.compositionLocalOf { BigClockStyles(emptyMap()) { _, _ -> } }

/** Where each Big Clock on Home is on screen right now (window pixels), so the editing bar can sit clear of it and read
 * the picture under the same patch the clock does. Only the real Home clocks write here, never a preview. */
internal object BigClockBounds { val bySlot = mutableStateMapOf<Int, Rect>() }

/** How the clock and its bar ease to a new value: Folio's quick spring, or an instant change with Reduce Motion on. */
@Composable
internal fun <T> clockMotion(): androidx.compose.animation.core.FiniteAnimationSpec<T> = if (LocalReduceMotion.current) snap() else FolioMotion.spring(FolioMotion.Quick)

internal fun clockFontFamily(face: String, weight: Int): FontFamily? = when (face) {
    "SERIF" -> FontFamily.Serif
    "MONO" -> FontFamily.Monospace
    "ROUNDED" -> runCatching { FontFamily(Typeface.create(Typeface.create("sans-serif-rounded", Typeface.NORMAL), weight.coerceIn(100, 900), false)) }.getOrNull()
    else -> null
}

// ---- contrast math, ported from the Mockup Lab's adaptive-clock.js (same WCAG relative-luminance formula) ----
private fun linearize(channel: Float): Float {
    val c = channel / 255f
    return if (c <= .03928f) c / 12.92f else ((c + .055f) / 1.055f).toDouble().pow(2.4).toFloat()
}
private fun luminance(r: Float, g: Float, b: Float) = .2126f * linearize(r) + .7152f * linearize(g) + .0722f * linearize(b)
private fun luminance(argb: Int) = luminance((argb shr 16 and 0xFF).toFloat(), (argb shr 8 and 0xFF).toFloat(), (argb and 0xFF).toFloat())
internal fun contrastRatio(a: Float, b: Float) = (max(a, b) + .05f) / (min(a, b) + .05f)

// ---- telling two colors apart: CIEDE2000 (CIE 142-2001), as written up by Sharma, Wu and Dalal (2005) ----

/** sRGB (D65) to CIELAB, the space CIEDE2000 measures in. */
internal fun argbToLab(argb: Int): DoubleArray {
    fun lin(c: Int) = (c / 255.0).let { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) }
    val r = lin(argb shr 16 and 0xFF); val g = lin(argb shr 8 and 0xFF); val b = lin(argb and 0xFF)
    val x = (.4124 * r + .3576 * g + .1805 * b) / .95047
    val y = .2126 * r + .7152 * g + .0722 * b
    val z = (.0193 * r + .1192 * g + .9505 * b) / 1.08883
    fun f(t: Double) = if (t > 216.0 / 24389) Math.cbrt(t) else (24389.0 / 27 * t + 16) / 116
    return doubleArrayOf(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
}

/** The CIEDE2000 color difference between two CIELAB colors (kL = kC = kH = 1). */
internal fun deltaE2000(lab1: DoubleArray, lab2: DoubleArray): Double {
    val (l1, a1, b1) = lab1.toList(); val (l2, a2, b2) = lab2.toList()
    val c7 = ((Math.hypot(a1, b1) + Math.hypot(a2, b2)) / 2).pow(7)
    val g = .5 * (1 - Math.sqrt(c7 / (c7 + 25.0.pow(7))))
    val a1p = (1 + g) * a1; val a2p = (1 + g) * a2
    val c1p = Math.hypot(a1p, b1); val c2p = Math.hypot(a2p, b2)
    fun hue(b: Double, ap: Double) = if (b == 0.0 && ap == 0.0) 0.0 else (Math.toDegrees(Math.atan2(b, ap)) + 360) % 360
    val h1 = hue(b1, a1p); val h2 = hue(b2, a2p)
    val chromaless = c1p * c2p == 0.0
    val dh = when { chromaless -> 0.0; h2 - h1 > 180 -> h2 - h1 - 360; h2 - h1 < -180 -> h2 - h1 + 360; else -> h2 - h1 }
    val dL = l2 - l1; val dC = c2p - c1p
    val dH = 2 * Math.sqrt(c1p * c2p) * Math.sin(Math.toRadians(dh / 2))
    val lMean = (l1 + l2) / 2; val cMean = (c1p + c2p) / 2
    val hMean = when { chromaless -> h1 + h2; Math.abs(h1 - h2) <= 180 -> (h1 + h2) / 2; h1 + h2 < 360 -> (h1 + h2 + 360) / 2; else -> (h1 + h2 - 360) / 2 }
    fun cos(deg: Double) = Math.cos(Math.toRadians(deg))
    val t = 1 - .17 * cos(hMean - 30) + .24 * cos(2 * hMean) + .32 * cos(3 * hMean + 6) - .2 * cos(4 * hMean - 63)
    val sL = 1 + .015 * (lMean - 50).pow(2) / Math.sqrt(20 + (lMean - 50).pow(2))
    val sC = 1 + .045 * cMean
    val sH = 1 + .015 * cMean * t
    val cMean7 = cMean.pow(7)
    val rT = -Math.sin(Math.toRadians(2 * 30 * Math.exp(-((hMean - 275) / 25).pow(2)))) * 2 * Math.sqrt(cMean7 / (cMean7 + 25.0.pow(7)))
    return Math.sqrt((dL / sL).pow(2) + (dC / sC).pow(2) + (dH / sH).pow(2) + rT * (dC / sC) * (dH / sH))
}

/**
 * Below this CIEDE2000 difference two color dots read as the same color. The usual reading of delta E (Mokrzycki and
 * Tatol, 2011, given for the older CIE76 formula, which reads larger than CIEDE2000, so 5 here is the stricter side)
 * puts "the observer notices two different colors" at 5 and above; under it the difference is one you have to look for,
 * which is no help in a row of 30 dp dots. Two periwinkle accents of one hue come out about 0.3 apart after [vividTint];
 * a periwinkle and a lavender about 19.
 */
internal const val SAME_COLOR_DELTA_E = 5.0

internal fun looksSame(a: Int, b: Int) = deltaE2000(argbToLab(a), argbToLab(b)) < SAME_COLOR_DELTA_E

/** Picture colors kept per sample (what saved indexes point into), and the most dots the edit bar shows. */
private const val SUGGESTIONS_KEPT = 8
private const val COLOR_DOTS_MAX = 5

internal data class RegionStats(val mean: Float, val p15: Float, val p85: Float, val r: Float, val g: Float, val b: Float)
internal data class ClockInkSample(val under: RegionStats, val whole: RegionStats, val suggestions: List<Int>,
    /** Built from Android's wallpaper colors, not the pixels under the clock: contrast there can't be measured. */
    val estimated: Boolean = false)

/**
 * A sample for Android's own wallpaper, whose pixels no app can read: the three main colors Android shares with every app
 * ([android.app.WallpaperColors], no permission), the first standing in for what is under the clock. The colors are offered
 * as they are, since their contrast under the clock can't be checked; [resolveClockInk] puts the soft shade behind them.
 */
internal fun sampleFromWallpaperColors(primary: Int, others: List<Int>): ClockInkSample {
    val r = (primary shr 16 and 0xFF).toFloat(); val g = (primary shr 8 and 0xFF).toFloat(); val b = (primary and 0xFF).toFloat()
    val lum = luminance(r, g, b)
    val region = RegionStats(lum, lum, lum, r, g, b)
    return ClockInkSample(region, region, (listOf(primary) + others).map(::vividTint).distinct(), estimated = true)
}

/** The worst case for an ink: a light ink fights the brightest 15% behind it, a dark one the darkest 15%. */
private fun worstCase(inkLuminance: Float, region: RegionStats) =
    min(contrastRatio(inkLuminance, if (inkLuminance > .18f) region.p85 else region.p15), contrastRatio(inkLuminance, region.mean))

/** White or dark, whichever holds more contrast against the sampled region: (dark?, its ratio). */
internal fun pickInk(region: RegionStats): Pair<Boolean, Float> {
    val white = contrastRatio(1f, region.p85)
    val dark = contrastRatio(.006f, region.p15)
    return (dark > white) to max(white, dark)
}

/** The part of a [width] by [height] picture that a window of [windowAspect] (width over height) shows when it is center-cropped. */
internal data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

internal fun centerCropRect(width: Int, height: Int, windowAspect: Float): CropRect {
    if (windowAspect <= 0f || !windowAspect.isFinite() || width <= 0 || height <= 0) return CropRect(0, 0, width, height)
    val sourceAspect = width.toFloat() / height
    val w: Int; val h: Int
    if (sourceAspect > windowAspect) { h = height; w = (h * windowAspect).toInt().coerceIn(1, width) }
    else { w = width; h = (w / windowAspect).toInt().coerceIn(1, height) }
    return CropRect((width - w) / 2, (height - h) / 2, w, h)
}

/**
 * Downsamples [bitmap] to a small grid once and reads back stats for the part of it under [box] (fractions 0..1 of the
 * picture as shown) and for the whole picture, plus up to five suggested colors: the most common vivid-enough buckets in
 * the whole picture, each lifted to [vividTint]'s saturation and brightness floor.
 *
 * Home draws the picture center-cropped to the window (drawLauncherBackground), so when [windowAspect] (width over
 * height) is given the same center crop is taken first and [box] is a fraction of what is on screen, not of the whole
 * file. Without it the bitmap is read as it is.
 */
internal fun sampleClockRegion(bitmap: Bitmap, box: Rect, windowAspect: Float = 0f): ClockInkSample {
    val cw = 72; val ch = 128
    val crop = centerCropRect(bitmap.width, bitmap.height, windowAspect)
    val visible = if (crop.width == bitmap.width && crop.height == bitmap.height) bitmap
        else Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
    val small = Bitmap.createScaledBitmap(visible, cw, ch, true)
    val pixels = IntArray(cw * ch)
    small.getPixels(pixels, 0, cw, 0, 0, cw, ch)
    if (small !== visible) small.recycle()
    if (visible !== bitmap) visible.recycle()

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

    val buckets = HashMap<Int, IntArray>() // key -> [n, r, g, b]
    for (p in pixels) {
        val r = p shr 16 and 0xFF; val g = p shr 8 and 0xFF; val b = p and 0xFF
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        if (mx < 60 || (mx - mn) < 28) continue
        val key = (r shr 6) * 100 + (g shr 6) * 10 + (b shr 6)
        val e = buckets.getOrPut(key) { IntArray(4) }
        e[0]++; e[1] += r; e[2] += g; e[3] += b
    }
    // A saved `customIndex` points into this list, so near twins stay in it and keep their places; [colorDots] leaves them
    // out of the row. More than five are kept so the row can still show five colors you can tell apart.
    val suggestions = buckets.values.sortedByDescending { it[0] }.take(SUGGESTIONS_KEPT)
        .map { vividTint(android.graphics.Color.rgb(it[1] / it[0], it[2] / it[0], it[3] / it[0])) }
    return ClockInkSample(under, whole, suggestions)
}

/** The picture's own colors that still hold WCAG AA (4.5:1) under the clock, in ranked order. */
internal fun ClockInkSample.readableSuggestions(): List<Int> = if (estimated) suggestions else suggestions.filter { worstCase(luminance(it), under) >= 4.5f }

/** The vivid tint of the picture under the clock, when it holds AA there. */
internal fun ClockInkSample.wallpaperTint(): Int? =
    vividTint(android.graphics.Color.rgb(under.r.toInt(), under.g.toInt(), under.b.toInt())).takeIf { estimated || worstCase(luminance(it), under) >= 4.5f }

/**
 * The color dots the edit bar shows after Wallpaper, each with its index in [readableSuggestions] (what `customIndex`
 * stores, so a saved choice keeps its color). One that looks the same as the Wallpaper dot is left out, since that dot
 * already offers it: with Android's wallpaper the first color always is the Wallpaper dot's own (#353's Fold8 check
 * showed the two side by side). A color too close to an earlier dot to tell apart is left out too, so the row shows at
 * most five colors you can tell apart, each still at its saved index.
 */
internal fun ClockInkSample.colorDots(): List<IndexedValue<Int>> {
    val tint = wallpaperTint()
    val shown = ArrayList<IndexedValue<Int>>()
    for (c in readableSuggestions().withIndex()) {
        if (shown.size == COLOR_DOTS_MAX) break
        if ((tint == null || !looksSame(c.value, tint)) && shown.none { looksSame(it.value, c.value) }) shown += c
    }
    return shown
}

/** The index of the dot that shows the color [style] draws: its own, or the look-alike that stands in for it in the row. */
internal fun ClockInkSample.selectedDotIndex(style: BigClockStyle): Int? {
    if (style.mode != "CUSTOM") return null
    val dots = colorDots()
    dots.firstOrNull { it.index == style.customIndex }?.let { return it.index }
    val drawn = readableSuggestions().getOrNull(style.customIndex) ?: return null
    return dots.firstOrNull { looksSame(it.value, drawn) }?.index
}

/** Whether [style] draws what the Wallpaper dot shows: Wallpaper itself, or a picked color left out for looking the same. */
internal fun ClockInkSample.wallpaperDotSelected(style: BigClockStyle): Boolean {
    if (style.mode == "WALLPAPER") return true
    if (style.mode != "CUSTOM") return false
    val tint = wallpaperTint() ?: return false
    val readable = readableSuggestions()
    val drawn = readable.getOrNull(style.customIndex) ?: readable.firstOrNull() ?: return false
    return looksSame(drawn, tint)
}

/** What `BigClockCard` draws: an ink and whether a soft dark halo goes behind the digits to keep it readable. */
internal data class ClockInkResult(val color: Color, val shade: Boolean, val ratio: Float)

private val DarkInk = FolioColors.SecondaryBackground

internal fun resolveClockInk(style: BigClockStyle, sample: ClockInkSample?, fallbackDark: Boolean): ClockInkResult {
    // No picture to read (still sampling), or Android's wallpaper with Automatic or White: the whole-wallpaper ink, or
    // White when asked for. A color from Android's wallpaper gets the soft shade, since its contrast can't be measured.
    if (sample == null || (sample.estimated && style.mode != "WALLPAPER" && style.mode != "CUSTOM"))
        return ClockInkResult(if (style.mode != "WHITE" && fallbackDark) DarkInk else Color.White, false, 21f)
    when (style.mode) {
        "WALLPAPER" -> sample.wallpaperTint()?.let { return ClockInkResult(Color(it), sample.estimated, worstCase(luminance(it), sample.under)) }
        "CUSTOM" -> {
            val readable = sample.readableSuggestions()
            val argb = readable.getOrNull(style.customIndex) ?: readable.firstOrNull()
            if (argb != null) return ClockInkResult(Color(argb), sample.estimated, worstCase(luminance(argb), sample.under))
        }
    }
    if (sample.estimated) return ClockInkResult(if (fallbackDark) DarkInk else Color.White, false, 21f)
    val shadedWhite = contrastRatio(1f, sample.under.p85 * .6f.toDouble().pow(2.2).toFloat())
    if (style.mode == "WHITE") {
        val ratio = contrastRatio(1f, sample.under.p85)
        return if (ratio >= 4.5f) ClockInkResult(Color.White, false, ratio) else ClockInkResult(Color.White, true, shadedWhite)
    }
    // Automatic (and Wallpaper/Custom falling back to it): whichever ink holds more contrast under the clock.
    val (dark, ratio) = pickInk(sample.under)
    if (ratio >= 4.5f) return ClockInkResult(if (dark) DarkInk else Color.White, false, ratio)
    // Neither ink reaches 4.5:1 over a busy picture: a soft shade goes behind the digits, white ink over it.
    return ClockInkResult(Color.White, true, shadedWhite)
}

/**
 * The clock's sample with Android's wallpaper, from the first source that has colors ([WallpaperTone.colorSource]):
 * Android's wallpaper colors, then the phone's theme colors (the system palette), else null, and nothing is made up.
 */
internal fun clockSampleFromTone(tone: WallpaperTone): ClockInkSample? = when (tone.colorSource()) {
    WallpaperColorSource.ANDROID_COLORS -> sampleFromWallpaperColors(tone.primary!!, listOfNotNull(tone.secondary, tone.tertiary))
    WallpaperColorSource.SYSTEM_PALETTE -> tone.palette!!.let { sampleFromWallpaperColors(it.accent1, listOf(it.accent2, it.accent3)) }
    WallpaperColorSource.NONE -> null
}

/**
 * Samples the picture under [box] (fractions of the whole background, 0..1) whenever the background or the box
 * changes. With Android's own wallpaper behind Home ([WallpaperTone.fromAndroid]), whose pixels no app can read, it is
 * built from the wallpaper's main colors instead ([clockSampleFromTone]), from the same tone Home's ink uses, so it
 * follows the wallpaper and is there on the first frame. Whether that is the case comes from the user's setting, never
 * from whether a Folio picture is saved: switching to Android's wallpaper keeps the picture for later, and reading it
 * then sampled a picture nobody could see (the Fold8 check of #353).
 */
@Composable
internal fun rememberClockInkSample(box: Rect, enabled: Boolean, windowAspect: Float = 0f): ClockInkSample? {
    val context = LocalContext.current
    val tone = LocalWallpaperTone.current
    if (tone.fromAndroid) {
        val estimate = remember(tone, enabled) { if (enabled) clockSampleFromTone(tone) else null }
        // Smart clock ink: with its service on, a short-lived picture of Home replaces the estimate built from Android's
        // wallpaper colors (SmartClockInk). Off, or while Folio is not in front, the estimate stands, as it always did.
        val smart by SmartInkService.connected.collectAsStateWithLifecycle()
        val inFront by FolioForeground.visible.collectAsStateWithLifecycle()
        val tick by rememberMinuteTick()
        val displayId = context.display?.displayId ?: 0
        var measured by remember { mutableStateOf<ClockInkSample?>(null) }
        LaunchedEffect(enabled && smart) { if (!(enabled && smart)) measured = null }
        LaunchedEffect(box, tone, tick, inFront, enabled, smart) {
            if (!enabled || !smart || !inFront || box.isEmpty) return@LaunchedEffect
            val fresh = SmartClockInk.sample(box, displayId) ?: return@LaunchedEffect
            measured = keepOrSwap(measured, fresh)
        }
        return if (enabled && smart) measured ?: estimate else estimate
    }
    val revision = LauncherBackgroundCache.revision.intValue
    var sample by remember { mutableStateOf<ClockInkSample?>(null) }
    LaunchedEffect(box, revision, enabled, windowAspect) {
        if (!enabled || box.isEmpty) { sample = null; return@LaunchedEffect }
        sample = withContext(Dispatchers.Default) {
            val bitmap = loadLauncherBackground(context) ?: return@withContext null
            runCatching { sampleClockRegion(bitmap, box, windowAspect) }.getOrNull()
        }
    }
    return sample
}

/** A one-tap look: a color mode, a weight, a typeface and a shadow, nothing else. */
internal data class ClockLook(val id: String, val name: Int, val mode: String, val weight: Int, val face: String, val shadow: String, val stacked: Boolean = false)

internal val ClockLooks = listOf(
    ClockLook("classic", R.string.classic, "AUTO", 600, "SANS", "SOFT"),
    ClockLook("thin", R.string.look_thin, "AUTO", 200, "SANS", "SOFT"),
    ClockLook("bold", R.string.look_bold, "AUTO", 850, "SANS", "SOFT"),
    ClockLook("tinted", R.string.tinted, "WALLPAPER", 600, "SANS", "SOFT"),
    ClockLook("soft", R.string.soft, "AUTO", 700, "ROUNDED", "GLOW"),
    ClockLook("editorial", R.string.look_editorial, "AUTO", 400, "SERIF", "SOFT"),
    ClockLook("stacked", R.string.look_stacked, "AUTO", 700, "SANS", "SOFT", stacked = true),
)

/**
 * Edits a Big Clock on Home itself, with the real clock in view: Looks, one color row, and a Fine tune panel that stays
 * closed until asked for. It sits in the half of the screen the clock is not in. With Android's own wallpaper behind Home,
 * which Folio cannot read, the colors are the ones Android shares for it; when it shares none, only Automatic and White
 * are offered and a note says why.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClockEditBar(
    slot: Int, style: BigClockStyle?, onStyle: (BigClockStyle?) -> Unit,
    onDone: () -> Unit, modifier: Modifier = Modifier, enterFromTop: Boolean = false,
) {
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    // Slides in from the edge it sits on and out again, so Done is a motion and not a cut.
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduceMotion) appear.animateTo(1f, FolioMotion.spring(FolioMotion.Settle)) }
    val close: () -> Unit = { scope.launch { if (!reduceMotion) appear.animateTo(0f, FolioMotion.spring(FolioMotion.Firm)); onDone() } }
    val window = LocalWindowInfo.current.containerSize
    val bounds = BigClockBounds.bySlot[slot]
    val fraction = remember(bounds, window) {
        if (bounds == null || window.width <= 0 || window.height <= 0) Rect.Zero
        else Rect(bounds.left / window.width, bounds.top / window.height, bounds.right / window.width, bounds.bottom / window.height)
    }
    val tone = LocalWallpaperTone.current
    val systemWallpaper = tone.fromAndroid
    val sample = rememberClockInkSample(fraction, enabled = true, windowAspect = if (window.height > 0) window.width.toFloat() / window.height else 0f)
    var s by remember(slot) { mutableStateOf(style.orDefault()) }
    var fine by remember(slot) { mutableStateOf(false) }
    fun push(next: BigClockStyle) { s = next; onStyle(next) }
    val tint = sample?.wallpaperTint()
    val dots = remember(sample) { sample?.colorDots() ?: emptyList() }
    val maxHeight = with(density) { (window.height * .5f).toDp() }
    Column(modifier.padding(horizontal = FolioSpace.MEDIUM.dp).fillMaxWidth()
        .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 56.dp.toPx() * (if (enterFromTop) -1f else 1f) }
        .heightIn(max = maxHeight)
        .clip(RoundedCornerShape(FolioRadius.PANEL.dp)).background(FolioColors.SecondaryBackground.copy(alpha = .985f))
        .verticalScroll(rememberScrollState()).padding(FolioSpace.MEDIUM.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
        BarLabel(stringResource(R.string.looks))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            ClockLooks.forEach { look ->
                val on = s.mode == look.mode && s.weight == look.weight && s.face == look.face && s.shadow == look.shadow && s.stacked == look.stacked
                val unavailable = look.mode == "WALLPAPER" && tint == null
                val name = stringResource(look.name)
                val fill by animateFloatAsState(if (on) .22f else .12f, clockMotion(), label = "look fill")
                val ring by animateColorAsState(if (on) Color.White else Color.Transparent, clockMotion(), label = "look ring")
                Column(Modifier.width(76.dp).heightIn(min = FolioTouch.MIN.dp).clip(RoundedCornerShape(FolioRadius.CONTROL.dp))
                    .background(Color.White.copy(alpha = fill))
                    .border(2.dp, ring, RoundedCornerShape(FolioRadius.CONTROL.dp))
                    .clickable(enabled = !unavailable) { push(s.copy(mode = look.mode, weight = look.weight, face = look.face, shadow = look.shadow, stacked = look.stacked)) }
                    .semantics { role = Role.RadioButton; selected = on; contentDescription = name }.padding(FolioSpace.TINY.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val sample10 = Color.White.copy(alpha = if (unavailable) .3f else 1f)
                    Text(if (look.stacked) "10\n09" else "10:09", color = if (look.mode == "WALLPAPER" && tint != null) Color(tint) else sample10, fontSize = FolioType.BODY.sp,
                        fontWeight = FontWeight(look.weight), fontFamily = clockFontFamily(look.face, look.weight),
                        lineHeight = if (look.stacked) FolioType.BODY.sp * .9f else androidx.compose.ui.unit.TextUnit.Unspecified,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
                    Text(name, color = sample10, fontSize = FolioType.GROUP_LABEL.sp, maxLines = 1)
                }
            }
        }
        BarLabel(stringResource(R.string.color))
        FlowRow {
            ColorDot(selected = s.mode == "AUTO", label = stringResource(R.string.automatic), color = Color.White.copy(alpha = .18f),
                onClick = { push(s.copy(mode = "AUTO")) }) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            if (tint != null) ColorDot(selected = sample?.wallpaperDotSelected(s) == true, label = stringResource(R.string.wallpaper), color = Color(tint),
                onClick = { push(s.copy(mode = "WALLPAPER")) })
            val picked = remember(sample, s) { sample?.selectedDotIndex(s) }
            dots.forEachIndexed { n, (i, argb) ->
                ColorDot(selected = picked == i, label = stringResource(R.string.picture_color_n, n + 1), color = Color(argb),
                    onClick = { push(s.copy(mode = "CUSTOM", customIndex = i)) })
            }
            ColorDot(selected = s.mode == "WHITE", label = stringResource(R.string.white), color = Color.White, onClick = { push(s.copy(mode = "WHITE")) })
        }
        // Android can share no colors for its wallpaper (some live wallpapers, or just after a restart): then there is
        // nothing to tint with, and the note says so rather than leaving two dots unexplained.
        // The note also says which source the colors came from: the wallpaper's own, or the phone's theme colors.
        if (systemWallpaper) Text(stringResource(when (tone.colorSource()) {
            WallpaperColorSource.ANDROID_COLORS -> R.string.clock_colors_android_wallpaper
            WallpaperColorSource.SYSTEM_PALETTE -> R.string.clock_colors_system_palette
            WallpaperColorSource.NONE -> R.string.clock_no_android_wallpaper_colors
        }), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
        // The font sits beside Color, where people look for it, not inside Fine tune (#330).
        Choice(stringResource(R.string.clock_font), s.face, listOf("SANS" to R.string.default_choice, "ROUNDED" to R.string.face_rounded, "SERIF" to R.string.face_serif, "MONO" to R.string.face_mono)) { push(s.copy(face = it)) }
        AnimatedVisibility(fine, enter = fadeIn(clockMotion<Float>()) + expandVertically(clockMotion<androidx.compose.ui.unit.IntSize>()),
            exit = fadeOut(clockMotion<Float>()) + shrinkVertically(clockMotion<androidx.compose.ui.unit.IntSize>())) {
          Column(verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            val weightLabel = stringResource(R.string.weight)
            BarLabel(weightLabel)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(value = s.weight.toFloat(), onValueChange = { push(s.copy(weight = (it / 50f).toInt() * 50)) },
                    valueRange = 100f..900f, steps = 15, modifier = Modifier.weight(1f).heightIn(min = FolioTouch.MIN.dp).semantics { contentDescription = weightLabel })
                Text(s.weight.toString(), color = Color.White, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
            Choice(stringResource(R.string.size), s.size, listOf(.85f to R.string.small, 1f to R.string.default_choice, 1.15f to R.string.large)) { push(s.copy(size = it)) }
            Choice(stringResource(R.string.shadow), s.shadow, listOf("OFF" to R.string.off, "SOFT" to R.string.soft, "GLOW" to R.string.glow)) { push(s.copy(shadow = it)) }
            FineNote(R.string.fine_tune_look_note)
            Choice(stringResource(R.string.date), s.date, listOf("LONG" to R.string.date_long, "SHORT" to R.string.date_short, "OFF" to R.string.off)) { push(s.copy(date = it)) }
            Choice(stringResource(R.string.hours), s.hours, listOf("SYSTEM" to R.string.hours_system, "12" to R.string.hours_12, "24" to R.string.hours_24)) { push(s.copy(hours = it)) }
            FineNote(R.string.fine_tune_hours_note)
            if (s.hours != "24") Row(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_am_or_pm), Modifier.weight(1f), color = Color.White)
                IosSwitch(s.ampm, { push(s.copy(ampm = it)) })
            }
            Row(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_next_event), Modifier.weight(1f), color = Color.White)
                IosSwitch(s.showNext, { push(s.copy(showNext = it)) })
            }
            FineNote(R.string.fine_tune_next_note)
            Choice(stringResource(R.string.align), s.align, listOf("LEFT" to R.string.align_left, "CENTER" to R.string.align_center, "RIGHT" to R.string.align_right)) { push(s.copy(align = it)) }
          }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            IosChip(selected = false, onClick = { fine = !fine }, label = { Text(stringResource(if (fine) R.string.hide_fine_tune else R.string.fine_tune)) })
            if (fine) IosChip(selected = false, onClick = { s = BigClockStyle(); onStyle(null) }, label = { Text(stringResource(R.string.reset_style)) })
            Box(Modifier.weight(1f))
            IosChip(selected = true, onClick = close, label = { Text(stringResource(R.string.done)) })
        }
    }
}

@Composable
private fun BarLabel(text: String) = Text(text, color = Color.White.copy(alpha = .6f), fontSize = FolioType.GROUP_LABEL.sp)

/** A 30 dp color dot inside a 48 dp target, ringed when chosen. */
@Composable
private fun ColorDot(selected: Boolean, label: String, color: Color, onClick: () -> Unit, content: (@Composable () -> Unit)? = null) {
    val ring by animateColorAsState(if (selected) Color.White else Color.Transparent, clockMotion(), label = "dot ring")
    val grow by animateFloatAsState(if (selected) 1f else .86f, clockMotion(), label = "dot size")
    Box(Modifier.size(FolioTouch.MIN.dp).clickable(onClick = onClick).semantics { role = Role.RadioButton; this.selected = selected; contentDescription = label },
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).graphicsLayer { scaleX = grow; scaleY = grow }.clip(CircleShape).background(color)
            .border(1.dp, Color.White.copy(alpha = .5f), CircleShape).border(3.dp, ring, CircleShape), contentAlignment = Alignment.Center) { content?.invoke() }
    }
}

/** A titled row of choices, one chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(title: String, current: T, options: List<Pair<T, Int>>, onPick: (T) -> Unit) {
    Column {
        BarLabel(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp), verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            options.forEach { (value, label) ->
                val text = stringResource(label)
                IosChip(selected = value == current, onClick = { onPick(value) }, label = { Text(text) },
                    modifier = Modifier.semantics { contentDescription = "$title: $text" })
            }
        }
    }
}

/** One quiet line under a Fine tune group that says what it does, the way a tweak's settings pane explains itself. */
@Composable
private fun FineNote(@androidx.annotation.StringRes text: Int) =
    Text(stringResource(text), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp, modifier = Modifier.padding(horizontal = FolioSpace.TINY.dp))
