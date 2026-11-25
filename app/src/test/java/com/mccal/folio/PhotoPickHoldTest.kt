package com.mccal.folio

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Photos widget's picker closes live Discover's host first. That host sits over Home as the resumed activity, so
 * while it stays up Home is paused and Android holds the picked photos until Home resumes: the pick looked lost.
 */
class PhotoPickHoldTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private fun source(name: String) = File(root, "app/src/main/java/com/mccal/folio/$name").readText()
    private val events = mutableListOf<String>()
    private val hold = PhotoPickHold { events += if (it) "hold" else "release" }

    @Test fun `Discover's host is closed before the picker opens`() {
        hold.launch { events += "open" }
        assertEquals(listOf("hold", "open"), events)
        assertTrue(hold.pending)
    }

    @Test fun `the picker's answer lets Discover come back, once`() {
        hold.launch { events += "open" }
        hold.release(); hold.release()
        assertEquals(listOf("hold", "open", "release"), events)
        assertFalse(hold.pending)
    }

    @Test fun `a picker that will not open does not leave Discover closed`() {
        hold.launch { throw IllegalStateException("no picker") }
        assertEquals(listOf("hold", "release"), events)
        assertFalse(hold.pending)
    }

    @Test fun `opening the picker twice holds Discover once`() {
        hold.launch { events += "open" }
        hold.launch { events += "open" }
        hold.release()
        assertEquals(listOf("hold", "open", "open", "release"), events)
    }

    @Test fun `the Photos widget opens its picker through the hold and releases it on the answer`() {
        val card = source("PhotoWidgetCard.kt")
        assertTrue("the hold is Discover's external-result hold",
            Regex("""PhotoPickHold \{[^}]*LiveDiscover\.setExternalResultPending\(""").containsMatchIn(card))
        assertTrue("every pick goes through the hold",
            card.contains("val pick = { pickHold.launch { picker.launch("))
        val callback = card.substringAfter("PickMultipleVisualMedia(20)) { picked ->").substringBefore("\n    }")
        assertTrue("the answer, picked or cancelled, releases the hold", callback.trimStart().startsWith("pickHold.release()"))
        assertTrue("leaving Home releases the hold", card.contains("onDispose { pickHold.release() }"))
    }
}
