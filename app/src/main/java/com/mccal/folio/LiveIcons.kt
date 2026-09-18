package com.mccal.folio

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** Which installed apps get a live icon: the phone's calendar apps and clock apps. */
internal object LiveIcons {
    /** Calendar and clock packages, published together so a thread never sees one set without the other. */
    @Volatile private var sets: Pair<Set<String>, Set<String>>? = null

    enum class Kind { CALENDAR, CLOCK }

    fun kind(context: Context, packageName: String): Kind? {
        val (calendar, clock) = sets ?: load(context)
        return when (packageName) {
            in calendar -> Kind.CALENDAR
            in clock -> Kind.CLOCK
            else -> null
        }
    }

    @Synchronized private fun load(context: Context): Pair<Set<String>, Set<String>> {
        sets?.let { return it }
        val pm = context.packageManager
        fun packages(intent: Intent) = runCatching {
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        val calendar = packages(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)) +
            setOf("com.samsung.android.calendar", "com.google.android.calendar")
        val clock = packages(Intent(AlarmClock.ACTION_SHOW_ALARMS)) +
            setOf("com.sec.android.app.clockpackage", "com.google.android.deskclock")
        return (calendar to clock).also { sets = it }
    }
}

enum class IconStyle(@androidx.annotation.StringRes val label: Int) { DEFAULT(R.string.default_choice), DARK(R.string.dark), TINTED(R.string.tinted), CLEAR(R.string.clear) }

enum class IconShape(@androidx.annotation.StringRes val label: Int) { DEFAULT(R.string.default_choice), SQUIRCLE(R.string.squircle), CIRCLE(R.string.circle), ROUNDED(R.string.rounded_square) }
enum class BadgeStyle(@androidx.annotation.StringRes val label: Int) { OFF(R.string.off), DOT(R.string.dot), COUNT(R.string.count) }
enum class BadgeColor(@androidx.annotation.StringRes val label: Int, val fixed: Long? = null) {
    RED(R.string.red), APP(R.string.match_icon), SOFT(R.string.soft),
    BLUE(R.string.blue, 0xFF0A84FF), GREEN(R.string.green, 0xFF30D158), ORANGE(R.string.orange, 0xFFFF9F0A), PURPLE(R.string.purple, 0xFFBF5AF2)
}
/** iOS: today's flat pill. Classic: the older white-outlined, glossy badge. Glass: frosted dark with a light count. */
enum class BadgeLook(@androidx.annotation.StringRes val label: Int) { IOS(R.string.ios), CLASSIC(R.string.classic), GLASS(R.string.glass) }
enum class BadgeSize(@androidx.annotation.StringRes val label: Int, val scale: Float) { SMALL(R.string.small, .82f), STANDARD(R.string.standard, 1f), LARGE(R.string.large, 1.2f) }

/** Icon look for the whole launcher, provided from the saved settings. */
internal data class IconLook(val style: IconStyle = IconStyle.DEFAULT, val tint: Color = Color(0xFFFFB340),
    val shape: IconShape = IconShape.DEFAULT, val pack: String? = null, val badges: BadgeStyle = BadgeStyle.DOT,
    val badgeColor: BadgeColor = BadgeColor.RED, val liveIcons: Boolean = true, val liveLook: String = "AUTO",
    val badgeLook: BadgeLook = BadgeLook.IOS, val badgeSize: BadgeSize = BadgeSize.STANDARD)

/** Unread notification counts per package, for icon badges. */
internal val LocalBadgeCounts = androidx.compose.runtime.compositionLocalOf { emptyMap<String, Int>() }

private val SquircleShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    // Superellipse (n = 4), the iOS-style continuous-corner icon shape.
    val a = size.width / 2; val b = size.height / 2
    val steps = 64
    for (i in 0..steps) {
        val t = 2 * Math.PI * i / steps
        val c = kotlin.math.cos(t); val s = kotlin.math.sin(t)
        val x = a + a * Math.signum(c) * Math.pow(kotlin.math.abs(c), 0.5)
        val y = b + b * Math.signum(s) * Math.pow(kotlin.math.abs(s), 0.5)
        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
    }
    close()
}

private fun IconShape.toShape(): androidx.compose.ui.graphics.Shape? = when (this) {
    IconShape.DEFAULT -> null
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.CIRCLE -> androidx.compose.foundation.shape.CircleShape
    IconShape.ROUNDED -> RoundedCornerShape(22)
}
internal val LocalIconLook = androidx.compose.runtime.staticCompositionLocalOf { IconLook() }
/** Whether most app icons are dark (null until measured), for Automatic live Clock and Calendar icons. */
internal val LocalIconsAreDark = androidx.compose.runtime.staticCompositionLocalOf<Boolean?> { null }

private fun filterFor(look: IconLook): androidx.compose.ui.graphics.ColorFilter? = when (look.style) {
    IconStyle.DEFAULT -> null
    // Dimmer and a little less saturated, so bright icons sit calmly on a dark Home.
    IconStyle.DARK -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        .62f, .08f, .05f, 0f, -6f,
        .05f, .65f, .05f, 0f, -6f,
        .05f, .08f, .62f, 0f, -6f,
        0f, 0f, 0f, 1f, 0f)))
    // Luminance mapped onto one tint color, like iOS tinted icons.
    // Clear, for apps without their own one-color symbol: a light, colorless version of the icon.
    IconStyle.CLEAR -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
        .17f, .34f, .07f, 0f, 125f,
        .17f, .34f, .07f, 0f, 125f,
        .17f, .34f, .07f, 0f, 125f,
        0f, 0f, 0f, .88f, 0f)))
    IconStyle.TINTED -> {
        val r = look.tint.red; val g = look.tint.green; val b = look.tint.blue
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
            .299f * r, .587f * r, .114f * r, 0f, 0f,
            .299f * g, .587f * g, .114f * g, 0f, 0f,
            .299f * b, .587f * b, .114f * b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f)))
    }
}

/**
 * Drop-in replacement for an app's icon Image: live Clock and Calendar icons, like iPhone.
 * Pass [shape] instead of clipping the modifier so the notification badge can sit over the corner;
 * badges only show where a shape is passed (Home, dock, folders, App Library).
 */
@Composable
internal fun AppIcon(app: AppEntry, contentDescription: String?, modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape? = null, badge: Boolean = shape != null) {
    val context = LocalContext.current
    val look0 = LocalIconLook.current
    val kind = remember(app.component.packageName, look0.liveIcons) { if (look0.liveIcons) LiveIcons.kind(context, app.component.packageName) else null }
    val look = LocalIconLook.current
    val accent = if (look.style == IconStyle.TINTED) look.tint else null
    val lookShape = remember(look.shape) { look.shape.toShape() }
    val clipShape = lookShape ?: shape
    // Pack lookup: null until it finishes, then the pack's icon or none. An icon pack's own Clock or Calendar icon wins
    // over the live one, so a pack keeps one consistent look; live icons fill in where the pack has nothing.
    val packLookup by androidx.compose.runtime.produceState<PackLookup?>(if (look.pack == null) PackLookup(null) else null, look.pack, app.id) {
        value = PackLookup(look.pack?.let { pack ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IconPacks.icon(context, pack, app.component, 192) }
        })
    }
    val packIcon = packLookup?.icon
    val liveKind = kind.takeIf { packLookup != null && packIcon == null }
    // Default style follows the app's real icon: a dark system icon theme (like iDark through Theme Park) gets the dark
    // live icon, so Clock and Calendar match the icons around them.
    // Automatic follows the other icons on Home (an icon theme often leaves Calendar and Clock alone), falling back
    // to this app's own icon before those have been measured.
    val iconsAreDark = LocalIconsAreDark.current
    val darkSource = iconsAreDark ?: remember(app.icon) { kind != null && isDarkIcon(app.icon) }
    val palette = when {
        look.style == IconStyle.TINTED -> LivePalette.of(IconStyle.TINTED, accent)
        look.style == IconStyle.CLEAR -> LivePalette.of(IconStyle.CLEAR, null)
        look.liveLook == "LIGHT" -> LivePalette.of(IconStyle.DEFAULT, null)
        look.liveLook == "DARK" -> LivePalette.of(IconStyle.DARK, null)
        else -> LivePalette.of(if (look.style == IconStyle.DEFAULT && darkSource) IconStyle.DARK else look.style, accent)
    }
    val badgeCount = if (!badge || look.badges == BadgeStyle.OFF) 0 else LocalBadgeCounts.current[app.component.packageName] ?: 0
    Box(modifier.semantics { contentDescription?.let { this.contentDescription = it } }) {
        val fill = Modifier.fillMaxSize().then(if (clipShape != null) Modifier.clip(clipShape) else Modifier)
        // App icon bitmaps carry a small transparent margin; inset the drawn live icons to the same visual size.
        when {
            liveKind == LiveIcons.Kind.CALENDAR -> BoxWithConstraints(Modifier.fillMaxSize()) { CalendarIcon(Modifier.fillMaxSize().padding(maxWidth * .035f).then(if (clipShape != null) Modifier.clip(clipShape) else Modifier), palette) }
            liveKind == LiveIcons.Kind.CLOCK -> BoxWithConstraints(Modifier.fillMaxSize()) { ClockIcon(Modifier.fillMaxSize().padding(maxWidth * .035f).then(if (clipShape != null) Modifier.clip(clipShape) else Modifier), palette) }
            look.style == IconStyle.CLEAR -> ClearIcon(app, packIcon, fill)
            else -> {
                val source = packIcon ?: app.icon
                val bitmap = remember(source) { source.asImageBitmap() }
                val filter = remember(look.style, look.tint) { filterFor(look) }
                Image(bitmap, null, fill, colorFilter = filter)
            }
        }
        if (badgeCount > 0) {
            val color = when {
                look.badgeColor == BadgeColor.RED -> BadgeRed
                look.badgeColor.fixed != null -> Color(look.badgeColor.fixed)
                look.style == IconStyle.TINTED -> if (look.badgeColor == BadgeColor.SOFT) Color(softened(look.tint.toArgb())) else look.tint
                liveKind == LiveIcons.Kind.CALENDAR -> if (look.badgeColor == BadgeColor.SOFT) Color(softened(IconRed.toArgb())) else IconRed
                liveKind == LiveIcons.Kind.CLOCK -> if (look.badgeColor == BadgeColor.SOFT) Color(softened(IconOrange.toArgb())) else IconOrange
                else -> {
                    val source = packIcon ?: app.icon
                    val soft = look.badgeColor == BadgeColor.SOFT
                    val accent by produceState(BadgeAccents.cached(source, soft), source, soft) {
                        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { BadgeAccents.of(source, soft) }
                    }
                    accent?.let { Color(it) } ?: if (soft) SoftNeutral else BadgeRed
                }
            }
            IconBadge(badgeCount, look.badges, color, look.badgeLook, look.badgeSize.scale)
        }
        // Updating: the icon dims under an iOS-style progress ring until the installer finishes.
        LocalInstallProgress.current[app.component.packageName]?.let { progress -> InstallRing(progress, fill) }
    }
}

/**
 * iOS-style Clear icon: a frosted tile with the app's own one-color symbol in white. That symbol is the monochrome
 * layer apps provide for Android 13's themed icons; apps (and icon packs) without one get a light, colorless icon.
 */
@Composable
private fun ClearIcon(app: AppEntry, packIcon: android.graphics.Bitmap?, modifier: Modifier) {
    val context = LocalContext.current
    val glyph by produceState(if (packIcon == null) ClearGlyphs.cached(app.id) else null, app.id, packIcon) {
        if (packIcon == null && value == null)
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ClearGlyphs.load(context, app) }
    }
    Box(modifier.background(ClearTile).border(1.dp, Color.White.copy(alpha = .3f), RoundedCornerShape(22)), contentAlignment = Alignment.Center) {
        val symbol = glyph
        if (symbol != null) Image(remember(symbol) { symbol.asImageBitmap() }, null, Modifier.fillMaxSize())
        else {
            val source = packIcon ?: app.icon
            val bitmap = remember(source) { source.asImageBitmap() }
            Image(bitmap, null, Modifier.fillMaxSize(.78f), colorFilter = remember { filterFor(IconLook(style = IconStyle.CLEAR)) })
        }
    }
}

/** White symbols for Clear icons, from the monochrome layer of adaptive icons (Android 13+), cached per app. */
internal object ClearGlyphs {
    private val cache = android.util.LruCache<String, android.graphics.Bitmap>(96)
    private val missing = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    fun cached(id: String): android.graphics.Bitmap? = cache.get(id)

    fun load(context: android.content.Context, app: AppEntry, size: Int = 192): android.graphics.Bitmap? {
        cache.get(app.id)?.let { return it }
        if (android.os.Build.VERSION.SDK_INT < 33 || app.id in missing || app.isShortcut) return null
        val icon = runCatching {
            context.getSystemService(android.content.pm.LauncherApps::class.java)
                .getActivityList(app.packageName, app.user).firstOrNull { it.componentName == app.component }?.getIcon(0)
        }.getOrNull() as? android.graphics.drawable.AdaptiveIconDrawable
        val mono = icon?.monochrome ?: run { missing.add(app.id); return null }
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        // The layer is 108dp with the symbol inside the middle 72dp, the same framing as the icon itself.
        val bleed = (size * .25f).toInt()
        mono.mutate().setBounds(-bleed, -bleed, size + bleed, size + bleed)
        mono.setTint(android.graphics.Color.WHITE)
        mono.draw(canvas)
        cache.put(app.id, bitmap)
        return bitmap
    }
}

/** iOS download progress over an icon: a dark veil with a white pie filling clockwise inside a thin ring. */
@Composable
internal fun InstallRing(progress: Float, modifier: Modifier) {
    val shown by androidx.compose.animation.core.animateFloatAsState(progress.coerceIn(0f, 1f), label = "install progress")
    Canvas(modifier.semantics { contentDescription = "Installing, ${(progress * 100).toInt()} percent" }) {
        drawRect(Color.Black.copy(alpha = .45f))
        val r = size.minDimension * .22f
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.White.copy(alpha = .9f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(r * .14f))
        drawArc(Color.White, -90f, 360f * shown, true, Offset(c.x - r * .78f, c.y - r * .78f), androidx.compose.ui.geometry.Size(r * 1.56f, r * 1.56f))
    }
}

@Composable
private fun CalendarIcon(modifier: Modifier, palette: LivePalette) {
    val tick by rememberMinuteTick()
    val today = remember(tick) { LocalDate.now() }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(22)).background(palette.calendarBackground), contentAlignment = Alignment.Center) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val small = with(density) { (maxWidth * .17f).toSp() }
        val big = with(density) { (maxWidth * .46f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                color = palette.accent, fontSize = small, fontWeight = FontWeight.SemiBold, lineHeight = small * 1.15f)
            Text(today.dayOfMonth.toString(), color = palette.calendarNumber, fontSize = big,
                fontWeight = FontWeight.Light, lineHeight = big * 1.08f)
        }
    }
}

@Composable
private fun ClockIcon(modifier: Modifier, palette: LivePalette) {
    val tick by rememberSecondTick()
    val now = displayNow(tick).toLocalTime()
    Box(modifier.clip(RoundedCornerShape(22)).background(palette.clockBackground)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension * .42f
            drawCircle(palette.clockFace, r, c)
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0 - 90).toFloat()
                val inner = if (i % 3 == 0) r * .78f else r * .84f
                drawLine(palette.clockHands.copy(alpha = if (i % 3 == 0) 1f else .6f),
                    Offset(c.x + inner * cos(a), c.y + inner * sin(a)), Offset(c.x + r * .93f * cos(a), c.y + r * .93f * sin(a)),
                    strokeWidth = r * (if (i % 3 == 0) .05f else .03f), cap = StrokeCap.Round)
            }
            fun hand(fraction: Float, length: Float, width: Float, color: Color, tail: Float = .12f) {
                val a = (fraction * 2 * Math.PI - Math.PI / 2).toFloat()
                drawLine(color, Offset(c.x - r * tail * cos(a), c.y - r * tail * sin(a)),
                    Offset(c.x + r * length * cos(a), c.y + r * length * sin(a)), strokeWidth = r * width, cap = StrokeCap.Round)
            }
            val seconds = now.second.toFloat()
            val minutes = now.minute + seconds / 60f
            val hours = (now.hour % 12) + minutes / 60f
            hand(hours / 12f, .5f, .085f, palette.clockHands)
            hand(minutes / 60f, .74f, .06f, palette.clockHands)
            hand(seconds / 60f, .82f, .025f, palette.secondHand, tail = .2f)
            drawCircle(palette.secondHand, r * .06f, c)
        }
    }
}

private class PackLookup(val icon: android.graphics.Bitmap?)

/** Whether an icon is mostly dark: average luminance of its visible pixels, sampled on a coarse grid. */
internal fun isDarkIcon(bitmap: android.graphics.Bitmap): Boolean = runCatching {
    val step = maxOf(1, bitmap.width / 24)
    var sum = 0.0; var count = 0
    for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) {
        val c = bitmap.getPixel(x, y)
        if (android.graphics.Color.alpha(c) < 200) continue
        sum += (.2126 * android.graphics.Color.red(c) + .7152 * android.graphics.Color.green(c) + .0722 * android.graphics.Color.blue(c)) / 255.0
        count++
    }
    count > 0 && sum / count < .4
}.getOrDefault(false)

/**
 * Live Clock and Calendar colors for each icon style, like iOS 18: Default is the classic light Calendar and Clock,
 * Dark is the dark-mode versions, Tinted draws the details in the tint color on dark.
 */
internal data class LivePalette(val calendarBackground: Color, val calendarNumber: Color, val accent: Color,
    val clockBackground: Color, val clockFace: Color, val clockHands: Color, val secondHand: Color) {
    companion object {
        fun of(style: IconStyle, tint: Color?): LivePalette = when {
            style == IconStyle.TINTED && tint != null -> LivePalette(IconDark, tint, tint, IconDark, IconFace, tint, tint.copy(alpha = .7f))
            style == IconStyle.DARK -> LivePalette(IconDark, Color.White, IconRed, IconDark, IconFace, Color.White, IconOrange)
            style == IconStyle.CLEAR -> LivePalette(ClearTile, Color.White, Color.White.copy(alpha = .85f), ClearTile,
                Color.White.copy(alpha = .1f), Color.White, Color.White.copy(alpha = .7f))
            else -> LivePalette(Color.White, Color.Black, IconRedLight, Color.Black, Color.White, Color.Black, IconOrange)
        }
    }
}

private val IconRedLight = FolioColors.RedLight
/** The frosted tile behind Clear icons. */
internal val ClearTile = Color.White.copy(alpha = .2f)
private val IconDark = FolioColors.SecondaryBackground
private val IconFace = Color(0xFF2C2C2E)
private val IconRed = FolioColors.Red
private val IconOrange = FolioColors.Orange

internal val BadgeRed = FolioColors.RedLight
private val SoftNeutral = Color(0xFFE5E5EA)

/** iOS-style badge: sits over the icon's top-right corner, a dot or a pill that widens for 2+ digits. */
@Composable
internal fun androidx.compose.foundation.layout.BoxScope.IconBadge(count: Int, style: BadgeStyle, color: Color,
    badgeLook: BadgeLook = BadgeLook.IOS, scale: Float = 1f) {
    BoxWithConstraints(Modifier.matchParentSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val h = (if (style == BadgeStyle.COUNT) maxWidth * .34f else maxWidth * .22f) * scale
        val pill = androidx.compose.foundation.shape.CircleShape
        val fill = when (badgeLook) {
            BadgeLook.GLASS -> FolioColors.SecondaryBackground.copy(alpha = .78f)
            else -> color
        }
        val readable = when {
            badgeLook == BadgeLook.GLASS -> color.takeIf { it.luminance() > .2f } ?: Color.White
            color.luminance() > .62f -> Color.Black.copy(alpha = .85f)
            else -> Color.White
        }
        val look = when (badgeLook) {
            // iOS 7 and later: a flat pill, no shadow or outline.
            BadgeLook.IOS -> Modifier.background(fill, pill)
            // Classic (iOS 6): a white ring, a soft shadow and a glossy top half.
            BadgeLook.CLASSIC -> Modifier.shadow(with(density) { 2.dp }, pill, ambientColor = Color.Black, spotColor = Color.Black)
                .background(fill, pill).border(h * .09f, Color.White, pill)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.White.copy(alpha = .35f), .5f to Color.Transparent), pill)
            BadgeLook.GLASS -> Modifier.background(fill, pill).border(1.dp, Color.White.copy(alpha = .25f), pill)
        }
        Box(Modifier.align(Alignment.TopEnd).offset(h * .32f, -h * .32f)
            .heightIn(min = h).widthIn(min = h)
            .then(look)
            .padding(horizontal = if (style == BadgeStyle.COUNT && count > 9) h * .22f else 0.dp),
            contentAlignment = Alignment.Center) {
            if (style == BadgeStyle.COUNT) {
                val size = with(density) { (h * .6f).toSp() }
                Text(if (count > 99) "99+" else count.toString(), color = readable, fontWeight = FontWeight.SemiBold,
                    fontSize = size, maxLines = 1, softWrap = false,
                    style = androidx.compose.ui.text.TextStyle(lineHeight = size,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)))
            }
        }
    }
}

/** Main color of an icon for "Match icon" badges, cached per bitmap. */
internal object BadgeAccents {
    private val cache = android.util.LruCache<android.graphics.Bitmap, Int>(256)
    private val softCache = android.util.LruCache<android.graphics.Bitmap, Int>(256)
    private const val NONE = 0 // grayscale icons fall back to red (or a soft neutral)

    fun cached(bitmap: android.graphics.Bitmap, soft: Boolean = false): Int? =
        (if (soft) softCache else cache).get(bitmap)?.takeIf { it != NONE }

    fun of(bitmap: android.graphics.Bitmap, soft: Boolean = false): Int? {
        if (soft) {
            softCache.get(bitmap)?.let { return it.takeIf { c -> c != NONE } }
            val color = pixels(bitmap)?.let(::softBadgeColor)
            softCache.put(bitmap, color ?: NONE)
            return color
        }
        cache.get(bitmap)?.let { return it.takeIf { c -> c != NONE } }
        val accent = pixels(bitmap)?.let(::dominantAccent)
        cache.put(bitmap, accent ?: NONE)
        return accent
    }

    /** A 24×24 sample of the icon: enough to find its main colors without reading every pixel. */
    private fun pixels(bitmap: android.graphics.Bitmap): IntArray? = runCatching {
        val readable = if (bitmap.config == android.graphics.Bitmap.Config.HARDWARE) bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else bitmap
        val small = android.graphics.Bitmap.createScaledBitmap(readable, 24, 24, true)
        IntArray(24 * 24).also { small.getPixels(it, 0, 24, 0, 0, 24, 24) }
    }.getOrNull()
}

/**
 * Soft pastel badge: the icon's three most prominent color groups (transparent, white, black and gray
 * pixels ignored) blended by how much of the icon each covers, then softened like iOS pastels —
 * saturation capped at 40%, brightness raised to at least 90%. Null for icons with no real color.
 */
internal fun softBadgeColor(pixels: IntArray): Int? {
    val weight = FloatArray(12); val rs = FloatArray(12); val gs = FloatArray(12); val bs = FloatArray(12)
    var opaque = 0
    for (p in pixels) {
        if ((p ushr 24) < 160) continue
        opaque++
        val r = (p shr 16 and 255) / 255f; val g = (p shr 8 and 255) / 255f; val b = (p and 255) / 255f
        val max = maxOf(r, g, b); val delta = max - minOf(r, g, b)
        val sat = if (max == 0f) 0f else delta / max
        if (sat < .2f || max < .2f) continue
        val hue = when (max) { r -> ((g - b) / delta).mod(6f); g -> (b - r) / delta + 2f; else -> (r - g) / delta + 4f }
        val bucket = (hue * 2f).toInt().coerceIn(0, 11)
        weight[bucket] += 1f; rs[bucket] += r; gs[bucket] += g; bs[bucket] += b
    }
    val top = weight.indices.sortedByDescending { weight[it] }.take(3).filter { weight[it] >= maxOf(1f, opaque * .04f) }
    if (top.isEmpty()) return null
    val total = top.sumOf { weight[it].toDouble() }.toFloat()
    val r = top.sumOf { rs[it].toDouble() }.toFloat() / total
    val g = top.sumOf { gs[it].toDouble() }.toFloat() / total
    val b = top.sumOf { bs[it].toDouble() }.toFloat() / total
    return softened((0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt())
}

/** Caps saturation at 40% and lifts brightness to at least 90% (HSV), keeping the hue. */
internal fun softened(argb: Int): Int {
    val r = (argb shr 16 and 255) / 255f; val g = (argb shr 8 and 255) / 255f; val b = (argb and 255) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta).mod(6f))
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    val s = (if (max == 0f) 0f else delta / max).coerceAtMost(.40f)
    val v = max.coerceAtLeast(.90f)
    val c = v * s
    val x = c * (1 - kotlin.math.abs((hue / 60f).mod(2f) - 1))
    val m = v - c
    val (r1, g1, b1) = when ((hue / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    fun ch(v: Float) = kotlin.math.round((v + m) * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
}

/**
 * The most prominent saturated color in ARGB [pixels], or null when the icon is mostly
 * transparent, gray, black or white. Hues are bucketed and weighted by saturation × brightness.
 */
internal fun dominantAccent(pixels: IntArray): Int? {
    val weight = FloatArray(12); val rs = FloatArray(12); val gs = FloatArray(12); val bs = FloatArray(12)
    var opaque = 0
    for (p in pixels) {
        if ((p ushr 24) < 160) continue
        opaque++
        val r = (p shr 16 and 255) / 255f; val g = (p shr 8 and 255) / 255f; val b = (p and 255) / 255f
        val max = maxOf(r, g, b); val delta = max - minOf(r, g, b)
        val sat = if (max == 0f) 0f else delta / max
        if (sat < .35f || max < .25f) continue
        val hue = when (max) { r -> ((g - b) / delta).mod(6f); g -> (b - r) / delta + 2f; else -> (r - g) / delta + 4f }
        val bucket = (hue * 2f).toInt().coerceIn(0, 11)
        val w = sat * max
        weight[bucket] += w; rs[bucket] += r * w; gs[bucket] += g * w; bs[bucket] += b * w
    }
    val best = weight.indices.maxBy { weight[it] }
    if (opaque == 0 || weight[best] < opaque * .06f) return null
    fun channel(sum: Float) = kotlin.math.round(sum / weight[best] * 255f).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(rs[best]) shl 16) or (channel(gs[best]) shl 8) or channel(bs[best])
}

/** Main color of an icon or album art (cached), for Velvet/ColorFlow-style tinting; null until known or for gray images. */
@Composable
internal fun rememberAccent(bitmap: android.graphics.Bitmap?): Color? {
    if (bitmap == null) return null
    val accent by produceState(BadgeAccents.cached(bitmap), bitmap) {
        if (value == null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { BadgeAccents.of(bitmap) }
    }
    return accent?.let { Color(it) }
}

/** [base] mixed toward [accent] by [amount], keeping [base]'s alpha. */
internal fun mixColor(base: Color, accent: Color?, amount: Float): Color = if (accent == null) base else
    Color(base.red + (accent.red - base.red) * amount, base.green + (accent.green - base.green) * amount,
        base.blue + (accent.blue - base.blue) * amount, base.alpha)

/**
 * Clear Badge (in an app's long-press menu): hides the badge for the notifications showing now, without dismissing
 * them. A notification posted later brings the badge back, like iOS. Kept in memory only.
 */
internal object BadgeClears {
    /**
     * Keys of the notifications that were showing when Clear Badge was tapped. An app updating one of those (a download
     * progressing, music, a summary) keeps the badge cleared; a new notification brings it back.
     */
    val cleared = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())

    fun clear(packageName: String, items: List<NotificationItem>) {
        val keys = items.filter { it.packageName == packageName }.map { it.key }
        if (keys.isNotEmpty()) cleared.value = cleared.value + keys
    }

    fun counts(items: List<NotificationItem>, cleared: Set<String>): Map<String, Int> =
        items.filter { it.key !in cleared }.groupingBy { it.packageName }.eachCount()
}

/** Most icons dark? Samples up to 40 app icons (skipping Clock and Calendar themselves); null if there aren't enough. */
internal fun iconsMostlyDark(icons: List<android.graphics.Bitmap>): Boolean? {
    val sample = icons.take(40)
    if (sample.size < 4) return null
    return sample.count(::isDarkIcon) * 2 > sample.size
}
