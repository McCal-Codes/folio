package com.mccal.folio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mccal.folio.duet.DuetShader
import com.mccal.folio.duet.DuetStyles
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Every Duet style drawn by the real shader over a fixed test card and compared with its reference picture, the way
 * hingewave (MIT) checks its ports with golden renders. It runs on the phone because only the GPU runs AGSL, and it
 * also proves the shader compiles there, which no JVM test can.
 *
 * The references live in androidTest/assets/duet-golden. With one missing, the test saves what it drew to the app's
 * files (duet-golden/) and fails, so a new or changed style is recorded on purpose:
 * `adb exec-out run-as com.mccal.folio.dev cat files/duet-golden/<style>.png > app/src/androidTest/assets/duet-golden/<style>.png`
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class DuetRenderCheckTest {
    private val w = 480
    private val h = 320

    /** A card with sharp stripes, a grid and flat colour, so blur, shade, tilt and the soft edge all show. */
    private fun card(): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
        val c = Canvas(bmp)
        val p = Paint()
        for (x in 0 until w step 24) { p.color = if ((x / 24) % 2 == 0) Color.rgb(230, 80, 60) else Color.rgb(40, 120, 220); c.drawRect(x.toFloat(), 0f, x + 24f, h / 2f, p) }
        p.color = Color.rgb(250, 250, 245); c.drawRect(0f, h / 2f, w.toFloat(), h.toFloat(), p)
        p.color = Color.BLACK; p.strokeWidth = 2f
        for (x in 0..w step 40) c.drawLine(x.toFloat(), h / 2f, x.toFloat(), h.toFloat(), p)
        for (y in h / 2..h step 40) c.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), p)
    }

    private fun render(style: com.mccal.folio.duet.DuetStyle, m: Float): Bitmap {
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            val content = RenderNode("card").apply {
                setPosition(0, 0, w, h)
                beginRecording().drawBitmap(card(), 0f, 0f, null); endRecording()
                setRenderEffect(DuetShader().effect(w.toFloat(), h.toFloat(), m, cover = false,
                    geometry = FoldGeometry(horizontal = false, hingePx = w / 2f, movingAfterHinge = false),
                    style = style, cornerPx = 16f).asAndroidRenderEffect())
            }
            val root = RenderNode("root").apply {
                setPosition(0, 0, w, h)
                beginRecording().also { it.drawColor(Color.BLACK); it.drawRenderNode(content) }; endRecording()
            }
            renderer.setContentRoot(root)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage().use { image ->
                val hw = Bitmap.wrapHardwareBuffer(image.hardwareBuffer!!, ColorSpace.get(ColorSpace.Named.SRGB))!!
                return hw.copy(Bitmap.Config.ARGB_8888, false)
            }
        } finally { renderer.destroy(); reader.close() }
    }

    /** Mean channel difference out of 255; small GPU and driver rounding stays well under the limit. */
    private fun difference(a: Bitmap, b: Bitmap): Double {
        var sum = 0L
        for (y in 0 until h) for (x in 0 until w) {
            val p = a.getPixel(x, y); val q = b.getPixel(x, y)
            sum += abs(Color.red(p) - Color.red(q)) + abs(Color.green(p) - Color.green(q)) + abs(Color.blue(p) - Color.blue(q))
        }
        return sum / (w * h * 3.0)
    }

    @Test fun everyStyleMatchesItsReference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val out = File(app.filesDir, "duet-golden").apply { mkdirs() }
        val recorded = mutableListOf<String>()
        val problems = mutableListOf<String>()
        for (style in DuetStyles.all) {
            val drawn = render(style, m = .6f)
            val name = "${style.id}.png"
            val golden = runCatching { instrumentation.context.assets.open("duet-golden/$name").use(BitmapFactory::decodeStream) }.getOrNull()
            if (golden == null) {
                File(out, name).outputStream().use { drawn.compress(Bitmap.CompressFormat.PNG, 100, it) }
                recorded += name
                continue
            }
            val d = difference(drawn, golden)
            if (d > 2.0) {
                File(out, "changed-$name").outputStream().use { drawn.compress(Bitmap.CompressFormat.PNG, 100, it) }
                problems += "${style.id} differs from its reference by ${"%.2f".format(d)}/255"
            }
        }
        // The styles have to look different from each other, or a style has silently stopped doing anything.
        val renders = DuetStyles.all.associate { it.id to render(it, .6f) }
        for ((a, b) in renders.keys.toList().let { ids -> ids.flatMap { x -> ids.filter { it > x }.map { x to it } } }) {
            assertTrue("$a and $b look the same", difference(renders.getValue(a), renders.getValue(b)) > .5)
        }
        if (recorded.isNotEmpty()) fail("No reference yet for ${recorded.joinToString()}: saved in files/duet-golden; copy them into androidTest/assets/duet-golden")
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
