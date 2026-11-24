# Publishing a rollback build

A rollback build lets someone go back to an older version from Software Update › Older versions. It is an old release rebuilt under a higher `versionCode`, because Android refuses to install a lower one over a higher one and an uninstall wipes the phone's data (REL-34). Rollback builds are rare and deliberate: publish one only when a version has a real problem and people need a way off it.

## What the app does with it

- The app lists releases on the public repository whose tag looks like `v0.6.9.1-rollback.1`, and offers the ones below the installed version, newest first, one per version (the highest rollback number).
- A rollback build is never offered as an ordinary update (`SoftwareUpdate.newestOf` leaves it out).
- Going back offers a layout backup first, then downloads, checks the checksum and the signing key, and refuses any build whose `versionCode` is not higher than the installed one.
- After a rollback, only a release with a higher `versionCode` is offered, which is the next feature release (`SoftwareUpdate.offerable`).

## The number

A rollback build takes the top `versionCode` of the release family it is installed into: **7009 while people are on 0.7.x**, 7109 for 0.71.x, and so on. Digit 9 is reserved for rollbacks, so hotfixes run 1 to 8. It must be higher than every build someone can be on in that family (0.7.0-beta.N and 0.7.0 share 7000; a hotfix is 7001 to 7008).

Consequence to tell people: after going back, the final `0.7.0` and any `0.7.0.N` hotfix cannot install over the rollback. Updates resume with the next feature release (0.7.1, code 7010).

## Steps

Never build from a topic branch (REL-4). A rollback is built from the old release's tag.

1. **Pick the target and the family.** Target: the stable tag to go back to, for example `v0.6.9.1`. Family: the version people are on now, which fixes the code (7009 for 0.7.x).
2. **Check out the old tag in its own worktree**, not in a working tree with other changes:
   ```bash
   git worktree add ../folio-rollback v0.6.9.1
   ```
3. **Edit that checkout only (never commit it or push it).** In `app/build.gradle.kts`, set the version name and replace the `versionCode = ...` expression with the family's code:
   ```kotlin
   val folioVersion = "0.6.9.1-rollback.1"
   ...
   versionCode = 7009
   ```
   Nothing else changes, so the rebuilt app is the old code under a new number.
4. **Build and sign with the release key:**
   ```bash
   ./gradlew.bat :app:assembleRelease
   ```
   Signing needs the vault password; the person who owns the key types it. Use the same release keystore as every other release.
5. **Verify before publishing:**
   - `aapt dump badging` (or `apkanalyzer manifest version-code`) on the APK reports versionName `0.6.9.1-rollback.1` and versionCode `7009`.
   - The signing certificate matches the one on the previous release (`signing-certificate.txt`).
   - Install it over a phone on the newer version with `adb install -r` (not `-d`): it must succeed and keep data.
   - Install the next family's APK (for example a 7010 build) over it: it must succeed.
6. **Write `SHA256SUMS.txt`** for the APK, and a `signing-certificate.txt`, like any release.
7. **Publish** a GitHub release on the public `McCal-Codes/folio` repository with the tag `v0.6.9.1-rollback.1`, the APK, the checksums and the certificate. Mark it a pre-release if you like: the app ignores that flag for rollbacks. Put one plain sentence in the notes saying why it exists.
8. **Check it appears** in Software Update › Older versions on a phone running a newer version.
9. **Record it:** add a row to [the release catalog](README.md) with kind `rollback`.

## Do not

- Do not reuse a tag or a rollback number. A second rollback of the same version is `-rollback.2`.
- Do not publish a rollback with a `versionCode` that is not the family's top, or one that equals an existing release's code (REL-16: a number means one build).
- Do not build it from a branch, and do not commit or push the build edit.
