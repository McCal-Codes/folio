package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRenamingTest {
    private val id = profileAppId("com.example/.Main", 0, 0)

    @Test fun renamingKeepsTheTrimmedNamePerApp() {
        val names = editAppName(emptyMap(), id, "  Mail  ")
        assertEquals(mapOf(id to "Mail"), names)
        assertEquals(mapOf(id to "Post"), editAppName(names, id, "Post"))
    }

    @Test fun blankNameClearsTheCustomNameAndLongNamesAreCapped() {
        val names = editAppName(emptyMap(), id, "Mail")
        assertEquals(emptyMap<String, String>(), editAppName(names, id, "   "))
        assertEquals("N".repeat(MAX_APP_NAME), editAppName(names, id, "N".repeat(MAX_APP_NAME + 20))[id])
    }
}
