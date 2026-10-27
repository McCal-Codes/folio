package com.mccal.folio

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * A stand-in for blur, for a panel drawn over another app on a phone whose system will not blur windows (One UI turns it off; see
 * [LocalShadeBackdropBlur]). Folio cannot read the app behind it and does not try: instead of a blurred copy of that app, the panels
 * sit on frosted glass in the wallpaper's own colors, soft patches of them darkened enough for white text, with fine grain on top, the
 * way a privacy screen turns what is behind into a haze. It hides the app almost completely (that is the point), costs no permission,
 * and draws nothing from the screen.
 */
@Composable
internal fun FrostedBackdrop(tone: WallpaperTone, modifier: Modifier = Modifier) {
    val grain = remember { grainBrush() }
    Canvas(modifier) {
        val base = FolioColors.FrostBase
        fun shade(argb: Int?, fallback: Color) = lerp(argb?.let { Color(it) } ?: fallback, Color.Black, .5f)
        val a = shade(tone.primary, FolioColors.FrostPatchA)
        val b = shade(tone.secondary ?: tone.primary, FolioColors.FrostPatchB)
        val c = shade(tone.tertiary ?: tone.secondary ?: tone.primary, FolioColors.FrostPatchC)
        val reach = maxOf(size.width, size.height)
        drawRect(base)
        drawRect(Brush.radialGradient(listOf(a.copy(alpha = .95f), Color.Transparent), Offset(0f, 0f), reach * .9f))
        drawRect(Brush.radialGradient(listOf(b.copy(alpha = .9f), Color.Transparent), Offset(size.width, size.height * .35f), reach * .8f))
        drawRect(Brush.radialGradient(listOf(c.copy(alpha = .85f), Color.Transparent), Offset(size.width * .25f, size.height), reach * .85f))
        // The top and bottom stay a little darker, where the clock and the strip's buttons sit.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .28f), Color.Transparent, Color.Black.copy(alpha = .22f))))
        drawRect(grain, alpha = .09f)
    }
}

/** Fine grain: a small tile of random light and dark specks, repeated. */
private fun grainBrush(): ShaderBrush {
    val side = 128
    val pixels = IntArray(side * side)
    val random = java.util.Random(26)
    for (i in pixels.indices) {
        val level = random.nextInt(256)
        pixels[i] = android.graphics.Color.argb(40 + random.nextInt(60), level, level, level)
    }
    val bitmap = android.graphics.Bitmap.createBitmap(pixels, side, side, android.graphics.Bitmap.Config.ARGB_8888)
    return ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}
