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
 * `stacked`: hours over minutes. `hours`: SYSTEM (follow the phone), 12 or 24; `ampm`: show AM/PM in 12-hour.
 */
data class BigClockStyle(
    val mode: String = "AUTO", val customIndex: Int = 0, val weight: Int = 600,
    val size: Float = 1f, val face: String = "SANS", val shadow: String = "SOFT",
    val date: String = "LONG", val showNext: Boolean = true, val align: String = "CENTER",
    val stacked: Boolean = false, val hours: String = "SYSTEM", val ampm: Boolean = true,
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

internal data class RegionStats(val mean: Float, val p15: Float, val p85: Float, val r: Float, val g: Float, val b: Float)
internal data class ClockInkSample(val under: RegionStats, val whole: RegionStats, val suggestions: List<Int>)

/** The worst case for an ink: a light ink fights the brightest 15% behind it, a dark one the darkest 15%. */
private fun worstCase(inkLuminance: Float, region: RegionStats) =
    min(contrastRatio(inkLuminance, if (inkLuminance > .18f) region.p85 else region.p15), contrastRatio(inkLuminance, region.mean))

/** White or dark, whichever holds more contrast against the sampled region: (dark?, its ratio). */
internal fun pickInk(region: RegionStats): Pair<Boolean, Float> {
    val white = contrastRatio(1f, region.p85)
    val dark = contrastRatio(.006f, region.p15)
    return (dark > white) to max(white, dark)
}

/**
 * Downsamples [bitmap] to a small grid once and reads back stats for the part of it under [box] (fractions 0..1 of the
 * whole picture) and for the whole picture, plus up to five suggested colors: the most common vivid-enough buckets in
 * the whole picture, each lifted to [vividTint]'s saturation and brightness floor.
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

/** The picture's own colors that still hold WCAG AA (4.5:1) under the clock, in ranked order. */
internal fun ClockInkSample.readableSuggestions(): List<Int> = suggestions.filter { worstCase(luminance(it), under) >= 4.5f }

/** The vivid tint of the picture under the clock, when it holds AA there. */
internal fun ClockInkSample.wallpaperTint(): Int? =
    vividTint(android.graphics.Color.rgb(under.r.toInt(), under.g.toInt(), under.b.toInt())).takeIf { worstCase(luminance(it), under) >= 4.5f }

/** What `BigClockCard` draws: an ink and whether a soft dark halo goes behind the digits to keep it readable. */
internal data class ClockInkResult(val color: Color, val shade: Boolean, val ratio: Float)

private val DarkInk = FolioColors.SecondaryBackground

internal fun resolveClockInk(style: BigClockStyle, sample: ClockInkSample?, fallbackDark: Boolean): ClockInkResult {
    // No picture to read (Android's own wallpaper, or still sampling): the whole-wallpaper ink, or White when asked for.
    if (sample == null) return ClockInkResult(if (style.mode != "WHITE" && fallbackDark) DarkInk else Color.White, false, 21f)
    when (style.mode) {
        "WALLPAPER" -> sample.wallpaperTint()?.let { return ClockInkResult(Color(it), false, worstCase(luminance(it), sample.under)) }
        "CUSTOM" -> {
            val readable = sample.readableSuggestions()
            val argb = readable.getOrNull(style.customIndex) ?: readable.firstOrNull()
            if (argb != null) return ClockInkResult(Color(argb), false, worstCase(luminance(argb), sample.under))
        }
    }
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
 * Samples the picture under [box] (fractions of the whole background, 0..1) whenever the background or the box
 * changes. Only runs when Folio's own picture is behind Home: Android gives no API to read the system wallpaper's
 * pixels, so Wallpaper and Custom need a Folio picture and this returns null without one.
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
 * closed until asked for. It sits in the half of the screen the clock is not in. [systemWallpaper] is true when Android's
 * own wallpaper is behind Home, which Folio cannot read, so only Automatic and White are offered then.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClockEditBar(
    slot: Int, style: BigClockStyle?, onStyle: (BigClockStyle?) -> Unit, systemWallpaper: Boolean,
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
    val sample = rememberClockInkSample(fraction, enabled = !systemWallpaper)
    var s by remember(slot) { mutableStateOf(style.orDefault()) }
    var fine by remember(slot) { mutableStateOf(false) }
    fun push(next: BigClockStyle) { s = next; onStyle(next) }
    val tint = sample?.wallpaperTint()
    val readable = remember(sample) { sample?.readableSuggestions() ?: emptyList() }
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
                val unavailable = look.mode == "WALLPAPER" && (systemWallpaper || tint == null)
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
            if (tint != null) ColorDot(selected = s.mode == "WALLPAPER", label = stringResource(R.string.wallpaper), color = Color(tint),
                onClick = { push(s.copy(mode = "WALLPAPER")) })
            readable.forEachIndexed { i, argb ->
                ColorDot(selected = s.mode == "CUSTOM" && s.customIndex == i, label = stringResource(R.string.custom), color = Color(argb),
                    onClick = { push(s.copy(mode = "CUSTOM", customIndex = i)) })
            }
            ColorDot(selected = s.mode == "WHITE", label = stringResource(R.string.white), color = Color.White, onClick = { push(s.copy(mode = "WHITE")) })
        }
        if (systemWallpaper) Text(stringResource(R.string.wallpaper_from_folios_own_pictures_only), color = Color.White.copy(alpha = .6f), fontSize = FolioType.FOOTNOTE.sp)
        AnimatedVisibility(fine, enter = fadeIn(clockMotion<Float>()) + expandVertically(clockMotion<androidx.compose.ui.unit.IntSize>()),
            exit = fadeOut(clockMotion<Float>()) + shrinkVertically(clockMotion<androidx.compose.ui.unit.IntSize>())) {
          Column(verticalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            BarLabel(stringResource(R.string.weight))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(value = s.weight.toFloat(), onValueChange = { push(s.copy(weight = (it / 50f).toInt() * 50)) },
                    valueRange = 100f..900f, steps = 15, modifier = Modifier.weight(1f).heightIn(min = FolioTouch.MIN.dp))
                Text(s.weight.toString(), color = Color.White, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
            Choice(stringResource(R.string.size), s.size, listOf(.85f to R.string.small, 1f to R.string.default_choice, 1.15f to R.string.large)) { push(s.copy(size = it)) }
            Choice(stringResource(R.string.style), s.face, listOf("SANS" to R.string.default_choice, "ROUNDED" to R.string.face_rounded, "SERIF" to R.string.face_serif, "MONO" to R.string.face_mono)) { push(s.copy(face = it)) }
            Choice(stringResource(R.string.shadow), s.shadow, listOf("OFF" to R.string.off, "SOFT" to R.string.soft, "GLOW" to R.string.glow)) { push(s.copy(shadow = it)) }
            Choice(stringResource(R.string.date), s.date, listOf("LONG" to R.string.date_long, "SHORT" to R.string.date_short, "OFF" to R.string.off)) { push(s.copy(date = it)) }
            Choice(stringResource(R.string.hours), s.hours, listOf("SYSTEM" to R.string.hours_system, "12" to R.string.hours_12, "24" to R.string.hours_24)) { push(s.copy(hours = it)) }
            if (s.hours != "24") Row(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_am_or_pm), Modifier.weight(1f), color = Color.White)
                IosSwitch(s.ampm, { push(s.copy(ampm = it)) })
            }
            Row(Modifier.fillMaxWidth().heightIn(min = FolioTouch.MIN.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_next_event), Modifier.weight(1f), color = Color.White)
                IosSwitch(s.showNext, { push(s.copy(showNext = it)) })
            }
            Choice(stringResource(R.string.align), s.align, listOf("LEFT" to R.string.align_left, "CENTER" to R.string.align_center, "RIGHT" to R.string.align_right)) { push(s.copy(align = it)) }
          }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FolioSpace.SMALL.dp)) {
            IosChip(selected = false, onClick = { fine = !fine }, label = { Text(stringResource(if (fine) R.string.hide_fine_tune else R.string.fine_tune)) })
            if (fine) IosChip(selected = false, onClick = { push(BigClockStyle()) }, label = { Text(stringResource(R.string.reset_style)) })
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
