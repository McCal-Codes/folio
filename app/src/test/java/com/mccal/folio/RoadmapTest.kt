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

    @Test fun `reads a release's optional subtitle and tolerates its absence`() {
        val content = Roadmap.parse("""{"roadmap":1,"sections":[
            {"release":"0.6.9","subtitle":"Foundation","items":[{"title":"A","status":"planned"}]},
            {"release":"0.7.0","items":[{"title":"B","status":"planned"}]},
            {"release":"0.7.1","subtitle":"${"x".repeat(80)}","items":[{"title":"C","status":"planned"}]}]}""")!!
        assertEquals("Foundation", content.sections[0].subtitle)
        assertNull(content.sections[1].subtitle)
        assertEquals(40, content.sections[2].subtitle!!.length)
    }

    private fun release(version: String, vararg statuses: String, beta: Boolean = false) =
        """{"release":"$version","items":[${statuses.joinToString(",") { """{"title":"t","status":"$it"${if (beta) ""","beta":true""" else ""}}""" }}]}"""
    private fun roadmap(vararg sections: String) = Roadmap.parse("""{"roadmap":1,"sections":[${sections.joinToString(",")}]}""")!!
    private val newer = SoftwareUpdate::isNewer

    @Test fun `a beta of a release has its own section as Now while that release has open items`() {
        val content = roadmap(release("0.6.8", "building", "building"), release("0.6.7", "done"), release("0.6.9", "planned"),
            release("0.7.0", "planned"), """{"title":"Later","items":[{"title":"x","status":"planned"}]}""")
        val groups = Roadmap.group(content, "0.6.8-beta.5", newer)
        assertEquals("0.6.8", groups.now?.release)
        assertEquals(listOf("0.6.9", "0.7.0"), groups.next.map { it.release })
        assertEquals(listOf("0.6.7"), groups.shipped.map { it.release })
        assertEquals(listOf("Later"), groups.titled.map { it.title })
    }

    @Test fun `when the installed release is finished the next one becomes Now and it moves to Shipped`() {
        val content = roadmap(release("0.6.8", "building"), release("0.6.7", "done", "done"), release("0.6.6", "done"))
        val groups = Roadmap.group(content, "0.6.7.3", newer)
        assertEquals("0.6.8", groups.now?.release)
        assertEquals(listOf("0.6.7", "0.6.6"), groups.shipped.map { it.release }) // newest first
        assertTrue(groups.next.isEmpty())
    }

    @Test fun `with nothing newer and nothing open there is no Now`() {
        val groups = Roadmap.group(roadmap(release("0.6.7", "done"), release("0.6.6", "done")), "0.6.7", newer)
        assertNull(groups.now)
        assertEquals(listOf("0.6.7", "0.6.6"), groups.shipped.map { it.release })
    }

    @Test fun `items in the current beta count as ready to try but never as done`() {
        val section = roadmap(release("0.6.8", "building", "building", beta = true), release("0.6.9", "planned")).sections[0]
        assertEquals(2 to 2, Roadmap.ready(section))
        assertTrue(section.items.all { it.status == Roadmap.Status.BUILDING && it.beta })
        assertEquals(0 to 1, Roadmap.ready(roadmap(release("0.6.9", "planned")).sections[0]))
    }

    @Test fun `ignores oversized files`() {
        assertNull(Roadmap.parse("""{"roadmap":1,"note":"${"x".repeat(70_000)}","sections":[]}"""))
    }

    @Test fun `a failed fetch is not retried for an hour, and a fresh copy is not fetched at all`() {
        val hour = 60 * 60 * 1000L
        val day = 24 * hour
        assertEquals("nothing saved and nothing failed: fetch", true, Roadmap.shouldFetch(null, null, day))
        assertEquals("a copy from a minute ago: no fetch", false, Roadmap.shouldFetch(day - 60_000L, null, day))
        assertEquals("an old copy: fetch", true, Roadmap.shouldFetch(0L, null, day))
        assertEquals("an old copy but it failed ten minutes ago: wait", false, Roadmap.shouldFetch(0L, day - 10 * 60_000L, day))
        assertEquals("an old copy and the failure was over an hour ago: fetch again", true, Roadmap.shouldFetch(0L, day - hour - 1L, day))
    }
}
