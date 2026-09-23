# Releases standard (`REL`)

How work reaches a phone: branches, the changelog, version numbers, betas and what goes out beside a build.

The short version:

> **`main` is always releasable, notes are written where the work lands, and a version number means one build
> everywhere it appears.**

This standard exists because 0.6.7 nearly went out twice. A branch held `0.6.7-beta.2` and gave supporters four
features; `main` separately gained seven fixes and claimed the same number. Since the updater orders
`0.6.7-beta.2 < 0.6.7`, the fix-only build would have been offered to supporters as an update that quietly removed
what they were testing. Every rule below is aimed at that shape of mistake.

## Rules

### Branches

- **REL-1 MUST** keep `main` releasable. Every change lands as a topic branch and a squashed pull request, and
  `main` is the only branch anything is ever built from.
- **REL-2 MUST NOT** let a topic branch live long enough to hold a release. A branch that cannot merge within about
  a week is too big: split it, or merge the finished part behind a setting that is off.
- **REL-3 SHOULD** merge `main` into a topic branch whenever `main` moves, not once at the end. A branch that is
  both ahead and behind by double figures is a merge nobody can review.
- **REL-4 MUST NOT** build, tag or publish anything from a topic branch. If supporters need it, it goes to `main`
  first. There is no stable branch and no beta branch: one trunk, and the two audiences are separated by a gate in the
  build ([ADR 0007](../adr/0007-release-trains.md)).
- **REL-4a MUST** gate a feature that is not ready for everyone on the `beta` scope rather than holding it on a
  branch. A supporter sees it the day it merges; opening the gate is what shipping it means. Gate it at the entry
  point, not only in the UI, so hidden work costs nobody battery.
- **REL-4b** A stable release is the same tree as the beta before it, with gates open. Nothing is ported,
  cherry-picked or rebuilt between the two.
- **REL-4c** A `hotfix/x.y.z.n` branch from a tag is the last resort, only when a breaking migration is mid-flight on
  `main`. It ships and merges back the same day.
- **REL-5 MUST** name branches after the change, never after the agent, the session or the tool. No AI attribution
  in branch names, commit messages, pull request text or code comments.
- **REL-6 MUST** check the other worktrees and open pull requests before editing a file more than one change is
  likely to touch: `CHANGELOG.md`, `app/src/main/assets/roadmap.json`, `strings.xml`, `app/build.gradle.kts`.
  Several sessions work on Folio at once.

### The changelog

- **REL-7 MUST** keep the notes for the next release on `main`, in a `## [x.y.z] - Unreleased` section, and add each
  change's line in the same pull request as the change.
- **REL-8 MUST NOT** accumulate release notes on a feature branch. Two branches each growing their own section for
  the same version is how a release ends up meaning two different things.
- **REL-9 MUST** write each line as what a person sees, not what the code does, and punctuate without em dashes.
  One line per user-visible change; invisible work gets no line.
- **REL-10 MUST** date the section in the version-bump commit, not before. `Unreleased` is the truth until then.

### Versions

- **REL-11 MUST** follow semver in `folioVersion`, and derive `versionCode` from it rather than writing it by hand.
  The formula is REL-12's. Until the release that adopts it, the code in `app/build.gradle.kts` is still the old
  `MAJOR * 10000 + MINOR * 100 + PATCH` (gap 5 below), and every new code must be larger than the last either way.
- **REL-12** A fix on top of a release is a four-part version, `x.y.z.n`, and `versionCode` leaves nine slots per
  release for them: `(MAJOR * 10000 + MINOR * 100 + PATCH) * 10 + HOTFIX` ([ADR 0007](../adr/0007-release-trains.md)).
  A fix release never has to consume the number a feature release wanted, which is what went wrong on 23 Sep 2026.
- **REL-13 MUST** make the version bump its own commit, the last one before the tag, touching only the version, the
  changelog date, the roadmap's statuses for that release and its release doc.
- **REL-14 MUST** give every release a section in `app/src/main/assets/roadmap.json` matching its version, so
  `RoadmapTest` passes and Settings › Roadmap agrees with What's New.
- **REL-15 MUST NOT** publish a stable release carrying less than a beta of the same version. Either the stable
  includes everything its betas had, or the beta line is renumbered before the stable goes out.
- **REL-16** A version number means one build. If what ships has to change after a beta, the number moves; the
  contents of a number never do.

### Betas

- **REL-17 MUST** cut betas from `main`, tagged `vX.Y.Z-beta.N`, published as pre-releases on the private
  `McCal-Codes/folio-beta` repository. A beta is a tag, never a branch.
- **REL-17a** Betas are not on a calendar: one goes out when there is something worth testing. That means anything
  touching how Folio updates, installs or checks a supporter code, since those paths cannot be tested any other way,
  and any feature a supporter would want to try (McCal, 23 Sep 2026).
- **REL-18 MUST** sign every build, beta included, with the release keystore. A different signer does not install
  over what is already on the phone, and Android's message for that says almost nothing useful.
- **REL-19** `versionCode` is the same across `beta.1`, `beta.2` and the stable, because neither the patch number nor
  the hotfix digit has changed. That is fine: Android refuses only a *lower* versionCode, and `SoftwareUpdate.isNewer` orders by the
  version name, so `beta.1 < beta.2 < x.y.z`.
- **REL-20 MUST** ship a beta first for anything that changes how Folio updates, installs or checks a supporter
  code, and let it sit with supporters for at least a day. Those paths cannot be tested any other way.
- **REL-21 SHOULD** say in the beta's notes what is worth trying, most-likely-broken first, and ask for the device,
  the screen and the version from Settings › What's New.

### What goes out beside a build

- **REL-22 MUST** publish the APK's SHA-256 next to the download, in whatever message carries the link.
- **REL-23 MUST** run each release through VirusTotal, link the report, and say up front that a sideloaded launcher
  holding `REQUEST_INSTALL_PACKAGES` usually collects a heuristic flag or two from small engines.
- **REL-24 MUST** point at [PERMISSIONS.md](../../PERMISSIONS.md), and say when a release asks for something the
  last one didn't.
- **REL-25 MUST** use McCal's own words for anything supporters or users read: release notes, Ko-fi posts, replies
  to whoever reported the bug.
- **REL-26 SHOULD** keep a runbook per release in `docs/releases/`, written before the build, not after.

### Dependencies

- **REL-27 MUST NOT** take a dependency bump that reaches the APK into a release already carrying a lot. Test-only
  dependencies (`baselineprofile`, `androidTest`) are safe whenever, because they never ship.
- **REL-28 MUST** bump the Android Gradle plugins together. `com.android.application` and `com.android.library` are
  pinned in the root `build.gradle.kts` and want the same version.

## Where Folio is today

- `main` is protected by CI (build, tests, lint) and every merge is a squash, so the history reads one change per
  line. Good.
- `versionCode` has been derived from `folioVersion` since 0.6.0 (`app/build.gradle.kts:102`), and `RoadmapTest`
  already fails when the app's version has no roadmap section. Good.
- `SoftwareUpdate.isNewer` (`SoftwareUpdate.kt:222`) implements semver ordering including pre-releases, with a test.
  Good.
- **0.6.7 broke REL-4, REL-7 and REL-8 at once**: `folio-beta-source` ran 12 commits ahead and 5 behind `main`,
  published `0.6.7-beta.2` from the branch, and grew its own `## [0.6.7]` section while a second section for the
  same version was written elsewhere. The fix was to merge the two rather than pick one ([#88](https://github.com/McCal-Codes/folio/pull/88)).
- Release runbooks exist for 0.6.5, 0.6.6, 0.6.7-beta.2 and 0.6.7, and they are genuinely written before the build.
  Good.
- REL-7, REL-10, REL-13 and REL-16 are checked by machine now: `tools/check-release-rules.sh` in CI, and guards at
  the top of `scripts/release-signed.sh`. Both name the rule they are enforcing in the failure, so the message is
  useful without opening this file.
- Several agent sessions work in parallel worktrees with no claim on shared files, which is how the same two
  strings, the same roadmap and the same changelog got edited three ways in two days.

## Gaps

| | What it takes | Size |
|---|---|---|
| 1 | A written claim on shared files for concurrent sessions, even just a `docs/in-flight.md` listing branch, files and session (REL-6) | S |
| 2 | Branch protection on `main` requiring the `release-rules` check, so nothing can be pushed straight to it and the check cannot be skipped by merging early | S |
| 3 | Betas published by CI from a tag, rather than by hand on the Mac, so REL-17 and REL-18 cannot be got wrong | M |
| 4 | A check that a pull request touching `themes/` or a Market package carries its AI-assisted label (AI-6), the same shape as the changelog check | S |
| 5 | The `versionCode` formula and REL-12's hotfix digit, with a test that `0.6.7.1` sorts above `0.6.7` in both `versionCode` and `isNewer` | S |
| 6 | A `Feature` gate helper, so REL-4a is one line at a feature's entry point rather than a scope check copied around | S |
| 7 | A check that lists every closed gate and how long it has been closed, so a finished feature cannot sit hidden and forgotten | S |

### Done

- REL-7 and REL-13 are enforced by `tools/check-release-rules.sh`, run as the `release-rules` job on every pull
  request. Waivable by labelling the pull request `no-changelog` or `release-exception`, so an exception is visible
  where the change is reviewed rather than in someone's shell.
- REL-10, REL-16 and a reproducibility check are enforced by `scripts/release-signed.sh`, which refuses to build when
  the tag already exists, when the changelog has no section for the version, when a **stable** version's section
  still says `Unreleased` (a beta may be built undated), or when the working tree is dirty. `FOLIO_SKIP_RELEASE_CHECKS=1`
  is the way out for a build that will never be published.
