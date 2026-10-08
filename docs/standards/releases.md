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
  two working days is too big: split it, or merge the finished part behind a gate that is closed (REL-4a). The limit
  was a week; on 6 Oct 2026 sixteen branches of one session were all more than a day old, three were stacked on each
  other, and `main` moved under them twice, which cost a day of rebasing that landing each one would not have.
- **REL-2a MUST NOT** hold more than five open topic branches at once in one session (a person, or an agent working
  in its own worktree). Three is the number the DORA research associates with teams that ship fast; five is the
  most this project can review. At the limit, land one, or park one (delete the branch after writing down what is
  left) before starting another. `bash tools/check-branches.sh` counts them and lists what has already landed.
- **REL-3 MUST** bring a topic branch up to date with `main` whenever `main` moves, not once at the end, and MUST
  NOT leave one behind `main` by ten commits or more: update it or close it. A branch that is both ahead and behind
  by double figures is a merge nobody can review. A stack of branches is at most two deep, and the lower one lands
  first.
- **REL-3a MAY** build a throwaway integration branch that merges several topic branches, to check they work
  together. It MUST NOT be built, tagged, published or handed out (REL-4), it is deleted once its topic branches land
  or after two days, and topic branches never wait for it: each lands on `main` on its own, behind its own gate.
- **REL-4 MUST NOT** build, tag or publish anything from a topic branch. If supporters need it, it goes to `main`
  first. There is no stable branch and no beta branch: one trunk, and the two audiences are separated by a gate in the
  build ([ADR 0007](../adr/0007-release-trains.md)).
- **REL-4a MUST** gate a feature that is not ready for everyone on the `beta` scope rather than holding it on a
  branch. A supporter sees it the day it merges; opening the gate is what shipping it means. Gate it at the entry
  point, not only in the UI, so hidden work costs nobody battery.
- **REL-4b** A stable release ships the same app as the beta before it, with gates open: the same code and resources
  under `app/` and `market/`, the same built-in Market source (`docs/sdk/source`, which the APK bundles) and the same
  Gradle setup, with only the version, the roadmap and the notes moved
  (REL-13). Nothing is ported, cherry-picked or rebuilt between the two. Docs, tools and tests may change in between,
  since nobody installs them. (Until 2 Oct 2026 this said "the same tree", which held a fix to a tool or a note back a
  whole release.)
- **REL-4c** A `hotfix/x.y.z.n` branch from a tag is the last resort, only when a breaking migration is mid-flight on
  `main`. It ships and merges back the same day.
- **REL-5 MUST** name branches after the change, never after the agent, the session or the tool. No AI attribution
  in branch names, commit messages, pull request text or code comments.
- **REL-6 MUST** check [docs/in-flight.md](../in-flight.md), the other worktrees and the open pull requests before
  editing a file more than one change is likely to touch, and add a row there when starting on one: `CHANGELOG.md`, `app/src/main/assets/roadmap.json`, `strings.xml`, `app/build.gradle.kts`.
  Several sessions work on Folio at once.
- **REL-6a SHOULD** run `bash tools/check-branches.sh` at the start and the end of a working session: at the start to
  see what is already open before adding to it, at the end to delete the branches and worktrees that have landed.

### The changelog

- **REL-7 MUST** keep the notes for the next release on `main`, in a `## [x.y.z] - Unreleased` section, and add each
  change's line in the same pull request as the change.
- **REL-8 MUST NOT** accumulate release notes on a feature branch. Two branches each growing their own section for
  the same version is how a release ends up meaning two different things.
- **REL-9 MUST** write each line as what a person sees, not what the code does, and punctuate without em dashes.
  One line per user-visible change; invisible work gets no line.
- **REL-10 MUST** date the section in the version-bump commit, not before. `Unreleased` is the truth until then.

### Versions

- **REL-11 MUST** follow semver in `folioVersion`, and derive `versionCode` from it rather than writing it by hand,
  with REL-12's formula. Every new code must be larger than the last, whatever the formula was when the last one
  shipped.
- **REL-12** A fix on top of a release is a four-part version, `x.y.z.n`, and `versionCode` leaves nine slots per
  release for them: `(MAJOR * 10000 + MINOR * 100 + PATCH) * 10 + HOTFIX` ([ADR 0007](../adr/0007-release-trains.md)).
  A fix release never has to consume the number a feature release wanted, which is what went wrong on 23 Sep 2026.
- **REL-13 MUST** make the version bump its own commit, the last one before the tag, touching only the version, the
  changelog date, the roadmap's statuses for that release and its release doc.
- **REL-14 MUST** give every release a section in `app/src/main/assets/roadmap.json` matching its version, so
  `RoadmapTest` passes and Settings › Roadmap agrees with What's New. A stable's own section has no item still
  Building or Planned: each is marked done, or moved to the release it now belongs to, in the bump
  (`tools/check-release-rules.sh`, REL-14b). A section may carry an optional `subtitle`, its theme.
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
- **REL-29 MUST** treat a layout change as invalidating published pictures. When a change alters something visible
  in a screenshot that is already out (the site, `~/dev/folio-marketing`, a Ko-fi post, a store listing), list those
  pictures in the release's runbook and re-take them **when that release ships**, not when the change merges.
  Published pictures document what people can download, and the repository runs ahead of that.
- **REL-30 MUST** take a published screenshot from the build being released, or from the Mockup Lab matching it.
  The lab follows `main`, so a lab render is only fit to publish once `main`'s behaviour is the behaviour people have.


### Dependencies

- **REL-31 MUST** keep the roadmap's marks in step with the tags. A release section whose items are all `done` names a release that has its stable tag (except the stable the same pull request is bumping, whose tag follows the merge), and an item marked `beta` says which beta it is in (`"inBeta": N`, ignored by the app) and that beta's tag exists, so a feature meant for beta.2 cannot be called "in beta" while only beta.1 is out. Every install reads `roadmap.json` from `main`, so "done" for a release nobody can install is a promise the page cannot keep: shipped means tagged, not merged. `tools/check-release-rules.sh` checks it whenever a pull request changes the roadmap; the `release-exception` label waives it.
- **REL-27 MUST NOT** take a dependency bump that reaches the APK into a release already carrying a lot. Test-only
  dependencies (`baselineprofile`, `androidTest`) are safe whenever, because they never ship.
- **REL-28 MUST** bump the Android Gradle plugins together. `com.android.application` and `com.android.library` are
  pinned in the root `build.gradle.kts` and want the same version.

## Where Folio is today

- `main` is protected by CI (build, tests, lint) and every merge is a squash, so the history reads one change per
  line. Good.
- `versionCode` carries REL-12's hotfix digit since 0.6.7 (`app/build.gradle.kts`), checked by `VersionCodeTest`
  against the packaged manifest rather than a copy of the arithmetic, and `RoadmapTest`
  already fails when the app's version has no roadmap section. Good.
- `SoftwareUpdate.isNewer` (`SoftwareUpdate.kt:222`) implements semver ordering including pre-releases, with a test.
  Good.
- **0.6.7 broke REL-4, REL-7 and REL-8 at once**: `folio-beta-source` ran 12 commits ahead and 5 behind `main`,
  published `0.6.7-beta.2` from the branch, and grew its own `## [0.6.7]` section while a second section for the
  same version was written elsewhere. The fix was to merge the two rather than pick one ([#88](https://github.com/McCal-Codes/folio/pull/88)).
- Release runbooks exist for 0.6.5, 0.6.6, 0.6.7-beta.2 and 0.6.7, and they are genuinely written before the build.
  Good.
- Nothing yet connects a layout change to the pictures it makes wrong, which is why REL-29 exists. The Market's pane
  rule (#72, `THREE_PANES_DP`) is on `main` and in no tag, so 0.6.6, which is what the site documents and what people
  download, still draws three panes on a Fold's inner screen. The site's Fold screenshot is a real capture from
  22 Sep and is correct until 0.6.7 ships, at which point it has to be re-taken.
- REL-4b (on the stable's bump), REL-5 (AI credit in commits, the branch name and the description), REL-7, REL-10, REL-13 and REL-16 are checked by machine now: `tools/check-release-rules.sh` in CI, and guards at
  the top of `scripts/release-signed.sh`. Both name the rule they are enforcing in the failure, so the message is
  useful without opening this file.
- One feature is gated today: the Market, shut since 19 Sep 2026, due to open in 0.7.0.
- `docs/in-flight.md` lists what each open branch is holding. It is a courtesy rather than a lock, and it only works
  if a row is deleted in the pull request that finishes the work.

## Gaps

| | What it takes | Size |
|---|---|---|
| 1 | ~~A written claim on shared files for concurrent sessions~~ (done: [docs/in-flight.md](../in-flight.md), named in REL-6) | S |
| 2 | Branch protection on `main` requiring the `release-rules` check, so nothing can be pushed straight to it and the check cannot be skipped by merging early | S |
| 3 | Betas published by CI from a tag, rather than by hand on the Mac, so REL-17 and REL-18 cannot be got wrong | M |

### Done

- REL-7 and REL-13 are enforced by `tools/check-release-rules.sh`, run as the `release-rules` job on every pull
  request. Waivable by labelling the pull request `no-changelog` or `release-exception`, so an exception is visible
  where the change is reviewed rather than in someone's shell.
- REL-12's hotfix digit is in the build, and `VersionCodeTest` checks the packaged manifest against the formula, that
  a fix sorts above its release and below the next one, that a pre-release shares its release's code, and that every
  code already published is below this build's.
- REL-4a's gates are `FeatureGate` (`FeatureGate.kt`), one entry per feature held back, each naming the day it was
  gated and the release it should open in. `FeatureGateTest` fails once that release arrives and the gate is still
  shut, so a finished feature cannot sit hidden and forgotten. The Market is the first entry, and
  `MarketAccess.isOpen` asks the gate now.
- REL-10, REL-16 and a reproducibility check are enforced by `scripts/release-signed.sh`, which refuses to build when
  the version's tag already exists on a different commit (building the tagged commit itself is fine), when the changelog has no section for the version, when a **stable** version's section
  still says `Unreleased` (a beta may be built undated), or when the working tree is dirty. `FOLIO_SKIP_RELEASE_CHECKS=1`
  is the way out for a build that will never be published.
