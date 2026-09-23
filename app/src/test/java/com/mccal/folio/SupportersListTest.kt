package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Supporters list: a public file of names people agreed to, read the way the Roadmap's file is read. */
class SupportersListTest {
    private val two = """{"supporters":1,"people":[{"name":"Alex R.","since":"2026-09"},{"name":"Sam K.","since":"2025-12"}]}"""

    @Test fun `a list reads its people, newest year first`() {
        val content = Supporters.parse(two)!!
        assertEquals(listOf("Alex R.", "Sam K."), content.people.map { it.name })
        assertEquals(listOf("2026", "2025"), Supporters.byYear(content).map { it.first })
    }

    @Test fun `anything that isn't a supporters file is refused`() {
        assertNull(Supporters.parse("not json"))
        assertNull(Supporters.parse("""{"people":[]}"""))                       // no marker
        assertNull(Supporters.parse("""{"supporters":1,"note":"${"x".repeat(70_000)}","people":[]}"""))
    }

    @Test fun `a nameless row is skipped and a bad month is simply not shown`() {
        val content = Supporters.parse("""{"supporters":1,"people":[{"name":"  "},{"name":"Jo","since":"whenever"}]}""")!!
        assertEquals(listOf("Jo"), content.people.map { it.name })
        assertNull(content.people.single().since)
    }

    @Test fun `an empty list is a list, not a failure`() {
        assertEquals(0, Supporters.parse("""{"supporters":1,"people":[]}""")!!.people.size)
    }

    @Test fun `the file Folio ships is a valid, empty list`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val shipped = java.io.File(root, "app/src/main/assets/supporters.json").readText()
        assertEquals(0, Supporters.parse(shipped)!!.people.size)
    }
}
