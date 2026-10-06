package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevBuildTest {
    private val json = """{"branch":"capability-broker","sha":"465d9147","dirty":false,"builtAt":"2026-10-06T15:05:10-04:00",
        "commits":[{"sha":"465d9147","subject":"System Bridge page"},{"sha":"0dc168f9","subject":"Capability Broker"},{"sha":"6c16d5f8","subject":"Capability Viewer"}],
        "notes":["Open System Bridge","Turn the switch off"]}"""

    private fun info() = DevBuild.parse(json)!!

    @Test fun `the build file is read as written`() {
        val i = info()
        assertEquals("capability-broker", i.branch); assertEquals("465d9147", i.sha); assertFalse(i.dirty)
        assertEquals(listOf("System Bridge page", "Capability Broker", "Capability Viewer"), i.commits.map { it.subject })
        assertEquals(listOf("Open System Bridge", "Turn the switch off"), i.notes)
    }

    @Test fun `no checklist is fine and a damaged file is nothing`() {
        assertEquals(emptyList<String>(), DevBuild.parse("""{"branch":"b","sha":"s","builtAt":"","commits":[]}""")!!.notes)
        assertNull(DevBuild.parse("not json")); assertNull(DevBuild.parse("""{"branch":"b"}"""))
    }

    @Test fun `a build shows once and a different commit shows again`() {
        assertTrue(DevBuild.isNewBuild(info(), DevBuildSeen(null, null)))
        assertTrue(DevBuild.isNewBuild(info(), DevBuildSeen("159e9e75", "beta1-rehearsal")))
        assertFalse(DevBuild.isNewBuild(info(), DevBuildSeen("465d9147", "capability-broker")))
    }

    @Test fun `changes are the commits newer than the last build`() {
        assertEquals(listOf("465d9147", "0dc168f9"), DevBuild.changesSince(info(), "6c16d5f8").map { it.sha })
        assertEquals(emptyList<String>(), DevBuild.changesSince(info(), "465d9147").map { it.sha })
        // Another branch's build is not in the list, so everything the file has is shown rather than nothing.
        assertEquals(3, DevBuild.changesSince(info(), "159e9e75").size)
        assertEquals(3, DevBuild.changesSince(info(), null).size)
    }

    @Test fun `the summary says which build and nothing about the person`() {
        assertEquals("Folio Dev capability-broker 465d9147, built 2026-10-06T15:05:10-04:00", DevBuild.summary(info()))
        assertTrue(DevBuild.summary(info().copy(dirty = true)).contains("uncommitted changes"))
        assertNotNull(info())
    }

    @Test fun `the page knows whether it is the first run, a new build or the same one`() {
        assertEquals(DevBuild.Kind.FIRST, DevBuild.kind(info(), DevBuildSeen(null, null)))
        assertEquals(DevBuild.Kind.NEW, DevBuild.kind(info(), DevBuildSeen("159e9e75", "beta1-rehearsal")))
        assertEquals(DevBuild.Kind.REOPENED, DevBuild.kind(info(), DevBuildSeen("465d9147", "capability-broker")))
    }

    @Test fun `a reopened page lists the latest commits instead of nothing`() {
        assertEquals(3, DevBuild.listed(info(), DevBuildSeen("465d9147", "capability-broker")).size)
        assertEquals(listOf("465d9147", "0dc168f9"), DevBuild.listed(info(), DevBuildSeen("6c16d5f8", "x")).map { it.sha })
    }

    // The formatter may put a narrow no-break space before PM on newer JDKs; compare with plain spaces.
    private fun footer(i: DevBuildInfo, s: DevBuildSeen) = DevBuild.footer(i, s, java.util.Locale.US).replace('\u202f', ' ')

    @Test fun `the footer says when it was built and what it replaced`() {
        assertEquals("Built Oct 6, 2026, 3:05 PM · replaced beta1-rehearsal 159e9e75", footer(info(), DevBuildSeen("159e9e75", "beta1-rehearsal")))
        assertEquals("Built Oct 6, 2026, 3:05 PM", footer(info(), DevBuildSeen("465d9147", "capability-broker")))
        assertEquals("Built Oct 6, 2026, 3:05 PM, with uncommitted changes", footer(info().copy(dirty = true), DevBuildSeen(null, null)))
    }

    @Test fun `a time that cannot be read is shown as it is`() {
        assertEquals("yesterday", DevBuild.shortTime("yesterday"))
    }
}
