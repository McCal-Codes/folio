package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Dock places beyond six live beside the state, so an older Folio reads a whole six-place dock and loses nothing. */
class DockMirrorTest {
    private val eight = listOf("a", "b", "c", "d", "e", "f", "g", "h")

    @Test fun `a dock of six or fewer needs no mirror`() {
        assertNull(dockMirrorJson(eight.take(6)))
        assertNull(dockMirrorJson(eight.take(4)))
    }

    @Test fun `the places beyond six come back when the first six are unchanged`() {
        val mirror = dockMirrorJson(eight)
        assertEquals(eight, mergeDockMirror(eight.take(6), mirror, emptySet()))
    }

    @Test fun `gaps in the first six survive the round trip`() {
        val dock = listOf("a", null, "c", null, null, "f", "g", null)
        assertEquals(dock, mergeDockMirror(dock.take(6), dockMirrorJson(dock), emptySet()).let { it + List(8 - it.size) { null } })
    }

    @Test fun `an older Folio that changed the first six leaves the mirror unused`() {
        val edited = listOf("a", "b", "x", "d", "e", "f")
        assertEquals(edited, mergeDockMirror(edited, dockMirrorJson(eight), emptySet()))
    }

    @Test fun `a place an older Folio put on Home meanwhile is not doubled in the dock`() {
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", null), mergeDockMirror(eight.take(6), dockMirrorJson(eight), setOf("h")))
    }

    @Test fun `no mirror or a damaged one leaves the dock as it was read`() {
        assertEquals(eight.take(6), mergeDockMirror(eight.take(6), null, emptySet()))
        assertEquals(eight.take(6), mergeDockMirror(eight.take(6), "{not json", emptySet()))
    }
}
