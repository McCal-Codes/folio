package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a folio://redeem link from a supporter email is allowed to carry. */
class RedeemLinkTest {
    private val code = "047G4-0BDYA-M6P6F-SF96Z-VKRZ7-8KVV7"

    @Test fun `a code comes through as a query or a path`() {
        assertEquals(code, redeemCode("folio://redeem?c=$code"))
        assertEquals(code, redeemCode("FOLIO://REDEEM?c=$code"))
        assertEquals(code, redeemCode("folio://redeem/$code"))
        assertEquals(code, redeemCode("folio://redeem?from=kofi&c=$code"))
        assertEquals(code, redeemCode(" folio://redeem?c=${code.replace("-", "%2D")} "))
    }

    @Test fun `the older early link is read the same as redeem`() {
        assertEquals(code, redeemCode("folio://early?c=$code"))
        assertEquals(code, redeemCode("folio://early/$code"))
    }

    @Test fun `anything else is ignored`() {
        assertNull(redeemCode(null))
        assertNull(redeemCode("https://ko-fi.com/mccal"))
        assertNull(redeemCode("folio://settings?c=$code"))
        assertNull(redeemCode("folio://redeem"))
        assertNull(redeemCode("folio://redeem?c=short"))
        // No smuggling: a link isn't a place to put anything but a code.
        assertNull(redeemCode("folio://redeem?c=<script>alert(1)</script>aaaaaaaaaaaaaaaaaaaa"))
        assertNull(redeemCode("folio://redeem?c=" + "A".repeat(300)))
    }
}
