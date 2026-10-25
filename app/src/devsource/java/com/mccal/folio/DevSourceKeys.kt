package com.mccal.folio

/**
 * The two public keys the Folio Dev debug source trusts. Both are public halves: the private ones live in
 * ~/.folio on McCal's machine and never go anywhere near this repository.
 *
 * Only compiled into the debug and fast builds (src/devsource). A release build has no copy of either.
 */
internal object DevSourceKeys {
    /**
     * Signs the dev code that opens the worker's /dev/ routes. A temporary key (24 Oct 2026), kept apart from
     * [BetaKeys.SUPPORTER] and [BetaKeys.TEST] so a supporter code can never open this, and a dev code never opens
     * /beta. Swap it for a lasting key by changing this one constant and the worker's DEV_KEYS; codes die with it.
     */
    const val CODE = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESqpQhSJCbk+9AQOPzn0zihZRSlMOp2jr9o0RsxcjC7ad/X6IKMXJkMqjDgdSZRr+TwSZQe2iOgszYQ0apps+zA=="

    /** Signs builds.json and the work-in-progress tweaks source. Not the public Community Tweaks key, and never shared with it. */
    const val PUBLISHER = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEUczgMGnLEEXd2kE7b4+dB4wNGuFV5hUszREsxskrAqUZ3trdIhM43diJyMZhfOe0DFY2Oys7aVqgBr2gusdDQw=="

    val codeKeys: List<String> = listOf(CODE)
    val publisherKeys: List<String> = listOf(PUBLISHER)
}
