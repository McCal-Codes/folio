package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every Quick Settings tile has an icon, a translated label and the permission Android needs to bind it. */
class QuickSettingsTilesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private val res = File(root, "app/src/main/res")
    private val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()

    private fun attr(tag: String, name: String) = Regex("""android:$name="([^"]*)"""").find(tag)?.groupValues?.get(1)

    private val tiles: List<String> by lazy {
        manifest.split("<service ").drop(1).map { it.substringBefore("</service>") }
            .filter { "android.service.quicksettings.action.QS_TILE\"" in it }
    }

    @Test
    fun `every tile has an icon, a translated label, the bind permission and is exported`() {
        assertTrue("no tiles found in the manifest", tiles.isNotEmpty())
        val strings = listOf("values", "values-b+ko", "values-b+zh+Hans").associateWith { File(res, "$it/strings.xml").readText() }
        for (service in tiles) {
            val open = service.substringBefore(">")
            val name = attr(open, "name") ?: "?"
            assertTrue("tile $name has no android:icon", !attr(open, "icon").isNullOrBlank())
            val label = attr(open, "label").orEmpty()
            assertTrue("tile $name label must be a @string/ reference, was '$label'", label.startsWith("@string/") && label.length > 8)
            val key = label.removePrefix("@string/")
            for ((dir, text) in strings) {
                assertTrue("tile $name label $key is missing from $dir/strings.xml", Regex("""<string\s+name="$key"[^>]*>[^<]+</string>""").containsMatchIn(text))
            }
            assertEquals("tile $name permission", "android.permission.BIND_QUICK_SETTINGS_TILE", attr(open, "permission"))
            assertEquals("tile $name must be exported", "true", attr(open, "exported"))
        }
    }
}