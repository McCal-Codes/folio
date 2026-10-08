package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubSourceUrlTest {
    @Test fun `a GitHub repository address is its Pages site`() {
        assertEquals("https://mccal-codes.github.io/folio-tweaks/", normalizeSourceUrl("github.com/McCal-Codes/folio-tweaks"))
        assertEquals("https://mccal-codes.github.io/folio-tweaks/", normalizeSourceUrl("https://github.com/McCal-Codes/folio-tweaks/"))
        assertEquals("https://mccal-codes.github.io/folio-tweaks/", normalizeSourceUrl("  https://www.github.com/McCal-Codes/folio-tweaks.git "))
    }

    @Test fun `a repository named like the owner's site is the site's root`() {
        assertEquals("https://alice.github.io/", normalizeSourceUrl("github.com/alice/alice.github.io"))
    }

    @Test fun `anything that is not exactly owner and repo is left as typed`() {
        assertEquals("https://github.com/o/r/tree/main/", normalizeSourceUrl("https://github.com/o/r/tree/main"))
        assertEquals("https://example.com/source/", normalizeSourceUrl("https://example.com/source"))
        assertEquals("https://github.com/o/", normalizeSourceUrl("https://github.com/o"))
        assertEquals("http://github.com/o/r/", normalizeSourceUrl("http://github.com/o/r"))
    }

    @Test fun `a lookalike host is never rewritten`() {
        assertNull(githubPagesUrl("https://github.com.evil.example/o/r"))
        assertNull(githubPagesUrl("https://notgithub.com/o/r"))
        assertNull(githubPagesUrl("https://github.com/-bad/r"))
        assertNull(githubPagesUrl("https://github.com/o/.."))
    }

    @Test fun `the same repository typed two ways is one source`() {
        assertEquals(normalizeSourceUrl("github.com/o/r"), normalizeSourceUrl("https://o.github.io/r"))
    }
}
