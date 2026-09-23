# 0007: One trunk, two audiences, gates instead of branches

- **Status:** proposed, 2026-09-23

## Context

Folio has two audiences with different appetites. Supporters want new features early, which is what the `beta` scope
in `BetaCodes.kt` was built for ("new features a release or two early"). Everyone else wants a launcher that does not
change under them, and wants reported bugs fixed without waiting for whatever else is half built.

The obvious shape, a stable branch and a beta branch, is the shape that just failed. `release/0.7.0` ran 74 commits
ahead and 24 behind `main` before it was abandoned and renamed 0.6.6. Then on 23 Sep 2026 two branches held two
different meanings of **0.6.7** for a day: `folio-beta-source` had built and published `0.6.7-beta.2` to supporters
with four features in it, while `main` gained seven bug fixes and claimed the same number. Because
`SoftwareUpdate.isNewer` orders `0.6.7-beta.2 < 0.6.7`, the fix-only build would have been offered to every supporter
as an update that silently removed what they were testing. It was caught by reading the branches, not by any check.

Two long-lived lines also mean every fix exists twice, in two slightly different forms, and the two lines drift until
merging them is a day's work nobody scheduled.

A second, smaller problem made the first one worse. `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH` leaves no
number between two releases, so `0.6.6.1` could not exist and the seven fixes had to take the number the feature
release wanted. That renumbering is what forced the collision into the open.

## Decision

**One trunk. One build. The split between audiences is a gate in the build, not a branch in git.**

- **`main` is the only branch anything is built from** (already [REL-4](../standards/releases.md)). No line, channel
  or release lives on a branch of its own.
- **A new feature lands on `main` behind the `beta` scope.** A supporter sees it the day it merges, which is as early
  as it can be seen at all. Everyone else does not see it yet, so `main` stays shippable as a stable release with
  unfinished work inside it.
- **A beta is a tag, not a branch:** `vX.Y.Z-beta.N` cut from `main` when there is something worth testing. Betas are
  not on a calendar. What qualifies: anything that touches how Folio updates, installs or checks a supporter code
  (those paths cannot be tested any other way), and any feature a supporter would want to try.
- **A stable is the same tree with gates open.** When a feature is ready for everyone, one small pull request opens
  its gate and adds its changelog line. Nothing is ported, cherry-picked or rebuilt.
- **A fix goes out in the next beta immediately and the next stable.** One commit, one form, both audiences.
- **A hotfix branch is a last resort**, only when a breaking migration is genuinely mid-flight on `main`:
  `hotfix/x.y.z.n` from the tag, shipped and merged back the same day, never left to drift.

And to make the last two possible:

- **`versionCode` gets headroom:** `(MAJOR * 10000 + MINOR * 100 + PATCH) * 10 + HOTFIX`, so 0.6.7 is 6070 and a
  `0.6.7.1` on top of it is 6071. Nine slots per release. This replaces REL-12's ban on four-part versions, which was
  never a principle: it was a workaround for the old formula. `isNewer` already compares four numeric parts correctly,
  so the updater needs no change, and every new code is larger than the old one, so nothing installed breaks.

## Why the gate is the beta scope and not a new mechanism

Folio already ships one APK that shows different things to different people, checked offline, with no account:
`BetaCodes` carries scope bits, and `MarketFeature`, `Supporter`, `Dev` and Settings already read them. A feature gated
on `beta` is early access; opening the gate is what shipping means. Adding a second, parallel flag system to say "not
yet" would be two mechanisms for one idea.

The honest limit: the scopes are a thank-you, not a lock. Folio is open source, so anyone can build the app and see
everything. That is fine, and it is already written down in `BetaCodes.kt`. The gate exists so the *official* build
can hold a feature back, not to keep a secret.

## Consequences

- **Good:** a beta and a stable cut from the same commit are the same code. A fix cannot be in one and missing from
  the other, and there is no branch to drift or to merge.
- **Good:** supporters get a feature the day it lands rather than the release it ships in, which is the promise the
  `beta` scope already makes.
- **Good:** a fix release stops being blocked by unfinished features, because unfinished features are invisible.
  `main` being releasable becomes a fact rather than an aspiration.
- **Good:** hotfix numbers exist, so a fix never again has to consume the number a feature release wanted.
- **Bad:** a gate is a branch you have to remember to open. A feature can sit finished and hidden, and nothing
  currently notices. Worth a check that lists every gate and how long it has been closed.
- **Bad:** gated code ships to everyone, dead. Small for settings and screens, but a feature with its own background
  work has to be gated at the entry point, not just in the UI, or it costs everyone battery to do nothing.
- **Bad:** every existing version code changes shape once, at the release that adopts the new formula. Monotonic, so
  it is safe, but the numbers stop being readable as `MINOR * 100 + PATCH` at a glance.
- **Bad:** it puts more weight on the changelog's `Unreleased` section, since that is now the only record of what a
  stable will contain. [REL-7](../standards/releases.md)'s CI check is what keeps that honest.
