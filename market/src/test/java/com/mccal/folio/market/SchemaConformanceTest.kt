package com.mccal.folio.market

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * ADR 0005: the published JSON Schemas are the contract, so Folio's parsers must agree with them.
 *
 * - Schema says valid → the parser reads it with nothing skipped.
 * - Schema says invalid → the parser rejects it, says it needs a newer Folio, or reports what it skipped
 *   (newer fields and blocks are skipped on purpose; see "Versioning this format").
 *
 * Two kinds of rule the parser applies that JSON Schema can't express:
 * - **Cross-field:** an index entry's `id` and `version` must match the manifest copy beside it, or a source could show
 *   one package and ship another. Errors like these are listed in [CROSS_FIELD] and don't count as a disagreement.
 * - **Kept out of the generated samples:** language tags that differ only in case, and a trailing newline, which Java's
 *   `$` would accept but Folio won't.
 */
class SchemaConformanceTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val schemaDir = File(root, "docs/sdk/schema/v1")
    private val mapper = ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
    // index.schema.json refers to manifest.schema.json by its published URL; map that prefix to this folder so the
    // tests read the schemas from disk and never reach for the network.
    private val factory = JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012))
        .schemaMappers { it.mapPrefix("https://folio.mccal.dev/schema/v1/", schemaDir.toURI().toString()) }
        .build()
    private val manifestSchema = schema("manifest")
    private val depictionSchema = schema("depiction")
    private val entrySchema = schema("entry")
    private val indexSchema = schema("index")
    private val revokedSchema = schema("revoked")
    private val tweaksSchema = schema("tweaks")
    private val sourceDir = File(root, "docs/sdk/source")

    private fun schema(name: String): JsonSchema = factory.getSchema(File(schemaDir, "$name.schema.json").readText())
    private fun resource(name: String) = javaClass.getResource("/conformance/$name")!!.readText()

    private fun schemaErrors(schema: JsonSchema, text: String): List<String> = try {
        schema.validate(mapper.readTree(text)).map { it.message }
    } catch (e: Exception) {
        listOf("unparseable: ${e.message}")
    }

    private fun agree(schema: JsonSchema, text: String, parse: (String) -> ParseResult<*>): String? {
        val errors = schemaErrors(schema, text)
        val result = parse(text)
        val clean = result is ParseResult.Ok && result.ignored.isEmpty()
        if (result is ParseResult.Invalid && result.errors.all { e -> CROSS_FIELD.any { it in e } }) return null
        return when {
            errors.isEmpty() && !clean -> "schema accepts, parser says $result"
            errors.isNotEmpty() && clean -> "schema rejects ($errors), parser accepts"
            else -> null
        }
    }

    @Test fun `every file Folio publishes is valid for both the schema and the parser`() {
        val files = mutableListOf<Triple<JsonSchema, String, (String) -> ParseResult<*>>>()
        for (dir in sourceDir.resolve("packages").listFiles()!!.sortedBy { it.name }) {
            files += Triple(manifestSchema, File(dir, "manifest.json").readText(), PackageManifest::parse)
            files += Triple(depictionSchema, File(dir, "depiction.json").readText(), Depiction::parse)
        }
        files += Triple(indexSchema, sourceDir.resolve("index.json").readText(), RepoIndex::parse)
        files += Triple(revokedSchema, sourceDir.resolve("revoked.json").readText(), RevocationList::parse)
        files += Triple(manifestSchema, resource("manifest-all-fields.json"), PackageManifest::parse)
        files += Triple(depictionSchema, resource("depiction-all-blocks.json"), Depiction::parse)
        assertEquals(24, files.size)
        for ((schema, text, parse) in files) {
            assertEquals(emptyList<String>(), schemaErrors(schema, text))
            assertEquals(null, agree(schema, text, parse))
        }
    }

    @Test fun `the Duet example, and tweak options in general, get the same answer from the schema and the parser`() {
        val example = File(root, "docs/sdk/examples/duet-deep")
        val manifest = example.resolve("manifest.json").readText()
        val bundle = example.resolve("tweaks.json").readText()
        assertEquals(emptyList<String>(), schemaErrors(manifestSchema, manifest))
        assertEquals(emptyList<String>(), schemaErrors(tweaksSchema, bundle))
        assertEquals(null, agree(manifestSchema, manifest, PackageManifest::parse))
        assertEquals(null, agree(tweaksSchema, bundle, TweakBundle::parse))
        fun tweak(id: String, options: String) = """{"format":1,"tweaks":[{"id":"$id","enabled":true,"options":$options}]}"""
        for (text in listOf(
            tweak("duet", """{"style":"classic","direction":"closing","intensity":1.5,"frost":2,"darkening":0,"perspective":1.33}"""),
            tweak("duet", "{}"),
            tweak("duet", """{"style":"sparkle"}"""),           // a style Folio doesn't have
            tweak("duet", """{"frost":2.5}"""),                 // out of range
            tweak("duet", """{"ripple":true}"""),               // a key from a newer Folio: the parser skips it, the schema says so
            tweak("appPanels", """{"style":"deep"}"""),         // options on a tweak that takes none
            tweak("appPanels", "{}"),
        )) assertEquals(text, null, agree(tweaksSchema, text, TweakBundle::parse))
    }

    @Test fun `mutated source files get the same answer from the schema and the parser`() {
        val entry = """{"format":1,"keyId":"A1B2C3D4E5F60789","timestamp":1789660320,"maxAge":1209600,
            "index":{"path":"index.json","sha256":"${"3b".repeat(32)}","size":18342}}"""
        differential(entrySchema, listOf(entry), seed = 13, minValid = 20) { SourceEntry.parse(it) }
        differential(revokedSchema, listOf(sourceDir.resolve("revoked.json").readText(), REVOKED_ENTRIES), seed = 17, minValid = 40) {
            RevocationList.parse(it)
        }
        differential(indexSchema, listOf(sourceDir.resolve("index.json").readText()), seed = 19, rounds = 1500, minValid = 20) {
            RepoIndex.parse(it)
        }
    }

    @Test fun `a thousand packages parse quickly`() {
        val one = org.json.JSONObject(sourceDir.resolve("index.json").readText()).getJSONArray("packages").getJSONObject(0)
        val packages = JSONArray()
        repeat(1000) { i ->
            val copy = org.json.JSONObject(one.toString())
            val id = "com.mccal.folio.bulk-" + "%04d".format(i)
            copy.put("id", id)
            copy.getJSONObject("manifest").put("id", id)
            packages.put(copy)
        }
        val text = org.json.JSONObject(sourceDir.resolve("index.json").readText()).put("packages", packages).toString()
        // Warm up, then measure: the plan's budget is 150 ms, and this bound only catches a real regression.
        RepoIndex.parse(text)
        val started = System.nanoTime()
        val parsed = RepoIndex.parse(text)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertEquals(1000, ((parsed as ParseResult.Ok).value).packages.size)
        assertTrue("1,000 packages took $millis ms", millis < 1500)
        println("1,000-package index parsed in $millis ms")
    }

    @Test fun `Kotlin enums list exactly the schema's values`() {
        val props = JSONObject(File(schemaDir, "manifest.schema.json").readText()).getJSONObject("properties")
        fun JSONArray.strings() = (0 until length()).map(::getString).toSet()
        fun items(name: String) = props.getJSONObject(name).getJSONObject("items").getJSONArray("enum").strings()
        assertEquals(items("kind"), PackageKind.entries.map { it.id }.toSet())
        assertEquals(items("permissions"), PackagePermission.entries.map { it.id }.toSet())
        assertEquals(items("screens"), Screen.entries.map { it.id }.toSet())
        assertEquals(items("provides"), Provides.entries.map { it.id }.toSet())
        assertEquals(props.getJSONObject("section").getJSONArray("enum").strings(), Section.entries.map { it.id }.toSet())
        assertEquals(
            props.getJSONObject("requires").getJSONObject("properties").getJSONObject("features").getJSONObject("items").getJSONArray("enum").strings(),
            Capability.entries.map { it.id }.toSet(),
        )
        assertEquals(
            props.getJSONObject("via").getJSONObject("items").getJSONObject("properties").getJSONObject("store").getJSONArray("enum").strings(),
            ExternalSource.Store.entries.map { it.id }.toSet(),
        )
        val blockTypes = JSONObject(File(schemaDir, "depiction.schema.json").readText()).getJSONObject("properties")
            .getJSONObject("blocks").getJSONObject("items").getJSONArray("oneOf")
            .let { a -> (0 until a.length()).map { a.getJSONObject(it).getJSONObject("properties").getJSONObject("type").getString("const") } }
        val samples = listOf(
            DepictionBlock.Hero("a.png"), DepictionBlock.Screenshots(listOf("a.png")), DepictionBlock.Markdown(LocalizedText.of("x")),
            DepictionBlock.FeatureList(listOf(LocalizedText.of("x"))), DepictionBlock.Compatibility(emptySet(), null),
            DepictionBlock.Changelog(emptyList()), DepictionBlock.Link(LocalizedText.of("x"), "https://a.b"), DepictionBlock.Donation("https://a.b"),
        )
        assertEquals(blockTypes.toSet(), samples.map(::typeOf).toSet())
    }

    // Exhaustive: a new block class won't compile until it's listed here and in the schema.
    private fun typeOf(block: DepictionBlock) = when (block) {
        is DepictionBlock.Hero -> "hero"
        is DepictionBlock.Screenshots -> "screenshots"
        is DepictionBlock.Markdown -> "markdown"
        is DepictionBlock.FeatureList -> "featureList"
        is DepictionBlock.Compatibility -> "compatibility"
        is DepictionBlock.Changelog -> "changelog"
        is DepictionBlock.Link -> "link"
        is DepictionBlock.Donation -> "donation"
    }

    @Test fun `mutated manifests get the same answer from the schema and the parser`() {
        differential(manifestSchema, listOf(sourceDir.resolve("packages/cabinet/manifest.json").readText(), resource("manifest-all-fields.json")), seed = 7) {
            PackageManifest.parse(it)
        }
    }

    @Test fun `mutated pages get the same answer from the schema and the parser`() {
        differential(depictionSchema, listOf(sourceDir.resolve("packages/cabinet/depiction.json").readText(), resource("depiction-all-blocks.json")), seed = 11) {
            Depiction.parse(it)
        }
    }

    /**
     * Mutates [seeds] and fails on any disagreement. [minValid] is how many mutants must still be valid for the run to
     * prove anything; small files where every field is required survive mutation far less often than a manifest does.
     */
    private fun differential(schema: JsonSchema, seeds: List<String>, seed: Int, rounds: Int = ROUNDS, minValid: Int = rounds / 20,
                             parse: (String) -> ParseResult<*>) {
        val random = Random(seed)
        val disagreements = LinkedHashMap<String, String>()
        var clean = 0
        repeat(rounds) {
            val doc = JSONObject(seeds.random(random))
            repeat(1 + random.nextInt(3)) { mutate(doc, random) }
            val text = doc.toString()
            agree(schema, text, parse)?.let { disagreements.putIfAbsent(it.take(300), text.take(600)) }
            val result = parse(text)
            if (result is ParseResult.Ok && result.ignored.isEmpty()) clean++
        }
        assertTrue(disagreements.entries.take(5).joinToString("\n\n") { "${it.key}\n  ${it.value}" }, disagreements.isEmpty())
        // The generator must still produce a good share of valid files, or the check proves little.
        assertTrue("only $clean of $rounds mutants were valid", clean >= minValid)
    }

    private fun containers(node: Any, out: MutableList<Any>) {
        when (node) {
            is JSONObject -> { out += node; node.keys().asSequence().toList().forEach { containers(node.get(it), out) } }
            is JSONArray -> { out += node; for (i in 0 until node.length()) containers(node.get(i), out) }
        }
    }

    private fun mutate(doc: JSONObject, random: Random) {
        val all = mutableListOf<Any>()
        containers(doc, all)
        when (val target = all.random(random)) {
            is JSONObject -> {
                val keys = target.keys().asSequence().toList()
                when (random.nextInt(4)) {
                    0 -> if (keys.isNotEmpty()) target.remove(keys.random(random))
                    1 -> target.put(if (random.nextInt(8) == 0) "extra" else FIELD_NAMES.random(random), value(random))
                    else -> if (keys.isNotEmpty()) target.put(keys.random(random), value(random))
                }
            }
            is JSONArray -> {
                val n = target.length()
                when (random.nextInt(4)) {
                    0 -> if (n > 0) target.remove(random.nextInt(n))
                    1 -> if (n > 0) target.put(target.get(random.nextInt(n)))
                    2 -> if (n > 0) target.put(random.nextInt(n), value(random))
                    else -> target.put(value(random))
                }
            }
        }
    }

    private fun value(random: Random): Any = when (val v = POOL.random(random)) {
        is String -> if (v.startsWith("[") || v.startsWith("{")) (if (v.startsWith("[")) JSONArray(v) else JSONObject(v)) else v
        else -> v
    }

    private companion object {
        const val ROUNDS = 4000
        val CROSS_FIELD = listOf("doesn't match the manifest's")
        val FIELD_NAMES = listOf(
            "format", "id", "name", "version", "author", "minFolio", "section", "kind", "permissions", "screens", "depends", "conflicts",
            "icon", "depiction", "license", "description", "via", "provides", "requires", "features", "url", "store", "repoUrl",
            "tint", "blocks", "type", "image", "images", "text", "items", "notes", "entries", "date", "title", "\$schema", "en", "es",
        )
        val REVOKED_ENTRIES = """{"format":1,"timestamp":1789660320,
            "packages":[{"id":"com.mccal.folio.cabinet","versions":["*","1.0.0"],"reason":"Test input"}],
            "sources":[{"url":"https://folio.mccal.dev/source/","reason":"Test input"}]}"""
        val POOL: List<Any> = listOf(
            JSONObject.NULL, true, 0, 1, 2, -1, 1.5, "", "a", "x".repeat(40), "x".repeat(41), "x".repeat(400), "x".repeat(401),
            "x".repeat(4001), "https://example.com", "http://example.com", "https://", "https://a b", "assets/a.png", "/abs.png",
            "a/../b.png", "#12345G", "#0A84FF", "1.0", "1:2.0~b-1", "0.7", "0.7.0", "0.07.0", "1234567890.0.0", "01.0", "1.0-", "x1",
            "dev.maya.icons", "dev.maya.icons (>= 1.2)", "dev.maya.icons (> 1.2)", "dev.maya.icons (= ${"1".repeat(70)})", "Dev.Maya",
            "com.example.app", "com.example.app-2", "tweaks", "tweakBundle", "theme", "externalApp", "hologram", "playStore", "fdroid",
            "obtainium", "aurora", "cover", "inner", "rear", "tweaks.appPanels", "icons", "iconPack", "markdown", "hero", "link", "donation",
            "future", "2026-09-17", "2026-9-17", "MIT", "[]", "[\"tweaks\"]", "[\"tweaks\",\"tweaks\"]", "[\"cover\",\"inner\"]",
            "[\"cover\",\"cover\"]", "[\"a.png\"]", "[1]", "{}", "{\"en\":\"x\"}", "{\"es\":\"x\"}", "{\"en\":\"x\",\"pt-BR\":\"y\"}",
            "{\"en\":\"\"}", "{\"EN\":\"x\"}", "{\"en\":\"x\",\"e\":\"y\"}", "{\"store\":\"playStore\"}",
            "{\"store\":\"playStore\",\"id\":\"com.example.app\"}", "{\"store\":\"obtainium\",\"repoUrl\":\"https://github.com/a/b\"}",
            "{\"type\":\"hero\",\"image\":\"a.png\"}", "{\"type\":\"future\"}", "{\"type\":\"markdown\"}", "{\"features\":[\"icons\"]}",
            "{\"version\":\"1.0\",\"notes\":\"x\"}", "{\"name\":\"x\"}", "index.json", "packages/a_1.0.0.foliopkg",
            "A1B2C3D4E5F60789", "a1b2c3d4e5f60789", "${"a".repeat(64)}", "*", 1789660320, 1209600, 3599, 18342,
            "{\"path\":\"index.json\",\"sha256\":\"${"a".repeat(64)}\",\"size\":10}", "{\"repo\":\"McCal-Codes/folio\",\"commit\":\"3f9c2a1\"}",
            "{\"package\":\"com.mccal.folio.cabinet\"}", "{\"id\":\"com.mccal.folio.cabinet\",\"versions\":[\"*\"],\"reason\":\"x\"}",
        )
    }
}
