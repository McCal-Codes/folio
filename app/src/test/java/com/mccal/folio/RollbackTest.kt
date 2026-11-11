package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Going back is a roll-forward under a higher versionCode (REL-34): rollback builds are only ever offered from Older versions. */
class RollbackTest {
    private fun release(tag: String, draft: Boolean = false) = JSONObject().put("tag_name", tag).put("draft", draft)
        .put("prerelease", false).put("body", "").put("html_url", "")
        .put("assets", JSONArray().put(JSONObject().put("name", "Folio-$tag.apk").put("size", 10).put("browser_download_url", "https://x/$tag.apk")))

    private val feed = listOf(release("v0.7.0-beta.2"), release("v0.6.9.1"), release("v0.6.9"),
        release("v0.6.9-rollback.1"), release("v0.6.8-rollback.1"), release("v0.6.8-rollback.2"), release("v0.6.7-rollback.1", draft = true))

    @Test fun `a rollback build is never offered as an update`() {
        assertEquals("0.7.0-beta.2", SoftwareUpdate.newestOf(feed)!!.version)
        assertNull(SoftwareUpdate.newestOf(listOf(release("v0.6.9-rollback.1"))))
    }

    @Test fun `only builds below the installed version are offered, newest first`() {
        assertEquals(listOf("0.6.9-rollback.1", "0.6.8-rollback.2"), SoftwareUpdate.rollbackOptions(feed, "0.7.0-beta.2").map { it.version })
        assertEquals(listOf("0.6.8-rollback.2"), SoftwareUpdate.rollbackOptions(feed, "0.6.9").map { it.version })
        assertTrue(SoftwareUpdate.rollbackOptions(feed, "0.6.8").isEmpty())
    }

    @Test fun `drafts are left out and the highest rollback number wins`() {
        assertTrue(SoftwareUpdate.rollbackOptions(feed, "0.7.0-beta.2").none { it.version.startsWith("0.6.7") })
        assertEquals("0.6.8-rollback.2", SoftwareUpdate.rollbackOptions(feed, "0.7.0").last().version)
    }

    @Test fun `being on a rollback build counts as the version it rebuilds`() {
        assertEquals("0.6.9", SoftwareUpdate.rollbackBase("0.6.9-rollback.1"))
        assertEquals(listOf("0.6.8-rollback.2"), SoftwareUpdate.rollbackOptions(feed, "0.6.9-rollback.1").map { it.version })
    }

    @Test fun `the build's versionCode is worked out from the name`() {
        assertEquals(7000, SoftwareUpdate.versionCodeOf("0.7.0-beta.2"))
        assertEquals(6091, SoftwareUpdate.versionCodeOf("0.6.9.1"))
        assertEquals(7010, SoftwareUpdate.versionCodeOf("0.7.1"))
        assertEquals(6091, SoftwareUpdate.versionCodeOf("0.6.9.1-rollback.1"))
        assertNull(SoftwareUpdate.versionCodeOf("nightly"))
    }

    @Test fun `after a rollback only the next release family is offered`() {
        val code = 7009L  // the rollback build sits at the top of the 0.7.x family
        for (v in listOf("0.7.0-beta.2", "0.7.0", "0.7.0.1", "0.7.0.8", "0.6.9.1")) assertFalse(v, SoftwareUpdate.offerable(v, "0.6.9.1-rollback.1", code))
        assertTrue(SoftwareUpdate.offerable("0.7.1", "0.6.9.1-rollback.1", code))
        assertTrue(SoftwareUpdate.offerable("0.7.1-beta.1", "0.6.9.1-rollback.1", code))
    }

    @Test fun `without a rollback the rule is only newer`() {
        assertTrue(SoftwareUpdate.offerable("0.7.0", "0.7.0-beta.2", 7000))
        assertTrue(SoftwareUpdate.offerable("0.7.0-beta.2", "0.6.9", 6090))
        assertFalse(SoftwareUpdate.offerable("0.6.9", "0.6.9", 6090))
    }
}
