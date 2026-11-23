package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

/** The drawn badge is not read by TalkBack, so its number rides on the icon's name (#561 follow-up). */
class BadgeSpeechTest {
    @Test fun `a number badge is spoken as the count`() = assertEquals(BadgeSpeech.COUNT, badgeSpeech(5, BadgeStyle.COUNT))

    @Test fun `a dot says only that there are notifications`() = assertEquals(BadgeSpeech.DOT, badgeSpeech(5, BadgeStyle.DOT))

    @Test fun `a count Folio cannot trust is drawn as a dot and spoken as one`() =
        assertEquals(BadgeSpeech.DOT, badgeSpeech(3, badgeShape(BadgeStyle.COUNT, dot = true)))

    @Test fun `badges off or nothing unread says nothing`() {
        assertEquals(BadgeSpeech.NONE, badgeSpeech(5, BadgeStyle.OFF))
        assertEquals(BadgeSpeech.NONE, badgeSpeech(0, BadgeStyle.COUNT))
        assertEquals(BadgeSpeech.NONE, badgeSpeech(0, BadgeStyle.DOT))
    }
}
