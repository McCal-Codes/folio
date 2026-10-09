package com.mccal.folio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstUseHintsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `a phone that never went through setup is never shown a hint`() {
        context.getSharedPreferences("first_use_hints", Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(FirstUseHints.pending(context, FirstUseHint.HOLD))
        assertFalse(FirstUseHints.pending(context, FirstUseHint.UNFOLD))
    }

    @Test fun `a hint is pending until it is done, and done is remembered`() {
        context.getSharedPreferences("first_use_hints", Context.MODE_PRIVATE).edit().clear().commit()
        FirstUseHints.arm(context)
        assertTrue(FirstUseHints.pending(context, FirstUseHint.HOLD))
        FirstUseHints.done(context, FirstUseHint.HOLD)
        assertFalse(FirstUseHints.pending(context, FirstUseHint.HOLD))
        // The other hint is its own.
        assertTrue(FirstUseHints.pending(context, FirstUseHint.UNFOLD))
    }

    @Test fun `with no island to show it in, a hint is not used up`() {
        context.getSharedPreferences("first_use_hints", Context.MODE_PRIVATE).edit().clear().commit()
        FirstUseHints.arm(context)
        FirstUseHints.show(context, FirstUseHint.UNFOLD)
        assertTrue("nothing showed it, so it is still to come", FirstUseHints.pending(context, FirstUseHint.UNFOLD))
    }
}
