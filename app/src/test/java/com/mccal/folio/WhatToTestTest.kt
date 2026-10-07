package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhatToTestTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "CHANGELOG.md").exists() }

    @Test fun `the list that ships parses, has a release, distinct ids and steps for every item`() {
        val list = assertNotNull(WhatToTest.parse(File(root, "app/src/main/assets/what-to-test.json").readText())).let { WhatToTest.parse(File(root, "app/src/main/assets/what-to-test.json").readText())!! }
        assertTrue(list.release.contains("beta"))
        assertEquals(list.items.size, list.items.map { it.id }.toSet().size)
        assertTrue(list.items.all { it.steps.isNotEmpty() && it.title.length <= 40 })
        assertEquals("the new setup is the optional item", 1, list.items.count { it.action == "setup" })
    }

    @Test fun `a bad item is skipped and a bad file is refused`() {
        val list = WhatToTest.parse("""{"release":"0.6.9-beta.1","items":[
            {"id":"a","title":"A","steps":["one"]},
            {"id":"b","title":"B","steps":[]},
            {"title":"no id","steps":["x"]},
            {"id":"a","title":"A again","steps":["x"]}]}""")
        assertEquals(listOf("a"), list?.items?.map { it.id })
        assertNull(WhatToTest.parse("not json"))
        assertNull(WhatToTest.parse("""{"items":[{"id":"a","title":"A","steps":["x"]}]}"""))
        assertNull(WhatToTest.parse("""{"release":"r","items":[]}"""))
    }

    @Test fun `answers are counted and only a beta or Folio Dev sees the list`() {
        val list = TestList("r", listOf(TestItem("a", "A", "", listOf("x")), TestItem("b", "B", "", listOf("x")), TestItem("c", "C", "", listOf("x"))))
        assertEquals(2, WhatToTest.answered(list, mapOf("a" to WhatToTest.Result.OK, "c" to WhatToTest.Result.SKIP, "gone" to WhatToTest.Result.BAD)))
        assertTrue(WhatToTest.available("0.6.9-beta.1", "com.mccal.folio"))
        assertTrue(WhatToTest.available("0.6.8", "com.mccal.folio.dev"))
        assertTrue(!WhatToTest.available("0.6.8", "com.mccal.folio"))
    }
}
