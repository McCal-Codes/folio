package com.mccal.folio.market

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The published format docs stay loadable, and the Cabinet sample keeps matching what the docs promise. */
class SdkSchemasTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val sdk = File(root, "docs/sdk")

    @Test fun `every v1 schema is JSON with the right id`() {
        val schemas = File(sdk, "schema/v1").listFiles { f -> f.name.endsWith(".schema.json") }!!.sortedBy { it.name }
        assertEquals(listOf("depiction", "entry", "index", "manifest", "revoked", "settings", "tweaks"), schemas.map { it.name.substringBefore('.') })
        for (file in schemas) {
            val schema = JSONObject(file.readText())
            assertEquals(file.name, "https://folio.mccal.dev/schema/v1/${file.name}", schema.getString("\$id"))
        }
    }

    @Test fun `every package in the Folio source uses only declared permissions and kinds`() {
        val schema = JSONObject(File(sdk, "schema/v1/manifest.schema.json").readText()).getJSONObject("properties")
        val allowed = { name: String -> schema.getJSONObject(name).getJSONObject("items").getJSONArray("enum").let { a -> (0 until a.length()).map(a::getString) } }
        val packages = File(sdk, "source/packages").listFiles()!!.sortedBy { it.name }
        assertEquals(10, packages.size)
        for (dir in packages) {
            val manifest = JSONObject(File(dir, "manifest.json").readText())
            assertEquals(dir.name, 1, manifest.getInt("format"))
            val kinds = manifest.getJSONArray("kind").let { a -> (0 until a.length()).map(a::getString) }
            val permissions = manifest.getJSONArray("permissions").let { a -> (0 until a.length()).map(a::getString) }
            assertTrue(dir.name, kinds.all { it in allowed("kind") })
            assertTrue(dir.name, permissions.all { it in allowed("permissions") })
        }
    }
}
