package com.mccal.folio.market

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Who wrote a package, as opposed to who handed it over.
 *
 * The case that matters: a mirror should be able to carry someone's package - that's the point of a mirror - but
 * should not be able to change it, and nobody should be able to publish under a name that already belongs to
 * somebody else's key.
 */
class AuthorSignatureTest {
    private val maya = newKeyPair()
    private val mallory = newKeyPair()
    private val id = "dev.maya.sunset-icons"
    private val version = requireNotNull(DebVersion.parse("1.2.0"))
    private val sha = "a".repeat(64)

    private fun newKeyPair() =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun publicKey(pair: KeyPair) = Base64.getEncoder().encodeToString(pair.public.encoded)

    private fun signedBy(pair: KeyPair, id: String = this.id, version: DebVersion = this.version, sha: String = this.sha) =
        AuthorSignature(
            publicKey(pair),
            Base64.getEncoder().encodeToString(
                Signature.getInstance(SourceKey.ALGORITHM).run {
                    initSign(pair.private)
                    update(AuthorSignature.payload(id, version, sha).toByteArray())
                    sign()
                },
            ),
        )

    private fun trust() = AuthorTrust(MemoryStore())

    @Test fun `a package signed by its author verifies, and the key is pinned to the id`() {
        val authors = trust()
        val first = authors.check(id, version, sha, signedBy(maya))
        assertTrue("$first", first is AuthorTrust.Result.FirstTime)
        authors.remember(id, (first as AuthorTrust.Result.FirstTime).keyBase64)
        // The next copy, from anywhere, is recognised.
        assertEquals(AuthorTrust.Result.Signed, authors.check(id, version, sha, signedBy(maya)))
    }

    @Test fun `a mirror can carry the package, byte for byte`() {
        val authors = trust()
        authors.remember(id, publicKey(maya))
        // A different source, the same bytes and the same signature: nothing about the signature mentions a source.
        assertEquals(AuthorTrust.Result.Signed, authors.check(id, version, sha, signedBy(maya)))
    }

    @Test fun `a mirror that alters the package is caught`() {
        val authors = trust()
        authors.remember(id, publicKey(maya))
        val tampered = "b".repeat(64)
        assertEquals(AuthorTrust.Result.Broken, authors.check(id, version, tampered, signedBy(maya)))
        assertTrue(!AuthorTrust.Result.Broken.installable)
    }

    @Test fun `somebody else's key can't publish under a name that's taken`() {
        val authors = trust()
        authors.remember(id, publicKey(maya))
        assertEquals(AuthorTrust.Result.WrongAuthor, authors.check(id, version, sha, signedBy(mallory)))
        assertTrue(!AuthorTrust.Result.WrongAuthor.installable)
    }

    @Test fun `a signature can't be lifted onto another package or version`() {
        val authors = trust()
        val forOther = signedBy(maya, id = "dev.maya.something-else")
        assertEquals(AuthorTrust.Result.Broken, authors.check(id, version, sha, forOther))
        val forOlder = signedBy(maya, version = requireNotNull(DebVersion.parse("1.0.0")))
        assertEquals(AuthorTrust.Result.Broken, authors.check(id, version, sha, forOlder))
    }

    @Test fun `unsigned is allowed, until a signed copy has been seen`() {
        val authors = trust()
        assertEquals(AuthorTrust.Result.Unsigned, authors.check(id, version, sha, null))
        assertTrue(AuthorTrust.Result.Unsigned.installable)

        // Dropping the signature afterwards is how someone would get around all of this, so it's refused.
        authors.remember(id, publicKey(maya))
        assertEquals(AuthorTrust.Result.SignatureMissing, authors.check(id, version, sha, null))
        assertTrue(!AuthorTrust.Result.SignatureMissing.installable)
    }

    @Test fun `a package shared as a file carries its own signature`() {
        val files = mapOf(
            "manifest.json" to """{"id":"$id"}""".toByteArray(),
            "assets/hero.png" to byteArrayOf(1, 2, 3),
        )
        val payload = AuthorSignature.filesPayload(id, version, files)
        val signature = AuthorSignature(
            publicKey(maya),
            Base64.getEncoder().encodeToString(
                Signature.getInstance(SourceKey.ALGORITHM).run { initSign(maya.private); update(payload.toByteArray()); sign() },
            ),
        )
        val signed = files + ("signature.json" to
            """{"format":1,"key":"${signature.keyBase64}","signature":"${signature.signature}"}""".toByteArray())

        val authors = trust()
        val first = authors.checkFiles(id, version, signed)
        assertTrue("$first", first is AuthorTrust.Result.FirstTime)
        authors.remember(id, publicKey(maya))
        assertEquals(AuthorTrust.Result.Signed, authors.checkFiles(id, version, signed))

        // Changing any file in the package breaks it, because the signature is over all of them.
        val edited = signed + ("assets/hero.png" to byteArrayOf(9, 9, 9))
        assertEquals(AuthorTrust.Result.Broken, authors.checkFiles(id, version, edited))
        // And re-zipping the same files doesn't: the signature is over the files, not the archive's bytes.
        assertEquals(AuthorTrust.Result.Signed, authors.checkFiles(id, version, LinkedHashMap(signed.entries.reversed().associate { it.toPair() })))
        // Stripping it out is refused, once a signed copy has been seen.
        assertEquals(AuthorTrust.Result.SignatureMissing, authors.checkFiles(id, version, files))
    }

    @Test fun `an index carries the signature through the parser`() {
        val signature = signedBy(maya)
        val manifest = java.io.File(
            generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "CHANGELOG.md").exists() },
            "docs/sdk/source/packages/cabinet/manifest.json",
        ).readText().replace("\"\$schema\": \"https://folio.mccal.dev/schema/v1/manifest.schema.json\",", "")
        val index = """
            {"format":1,"name":"Maya's packages","packages":[
              {"id":"com.mccal.folio.cabinet","version":"1.0.0","url":"packages/cabinet.foliopkg",
               "sha256":"$sha","size":2048,
               "signedBy":{"key":"${signature.keyBase64}","signature":"${signature.signature}"},
               "manifest":$manifest}]}
        """.trimIndent()
        val parsed = RepoIndex.parse(index)
        assertTrue("$parsed", parsed is ParseResult.Ok)
        val entry = (parsed as ParseResult.Ok).value.packages.single()
        assertEquals(signature, entry.signedBy)
    }

    // A listing for a kind Folio won't install says so, instead of offering Get for a package that is then refused.
    @Test fun `an index listing of a reserved kind needs a newer Folio`() {
        val manifest = """{"format":1,"id":"dev.example.test.script","name":"Script","version":"1.0","author":{"name":"Example"},
            "minFolio":"0.6.6","section":"tweaks","kind":["script"],"permissions":[]}"""
        val index = """{"format":1,"name":"Example","packages":[{"id":"dev.example.test.script","version":"1.0",
            "url":"packages/script.foliopkg","manifest":$manifest}]}"""
        val parsed = RepoIndex.parse(index)
        assertTrue("$parsed", parsed is ParseResult.Ok)
        assertEquals(listOf("script"), (parsed as ParseResult.Ok).value.packages.single().needs)
    }
}
