## What this changes

<!-- What's different for someone using Folio, and why. -->

## How I checked it

<!-- Phone and screens you tried: folded, unfolded, half folded, a regular phone or tablet. -->

- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug` passes
- [ ] Screenshots don't show personal info (notifications, accounts, contacts, work data)
- [ ] New themes in `themes/` pass `CommunityThemesTest` and have `"iconPack": null`
- [ ] No GPL code and nothing that needs root
- [ ] What a person will see is written as a line in the `## [x.y.z] - Unreleased` section of [CHANGELOG.md](../CHANGELOG.md), or this changes nothing they can see and the pull request is labelled `no-changelog` ([REL-7](../docs/standards/releases.md))
- [ ] Follows the [Folio Standards](../docs/standards/README.md); rule IDs it touches: <!-- e.g. ADP-1, DYN-11 -->
- [ ] An AI agent helped: <!-- leave unticked if not. If ticked: which tool, and what it did. Bug tests and fixes only, see docs/standards/ai-contributions.md -->

<!--
CI runs a `release-rules` job on every pull request, from docs/standards/releases.md:
  REL-7   a change under app/src/main or market/src/main adds a line to the Unreleased notes.
          Label the pull request `no-changelog` when nobody will see a difference.
  REL-13  a folioVersion bump comes on its own, with nothing but the notes, the roadmap and its release doc.
          Label it `release-exception` if a release and a change really do belong together.
Run it before you push, from the repository you are standing in:
  bash tools/check-release-rules.sh origin/main
-->
