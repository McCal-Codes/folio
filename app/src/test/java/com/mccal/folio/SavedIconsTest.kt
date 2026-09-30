package com.mccal.folio

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Icons saved between starts are used only while the app and what they were drawn under are unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedIconsTest {
    @get:Rule val folder = TemporaryFolder()

    private val maps = ComponentName("com.example.maps", "com.example.maps.Main")
    private val notes = ComponentName("com.example.notes", "com.example.notes.Main")
    private val mapsApk = "/data/app/~~a1/com.example.maps-b2/base.apk"
    private fun icon(color: Int) = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun icons(fingerprint: String = "420|en-US|33|0") = SavedIcons(folder.root).apply { check(fingerprint) }
    private fun SavedIcons.save(id: String, component: ComponentName, apk: String, color: Int = Color.RED) =
        write(listOf(SavedIcons.Saved(id, id.replaceFirstChar(Char::uppercase), icon(color), component, apk)))

    @Test fun `an icon comes back with its name while the app is unchanged`() {
        val icons = icons().apply { save("maps", maps, mapsApk) }
        val (label, bitmap) = icons.read("maps", maps, mapsApk)!!
        assertEquals("Maps", label)
        assertEquals(144, bitmap.width)
        assertEquals(Color.RED, bitmap.getPixel(72, 72))
    }

    @Test fun `an updated app, another of its icons, or an app never saved is loaded again`() {
        val icons = icons().apply { save("maps", maps, mapsApk) }
        assertNull("an update installs to a new path", icons.read("maps", maps, "/data/app/~~c3/com.example.maps-d4/base.apk"))
        assertNull("an alternate icon is another component", icons.read("maps", ComponentName("com.example.maps", "com.example.maps.Alias"), mapsApk))
        assertNull(icons.read("notes", notes, "/data/app/notes/base.apk"))
    }

    @Test fun `a new fingerprint starts over, and the same one keeps what was saved`() {
        icons("420|en-US|33|0").save("maps", maps, mapsApk)
        val darker = icons("420|en-US|17|0")
        assertNull("drawn in light mode, now dark", darker.read("maps", maps, mapsApk))
        darker.save("maps", maps, mapsApk, Color.BLUE)
        assertEquals(Color.BLUE, icons("420|en-US|17|0").read("maps", maps, mapsApk)!!.second.getPixel(72, 72))
    }

    @Test fun `apps no longer listed are forgotten, and clearing forgets everything`() {
        val icons = icons().apply { save("maps", maps, mapsApk); save("notes", notes, "/data/app/notes/base.apk") }
        icons.prune(setOf("maps"))
        assertNotNull(icons.read("maps", maps, mapsApk))
        assertNull(icons.read("notes", notes, "/data/app/notes/base.apk"))
        icons.clear()
        assertNull(icons.read("maps", maps, mapsApk))
    }

    @Test fun `a damaged file is loaded again rather than trusted`() {
        val icons = icons().apply { save("maps", maps, mapsApk) }
        File(folder.root, SavedIcons.fileName("maps")).writeBytes(byteArrayOf(0, 0, 0, SavedIcons.FORMAT.toByte(), 0))
        assertNull(icons.read("maps", maps, mapsApk))
    }
}
