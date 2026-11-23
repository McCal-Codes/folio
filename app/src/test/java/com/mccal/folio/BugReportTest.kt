package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class BugReportTest {
    @Test fun `fills the bug form with the version, phone and Android`() {
        assertEquals("https://github.com/McCal-Codes/folio/issues/new?template=bug_report.yml&version=0.4.0" +
            "&phone=samsung+SM-F971U&android=17+%28API+37%29", BugReport.url("0.4.0", "samsung", "SM-F971U", "17", 37))
    }

    @Test fun `doesn't repeat the maker and encodes everything`() {
        assertEquals("https://github.com/McCal-Codes/folio/issues/new?template=bug_report.yml&version=" +
            "&phone=Google+Pixel+%26+Co&android=16+%28API+36%29", BugReport.url(null, "Google", "Google Pixel & Co", "16", 36))
    }

    @Test fun `adds the screen summary to the diagnostics field`() {
        assertEquals(true, BugReport.url("0.6.1", "samsung", "SM-F966U", "16", 36, "folded 411×891 dp")
            .endsWith("&diagnostics=Screen%3A+folded+411%C3%97891+dp%0A%0A"))
    }

    @Test fun `a foldable's screen is picked and anything else is not a foldable`() {
        assertEquals(BugReport.SCREEN_UNFOLDED, BugReport.screenChoice(foldable = true, unfolded = true))
        assertEquals(BugReport.SCREEN_FOLDED, BugReport.screenChoice(foldable = true, unfolded = false))
        assertEquals(BugReport.SCREEN_OTHER, BugReport.screenChoice(foldable = false, unfolded = true))
        assertEquals(true, BugReport.url("0.7.0", "samsung", "SM-F966U", "16", 36, screenChoice = BugReport.SCREEN_FOLDED)
            .contains("&screen=Folded+%28cover+screen%29"))
    }

    // GitHub fills a dropdown only from the option's exact words, so the three Folio sends must be the form's own.
    @Test fun `the screen options are the form's options word for word`() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
        val form = root.resolve(".github/ISSUE_TEMPLATE/bug_report.yml").readText()
        listOf(BugReport.SCREEN_FOLDED, BugReport.SCREEN_UNFOLDED, BugReport.SCREEN_OTHER).forEach {
            assertEquals("The form has no option \"$it\"", true, "        - $it" in form)
        }
    }

    private val base = "https://github.com/McCal-Codes/folio/issues/new?template=bug_report.yml&version=0.7.0&phone=samsung+SM-F966U&android=16+%28API+36%29"

    @Test fun `a short summary goes in whole, then the footer`() {
        val text = BugReport.fit(base, "Screen: unfolded\n", listOf("Folio: 0.7.0", "Recent events:\n12:00:01  Folio started"))
        assertEquals("Screen: unfolded\n\nFolio: 0.7.0\n\nRecent events:\n12:00:01  Folio started\n\n${BugReport.FOOTER}\n", text)
    }

    @Test fun `a long summary is cut to keep the link under the limit, in order, and keeps the footer`() {
        val long = "Recent events:\n" + (1..200).joinToString("\n") { "12:00:%02d.000  something happened that takes a fair few words to say #$it".format(it % 60) }
        val sections = listOf("Folio: 0.7.0 (code 70050, release)", "Home: inner, Home page 2 of 4", long, "Latest crash or freeze:\nboom")
        val text = BugReport.fit(base, "Screen: unfolded\n", sections)
        val link = base + "&diagnostics=" + java.net.URLEncoder.encode(text, "UTF-8")
        assertEquals(true, link.length <= BugReport.LINK_LIMIT)
        assertEquals(true, link.length > BugReport.LINK_LIMIT - 400) // as much as fits, not a token amount
        assertEquals(true, text.endsWith(BugReport.FOOTER + "\n"))
        assertEquals(true, text.indexOf("Folio: 0.7.0") < text.indexOf("Home: inner"))
        assertEquals(true, text.indexOf("Home: inner") < text.indexOf("Recent events:"))
        assertEquals(false, "Latest crash or freeze:" in text) // it came after the section that filled the link
    }

    @Test fun `a summary is never added past a section that did not fit whole`() {
        val text = BugReport.fit(base, "", listOf("A", "B\n" + "x".repeat(10_000), "C"), limit = 600)
        assertEquals(true, "A" in text && "B" in text)
        assertEquals(false, "\n\nC\n" in text)
    }

    @Test fun `a crash report is cut to what it was, when, and the top of its stack`() {
        val report = "Folio crash report\nTime: 2026-11-23T09:00:00\nFolio: 0.7.0 (code 70050, release)\nDevice: samsung SM-F966U\nThread: main\nProcess: up 1 min\n\n" +
            "java.lang.IllegalStateException: boom\n" + (1..30).joinToString("\n") { "\tat com.mccal.folio.Thing.run($it)" } + "\nBefore it:\n12:00  Folio started"
        val top = BugReport.crashTop(report, stackLines = 5).lines()
        assertEquals(listOf("Folio crash report", "Time: 2026-11-23T09:00:00", "Thread: main", "java.lang.IllegalStateException: boom"), top.take(4))
        assertEquals(8, top.size)
    }
}
