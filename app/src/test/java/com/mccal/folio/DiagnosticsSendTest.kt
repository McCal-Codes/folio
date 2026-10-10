package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsSendTest {
    // A mail selector in a chooser showed "No apps can perform this action" on a phone with Gmail (6 Oct 2026), so email
    // goes as the type mail apps declare and everything else as plain text.
    @Test fun `email goes as the type mail apps declare and a share goes as text`() {
        assertEquals("message/rfc822", Diagnostics.mimeFor(email = true))
        assertEquals("text/plain", Diagnostics.mimeFor(email = false))
    }

    @Test fun `the manifest asks whether a mail app exists`() {
        val manifest = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() }
            .resolve("app/src/main/AndroidManifest.xml").readText()
        assert("message/rfc822" in manifest) { "hasMailApp needs the <queries> intent for SEND message/rfc822" }
    }
}
