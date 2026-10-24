package com.mccal.folio

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** S-4 in the beta 4 audit: staying in Safe Mode keeps Market refresh and auto-update paused past the first 30 seconds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeModeStableTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun clean() { context.getSharedPreferences("safe_mode", 0).edit().clear().commit() }

    private fun pastTheWindow() = shadowOf(Looper.getMainLooper()).idleFor(31, TimeUnit.SECONDS)

    @Test fun `a while without a crash does not end Safe Mode for background work while the user stays in it`() {
        SafeMode.onStart(context) { emptyList() }
        SafeMode.onCrash(context)
        SafeMode.onStart(context) { emptyList() }
        SafeMode.onCrash(context)
        SafeMode.onStart(context) { emptyList() }
        assertTrue(SafeMode.active)

        pastTheWindow()
        SafeMode.markStable(context)
        assertTrue("Market refresh and auto-update still see Safe Mode", SafeMode.isOn(context))

        SafeMode.exit(context)
        assertFalse("Restart Normally is the way out", SafeMode.isOn(context))
    }

    @Test fun `a crash after a long run in Safe Mode keeps Safe Mode on for the next launch`() {
        SafeMode.onStart(context) { emptyList() }
        SafeMode.onCrash(context)
        SafeMode.onStart(context) { emptyList() }
        SafeMode.onCrash(context)
        SafeMode.onStart(context) { emptyList() }
        assertTrue(SafeMode.active)

        pastTheWindow()
        SafeMode.onCrash(context)
        assertTrue("still on for background work", SafeMode.isOn(context))
        SafeMode.onStart(context) { emptyList() }
        assertTrue("and for the next launch", SafeMode.active)
    }

    @Test fun `outside Safe Mode a stable run still forgets an earlier quick crash`() {
        SafeMode.onStart(context) { emptyList() }
        SafeMode.onCrash(context)
        SafeMode.onStart(context) { emptyList() }
        assertFalse(SafeMode.active)

        pastTheWindow()
        SafeMode.markStable(context)
        SafeMode.onStart(context) { emptyList() }
        SafeMode.onCrash(context)
        SafeMode.onStart(context) { emptyList() }
        assertFalse("the earlier crash was forgotten, so one more is not two", SafeMode.active)
    }
}
