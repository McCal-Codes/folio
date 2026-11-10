package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareUpdateTest {
    private val standings = SoftwareUpdate.CodeStanding.values()

    @Test fun `every combination of supporter state gets exactly the note it should`() {
        // Four standings and four yes/no causes: all 64, checked against the order the causes are told in.
        for (code in standings) for (bits in 0 until 16) {
            val early = bits and 1 != 0; val updates = bits and 2 != 0; val failed = bits and 4 != 0; val refused = bits and 8 != 0
            val expected = when {
                code == SoftwareUpdate.CodeStanding.NONE -> null
                code == SoftwareUpdate.CodeStanding.INVALID -> R.string.beta_note_code_invalid
                code == SoftwareUpdate.CodeStanding.ENDED -> R.string.beta_note_code_ended
                !early -> R.string.beta_note_early_access_off
                !updates -> R.string.beta_note_updates_off
                refused -> R.string.beta_note_refused
                failed -> R.string.beta_note_unreachable
                else -> null
            }
            assertEquals("$code bits=$bits", expected, SoftwareUpdate.betaNoteFor(code, early, updates, failed, refused))
        }
    }

    @Test fun `a phone with no code is never told anything about betas`() {
        for (bits in 0 until 16) {
            assertEquals(null, SoftwareUpdate.betaNoteFor(SoftwareUpdate.CodeStanding.NONE, bits and 1 != 0, bits and 2 != 0, bits and 4 != 0, bits and 8 != 0))
        }
    }

    @Test fun `a code that is not a Folio code is called that, not called ended`() {
        assertEquals(R.string.beta_note_code_invalid, SoftwareUpdate.betaNoteFor(SoftwareUpdate.CodeStanding.INVALID, true, true, false, false))
        assertEquals(R.string.beta_note_code_ended, SoftwareUpdate.betaNoteFor(SoftwareUpdate.CodeStanding.ENDED, true, true, false, false))
    }

    @Test fun `the Supporter button shows only for notes the Supporter page can fix`() {
        assertFalse(SoftwareUpdate.noteNeedsSupporter(null))
        assertFalse("a switch on the update page itself", SoftwareUpdate.noteNeedsSupporter(R.string.beta_note_updates_off))
        assertFalse("a server problem, not the person's", SoftwareUpdate.noteNeedsSupporter(R.string.beta_note_unreachable))
        for (note in listOf(R.string.beta_note_code_invalid, R.string.beta_note_code_ended,
            R.string.beta_note_early_access_off, R.string.beta_note_refused))
            assertTrue(SoftwareUpdate.noteNeedsSupporter(note))
    }

    @Test fun `a refusal from the beta server is told apart from the server being out of reach`() {
        val w = SoftwareUpdate.CodeStanding.WORKING
        assertEquals(R.string.beta_note_refused, SoftwareUpdate.betaNoteFor(w, true, true, lookupFailed = true, refused = true))
        assertEquals(R.string.beta_note_unreachable, SoftwareUpdate.betaNoteFor(w, true, true, lookupFailed = true, refused = false))
    }

    @Test fun `a working code with everything on and a good lookup says nothing`() {
        assertEquals(null, SoftwareUpdate.betaNoteFor(SoftwareUpdate.CodeStanding.WORKING, true, true, false, false))
    }

    private fun release(tag: String, vararg assets: String, draft: Boolean = false, pre: Boolean = false) = org.json.JSONObject()
        .put("tag_name", tag).put("draft", draft).put("prerelease", pre).put("body", "").put("html_url", "")
        .put("assets", org.json.JSONArray(assets.map { org.json.JSONObject().put("name", it).put("size", 10).put("browser_download_url", "https://x/$it") }))

    // What the broker sends a supporter, and what GitHub sends everyone, as they stood on 10 Nov 2026.
    private val broker = listOf(
        release("v0.7.0-beta.2", "Folio-0.7.0-beta.2.apk", "SHA256SUMS.txt", pre = true),
        release("v0.7.0-beta.1", "Folio-0.7.0-beta.1.apk", "SHA256SUMS.txt", pre = true),
        release("v0.6.9-beta.8", "Folio-0.6.9-beta.8.apk", "SHA256SUMS.txt", "signing-certificate.txt", pre = true))
    private val publicList = listOf(
        release("v0.6.9", "Folio-0.6.9.apk", "SHA256SUMS.txt", "signing-certificate.txt"),
        release("v0.6.8", "Folio-0.6.8.apk", "SHA256SUMS.txt"))

    @Test fun `a phone on 0-6-9 is offered 0-7-0 beta 2 from the broker's list and the public list together`() {
        val newest = SoftwareUpdate.newestOf(broker + publicList)!!
        assertEquals("0.7.0-beta.2", newest.version)
        assertTrue(SoftwareUpdate.isNewer(newest.version, "0.6.9"))
        assertTrue(SoftwareUpdate.isNewer(newest.version, "0.6.9.1"))
    }

    @Test fun `the order the lists arrive in does not change the pick`() {
        assertEquals("0.7.0-beta.2", SoftwareUpdate.newestOf(publicList + broker)!!.version)
        assertEquals("0.7.0-beta.2", SoftwareUpdate.newestOf((broker + publicList).reversed())!!.version)
    }

    @Test fun `without the broker's list only the public release is found, which is what a lapsed code looks like`() {
        val newest = SoftwareUpdate.newestOf(publicList)!!
        assertEquals("0.6.9", newest.version)
        assertFalse(SoftwareUpdate.isNewer(newest.version, "0.6.9"))
    }

    @Test fun `a stable release beats the beta it came from`() {
        val list = broker + release("v0.7.0", "Folio-0.7.0.apk")
        assertEquals("0.7.0", SoftwareUpdate.newestOf(list)!!.version)
    }

    @Test fun `drafts and releases with no APK are never offered`() {
        val list = listOf(release("v0.7.0-beta.3", "Folio-0.7.0-beta.3.apk", draft = true), release("v0.7.0-beta.2", "notes.txt"), release("v0.7.0-beta.1", "Folio-0.7.0-beta.1.apk"))
        assertEquals("0.7.0-beta.1", SoftwareUpdate.newestOf(list)!!.version)
        assertEquals(null, SoftwareUpdate.newestOf(listOf(release("v0.7.0-beta.2", "SHA256SUMS.txt"))))
        assertEquals(null, SoftwareUpdate.newestOf(emptyList()))
    }

    @Test fun `the pick keeps the checksum file and the APK address of the release it chose`() {
        val newest = SoftwareUpdate.newestOf(broker + publicList)!!
        assertEquals("https://x/Folio-0.7.0-beta.2.apk", newest.apkUrl)
        assertEquals("https://x/SHA256SUMS.txt", newest.sumsUrl)
    }

    @Test fun `beta numbers compare as numbers, so beta 10 is newer than beta 9`() {
        assertTrue(SoftwareUpdate.isNewer("0.7.0-beta.10", "0.7.0-beta.9"))
        assertTrue(SoftwareUpdate.isNewer("0.7.0", "0.7.0-beta.10"))
        assertFalse(SoftwareUpdate.isNewer("0.7.0-beta.2", "0.7.0"))
    }

    @Test fun `newer versions compare part by part, not as text`() {
        assertTrue(SoftwareUpdate.isNewer("0.6.0", "0.5.0"))
        assertTrue(SoftwareUpdate.isNewer("v0.10.0", "0.9.9"))
        assertTrue(SoftwareUpdate.isNewer("1.0", "0.99.99"))
        assertFalse(SoftwareUpdate.isNewer("0.5.0", "0.5.0"))
        assertFalse(SoftwareUpdate.isNewer("0.4.9", "0.5.0"))
    }

    @Test fun `a beta comes before its release and after the one before`() {
        assertTrue(SoftwareUpdate.isNewer("0.7.0-beta.1", "0.6.0"))
        assertTrue(SoftwareUpdate.isNewer("0.7.0", "0.7.0-beta.3"))
        assertTrue(SoftwareUpdate.isNewer("0.7.0-beta.2", "0.7.0-beta.1"))
        assertTrue(SoftwareUpdate.isNewer("0.7.0-beta.10", "0.7.0-beta.9"))
        assertFalse(SoftwareUpdate.isNewer("0.7.0-beta.1", "0.7.0"))
        assertFalse(SoftwareUpdate.isNewer("0.6.0", "0.7.0-beta.1"))
        assertFalse(SoftwareUpdate.isNewer("0.7.0-beta.1", "0.7.0-beta.1"))
    }

    @Test fun `updates default to Automatic, and a choice made with the old switches carries over`() {
        assertEquals(SoftwareUpdate.Mode.AUTOMATIC, SoftwareUpdate.modeFromLegacy(null, false))
        // Nobody is moved to unattended installs by an update: on 0.6.0 both switches were off until you turned
        // them on, so an upgrade with no choice recorded waits to be asked rather than assuming a yes.
        assertEquals(SoftwareUpdate.Mode.MANUAL, SoftwareUpdate.modeFromLegacy(null, false, upgraded = true))
        assertEquals(SoftwareUpdate.Mode.MANUAL, SoftwareUpdate.modeFromLegacy(null, true, upgraded = true))
        // A choice that was made still carries over, upgrade or not.
        assertEquals(SoftwareUpdate.Mode.MANUAL, SoftwareUpdate.modeFromLegacy(false, true, upgraded = true))
        assertEquals(SoftwareUpdate.Mode.AUTOMATIC, SoftwareUpdate.modeFromLegacy(true, true, upgraded = true))
        assertEquals(SoftwareUpdate.Mode.NOTIFY, SoftwareUpdate.modeFromLegacy(true, false, upgraded = true))
        assertEquals(SoftwareUpdate.Mode.MANUAL, SoftwareUpdate.modeFromLegacy(false, true))
        assertEquals(SoftwareUpdate.Mode.NOTIFY, SoftwareUpdate.modeFromLegacy(true, false))
        assertEquals(SoftwareUpdate.Mode.AUTOMATIC, SoftwareUpdate.modeFromLegacy(true, true))
    }

    @Test fun `release notes show what's new as plain lines`() {
        val notes = "Intro\n\n## Install\n- Download it\n\n## What's new in 0.6.1\n### Added\n- **Status** options, see [docs](https://x)\n![gif](a.gif)"
        assertEquals(listOf("Added", "• Status options, see docs"), releaseNoteLines(notes))
        assertEquals(listOf("• one", "two"), releaseNoteLines("* one\ntwo"))
    }
}
