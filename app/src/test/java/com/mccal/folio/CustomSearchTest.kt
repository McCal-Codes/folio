package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your own search engine: only a real web address with a place for the search, and the search can't change the address. */
class CustomSearchTest {
    @Test fun `the search goes where the placeholder is, encoded like a browser would`() {
        assertEquals("https://example.com/s?q=hello%20world", customSearchUrl("https://example.com/s?q=%s", "hello world"))
        assertEquals("https://example.com/s?q=a%26b%3Dc%23d", customSearchUrl("https://example.com/s?q={query}", "a&b=c#d"))
        assertEquals("https://example.com/%E6%97%A5%E6%9C%AC", customSearchUrl("https://example.com/%s", "日本"))
        assertEquals("http://localhost:8080/?q=x&also=x", customSearchUrl(" http://localhost:8080/?q=%s&also={query} ", "x"))
    }

    @Test fun `the search is trimmed and cannot add to the address`() {
        assertEquals("https://example.com/?q=cats", customSearchUrl("https://example.com/?q=%s", "  cats  "))
        assertEquals("https://example.com/?q=%2F..%2F%3Fx%3D1", customSearchUrl("https://example.com/?q=%s", "/../?x=1"))
    }

    @Test fun `only http and https with a host and no sign-in are accepted`() {
        for (bad in listOf("", "   ", "example.com/?q=%s", "ftp://example.com/%s", "javascript:alert(%s)", "intent://x#Intent;end/%s",
            "file:///sdcard/%s", "content://x/%s", "https:///%s", "https://user:pw@example.com/?q=%s", "https://example.com/")) {
            assertNull(bad, customSearchUrl(bad, "q"))
        }
        assertFalse(customSearchTemplateIsValid("https://example.com/"))
        assertTrue(customSearchTemplateIsValid("https://example.com/?q=%s"))
    }

    @Test fun `a template that is too long is refused`() {
        assertNull(customSearchUrl("https://example.com/" + "a".repeat(600) + "?q=%s", "q"))
    }

    @Test fun `Enter uses your engine when it works, and Google when it does not or the name is unknown`() {
        assertEquals("https://example.com/?q=hi", enterSearchUrl(CUSTOM_SEARCH, "https://example.com/?q=%s", "hi"))
        assertTrue(enterSearchUrl(CUSTOM_SEARCH, "not a url", "hi").startsWith("https://www.google.com/search"))
        assertTrue(enterSearchUrl("SPARKLE", "", "hi").startsWith("https://www.google.com/search"))
    }

    @Test fun `a save from before, or a damaged one, searches with Google`() {
        assertEquals("GOOGLE", decodeLauncherState("{}", legacyRaw = null).searchEngine)
        assertEquals("GOOGLE", decodeLauncherState("""{"searchEngine":"SPARKLE"}""", legacyRaw = null).searchEngine)
        assertEquals("BRAVE", decodeLauncherState("""{"searchEngine":"BRAVE"}""", legacyRaw = null).searchEngine)
        val custom = decodeLauncherState("""{"searchEngine":"CUSTOM","searchCustomUrl":"https://example.com/?q=%s"}""", legacyRaw = null)
        assertEquals("CUSTOM", custom.searchEngine)
        assertEquals("https://example.com/?q=%s", custom.searchCustomUrl)
    }

    @Test fun `AI engines are not offered for Enter and a chosen Enter-only engine gets a chip`() {
        assertTrue(WebSearchTarget.enterEngines.none { it.ai })
        assertTrue(WebSearchTarget.chips("GOOGLE").none { it == WebSearchTarget.BING })
        assertTrue(WebSearchTarget.BING in WebSearchTarget.chips("BING"))
        assertEquals(WebSearchTarget.entries.filter { it.chip }, WebSearchTarget.chips(CUSTOM_SEARCH))
    }
}
