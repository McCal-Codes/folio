package com.mccal.folio

import com.mccal.folio.ApkIdentity.Archive
import com.mccal.folio.ApkIdentity.Refusal
import com.mccal.folio.ApkIdentity.Signing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a downloaded APK is the app its listing named: package, versionCode and signer, with key rotation allowed
 * the way Android allows it. Plain values only: no PackageManager.
 */
class ApkIdentityTest {
    private val old = "1".repeat(64)
    private val new = "2".repeat(64)
    private val other = "3".repeat(64)

    private fun single(key: String) = Signing(multiple = false, signers = setOf(key), history = listOf(key))
    private fun rotated(vararg lineage: String) = Signing(multiple = false, signers = setOf(lineage.last()), history = lineage.toList())
    private fun several(vararg keys: String) = Signing(multiple = true, signers = keys.toSet())

    private val keyd = setOf("com.mccal.keyd")
    private fun apk(appId: String = "com.mccal.keyd", code: Long = 3000, signing: Signing = single(new)) = Archive(appId, code, signing)

    @Test fun `the listed app, at the listed version, signed by the listed key, is accepted`() {
        assertNull(ApkIdentity.check(apk(), keyd, 3000, setOf(new)))
        assertNull("a later build is fine", ApkIdentity.check(apk(code = 3001), keyd, 3000, setOf(new)))
        assertNull("a listing with no versionCode doesn't check one", ApkIdentity.check(apk(code = 1), keyd, null, setOf(new)))
    }

    @Test fun `an APK Android can't read is refused`() {
        assertEquals(Refusal.UNREADABLE, ApkIdentity.check(null, keyd, 3000, setOf(new)))
    }

    @Test fun `a different package is refused`() {
        assertEquals(Refusal.WRONG_APP, ApkIdentity.check(apk(appId = "com.example.keyd"), keyd, 3000, setOf(new)))
        assertEquals("a listing naming no app id matches nothing", Refusal.WRONG_APP, ApkIdentity.check(apk(), emptySet(), 3000, setOf(new)))
    }

    @Test fun `a lower versionCode is refused`() {
        assertEquals(Refusal.TOO_OLD, ApkIdentity.check(apk(code = 2999), keyd, 3000, setOf(new)))
    }

    @Test fun `a signer the listing doesn't name is refused`() {
        assertEquals(Refusal.WRONG_SIGNER, ApkIdentity.check(apk(signing = single(other)), keyd, 3000, setOf(new)))
        assertEquals("a listing naming no signer trusts nothing", Refusal.WRONG_SIGNER, ApkIdentity.check(apk(), keyd, 3000, emptySet()))
        assertEquals("an unsigned APK matches nothing", Refusal.WRONG_SIGNER,
            ApkIdentity.check(apk(signing = Signing(false, emptySet())), keyd, 3000, setOf(new)))
    }

    @Test fun `a rotated key is accepted when the listing names the new key`() {
        // apksigner rotate: signed with the new key, carrying the old one as its past.
        assertTrue(ApkIdentity.signedBy(rotated(old, new), setOf(new)))
        assertTrue(ApkIdentity.signedBy(rotated(old, new), setOf(old, new)))
        assertNull(ApkIdentity.check(apk(signing = rotated(old, new)), keyd, 3000, setOf(old, new)))
    }

    @Test fun `a rotated key is refused while the listing still names only the old one`() {
        // The source vouches for the key the app is signed with now; naming only the past isn't that.
        assertFalse(ApkIdentity.signedBy(rotated(old, new), setOf(old)))
        assertFalse("and a lineage of strangers is still strangers", ApkIdentity.signedBy(rotated(other, new), setOf(old)))
    }

    @Test fun `several signers must match the listed set exactly`() {
        assertTrue(ApkIdentity.signedBy(several(old, new), setOf(old, new)))
        assertFalse("one short", ApkIdentity.signedBy(several(old, new), setOf(new)))
        assertFalse("one extra", ApkIdentity.signedBy(several(old, new), setOf(old, new, other)))
        assertEquals(Refusal.WRONG_SIGNER, ApkIdentity.check(apk(signing = several(old, other)), keyd, 3000, setOf(old, new)))
    }

    @Test fun `an update may come from the same key or one it rotated to`() {
        assertTrue(ApkIdentity.descendsFrom(single(old), single(old)))
        assertTrue("rotated from the key on the phone", ApkIdentity.descendsFrom(rotated(old, new), single(old)))
        assertTrue("the phone already rotated too", ApkIdentity.descendsFrom(rotated(old, new), rotated(old, new)))
        assertFalse("a stranger", ApkIdentity.descendsFrom(single(other), single(old)))
        assertFalse("a lineage that doesn't include the phone's key", ApkIdentity.descendsFrom(rotated(other, new), single(old)))
        assertFalse("going back to the old key", ApkIdentity.descendsFrom(single(old), rotated(old, new)))
        assertFalse("nothing to compare", ApkIdentity.descendsFrom(Signing(false, emptySet()), single(old)))
    }

    @Test fun `an update with several signers needs the same set`() {
        assertTrue(ApkIdentity.descendsFrom(several(old, new), several(old, new)))
        assertFalse(ApkIdentity.descendsFrom(several(old, other), several(old, new)))
        assertFalse(ApkIdentity.descendsFrom(several(old, new), single(old)))
        assertFalse(ApkIdentity.descendsFrom(single(old), several(old, new)))
    }
}
