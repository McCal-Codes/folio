package com.mccal.folio

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupporterExpiryTest {
    private val today = LocalDate.of(2026, 11, 9)

    @Test fun `a running code is active with the days it has left`() {
        val state = supporterStateFor(true, today.plusDays(10), true, today, 7L)
        assertEquals(SupporterState.Active(today.plusDays(10), 10L, 7L), state)
    }

    @Test fun `a code that never ends is active with no days`() {
        assertEquals(SupporterState.Active(null, null, 1L), supporterStateFor(true, null, true, today, 1L))
    }

    @Test fun `a genuine code that is not running has expired`() {
        assertEquals(SupporterState.Expired(today.minusDays(3), 5L), supporterStateFor(false, today.minusDays(3), true, today, 5L))
    }

    @Test fun `something that was never a code is no code`() {
        assertEquals(SupporterState.None, supporterStateFor(false, null, false, today))
    }

    @Test fun `the last day of a code still counts as running with none left`() {
        val state = supporterStateFor(true, today, true, today) as SupporterState.Active
        assertEquals(0L, state.daysLeft)
        assertEquals(SupporterReminderStage.SOON, supporterReminderStage(state))
    }

    @Test fun `a week before the end is the first reminder and more than a week is none`() {
        assertEquals(SupporterReminderStage.SOON, supporterReminderStage(SupporterState.Active(today.plusDays(7), 7L)))
        assertNull(supporterReminderStage(SupporterState.Active(today.plusDays(8), 8L)))
    }

    @Test fun `a code that never ends, or no code, gets no reminder`() {
        assertNull(supporterReminderStage(SupporterState.Active(null, null)))
        assertNull(supporterReminderStage(SupporterState.None))
    }

    @Test fun `an expired code gets the ended reminder`() {
        assertEquals(SupporterReminderStage.ENDED, supporterReminderStage(SupporterState.Expired(today.minusDays(1))))
    }

    @Test fun `each reminder goes once and in order`() {
        assertTrue(SupporterReminder.due(SupporterReminderStage.SOON, -1))
        assertFalse(SupporterReminder.due(SupporterReminderStage.SOON, SupporterReminderStage.SOON.ordinal))
        assertTrue(SupporterReminder.due(SupporterReminderStage.ENDED, SupporterReminderStage.SOON.ordinal))
        assertFalse(SupporterReminder.due(SupporterReminderStage.ENDED, SupporterReminderStage.ENDED.ordinal))
        assertFalse(SupporterReminder.due(null, -1))
    }

    private val notes = """
        # Folio 0.7.0-beta.2 for supporters

        ## What's new since beta 1

        - **Island over apps opens its card:** tapping the island opens the card.
        - **Island size:** Small, Standard or Large.
        - **Icon Actions:** a swipe on an icon runs an action.

        ## What to try
        1. **Island**: something else.
    """.trimIndent()

    @Test fun `highlights are the bullets under What's new with the markdown gone`() {
        val (shown, more) = betaHighlights(notes)
        assertEquals(listOf("Island over apps opens its card: tapping the island opens the card.", "Island size: Small, Standard or Large.",
            "Icon Actions: a swipe on an icon runs an action."), shown)
        assertEquals(0, more)
    }

    @Test fun `only the limit is shown and the rest are counted`() {
        val many = "## What's new"+"\n" + (1..8).joinToString("\n") { "- line $it" }
        val (shown, more) = betaHighlights(many, limit = 5)
        assertEquals(5, shown.size)
        assertEquals(3, more)
    }

    @Test fun `a long bullet is cut to a line and notes with no heading show nothing`() {
        val (shown, _) = betaHighlights("## What's new\n- " + "x".repeat(300))
        assertTrue(shown.single().length <= 150 && shown.single().endsWith("..."))
        assertEquals(emptyList<String>() to 0, betaHighlights("just some text"))
    }
}
