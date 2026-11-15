package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    @Test fun `parses version sections and ignores other headings`() {
        val md = """
            # Changelog
            Intro text.
            ## [0.4.0] - 2026-09-14
            ### Added
            - Focus
            - Themes
            ### Fixed
            - A bug
            ## [0.3.0] - 2026-09-13
            - Loose item
            ## DuoLauncher history
            ### 0.15.0
            - Not Folio
        """.trimIndent()
        val notes = WhatsNew.parse(md)
        assertEquals(listOf("0.4.0", "0.3.0"), notes.map { it.version })
        assertEquals("2026-09-14", notes[0].date)
        assertEquals(listOf("Added" to listOf("Focus", "Themes"), "Fixed" to listOf("A bug")), notes[0].sections)
        assertEquals(listOf("" to listOf("Loose item")), notes[1].sections)
    }
    @Test fun `the bundled changelog's newest section matches the app version`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val newest = WhatsNew.parse(java.io.File(root, "CHANGELOG.md").readText()).first()
        val gradle = java.io.File(root, "app/build.gradle.kts").readText()
        // A beta carries its release's notes, so the section to match is the version without the suffix.
        val version = Regex("""val folioVersion = "([^"]+)"""").find(gradle)!!.groupValues[1].substringBefore('-')
        // Notes for the next version are written under "Unreleased" before its bump (REL-7, REL-13), so until then
        // the app is still the newest dated release.
        val notes = WhatsNew.parse(java.io.File(root, "CHANGELOG.md").readText())
        val shipped = if (newest.date == "Unreleased" && newest.version != version) notes[1] else newest
        assertEquals(version, shipped.version)
    }

    @Test fun `a beta shows the notes for the release it belongs to`() {
        // 0.7.0-beta.1 and 0.7.0 share a versionCode on purpose, and the changelog has one section for both.
        assertEquals("0.7.0", "0.7.0-beta.1".substringBefore('-'))
        assertEquals("0.7.0", "0.7.0".substringBefore('-'))
    }

    @Test fun `bold titles split from their detail and plain lines keep their text`() {
        assertEquals(NoteItem("More rows", "Home adds rows."), WhatsNew.split("**More rows:** home adds rows."))
        assertEquals(NoteItem("Big Clock", "A clock."), WhatsNew.split("**Big Clock**: A clock."))
        assertEquals(NoteItem(null, "Folders can be moved again."), WhatsNew.split("Folders can be moved again."))
        // Markers survive: What's New draws them with MarketText rather than showing them as typed.
        assertEquals(NoteItem(null, "A folder holds **twelve** apps."), WhatsNew.split("A folder holds **twelve** apps."))
    }

    @Test fun `every new feature in the current release has a short bold title`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val latest = WhatsNew.parse(java.io.File(root, "CHANGELOG.md").readText()).first()
        // A hotfix (x.y.z.n) may carry only fixes; a feature release must have something under Added.
        val section = latest.sections.firstOrNull { it.first == "Added" }
        if (section == null) { assertTrue("${latest.version} has no Added section", latest.version.count { it == '.' } == 3); return }
        val added = section.second.map(WhatsNew::split)
        assertTrue(added.isNotEmpty())
        added.forEach { assertTrue("Needs a title: ${it.detail}", it.title != null && it.title!!.length <= 40) }
    }
    @Test fun `a release note's own emphasis and links survive into what Folio draws`() {
        // What's New hands the detail to MarketText, so an entry can stress a word or point at a page. The same
        // subset the Market's package pages use: bold, italic, https links, and nothing that escapes into markup.
        val note = WhatsNew.split("**Folders:** hold **twelve** apps now, see [the guide](https://github.com/McCal-Codes/folio/blob/main/docs/user-guide.md).")
        assertEquals("Folders", note.title)
        val drawn = MarketText.inline(note.detail)
        assertEquals("Hold twelve apps now, see the guide.", drawn.text)
        val bold = drawn.spanStyles.single { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.SemiBold }
        assertEquals("twelve", drawn.text.substring(bold.start, bold.end))
        val link = drawn.getLinkAnnotations(0, drawn.text.length).single()
        assertEquals("the guide", drawn.text.substring(link.start, link.end))
        assertEquals("https://github.com/McCal-Codes/folio/blob/main/docs/user-guide.md", (link.item as androidx.compose.ui.text.LinkAnnotation.Url).url)
    }

    @Test fun `a link that is not https is left as the author typed it`() {
        val drawn = MarketText.inline("see [here](http://example.com)")
        assertEquals("see [here](http://example.com)", drawn.text)
        assertEquals(0, drawn.getLinkAnnotations(0, drawn.text.length).size)
    }

    private val tagged = WhatsNew.parse("""
        ## [0.7.0] - Unreleased
        ### Added
        - **Old one:** from the start. <!-- beta 1 -->
        - **Middle:** came in beta 3. <!-- beta 3 -->
        - **Newest:** beta 5 only. <!-- beta 5 -->
        ### Fixed
        - A fix from beta 3 <!-- beta 3 -->
        - A fix from beta 5 <!--beta 5-->
        ## [0.6.0] - 2026-10-01
        ### Added
        - **Plain:** no tag at all.
    """.trimIndent())

    @Test fun `a hidden beta tag is taken off the line and remembered`() {
        val seven = tagged[0]
        assertEquals(listOf("**Old one:** from the start.", "**Middle:** came in beta 3.", "**Newest:** beta 5 only."), seven.sections[0].second)
        assertEquals(1, seven.betaOf["**Old one:** from the start."])
        assertEquals(5, seven.betaOf["A fix from beta 5"])
        assertEquals(listOf(1, 3, 5), seven.betas)
        assertTrue(tagged[1].betaOf.isEmpty())
        assertEquals(listOf("**Plain:** no tag at all."), tagged[1].sections[0].second)
    }

    @Test fun `forBeta keeps only that beta's lines and drops empty headings`() {
        val five = tagged[0].forBeta(5)
        assertEquals(listOf("Added" to listOf("**Newest:** beta 5 only."), "Fixed" to listOf("A fix from beta 5")), five.sections)
        assertEquals(listOf("Fixed" to listOf("A fix from beta 3")), tagged[0].forBeta(3).sections.filter { it.first == "Fixed" })
        assertTrue(tagged[0].forBeta(4).sections.isEmpty())
    }

    @Test fun `beta numbers come from the version name`() {
        assertEquals(5, WhatsNew.betaNumber("0.7.0-beta.5"))
        assertEquals(12, WhatsNew.betaNumber("0.7.0-beta.12"))
        assertEquals(null, WhatsNew.betaNumber("0.7.0"))
        assertEquals(null, WhatsNew.betaNumber("0.7.0-dev"))
        assertEquals(null, WhatsNew.betaNumber(""))
    }

    @Test fun `a beta's sheet shows only that beta and earlier betas move to history`() {
        val view = WhatsNew.sheetNotes(tagged, "0.7.0-beta.5")
        assertEquals("0.7.0 beta 5", view.release?.version)
        assertEquals(listOf("**Newest:** beta 5 only.", "A fix from beta 5"), view.release!!.sections.flatMap { it.second })
        // Newest earlier beta first, then the older releases; the whole 0.7.0 is not listed twice.
        assertEquals(listOf("0.7.0 beta 3", "0.7.0 beta 1", "0.6.0"), view.older.map { it.version })
        assertEquals(listOf("**Middle:** came in beta 3.", "A fix from beta 3"), view.older[0].sections.flatMap { it.second })
    }

    @Test fun `the release a dev build or an untagged release shows everything`() {
        val stable = WhatsNew.sheetNotes(tagged, "0.7.0")
        assertEquals("0.7.0", stable.release?.version)
        assertEquals(5, stable.release!!.sections.sumOf { it.second.size })
        assertEquals(listOf("0.6.0"), stable.older.map { it.version })
        assertEquals("0.7.0", WhatsNew.sheetNotes(tagged, "0.7.0-dev").release?.version)
        // A beta with no lines of its own still shows the release rather than an empty sheet.
        assertEquals("0.7.0", WhatsNew.sheetNotes(tagged, "0.7.0-beta.4").release?.version)
        assertEquals("0.6.0", WhatsNew.sheetNotes(tagged, "0.6.0").release?.version)
    }

    @Test fun `every line still unreleased carries a beta tag no newer than the next beta`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val newest = WhatsNew.parse(java.io.File(root, "CHANGELOG.md").readText()).first()
        if (!newest.date.equals("Unreleased", true)) return
        val gradle = java.io.File(root, "app/build.gradle.kts").readText()
        val version = Regex("""val folioVersion = "([^"]+)"""").find(gradle)!!.groupValues[1]
        val shipped = WhatsNew.betaNumber(version) ?: Int.MAX_VALUE
        val lines = newest.sections.flatMap { it.second }
        val untagged = lines.filter { it !in newest.betaOf }
        assertTrue("Add <!-- beta N --> to the end of: ${untagged.take(3)}", untagged.isEmpty())
        // A line may name the beta being written (N + 1) but not a beta that never existed.
        val tooNew = newest.betaOf.filterValues { it > shipped + 1 }
        assertTrue("Tagged for a beta further ahead than the next one: ${tooNew.keys.take(3)}", tooNew.isEmpty())
    }
}
