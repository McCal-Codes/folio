package com.mccal.folio.market

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `effect.json` as a package carries it (format-v1.md, Page effects): the four v1 numbers, and from 0.7.0 the optional
 * `channels` and the reserved `icons`. The app clamps and bakes what this lets through (PageEffectChannels); this is
 * where a package that breaks the format is refused, or told it needs a newer Folio.
 */
class PageEffectFileTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val tilt = File(root, "docs/sdk/examples/page-effect-tilt/effect.json").readText()

    private fun read(text: String) = PackageChange.PageEffect.read("dev.example.effect", "Example", text)
    private fun effect(channels: String) = """{"maxRotation":0,"pivot":"center","shrink":0.1,"cameraWidths":3,"channels":$channels}"""
    private fun ok(text: String) = (read(text) as ParseResult.Ok).value

    @Test fun `a v1 effect reads as it always did, with no channels and only the v1 capability`() {
        val result = read(tilt)
        assertTrue("$result", result is ParseResult.Ok && result.ignored.isEmpty())
        val effect = (result as ParseResult.Ok).value
        assertEquals(PackageChange.PageEffect("dev.example.effect", "Example", 18f, "center", .08f, 3.5f), effect)
        assertNull(effect.channels)
        assertEquals(setOf(Capability.PAGE_EFFECTS), effect.capabilities)
        // Out of range is still the app's to clamp, not a refusal: the same as before channels existed.
        assertTrue(read("""{"maxRotation":400,"pivot":"seam","shrink":2,"cameraWidths":0.1}""") is ParseResult.Ok)
    }

    @Test fun `channels are read into Folio's own words and need the channels capability`() {
        val effect = ok(effect("""{"translateY":{"keys":[{"at":0.1,"value":0},{"at":1,"value":0.12}],"ease":"inOut"},
            "scale":{"keys":[{"at":1,"value":0.85}]},"pivotX":"outer","pivotY":1.3}"""))
        assertEquals(setOf(Capability.PAGE_EFFECTS, Capability.PAGE_EFFECT_CHANNELS), effect.capabilities)
        val channels = JSONObject(effect.channels!!)
        assertEquals(setOf("translateY", "scale", "pivotX", "pivotY"), channels.keys().asSequence().toSet())
        assertEquals("linear", channels.getJSONObject("scale").getString("ease"))
        assertEquals("inOut", channels.getJSONObject("translateY").getString("ease"))
        assertEquals(2, channels.getJSONObject("translateY").getJSONArray("keys").length())
        assertEquals("outer", channels.getString("pivotX"))
        assertEquals(1.3, channels.getDouble("pivotY"), 0.0)
        // Values out of range are clamped by the app, like the four numbers.
        assertTrue(read(effect("""{"rotationZ":{"keys":[{"at":1,"value":200}]},"pivotX":5,"pivotY":-1}""")) is ParseResult.Ok)
    }

    @Test fun `a channel from a newer Folio is skipped and reported, the rest still reads`() {
        val result = read(effect("""{"skewX":{"keys":[{"at":1,"value":4}]},"scale":{"keys":[{"at":1,"value":0.9}]}}"""))
        assertTrue("$result", result is ParseResult.Ok)
        assertTrue((result as ParseResult.Ok).ignored.any { "skewX" in it })
        assertEquals(setOf("scale"), JSONObject(result.value.channels!!).keys().asSequence().toSet())
    }

    @Test fun `icons are reserved, so a package using them needs a newer Folio`() {
        val result = read(tilt.trimEnd().removeSuffix("}") + ""","icons":{"rotationZ":{"keys":[{"at":1,"value":8}]}}}""")
        assertEquals(ParseResult.Unsupported(listOf("icons")), result)
    }

    @Test fun `an ease this Folio does not know needs a newer Folio`() {
        val result = read(effect("""{"scale":{"keys":[{"at":1,"value":0.9}],"ease":"spring"}}"""))
        assertTrue("$result", result is ParseResult.Unsupported)
    }

    @Test fun `channels that break the format are refused`() {
        for (channels in listOf(
            "{}",                                                                                   // nothing in it
            """{"scale":{"keys":[]}}""",                                                            // no keys
            """{"scale":{}}""",                                                                     // keys left out
            """{"scale":{"keys":[${(1..9).joinToString(",") { """{"at":${it / 10.0},"value":0.9}""" }}]}}""", // nine keys
            """{"scale":{"keys":[{"at":1.5,"value":0.9}]}}""",                                      // past a full page
            """{"scale":{"keys":[{"at":-0.1,"value":0.9}]}}""",                                     // before settled
            """{"scale":{"keys":[{"at":0.8,"value":0.9},{"at":0.4,"value":0.8}]}}""",               // out of order
            """{"scale":{"keys":[{"at":0.5,"value":0.9},{"at":0.5,"value":0.8}]}}""",               // the same at twice
            """{"scale":{"keys":[{"at":1,"value":"0.9"}]}}""",                                      // a string for a number
            """{"scale":{"keys":[{"at":1}]}}""",                                                    // no value
            """{"scale":{"keys":[[1,0.9]]}}""",                                                     // a pair, not a key
            """{"pivotX":"hinge"}""",                                                               // a pivot Folio lacks
            """{"pivotX":true}""",
            """{"pivotY":"low"}""",
            """[]""",                                                                               // not an object
        )) {
            val result = read(effect(channels))
            assertTrue("$channels: $result", result is ParseResult.Invalid)
        }
    }

    @Test fun `what v1 required is still required`() {
        for (text in listOf(
            """{"pivot":"seam","shrink":0,"cameraWidths":2}""",
            """{"maxRotation":"90","pivot":"seam","shrink":0,"cameraWidths":2}""",
            """{"maxRotation":90,"pivot":"outer","shrink":0,"cameraWidths":2}""",
            """not json""",
        )) assertTrue(text, read(text) is ParseResult.Invalid)
    }

    @Test fun `a saved record keeps the channels, so Undo and a restart put back the same effect`() {
        val effect = ok(effect("""{"rotationZ":{"keys":[{"at":1,"value":8}],"ease":"out"},"pivotY":1.3}"""))
        val store = InstalledStore(MemoryStore())
        val back = store.decodeChanges(store.encodeChanges(listOf(effect)))!!.single() as PackageChange.PageEffect
        assertEquals(effect.copy(channels = null), back.copy(channels = null))
        assertEquals(JSONObject(effect.channels!!).toString(), JSONObject(back.channels!!).toString())
        assertEquals(effect.capabilities, back.capabilities)
        // A v1 record still decodes with no channels.
        val v1 = ok(tilt)
        assertEquals(v1, store.decodeChanges(store.encodeChanges(listOf(v1)))!!.single())
    }
}
