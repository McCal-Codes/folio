package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadmapTest {
    private val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }

    @Test fun `the roadmap Folio ships and fetches is valid`() {
        val raw = java.io.File(root, "app/src/main/assets/roadmap.json").readText()
        val content = assertNotNull(Roadmap.parse(raw)).let { Roadmap.parse(raw)!! }
        // Every item survived parsing: nothing was silently dropped for a typo in its status.
        val listed = Regex("\"status\"").findAll(raw).count()
        assertEquals(listed, content.sections.sumOf { it.items.size })
        // The app's own version has a section, so What's New and the Roadmap agree.
        val version = Regex("""val folioVersion = "([^"]+)"""")
            .find(java.io.File(root, "app/build.gradle.kts").readText())!!.groupValues[1].substringBefore('-')
            .split('.').take(3).joinToString(".") // a hotfix (0.6.7.1) lives in its release's section, as in Settings › Roadmap
        assertTrue(content.sections.any { it.release == version })
    }

    @Test fun `rejects files that aren't a roadmap and skips bad items`() {
        assertNull(Roadmap.parse("not json"))
        assertNull(Roadmap.parse("""{"sections":[]}"""))
        assertNull(Roadmap.parse("""{"roadmap":1,"sections":[{"title":"Next","items":[]}]}"""))
        val content = Roadmap.parse("""{"roadmap":1,"sections":[{"title":"Next","items":[
            {"title":"Good","status":"planned","color":"#30D158"},
            {"title":"Unknown status","status":"someday"},
            {"title":"","status":"done"},
            {"title":"Bad color","status":"done","color":"green"}]}]}""")!!
        assertEquals(listOf("Good", "Bad color"), content.sections.single().items.map { it.title })
        assertEquals(0xFF30D158, content.sections.single().items[0].color)
        assertEquals(0xFF8E8E93, content.sections.single().items[1].color)
    }

    @Test fun `ignores oversized files`() {
        assertNull(Roadmap.parse("""{"roadmap":1,"note":"${"x".repeat(70_000)}","sections":[]}"""))
    }
}
