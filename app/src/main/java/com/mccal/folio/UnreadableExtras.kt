package com.mccal.folio

import android.content.Intent
import android.os.Bundle

/**
 * Any app can start Folio's exported Home (and the assistant picker) with extras. On Android 12 and 12L, reading one extra
 * unpacks all of them, and an extra holding a class Folio does not have makes that read throw BadParcelableException (a
 * RuntimeException wrapping the ClassNotFoundException). In Home's onCreate that is a crash, and two quick crashes put the
 * phone in Safe Mode. This unpacks the extras once, up front: when they cannot be read the whole bundle is dropped, so the
 * intent is treated as having no extras. Returns true when it had to drop them.
 */
internal fun Intent.dropUnreadableExtras(): Boolean {
    try {
        extras?.keySet()
        return false
    } catch (_: Exception) {
        // Includes BadParcelableException, ClassNotFoundException and a RuntimeException from unparceling.
        try { replaceExtras(null as Bundle?) } catch (_: Exception) { }
        return true
    }
}
