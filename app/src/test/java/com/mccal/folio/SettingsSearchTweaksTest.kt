package com.mccal.folio

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings search finds tweaks by name, by what they're based on, and by what they do.
 *
 * Until 0.6.7.2 the index held only pages, so searching "flipbook" found nothing even with Flipbook installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsSearchTweaksTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun found(query: String) = searchableTweaks(context)
        .filter { (tweak, keywords) -> settingsMatches(query, tweak.name, keywords) }.map { it.first.name }

    @Test fun `a tweak is found by its name`() {
        assertEquals(listOf("Cabinet"), found("cabinet"))
        assertEquals(listOf("Roll Call"), found("roll call"))
    }

    @Test fun `a tweak is found by the tweak it's based on, and by what it does`() {
        assertEquals(listOf("Cabinet"), found("velox"))
        assertTrue(found("shortcuts").contains("Cabinet"))
    }

    @Test fun `every tweak on offer can be searched for`() {
        val offered = visibleTweaks(context)
        assertTrue(offered.isNotEmpty())
        offered.forEach { assertTrue("${it.name} isn't searchable", found(it.name).contains(it.name)) }
    }
}
