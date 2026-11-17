package com.mccal.folio

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The copy a removed icon leaves behind to shrink away (RemovedIcons). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemovedIconsTest {
    @get:Rule val compose = createComposeRule()

    private val app = AppEntry("com.example.a/.Main", "A", Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

    @Test fun anIconWithNoPlaceYetLeavesNothing() {
        val removed = RemovedIcons()
        removed.launch(app, Rect(), 60.dp, 14.dp, true)
        assertTrue(removed.icons.isEmpty())
    }

    @Test fun finishedDropsOnlyThatCopy() {
        val removed = RemovedIcons()
        removed.launch(app, Rect(10, 20, 70, 80), 60.dp, 14.dp, true)
        removed.launch(app, Rect(100, 20, 160, 80), 60.dp, 14.dp, false)
        removed.finished(removed.icons.first())
        assertEquals(1, removed.icons.size)
        assertEquals(100f, removed.icons.single().left)
    }

    @Test fun theCopyKeepsWhereTheIconWas() {
        val removed = RemovedIcons()
        removed.launch(app, Rect(10, 20, 70, 80), 60.dp, 14.dp, true)
        val icon = removed.icons.single()
        assertEquals(10f, icon.left)
        assertEquals(20f, icon.top)
    }

    @Test fun theCopyPlaysForAMomentAndThenGoes() {
        val removed = RemovedIcons()
        compose.mainClock.autoAdvance = false
        compose.setContent { RemovedIconsHost(removed) }
        removed.launch(app, Rect(10, 20, 70, 80), 60.dp, 14.dp, true)
        compose.mainClock.advanceTimeBy(50)
        assertEquals("still playing a moment in", 1, removed.icons.size)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("dropped once it has played", removed.icons.isEmpty())
    }

    @Test fun reduceMotionStillEndsWithTheCopyDropped() {
        val removed = RemovedIcons()
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalReduceMotion provides true) { RemovedIconsHost(removed) } }
        removed.launch(app, Rect(10, 20, 70, 80), 60.dp, 14.dp, false)
        compose.mainClock.advanceTimeBy(30)
        assertEquals(1, removed.icons.size)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(removed.icons.isEmpty())
    }
}
