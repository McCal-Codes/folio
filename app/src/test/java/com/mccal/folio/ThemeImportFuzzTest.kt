package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A theme file comes from anywhere: a download, a share, a message. `FolioTheme.fromJson` must answer every one of
 * them quickly, with a theme or with null, and never with a theme outside what Folio can draw (PRV-15). This throws
 * thousands of damaged, oversized and hostile files at it, the way the Market's parsers are fuzzed.
 */
class ThemeImportFuzzTest {
    private val seeds = FolioTheme.PRESETS.map { it.toJson().toString() }

    /** What a hostile or broken file might put where a value should be. */
    private val junk: List<Any> = listOf(
        JSONObject.NULL, "", " ", "null", "🙂".repeat(200), "x".repeat(5_000), -1, 0, Int.MAX_VALUE, Long.MIN_VALUE,
        Long.MAX_VALUE, 1e308, true, false, JSONArray(), JSONObject(), JSONArray().put(JSONArray().put(1)),
    )

    private fun mutate(source: String, random: Random): String {
        val json = JSONObject(source)
        repeat(random.nextInt(1, 6)) {
            val keys = json.keys().asSequence().toList()
            when (random.nextInt(4)) {
                0 -> if (keys.isNotEmpty()) json.put(keys.random(random), junk.random(random))
                1 -> if (keys.isNotEmpty()) json.remove(keys.random(random))
                2 -> json.put("extra${random.nextInt(100)}", junk.random(random))
                else -> if (keys.isNotEmpty()) json.put(keys.random(random), json.opt(keys.random(random)))
            }
        }
        val text = json.toString()
        // Sometimes the file is cut short or has bytes flipped, as a damaged download would be.
        return when (random.nextInt(6)) {
            0 -> text.take(random.nextInt(text.length + 1))
            1 -> text.toCharArray().also { it[random.nextInt(it.size)] = random.nextInt(32, 127).toChar() }.concatToString()
            else -> text
        }
    }

    private fun assertDrawable(theme: FolioTheme?, from: String) {
        if (theme == null) return
        assertTrue("a name Folio can show: $from", theme.name.isNotBlank() && theme.name.length <= 40)
        assertTrue("an opaque icon tint: $from", (theme.iconTint ushr 24) and 0xFF == 0xFFL)
        assertTrue("an icon pack that is a name or nothing: $from", theme.iconPack == null || (theme.iconPack.isNotBlank() && theme.iconPack != "null"))
    }

    @Test fun `damaged theme files give a drawable theme or nothing, and never take long`() {
        val random = Random(20260925)
        val started = System.nanoTime()
        repeat(5_000) {
            val input = mutate(seeds.random(random), random)
            assertDrawable(FolioTheme.fromJson(input), input.take(120))
        }
        val seconds = (System.nanoTime() - started) / 1e9
        assertTrue("5,000 files in ${"%.1f".format(seconds)} s", seconds < 20)
    }

    @Test fun `files that aren't themes at all are refused without trouble`() {
        val hostile = listOf(
            "", "{", "}", "[]", "null", "1", "\"folioTheme\"",
            "{".repeat(10_000) + "}".repeat(10_000),                      // nesting deep enough to blow a stack
            "[".repeat(10_000),
            """{"folioTheme":1,"name":"${"a".repeat(ThemeImportActivity.MAX_BYTES)}"}""",
            """{"folioTheme":2}""", """{"folioTheme":"1"}""", """{"folioTheme":1e400}""",
        )
        hostile.forEach { assertDrawable(FolioTheme.fromJson(it), it.take(60)) }
    }
}
