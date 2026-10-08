package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** `options` in `tweaks.json`: checked against the tweak's own list, strictly for values and leniently for keys. */
class TweakOptionsTest {
    private fun parse(options: String, id: String = "duet") =
        TweakBundle.parse("""{"format":1,"tweaks":[{"id":"$id","enabled":true,"options":$options}]}""")

    private fun ok(options: String, id: String = "duet"): ParseResult.Ok<TweakBundle> =
        parse(options, id) as? ParseResult.Ok ?: fail("expected Ok, got ${parse(options, id)}") as Nothing

    private fun errors(options: String) = (parse(options) as? ParseResult.Invalid)?.errors ?: fail("expected Invalid") as Nothing

    @Test fun `Duet reads a style and its sliders`() {
        val setting = ok("""{"style":"deep","frost":0.8,"darkening":1,"perspective":1.2,"intensity":1.4}""").value.tweaks.single()
        assertEquals(TweakId.DUET, setting.id)
        assertEquals(mapOf("style" to "deep", "frost" to .8, "darkening" to 1.0, "perspective" to 1.2, "intensity" to 1.4), setting.options)
        assertEquals(setOf(Capability.FOLD_TRANSITION), PackageChange.Tweaks(ok("{}").value).capabilities)
    }

    @Test fun `a value out of range or a style Folio doesn't have is refused`() {
        assertTrue(errors("""{"frost":3}""").single().contains("frost must be between"))
        assertTrue(errors("""{"style":"sparkle"}""").single().contains("must be one of"))
        assertTrue(errors("""{"frost":"1"}""").single().contains("must be a number"))
        assertTrue(errors("""{"style":2}""").single().contains("must be a string"))
    }

    @Test fun `a key from a newer Folio is skipped and reported, and the package still reads`() {
        val result = ok("""{"style":"subtle","ripple":true}""")
        assertEquals(mapOf("style" to "subtle"), result.value.tweaks.single().options)
        assertTrue(result.ignored.toString(), result.ignored.any { "ripple" in it })
    }

    @Test fun `a tweak with no options of its own takes none`() {
        val result = ok("""{"style":"deep"}""", id = "appPanels")
        assertEquals(emptyMap<String, Any>(), result.value.tweaks.single().options)
        assertTrue(result.ignored.any { "style" in it })
    }

    @Test fun `a bundle without options is unchanged`() {
        val result = TweakBundle.parse("""{"format":1,"tweaks":[{"id":"duet","enabled":true}]}""") as ParseResult.Ok
        assertEquals(emptyMap<String, Any>(), result.value.tweaks.single().options)
    }
}

class TweakOptionRecordTest {
    @Test fun `a saved record gives back numbers and strings, and drops shapes it doesn't know`() {
        val record = org.json.JSONObject("""{"style":"deep","frost":1,"ripple":true,"nested":{"a":1}}""")
        assertEquals(mapOf("style" to "deep", "frost" to 1.0), readOptionRecord(record))
    }
}
