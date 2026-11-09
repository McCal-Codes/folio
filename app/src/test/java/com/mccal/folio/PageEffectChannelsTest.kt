package com.mccal.folio

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Page effect channels (format 0.7.0) and the engine that draws them ([applyPageEffect]).
 *
 * The first test is the promise the refactor rests on: with no channels, every built-in effect and the Tilt example
 * set the layer number for number as the v1 engine did, at every position and on every screen width. The v1 engine
 * is copied below as it stood on main before channels, so a later change to the engine cannot quietly move it.
 */
class PageEffectChannelsTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }

    /** Every built-in effect, plus the Tilt example package read from its own effect.json. */
    private val v1Effects: Map<String, PageEffectSpec> = PageEffect.CHOICES.associate { it.name to it.spec!! } + ("TILT" to tilt())

    private fun tilt(): PageEffectSpec {
        val json = JSONObject(java.io.File(root, "docs/sdk/examples/page-effect-tilt/effect.json").readText())
        return PackagedPageEffect.of("com.mccal.folio.effect.tilt", "Tilt", json.getDouble("maxRotation").toFloat(),
            json.getString("pivot"), json.getDouble("shrink").toFloat(), json.getDouble("cameraWidths").toFloat()).spec
    }

    /** Positions across three pages either side, in 1/64 steps, plus values that land between baked samples. */
    private val positions: List<Float> = (-192..192).map { it / 64f } +
        listOf(-1e-6f, 1e-6f, .001f, -.37f, .37f, .999f, -.999f, 1.0001f, -1.0001f, 5f, -5f)

    /** Cover, inner, and the widths in between that Folio's panes and the Mockup Lab use, in pixels. */
    private val widths = listOf(720f, 984f, 1080f, 1248f, 1768f, 1968f, 2176f)

    /** The v1 layer block, exactly as `Modifier.pageEffect` set it before channels existed. Frozen on purpose. */
    private fun GraphicsLayerScope.v1(spec: PageEffectSpec, position: Float, widthPx: Float) {
        val pivotX = when (spec.pivot) {
            PageEffectSpec.Pivot.CENTER -> .5f
            PageEffectSpec.Pivot.SEAM -> if (position > 0f) 0f else 1f
        }
        transformOrigin = TransformOrigin(pivotX, .5f)
        rotationY = spec.maxRotation * position.coerceIn(-1f, 1f)
        scaleX = 1f - spec.shrink * abs(position).coerceAtMost(1f)
        scaleY = scaleX
        cameraDistance = spec.cameraWidths * widthPx / 72f
    }

    /** Everything a layer can carry, as raw bits, so 0 and -0 or a last-digit difference would show. */
    private fun GraphicsLayerScope.snapshot(): List<Any?> = listOf(
        scaleX.toRawBits(), scaleY.toRawBits(), alpha.toRawBits(), translationX.toRawBits(), translationY.toRawBits(),
        shadowElevation.toRawBits(), rotationX.toRawBits(), rotationY.toRawBits(), rotationZ.toRawBits(),
        cameraDistance.toRawBits(), transformOrigin, shape, clip, renderEffect, compositingStrategy,
        ambientShadowColor, spotShadowColor,
    )

    private fun layer(block: GraphicsLayerScope.() -> Unit) = GraphicsLayerScope().apply(block).snapshot()

    @Test fun `with no channels every v1 effect draws number for number as before, at every position and width`() {
        var compared = 0
        for ((name, spec) in v1Effects) {
            assertNull("$name is a v1 effect", spec.channels)
            for (width in widths) for (position in positions) {
                val before = layer { v1(spec, position, width) }
                val after = layer { applyPageEffect(spec, position, width, width * 1.1f, width) }
                assertEquals("$name at $position on a $width px page", before, after)
                compared++
            }
        }
        assertEquals(5 * widths.size * positions.size, compared)
        assertFalse("a v1 effect never needs the pager's page step", v1Effects.values.any { it.shiftsSideways })
    }

    private fun curve(vararg keys: Pair<Number, Number>, ease: String = "linear") = JSONObject().put("ease", ease)
        .put("keys", JSONArray().apply { keys.forEach { (at, value) -> put(JSONObject().put("at", at).put("value", value)) } })

    private fun channels(vararg entries: Pair<String, Any>): PageEffectChannels? =
        PageEffectChannels.from(JSONObject().apply { entries.forEach { (key, value) -> put(key, value) } })

    private fun spec(channels: PageEffectChannels?, base: PageEffectSpec = PageEffect.CUBE.spec!!) = base.copy(channels = channels)

    @Test fun `a straight rotationY curve draws the cube it describes`() {
        val cube = PageEffect.CUBE.spec!!
        val curved = spec(channels("rotationY" to curve(0 to 0, 1 to 90), "pivotX" to "seam"))
        for (width in widths) for (position in positions) {
            val v1 = GraphicsLayerScope().apply { v1(cube, position, width) }
            val v2 = GraphicsLayerScope().apply { applyPageEffect(curved, position, width, width, width) }
            assertEquals("rotation at $position", v1.rotationY, v2.rotationY, 1e-3f)
            assertEquals("pivot at $position", v1.transformOrigin, v2.transformOrigin)
            assertEquals(v1.scaleX, v2.scaleX, 0f)
            assertEquals(v1.cameraDistance, v2.cameraDistance, 0f)
        }
    }

    @Test fun `a settled page is untouched whatever the keys say at 0`() {
        val wild = spec(channels(
            "rotationY" to curve(0 to 60, 1 to 90), "rotationZ" to curve(0 to 30, 1 to 8),
            "translateX" to curve(0 to .5, 1 to -1), "translateY" to curve(0 to .2, 1 to .1),
            "scale" to curve(0 to .6, 1 to .8), "pivotY" to 1.3,
        ))
        val layer = GraphicsLayerScope().apply { applyPageEffect(wild, 0f, 1000f, 2000f, 1000f) }
        assertEquals(0f, layer.rotationY, 0f)
        assertEquals(0f, layer.rotationZ, 0f)
        assertEquals(0f, layer.translationX, 0f)
        assertEquals(0f, layer.translationY, 0f)
        assertEquals(1f, layer.scaleX, 0f)
        assertEquals(1f, layer.scaleY, 0f)
        // And it leaves 0 smoothly rather than jumping to the key's value on the first pixel of a swipe.
        assertTrue(abs(wild.rotationY(.001f)) < 1f)
        assertTrue(abs(1f - wild.scale(.001f)) < .01f)
    }

    @Test fun `turns and shifts mirror, the drop and the scale do not`() {
        val s = spec(channels(
            "rotationY" to curve(.5 to 40, 1 to 70, ease = "inOut"), "rotationZ" to curve(1 to 8),
            "translateX" to curve(1 to -.5), "translateY" to curve(1 to .1, ease = "out"), "scale" to curve(1 to .8),
        ))
        for (x in positions.filter { it > 0f }) {
            assertEquals(-s.rotationY(x), s.rotationY(-x), 0f)
            assertEquals(-s.rotationZ(x), s.rotationZ(-x), 0f)
            assertEquals(-s.translateX(x), s.translateX(-x), 0f)
            assertEquals(s.translateY(x), s.translateY(-x), 0f)
            assertEquals(s.scale(x), s.scale(-x), 0f)
        }
        assertTrue("a page on the right turns clockwise for a positive key", s.rotationZ(.5f) > 0f)
        assertTrue("a drop is down on both sides", s.translateY(-.5f) > 0f)
    }

    @Test fun `every channel is held to the range the engine draws well`() {
        val c = channels(
            "rotationY" to curve(1 to 400), "rotationZ" to curve(.5 to -100, 1 to 100),
            "translateX" to curve(1 to 3), "translateY" to curve(.5 to -.1, 1 to .9),
            "scale" to curve(.5 to .1, 1 to 1.4), "pivotX" to 1.5, "pivotY" to 5,
        )!!
        val s = spec(c)
        assertEquals(90f, s.rotationY(1f), 0f)
        assertEquals(-45f, s.rotationZ(.5f), 0f)
        assertEquals(45f, s.rotationZ(1f), 0f)
        assertEquals(1f, s.translateX(1f), 0f)
        assertEquals(0f, s.translateY(.5f), 0f)
        assertEquals(.25f, s.translateY(1f), 0f)
        assertEquals(.6f, s.scale(.5f), 0f)
        assertEquals(1f, s.scale(1f), 0f)
        assertEquals(1f, c.pivotX!!, 0f)
        assertEquals(PageEffectChannels.MAX_PIVOT_Y, s.pivotY, 0f)
        // Every baked value of every channel sits inside its range.
        for (table in listOfNotNull(c.rotationY, c.rotationZ, c.translateX, c.translateY, c.scale)) {
            assertEquals(PageEffectChannels.SAMPLES, table.size)
        }
        for (channel in PageEffectChannels.Channel.entries) assertTrue(channel.identity in channel.min..channel.max)
    }

    @Test fun `a curve uses its first eight keys and holds its last value`() {
        val keys = (1..12).map<Int, Pair<Number, Number>> { it / 12.0 to it * 5 }.toTypedArray()
        val c = PageEffectChannels.Curve.from(curve(*keys), PageEffectChannels.Channel.ROTATION_Y)!!
        assertEquals(PageEffectChannels.MAX_KEYS, c.keys.size)
        val s = spec(PageEffectChannels(rotationYCurve = c))
        assertEquals("held from the eighth key on", 40f, s.rotationY(.9f), 1e-4f)
        assertEquals(40f, s.rotationY(1f), 1e-4f)
        assertEquals("and past a full page out", 40f, s.rotationY(3f), 1e-4f)
        assertEquals(-40f, s.rotationY(-3f), 1e-4f)
    }

    @Test fun `the eases bend the curve between keys and the baked table follows them`() {
        fun valueAt(ease: String, x: Float) = spec(channels("rotationY" to curve(1 to 90, ease = ease))).rotationY(x)
        assertEquals(45f, valueAt("linear", .5f), 1e-3f)
        assertEquals(45f, valueAt("inOut", .5f), 1e-3f)
        assertTrue("in starts slow", valueAt("in", .25f) < valueAt("linear", .25f))
        assertTrue("out starts fast", valueAt("out", .25f) > valueAt("linear", .25f))
        assertTrue("inOut starts slow", valueAt("inOut", .25f) < valueAt("linear", .25f))
        assertTrue("inOut ends slow", valueAt("inOut", .75f) > valueAt("linear", .75f))
        // A flat start, the curve the pager's landing overshoot needs: nothing turns until a tenth of a page out.
        val flat = spec(channels("rotationY" to curve(.1 to 0, 1 to 90, ease = "inOut")))
        assertEquals(0f, flat.rotationY(.05f), 0f)
        assertEquals(0f, flat.rotationY(-.08f), 0f)
        // Between samples the table is within a fraction of a degree of the exact curve. A key on a sample (here a
        // quarter page, 8/32) keeps its corner; one between samples has its corner rounded across 1/32 of a page.
        for (ease in PageEffectChannels.Ease.entries) {
            val c = PageEffectChannels.Curve(listOf(PageEffectChannels.Key(.25f, 10f), PageEffectChannels.Key(1f, 90f)), ease)
            val s = spec(PageEffectChannels(rotationYCurve = c))
            for (i in 0..1000) {
                val x = i / 1000f
                assertEquals("$ease at $x", c.valueAt(PageEffectChannels.Channel.ROTATION_Y, x), s.rotationY(x), .25f)
            }
        }
    }

    @Test fun `the pivot is a share from the seam to the outer edge, mirrored on the left`() {
        val v1Seam = PageEffect.CUBE.spec!!
        val seam = spec(channels("pivotX" to "seam"))
        val outer = spec(channels("pivotX" to "outer"))
        val center = spec(channels("pivotX" to "center"))
        val quarter = spec(channels("pivotX" to .25))
        for (x in positions) {
            assertEquals("seam matches v1 at $x", v1Seam.pivotX(x), seam.pivotX(x), 0f)
            assertEquals(.5f, center.pivotX(x), 0f)
        }
        assertEquals("a page on the right hinges on its right edge", 1f, outer.pivotX(.5f), 0f)
        assertEquals("a page on the left on its left edge", 0f, outer.pivotX(-.5f), 0f)
        assertEquals(.25f, quarter.pivotX(.5f), 0f)
        assertEquals(.75f, quarter.pivotX(-.5f), 0f)
        assertNull("a keyword Folio does not know is left out", channels("pivotX" to "hinge"))
        // pivotY moves the axis below the page for a wheel: a hub 1.3 page heights down.
        val wheel = spec(channels("rotationZ" to curve(1 to 8), "pivotY" to 1.3))
        val layer = GraphicsLayerScope().apply { applyPageEffect(wheel, .5f, 1000f, 2000f, 1000f) }
        assertEquals(1.3f, layer.transformOrigin.pivotFractionY, 1e-6f)
    }

    @Test fun `a channel left out still comes from the four v1 numbers`() {
        val carousel = PageEffect.CAROUSEL.spec!!
        val stairs = spec(channels("translateY" to curve(1 to .1)), carousel)
        for (x in positions) {
            assertEquals(carousel.rotationY(x), stairs.rotationY(x), 0f)
            assertEquals(carousel.scale(x), stairs.scale(x), 0f)
            assertEquals(carousel.pivotX(x), stairs.pivotX(x), 0f)
            assertEquals(0f, stairs.rotationZ(x), 0f)
            assertEquals(0f, stairs.translateX(x), 0f)
        }
        assertEquals(.5f, stairs.pivotY, 0f)
        assertFalse(stairs.shiftsSideways)
        assertTrue(spec(channels("translateX" to curve(1 to -1))).shiftsSideways)
    }

    @Test fun `the layer carries the new channels in pixels and nothing that costs a buffer`() {
        val s = spec(channels("rotationZ" to curve(1 to 8), "translateX" to curve(1 to -1), "translateY" to curve(1 to .2)))
        val layer = GraphicsLayerScope().apply { applyPageEffect(s, .5f, 1000f, 2000f, 1040f) }
        assertEquals(4f, layer.rotationZ, 1e-4f)
        assertEquals("half of a stay-put shift, in the pager's own step", -520f, layer.translationX, 1e-2f)
        assertEquals("a drop in page heights", 200f, layer.translationY, 1e-2f)
        val untouched = GraphicsLayerScope()
        assertEquals(untouched.alpha, layer.alpha, 0f)
        assertEquals(untouched.clip, layer.clip)
        assertEquals(untouched.shadowElevation, layer.shadowElevation, 0f)
        assertEquals(untouched.renderEffect, layer.renderEffect)
        assertEquals(untouched.compositingStrategy, layer.compositingStrategy)
        assertEquals(untouched.rotationX, layer.rotationX, 0f)
    }

    @Test fun `what cannot be read is left out, and nothing usable is no channels at all`() {
        assertNull(PageEffectChannels.from(null))
        assertNull(PageEffectChannels.from(JSONObject()))
        assertNull(channels("rotationY" to JSONObject().put("keys", JSONArray())))
        assertNull(channels("rotationY" to JSONObject().put("keys", JSONArray().put(JSONObject().put("at", "a").put("value", 1)))))
        assertNull(channels("rotationY" to JSONObject().put("keys", JSONArray().put(JSONObject().put("at", 1)))))
        assertNull(channels("rotationY" to JSONObject().put("keys", JSONArray().put(JSONArray().put(1).put(30)))))
        assertNull("NaN is not a pivot", channels("pivotY" to "high"))
        val unknownEase = channels("rotationY" to curve(1 to 30, ease = "bounce"))!!
        assertEquals(PageEffectChannels.Ease.LINEAR, unknownEase.rotationYCurve!!.ease)
        // Keys out of order are sorted, and a repeated position keeps its first value.
        val messy = PageEffectChannels.Curve.from(curve(1 to 90, .5 to 10, .5 to 80), PageEffectChannels.Channel.ROTATION_Y)!!
        assertEquals(listOf(.5f, 1f), messy.keys.map { it.at })
        assertEquals(10f, messy.keys.first().value, 0f)
    }

    @Test fun `channels survive a save and a load, baked tables and all`() {
        val c = JSONObject().put("rotationY", curve(.1 to 0, 1 to 45, ease = "inOut")).put("translateY", curve(1 to .1))
            .put("scale", curve(1 to .85, ease = "out")).put("pivotX", "outer").put("pivotY", 1.3)
        val effect = PackagedPageEffect.of("dev.example.stairs", "Stairs", 20f, "center", .1f, 3f, c)
        assertNotNull(effect.spec.channels)
        val back = PackagedPageEffect.from(effect.toJson())!!
        assertEquals(effect, back)
        assertEquals("and through the saved text", effect, PackagedPageEffect.from(JSONObject(effect.toJson().toString())))
        val saved = effect.spec.channels!!
        val loaded = back.spec.channels!!
        assertArrayEquals(saved.rotationY, loaded.rotationY, 0f)
        assertArrayEquals(saved.scale, loaded.scale, 0f)
        // A v1 effect saves no channels key, so a v1 save reads back the same in an older Folio.
        val tilt = PackagedPageEffect.of("com.mccal.folio.effect.tilt", "Tilt", 18f, "center", .08f, 3.5f)
        assertFalse(tilt.toJson().has("channels"))
        assertEquals(tilt, PackagedPageEffect.from(tilt.toJson()))
    }

    @Test fun `the layer block reads baked tables only, and the pager's step only when it must`() {
        val effects = java.io.File(root, "app/src/main/java/com/mccal/folio/PageEffects.kt").readText()
        val modifier = effects.substringAfter("internal fun Modifier.pageEffect(effect: PageEffectSpec?")
            .substringBefore("\n/**")
        assertTrue("None still adds no layer", "if (effect == null) this else graphicsLayer" in modifier)
        assertTrue("the step is read only for a sideways shift", "if (effect.shiftsSideways) pager.layoutInfo" in modifier)
        val apply = effects.substringAfter("internal fun GraphicsLayerScope.applyPageEffect(").substringBefore("\n}\n")
        for (banned in listOf("alpha", "clip", "shadowElevation", "renderEffect", "compositingStrategy", "shape")) {
            assertFalse("the layer block sets $banned", banned in apply)
        }
        for (work in listOf("valueAt", "bake", "Curve", "JSONObject", "listOf", "FloatArray(")) {
            assertFalse("the layer block does $work", work in apply)
        }
    }
}
