package com.mccal.folio

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The folder is its own Dialog window, and while it is open Android sends Back to that window. A back handler composed
 * outside the Dialog registers on the activity and never hears it (Back did nothing on an open folder in beta 1).
 */
class FolderBackTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }
    private fun source(name: String) = File(root, "app/src/main/java/com/mccal/folio/$name").readText()

    @Test fun `the folder's back handler lives inside its Dialog, on the dialog's dispatcher`() {
        val panel = source("FolderPanel.kt")
        val dialog = panel.indexOf("Dialog(onDismissRequest = close")
        val handler = panel.indexOf("DialogPredictiveBack(")
        assertTrue("the folder is still a Dialog", dialog > 0)
        assertTrue("the folder's back handler comes after the Dialog opens, inside it", handler > dialog)
        assertFalse("a plain PredictiveBack in the folder registers on the activity", Regex("""(?<!Dialog)PredictiveBack\(""").containsMatchIn(panel))
    }

    @Test fun `the dialog handler registers on the dialog window's own dispatcher`() {
        val back = source("PredictiveBack.kt")
        assertTrue("DialogWindowProvider" in back)
        assertTrue("findViewTreeOnBackPressedDispatcherOwner()" in back)
    }
}
