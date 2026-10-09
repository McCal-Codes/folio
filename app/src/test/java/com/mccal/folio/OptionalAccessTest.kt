package com.mccal.folio

import org.junit.Assert.assertEquals
import org.junit.Test

class OptionalAccessTest {
    @Test fun `nothing installed finds nothing`() = assertEquals(emptyList<OptionalAccess>(), OptionalAccess.found { false })

    @Test fun `each is found by any of its packages, in a fixed order`() {
        assertEquals(listOf(OptionalAccess.SHIZUKU), OptionalAccess.found { it == "moe.shizuku.privileged.api" })
        assertEquals(listOf(OptionalAccess.ROOT_MANAGER), OptionalAccess.found { it == "me.weishu.kernelsu" })
        assertEquals(listOf(OptionalAccess.SHIZUKU, OptionalAccess.ROOT_MANAGER), OptionalAccess.found { it == "com.topjohnwu.magisk" || it == "moe.shizuku.privileged.api" })
    }
}
