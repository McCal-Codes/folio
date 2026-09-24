package com.mccal.folio

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Wallpaper & Appearance › Accent: the choice is remembered, it reaches whatever draws with it, and both
 * accents are readable (docs/standards/accessibility.md, A11Y-9).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccentTest {
    private fun context() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    /** WCAG 2.2 relative luminance, so the numbers here are the ones a contrast checker reports. */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return .2126 * channel(color.red) + .7152 * channel(color.green) + .0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val high = maxOf(luminance(a), luminance(b))
        val low = minOf(luminance(a), luminance(b))
        return (high + .05) / (low + .05)
    }

    @Test fun `Folio's teal is the default, and a choice is remembered`() {
        val store = AppearanceStore(context())
        assertEquals(AccentChoice.FOLIO_TEAL, store.state.accent)
        store.setAccent(AccentChoice.APPLE_BLUE, systemDark = false)
        assertEquals(AccentChoice.APPLE_BLUE, store.state.accent)
        // A second store reads the same preferences: the choice survives a restart.
        assertEquals(AccentChoice.APPLE_BLUE, AppearanceStore(context()).state.accent)
        // And anything outside the theme, like an overlay, sees it too.
        assertEquals(AccentChoice.APPLE_BLUE, DuoAppearanceRuntime.accent)
        store.setAccent(AccentChoice.FOLIO_TEAL, systemDark = false)
    }

    @Test fun `an unreadable saved value falls back to the default rather than failing`() {
        context().getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
            .edit().putString("accent", "SOMETHING_ELSE").commit()
        assertEquals(AccentChoice.FOLIO_TEAL, AppearanceStore(context()).state.accent)
    }

    @Test fun `every accent is readable, as fill under white text and as ink on Folio's dark surfaces`() {
        // The greys these sit on: a sheet group and a pop-up menu.
        val sheet = Color(0xFF2C2C2E)
        val menu = Color(0xFF3A3A3C)
        AccentChoice.entries.forEach { choice ->
            val accent = FolioAccents.of(choice)
            val onFill = contrast(Color.White, accent.fill)
            assertTrue("${choice.id}: white on its fill is ${"%.2f".format(onFill)}:1", onFill >= 4.5)
            listOf("sheet" to sheet, "menu" to menu).forEach { (name, background) ->
                val onDark = contrast(accent.ink, background)
                assertTrue("${choice.id}: its ink on a $name is ${"%.2f".format(onDark)}:1", onDark >= 4.5)
            }
        }
    }
}
