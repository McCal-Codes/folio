package com.mccal.folio

/**
 * The optional ways to reach past a normal app that setup may mention when it finds one already on the phone: Shizuku and a
 * root manager. Finding the app is only a package check; Folio asks for nothing, runs nothing and never asks for root in
 * the background (ADR 0009). Everything in Folio works without either.
 */
internal enum class OptionalAccess(val packages: Set<String>) {
    SHIZUKU(setOf("moe.shizuku.privileged.api")),
    ROOT_MANAGER(setOf("com.topjohnwu.magisk", "me.weishu.kernelsu", "me.bmax.apatch", "io.github.vvb2060.magisk"));

    companion object {
        /** Which of them [isInstalled] says are on the phone, in a fixed order. */
        fun found(isInstalled: (String) -> Boolean): List<OptionalAccess> = entries.filter { access -> access.packages.any(isInstalled) }
    }
}
