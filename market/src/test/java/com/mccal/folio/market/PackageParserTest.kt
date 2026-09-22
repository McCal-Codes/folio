package com.mccal.folio.market

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class PackageParserTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val cabinet = File(root, "docs/sdk/source/packages/cabinet/manifest.json").readText()
    private val full = javaClass.getResource("/conformance/manifest-all-fields.json")!!.readText()
    private val fullDepiction = javaClass.getResource("/conformance/depiction-all-blocks.json")!!.readText()

    private fun <T> ok(result: ParseResult<T>): T {
        if (result !is ParseResult.Ok) fail("expected Ok, got $result")
        return (result as ParseResult.Ok).value
    }

    private fun edit(json: String, change: JSONObject.() -> Unit) = JSONObject(json).apply(change).toString()

    private fun manifestErrors(text: String): List<String> {
        val result = PackageManifest.parse(text)
        if (result !is ParseResult.Invalid) fail("expected Invalid, got $result")
        return (result as ParseResult.Invalid).errors
    }

    @Test fun `reads the Cabinet sample`() {
        val m = ok(PackageManifest.parse(cabinet))
        assertEquals("com.mccal.folio.cabinet", m.id)
        assertEquals("Cabinet", m.name.english)
        assertEquals(setOf(PackageKind.TWEAK_BUNDLE), m.kinds)
        assertEquals(setOf(PackagePermission.TWEAKS), m.permissions)
        assertEquals(setOf(Capability.APP_PANELS), m.requiredFeatures)
        assertEquals(FolioVersion(0, 6, 6), m.minFolio)
        assertEquals("https://github.com/McCal-Codes/folio", m.author.url)
        assertEquals(setOf(Capability.APP_PANELS), m.missingCapabilities(setOf(Capability.THEME)))
        assertTrue(m.missingCapabilities(Capability.entries.toSet()).isEmpty())
    }

    @Test fun `reads every manifest field`() {
        val m = ok(PackageManifest.parse(full))
        assertEquals("Todos los campos", m.name.resolve(listOf("es")))
        assertEquals(DebVersion.parse("2:1.4.0~beta2-1"), m.version)
        assertEquals(setOf(Screen.INNER), m.screens)
        assertEquals(listOf("com.mccal.folio.theme.classic (>= 1.0)", "com.mccal.folio.cabinet"), m.depends.map { it.toString() })
        assertEquals(PackageRelation.Op.EARLIER, m.conflicts.single().op)
        assertEquals(listOf(ExternalSource.Store.OBTAINIUM), m.via.map { it.store })
        assertEquals("https://github.com/McCal-Codes/folio", m.via.single().repoUrl)
        assertEquals(setOf(Provides.FOLIO_THEME), m.provides)
    }

    @Test fun `reads who says AI helped, and refuses a malformed declaration`() {
        val m = ok(PackageManifest.parse(full))
        assertEquals(listOf("Claude", "Example tool"), m.aiAssisted?.tools)
        assertEquals("Escribió el primer borrador de los colores.", m.aiAssisted?.note?.resolve(listOf("es")))
        assertNull(ok(PackageManifest.parse(cabinet)).aiAssisted)
        assertNull(ok(PackageManifest.parse(edit(cabinet) { put("aiAssisted", JSONObject().put("tools", JSONArray().put("Claude"))) })).aiAssisted?.note)

        fun errors(ai: Any) = manifestErrors(edit(cabinet) { put("aiAssisted", ai) })
        assertTrue(errors(JSONObject()).any { "aiAssisted.tools is required" in it })
        assertTrue(errors(JSONObject().put("tools", JSONArray())).any { "aiAssisted.tools" in it })
        assertTrue(errors(JSONObject().put("tools", JSONArray().put("  "))).any { "aiAssisted.tools[0]" in it })
        assertTrue(errors(JSONObject().put("tools", JSONArray().put("Claude").put("Claude"))).any { "twice" in it })
        assertTrue(errors(JSONObject().put("tools", JSONArray((1..6).map { "Tool $it" }))).any { "more than 5" in it })
        assertTrue(errors(JSONObject().put("tools", JSONArray().put("x".repeat(61)))).any { "longer than 60" in it })
        assertTrue(errors(true).any { "aiAssisted must be an object" in it })
    }

    @Test fun `screens default to both`() {
        val m = ok(PackageManifest.parse(edit(cabinet) { remove("screens") }))
        assertEquals(setOf(Screen.COVER, Screen.INNER), m.screens)
    }

    @Test fun `fields from a newer format are skipped and reported`() {
        val result = PackageManifest.parse(edit(cabinet) { put("sparkles", true); getJSONObject("author").put("pronouns", "x") })
        assertTrue(result is ParseResult.Ok)
        assertEquals(listOf("sparkles", "author.pronouns"), (result as ParseResult.Ok).ignored.sorted().reversed())
    }

    @Test fun `unknown kinds, permissions, capabilities or a newer format need a newer Folio`() {
        val cases = listOf<JSONObject.() -> Unit>(
            { put("kind", JSONArray(listOf("tweakBundle", "hologram"))) },
            { put("permissions", JSONArray(listOf("camera"))) },
            { put("requires", JSONObject().put("features", JSONArray(listOf("tweaks.teleport")))) },
            { put("section", "games") },
            { put("screens", JSONArray(listOf("rear"))) },
            { put("format", 2) },
        )
        for (case in cases) {
            val result = PackageManifest.parse(edit(cabinet, case))
            assertTrue("$result", result is ParseResult.Unsupported)
        }
    }

    @Test fun `broken fields are reported with their path`() {
        assertTrue(manifestErrors(edit(cabinet) { remove("id") }).contains("id is required"))
        assertTrue(manifestErrors(edit(cabinet) { put("id", "Cabinet") }).single().startsWith("id must be lowercase"))
        assertTrue(manifestErrors(edit(cabinet) { put("id", "com.mccal.folio.cabinet\n") }).single().startsWith("id "))
        assertEquals(listOf("author.url must be an https:// link"), manifestErrors(edit(cabinet) { getJSONObject("author").put("url", "http://x.com") }))
        assertEquals(listOf("icon must be a relative path inside the package"), manifestErrors(edit(cabinet) { put("icon", "../../secrets.png") }))
        assertEquals(listOf("name must be 1–40 characters"), manifestErrors(edit(cabinet) { put("name", "x".repeat(41)) }))
        assertEquals(listOf("kind needs at least 1 item"), manifestErrors(edit(cabinet) { put("kind", JSONArray()) }))
        assertEquals(listOf("permissions lists something twice"), manifestErrors(edit(cabinet) { put("permissions", JSONArray(listOf("tweaks", "tweaks"))) }))
        assertEquals(listOf("format must be 1"), manifestErrors(edit(cabinet) { put("format", "1") }))
        assertEquals(listOf("minFolio must look like 0.7.0"), manifestErrors(edit(cabinet) { put("minFolio", "0.7") }))
        assertEquals(listOf("depends[0] must look like \"dev.maya.icons\" or \"dev.maya.icons (>= 1.2)\""),
            manifestErrors(edit(cabinet) { put("depends", JSONArray(listOf("dev.maya.icons (> 1)"))) }))
        // Several problems are all reported at once.
        assertEquals(3, manifestErrors(edit(cabinet) { remove("name"); put("version", "one"); put("license", 5) }).size)
    }

    @Test fun `external apps need a via list with the right fields per store`() {
        assertEquals(listOf("via is required for externalApp packages"), manifestErrors(edit(full) { remove("via") }))
        assertEquals(listOf("via[0].id is required for playStore"),
            manifestErrors(edit(full) { put("via", JSONArray().put(JSONObject().put("store", "playStore"))) }))
        assertEquals(listOf("via[0].repoUrl is required for obtainium"),
            manifestErrors(edit(full) { put("via", JSONArray().put(JSONObject().put("store", "obtainium").put("id", "com.a.b"))) }))
        assertTrue(PackageManifest.parse(edit(full) { put("via", JSONArray().put(JSONObject().put("store", "aurora"))) }) is ParseResult.Unsupported)
    }

    @Test fun `only strict JSON gets through`() {
        val bad = listOf(
            "", "[]", "null", "{", "{}{}", "{} x", """{"format":1,}""", "{format:1}", "{'format':1}", "{\"a\":1 // c\n}",
            """{"a":1,"a":2}""", """{"a":"a","a":2}""", """{"a":01}""", """{"a":1.}""", """{"a":NaN}""", "{\"a\":\"tab\there\"}",
            """{"a":"\x41"}""", """{"a":"\u12"}""", """{"a":"\u+123"}""", """{"a":tru}""", "﻿{}",
        )
        for (text in bad) {
            val result = PackageManifest.parse(text)
            assertTrue("$text -> $result", result is ParseResult.Invalid)
        }
        assertNull(JsonGuard.check("""{"a":[1,-0.5e+3,true,false,null,{"b":"\"\\\/\b\f\n\r\té"}]}""", 1000))
    }

    @Test fun `deep nesting and oversized files are refused without crashing`() {
        val deep = "{\"a\":" + "[".repeat(20_000) + "]".repeat(20_000) + "}"
        assertTrue((PackageManifest.parse(deep) as ParseResult.Invalid).errors.single().contains("nested deeper"))
        val justDeepEnough = "{\"a\":" + "[".repeat(JsonGuard.MAX_DEPTH - 1) + "]".repeat(JsonGuard.MAX_DEPTH - 1) + "}"
        assertNull(JsonGuard.check(justDeepEnough, 10_000))
        assertTrue(JsonGuard.check("{\"a\":" + "[".repeat(JsonGuard.MAX_DEPTH) + "]".repeat(JsonGuard.MAX_DEPTH) + "}", 10_000) != null)
        val big = edit(cabinet) { put("padding", "x".repeat(PackageManifest.MAX_CHARS)) }
        assertTrue((PackageManifest.parse(big) as ParseResult.Invalid).errors.single().contains("larger than"))
    }

    @Test fun `error lists stay short`() {
        val many = edit(cabinet) { put("depends", JSONArray((1..32).map { "BAD $it" })) }
        assertEquals(Problems.MAX_REPORTED, manifestErrors(many).size)
    }

    @Test fun `reads the Cabinet page and every block type`() {
        val cabinetPage = ok(Depiction.parse(File(root, "docs/sdk/source/packages/cabinet/depiction.json").readText()))
        assertEquals(0xFF0A84FF.toInt(), cabinetPage.tint)
        assertEquals(
            listOf("Markdown", "FeatureList", "Compatibility", "Changelog", "Link", "Donation"),
            cabinetPage.blocks.map { it::class.simpleName },
        )
        val page = ok(Depiction.parse(fullDepiction))
        assertEquals(0xFF0A84FF.toInt(), page.tint)
        assertEquals(
            listOf("Hero", "Screenshots", "Markdown", "FeatureList", "Compatibility", "Compatibility", "Changelog", "Link", "Donation"),
            page.blocks.map { it::class.simpleName },
        )
        val changelog = page.blocks.filterIsInstance<DepictionBlock.Changelog>().single()
        assertEquals(listOf("2026-09-17", null), changelog.entries.map { it.date })
        assertEquals(setOf(Screen.COVER, Screen.INNER), (page.blocks[5] as DepictionBlock.Compatibility).screens)
    }

    @Test fun `unknown blocks are skipped, broken blocks are errors`() {
        val withFuture = edit(fullDepiction) { getJSONArray("blocks").put(1, JSONObject().put("type", "video").put("url", "https://x.com/v.mp4")) }
        val result = Depiction.parse(withFuture) as ParseResult.Ok
        assertEquals(8, result.value.blocks.size)
        assertEquals(listOf("blocks[1] (video block)"), result.ignored)

        val broken = listOf<JSONObject.() -> Unit>(
            { getJSONArray("blocks").put(JSONObject().put("type", "link").put("title", "Site").put("url", "javascript:alert(1)")) },
            { getJSONArray("blocks").put(JSONObject().put("type", "hero").put("image", "/etc/passwd")) },
            { getJSONArray("blocks").put(JSONObject().put("image", "a.png")) },
            { getJSONArray("blocks").put("hero") },
            { getJSONArray("blocks").put(JSONObject().put("type", "screenshots").put("images", JSONArray((1..11).map { "a$it.png" }))) },
            { getJSONArray("blocks").put(JSONObject().put("type", "changelog").put("entries", JSONArray().put(JSONObject().put("version", "1.0").put("notes", "x").put("date", "Sept 17")))) },
            { put("tint", "#12345G") },
            { put("blocks", JSONArray((1..41).map { JSONObject().put("type", "donation").put("url", "https://a.b") })) },
        )
        for (change in broken) {
            val parsed = Depiction.parse(edit(fullDepiction, change))
            assertTrue("$parsed", parsed is ParseResult.Invalid)
        }
    }
}
