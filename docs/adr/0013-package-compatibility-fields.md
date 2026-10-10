# 0013: Package compatibility fields for format 1, decided before the 0.7.0 format freeze

- **Status:** accepted by McCal, 2026-10-10: every field below and both changes from the Lab (access derived, not
  declared; later breakage from the source). The three open questions were answered the same day (see "Decided
  after acceptance"). The format, its checks and `folio-pkg` came first; the slot switch, acting on `incompatible`
  and the tier table come next.
- **Extends:** [0004](0004-declarative-first.md) (packages stay declarative) and [0008](0008-platform-architecture.md) (packages never inherit privilege).
- **Shown in:** the Lab scene `package-tiers` ("Package access and safety", folio-mockups b26ad84 and later).

## Context

On 10 Oct 2026 McCal placed the package fields drawn in the Lab before the 0.7.0 format freeze, even though the
profile packages that use most of them come after the Market opens to everyone (docs/plan-resolver-placement-2026-10-10.md).
The fields came from comparing the Market with jailbreak and root package managers (docs/research-access-safety-2026-10-10.md):
Debian's `Breaks`, MMRL's `minApi`/`maxApi`, KernelSU's one-metamodule rule, and the root tools' habit of saying what a
module was tested on.

What format 1 has today (docs/sdk/format-v1.md, `manifest.schema.json`, `PackageManifest.kt`): `minFolio`, `depends`,
`conflicts` (Debian syntax), `provides` (a fixed list), `requires.features` (capability ids), `screens`, and a signed
per-source `revoked.json`. A declared conflict shows as "Replaces X. Both can't be on at once. Remove X first."
(`CompatCheck.Replaces`), which blocks the install.

The rule that makes the freeze matter, from format-v1 "Versioning this format": an **unknown field is skipped** by an
older Folio, and the rest of the file is used. A field that *restricts* installs (an Android range, `breaks`) is
therefore ignored by every Folio that predates it, which would install exactly what the field was meant to stop. Adding
them in 0.7.0 means every 0.7.x and later honors them, and a package that uses one can set `minFolio` to 0.7.0 so
older Folio never sees it.

## Decision

Three homes, one rule each: **the manifest decides, the depiction describes, the source speaks for later.**
A field that changes whether or how a package installs goes in the manifest. Words for people go in the depiction. A
fact discovered after publishing goes in the source's signed files, because a published manifest can't change.

### In the manifest (machine-checked)

| Field | Type | Meaning | Folio does |
|---|---|---|---|
| `minSdk`, `maxSdk` | integer, Android API level | Android versions the package works on. | A **hard limit**. Outside it the package can't be installed, and it is the one case the list hides by default ("1 package is made for another Android version", with Show). No Folio update can change the phone's Android. |
| `maxFolio` | string, `x.y` or `x.y.z` | The newest Folio the author tried. `0.7` means any 0.7.x. | **Advice, never a block.** Past it the page says "Not tried on Folio 0.8 yet" and Get stays. Most packages keep working, and Package Safe Mode catches the rest. |
| `slot` | capability id | "Only one of these at a time", named after a capability the package configures (`tweaks.foldTransition`). Must be one of its own `requires.features`. | Get says first what it will turn off ("Turns off Duet"), then turns the other package in the same slot off. The other one stays installed with its settings. No more "Remove X first" for this case. |
| `breaks` | array of string, the `depends` syntax | Installed versions this package doesn't work with: `com.mccal.folio.harborline (<< 1.1)`. | If a fixed version exists in the user's sources, the sheet offers **Update and Get** (update first, then install). If none exists, Get waits. Folio never turns a package off to make room for one that breaks it. |

All four are optional. A manifest that uses any of them must have `minFolio` of at least `0.7.0`; `folio-pkg validate`
enforces that, so no older Folio installs a package while skipping its limits.

`conflicts` keeps its current meaning and wording. `slot` covers the common case (two packages that do the same job);
`conflicts` stays for the rare "these two can never both be on".

### Not a field: the access a package needs

The Lab draws an Access row (Standard, Notification access, Accessibility, Shizuku, Root). **Folio works it out; the
author never declares it.** Each capability in `requires.features` has a tier in the broker, and a package's tier is the
highest of its capabilities'. An author can't under-declare, and ADR 0008's "packages never inherit privilege" holds:
a package asks for capabilities by id, and the tier is a property of Folio's own code.

### In the depiction (words for people)

The existing `compatibility` block (`screens`, `notes`) gains two optional keys:

| Key | Type | Shown as |
|---|---|---|
| `testedOn` | array of `{ device: text, android: integer, folio: string, date: date }`, at most 10 | "Tested on a Galaxy Z Fold8 and a Pixel 9 by its author", inside Compatibility. Folio checks the format, not the claim. |
| `fallback` | text | What still works without the access it needs ("The fold animation keeps using the phone's normal hinge reading"). Required by `folio-pkg validate` when the derived tier is above Standard. |

Unknown keys in a block are skipped by older Folio, so these are safe at any time; they are listed here so the format
doc changes once.

### In the source (facts found later)

`revoked.json` gains an optional `incompatible` list beside `packages`:
`{ "id", "versions", "folio": "(>= 0.8)", "reason" }`. It is how an author or source says "this version doesn't work
with Folio 0.8" after publishing (the Lab's "Doesn't work with Folio 0.8", with Notify Me When It's Fixed). A matching
package can't be installed on that Folio; if it is already installed, it is turned off with its settings kept and the
reason shown, the way a revoked one is. Signed like the rest of the file, and a source speaks only for its own packages.

This replaces the Lab's `brokenOn` manifest field: a published manifest can't know which future Folio will break it.

## What changes, if accepted

- `manifest.schema.json`: four optional properties. `depiction.schema.json`: two keys in `compatibility`.
  `revoked.schema.json`: the `incompatible` list. All additive, `format` stays 1.
- `PackageManifest.kt`: the four names join `KNOWN` and are parsed; `CompatCheck` gains `Android` (BLOCKED),
  `FolioRange` (NOTE), `Slot` (NOTE, with the package it turns off), `Breaks` (BLOCKED, with the update that fixes
  it) and `Incompatible` (BLOCKED, from the source). The installer refuses on exactly the BLOCKED ones, as today.
- `tools/folio-pkg.py`: the `minFolio >= 0.7.0` rule; `slot` must be in `requires.features`; `minSdk <= maxSdk`, both
  between Folio's own `minSdk` (31) and a known API level; `fallback` required above Standard.
- `docs/sdk/format-v1.md`: the field table and a short "Limits and advice" section. The template repo shows them.
- `CompatibilityCorpusTest`: new corpus packages that use each field. Existing entries are never changed.

## Decided after acceptance (McCal, 2026-10-10)

- **`maxFolio` takes only `x.y`** (`0.7` means every 0.7.x): simpler, and it is how people talk about versions.
- **The tier table lives next to `Capability` in `:market`**, as package trust tiers. It is kept apart from the System
  Bridge broker's tiers (ADR 0011), which describe what the phone can grant: a package never inherits those (ADR 0008).
  If a package capability ever needs a broker capability (a root hinge angle, say), that is a separate, explicit
  requirement the app checks against the broker at install time.
- **A source's `incompatible` list covers only its own packages**, like `revoked.json` today.

## Consequences

- Everyone sees one calm line by Get ("Works on this phone", or the reason), and the detail sits in Information, as the
  Lab's Everyone view shows. Nothing here adds a screen.
- Authors get the jailbreak managers' tools (`Breaks`, a version range, a one-at-a-time rule) without the shell
  scripts and boot-time code that came with them: every field is checked data, in line with 0004.
- After 0.7.0 ships, any further restricting field needs either `format: 2` or the same `minFolio` rule against a
  newer release. Advisory fields and depiction keys can still be added at any time.

## Alternatives considered

- **`brokenOn` in the manifest** (as first drawn in the Lab): can't describe a Folio that didn't exist when the package
  was published. Moved to the source.
- **An author-declared `access` field:** authors could under-declare, and the broker would have to distrust it anyway.
  Derived instead.
- **`maxFolio` as a hard limit:** blocks packages that still work after every Folio update; launchers change often.
  Kept as advice, with known breakage handled by `incompatible`.
- **Waiting for format 2:** the restricting fields would arrive after 0.7.x packages exist, and every 0.7.x Folio would
  ignore them.
