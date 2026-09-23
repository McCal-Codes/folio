package com.mccal.folio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The Settings scene draws Folio's real Settings, from `docs/mockups/lab/data/settings.json`. That file is generated
 * from the app, so this checks it still matches: a row added, renamed or moved in Settings has to reach the lab, or
 * the lab stops being a reference and becomes a nice drawing.
 *
 * It skips where the lab isn't checked out, since that folder isn't in git.
 */
class LabSettingsParityTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val data = File(root, "docs/mockups/lab/data/settings.json")
    private val sheet = File(root, "app/src/main/java/com/mccal/folio/CustomizationSheet.kt")
    private val checklist = File(root, "app/src/main/java/com/mccal/folio/SetupChecklist.kt")

    private fun json(): JSONObject {
        assumeTrue("the Mockup Lab isn't on this machine", data.isFile)
        return JSONObject(data.readText())
    }

    /** What a string resource says, so this can compare the lab with words that have moved into strings.xml. */
    private fun resource(name: String): String {
        val xml = File(root, "app/src/main/res/values/strings.xml").readText()
        val value = Regex("""<string name="${Regex.escape(name)}">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1) ?: error("no string named $name")
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("\\'", "'").replace("\\\"", "\"")
    }

    /** The Settings rows the app draws, in order, as (title, tag). */
    private fun appRows(): List<Pair<String, String>> {
        val body = sheet.readText().let { it.substring(it.indexOf("fun CustomizationSheet("), it.indexOf("@Composable private fun SwitchRow(")) }
        return Regex("""TweakRow\(Icons\.Rounded\.\w+, 0x[0-9A-Fa-f]{8}, "([^"]+)", "([^"]+)"""")
            .findAll(body).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private fun labRows(): List<Pair<String, String>> {
        val groups = json().getJSONArray("groups")
        return (0 until groups.length()).flatMap { g ->
            val group = groups.getJSONArray(g)
            (0 until group.length()).map { i ->
                group.getJSONObject(i).let { it.getString("title") to it.getString("tag") }
            }
        }
    }

    @Test fun `the lab shows the same Settings rows, in the same order`() {
        val lab = labRows()
        val app = appRows().take(lab.size)
        assertEquals("the lab's rows have drifted from CustomizationSheet.kt", app, lab)
        assertTrue("the lab shows the whole overview", lab.size >= 20)
        // The Market row is the one this release adds; if it ever disappears, the scene is out of date.
        assertTrue("customization-market" in lab.map { it.second })
    }

    @Test fun `the lab splits Settings at the same widths the app does`() {
        val scene = File(root, "docs/mockups/lab/scenes/settings.js")
        assumeTrue("the Mockup Lab isn't on this machine", scene.isFile)
        val style = scene.readText()
        val rule = File(root, "app/src/main/java/com/mccal/folio/SizeClass.kt").readText()
        // Both say 1200: the app in THREE_PANES_DP, the lab in the scene's own container query. A mockup drawn at a
        // different threshold would show a layout the phone never produces.
        assertTrue("the app's three-column threshold moved", "THREE_PANES_DP = 1200f" in rule)
        assertTrue("the lab's three-column threshold moved", "min-width:1200px" in style)
        assertTrue("the lab still has to draw the third column", "tweakPage(" in style)
    }

    @Test fun `the pages the lab draws are the app's own cards, in the app's own words`() {
        val data = json()
        assumeTrue("settings.json predates generated pages", data.has("pages"))
        val pages = data.getJSONObject("pages")
        assertTrue("the lab should draw most of the settings pages, not a handful", pages.length() >= 10)

        val body = sheet.readText()
        val strings = File(root, "app/src/main/res/values/strings.xml").readText()
        var checked = 0
        for (tag in pages.keys()) {
            val cards = pages.getJSONArray(tag)
            for (i in 0 until cards.length()) {
                val card = cards.getJSONObject(i)
                val controls = card.getJSONArray("controls")
                for (j in 0 until controls.length()) {
                    val control = controls.getJSONObject(j)
                    val text = control.optString("title").ifEmpty { control.optString("text") }
                    // A line that's built at run time keeps only its literal parts, so it won't be found whole.
                    if (text.isEmpty() || control.optBoolean("dynamic")) continue
                    val escaped = text.replace("&", "&amp;").replace("'", "\\'")
                    assertTrue(
                        "the lab says \"$text\" on $tag, and the app doesn't",
                        body.contains("\"$text\"") || strings.contains(">$escaped<") || strings.contains(">$text<"),
                    )
                    checked++
                }
            }
        }
        assertTrue("nothing was actually compared", checked >= 30)
    }

    @Test fun `the setup steps and permissions are the app's own`() {
        val data = json()
        val steps = data.getJSONArray("setupSteps").titles()
        val appSteps = Regex("""SetupStep\(Icons\.Rounded\.\w+, "([^"]+)"""").findAll(checklist.readText()).map { it.groupValues[1] }.toList()
        assertEquals(appSteps, steps)

        val perms = data.getJSONArray("permissions").titles()
        // A permission's name is a literal or a string resource, depending on whether it has been translated yet.
        val appPerms = Regex("""Perm\((?:"([^"]+)"|context\.getString\(R\.string\.(\w+)\))""")
            .findAll(sheet.readText())
            .map { it.groupValues[1].ifEmpty { resource(it.groupValues[2]) } }.toList()
        assertEquals(appPerms, perms)
    }

    @Test fun `the scene and the app say the same thing about refreshing`() {
        assumeTrue("the Mockup Lab isn't on this machine", data.isFile)
        val scene = File(root, "docs/mockups/lab/scenes/settings.js")
        assumeTrue(scene.isFile)
        val text = scene.readText()
        // Wording that appears in both places, so a change in one shows up as a failure rather than a surprise.
        for (line in listOf("Refresh in the background", "Only on Wi-Fi", "Once a day", "Never uses mobile data")) {
            assertTrue("the scene is missing \"$line\"", line in text)
            assertTrue("Settings is missing \"$line\"", line in sheet.readText())
        }
    }

    private fun JSONArray.titles() = (0 until length()).map { getJSONObject(it).getString("title") }
}
