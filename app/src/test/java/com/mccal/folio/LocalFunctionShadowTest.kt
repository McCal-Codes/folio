package com.mccal.folio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Arrange Like iPhone crashed because a local `fun put(...)` inside `buildMap { }` called `put(...)`: the local function
 * wins over the builder's own `put`, so it called itself until the stack ran out. Kotlin says nothing. A local function
 * inside a builder must not be named after one of the builder's own methods, so this fails the build if one is.
 */
class LocalFunctionShadowTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val builder = Regex("""\bbuild(?:Map|List|Set|String)\b(?:<[^>]*>)?\s*(?:\([^)]*\))?\s*\{""")
    private val shadow = Regex("""\bfun\s+(put|add|append|set|get|plus|remove|clear|insert)\s*\(""")

    @Test fun `no local function inside a builder is named after one of the builder's own methods`() {
        val bad = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" && "/src/main/" in it.path && "/build/" !in it.path }.forEach { file ->
            val text = file.readText()
            for (start in builder.findAll(text)) {
                var depth = 1
                var i = start.range.last + 1
                while (i < text.length && depth > 0) { when (text[i]) { '{' -> depth++; '}' -> depth-- }; i++ }
                shadow.find(text.substring(start.range.last, i))?.let { bad += "${file.name}: fun ${it.groupValues[1]} inside ${start.value.trim()}" }
            }
        }
        assertTrue("A local function shadows its builder's method (it will call itself): $bad", bad.isEmpty())
    }
}
