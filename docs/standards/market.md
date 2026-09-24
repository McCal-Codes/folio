# Market standard (`MKT`)

The Market lists software Folio didn't write, from places Folio doesn't control. This standard is about saying so
honestly: what Folio checked, what it can't check, and who is vouching for what.

> **Folio lists other people's software without pretending to vouch for it.**

Security rules for packages live in [privacy and permissions](privacy-permissions.md) (PRV-13 to PRV-16). These are
the rules on top of them, for bridges to other repositories, for what a package's page may contain, and for editorial.

## Rules

### Bridges to other repositories

A bridge lets Folio show packages from a repository that isn't a Folio source (an F-Droid style index, for example).

- **MKT-1 MUST** keep a bridge read-only. Folio never re-signs, re-hosts, repackages or edits someone else's package.
  It shows what the repository published and sends the user to it.
- **MKT-2 MUST** name the origin wherever a bridged listing appears: the repository and its address, on the row and on
  the package page, in the same place a Folio source is named.
- **MKT-3 MUST** show the signer of a bridged APK, and **MUST** stop an update when the signer changes between
  versions. A changed signer is a different developer until the user says otherwise; it is never resolved silently.
- **MKT-4 MUST** pin a fingerprint per bridged repository on first use and refuse its content when it changes, until
  the user confirms the new one. This is [PRV-14](privacy-permissions.md)'s rule, extended: a bridge is a source Folio
  didn't sign, so the pin is the only continuity there is.
- **MKT-5 MUST NOT** dress a bridged listing as a Folio package: no Folio badge, no "verified", no borrowed styling
  that implies review. **MUST** say what Folio did check (size, checksum against what the index published) and what it
  cannot (the app itself, its source, its updates).
- **MKT-6 MUST** treat a bridged index as untrusted input ([PRV-15](privacy-permissions.md)): strict parsing, size
  caps, no remote code, and a fuzz target per parser.
- **MKT-7 MUST** write every bridge parser from the published format specification. No code, and no derived code, from
  a GPL client. F-Droid's client is GPL-3.0 and Folio is MIT; copying from it would relicense Folio.
- **MKT-8 MUST NOT** install a bridged app automatically. Android asks, and the source that listed it is what vouches
  for it: [PRV-17](privacy-permissions.md)'s tier rule already says Folio's own features work without it.

### What a package page may contain

A depiction is text and pictures an author wrote. Folio draws it; it never executes it.

- **MKT-9 MUST** keep depiction markdown to the subset `format-v1.md` promises authors: paragraphs, bold, italic,
  lists and links. **MUST NOT** render raw HTML, and **MUST NOT** load a remote image: every picture is a
  package-relative path, which `depiction.schema.json` already enforces by pattern (no leading slash, no `..`).
- **MKT-10 MUST** hold the caps the format states: 4,000 characters per markdown block, 400 for other text, 40 for a
  name, 256 KB for a depiction and 64 KB for a manifest.
- **MKT-11 MUST** render anything outside the subset as plain text rather than dropping it silently, so an author can
  see what Folio did with their words, and a reader is never shown less than was written.
- **MKT-12 MUST** validate the subset in `folio-pkg` as well as in the app, so a package fails at publishing time
  rather than on a phone.
- **MKT-13 MUST NOT** let a page claim a review Folio didn't do. Words like "verified", "official" or "signed by
  Folio" belong to Folio's own labels, built from the manifest and the signature, never from the author's text.

### Editorial

A story is Folio's own writing about packages: a collection, an interview, a what's new.

- **MKT-14 MUST** carry a byline and a date. Editorial is someone's opinion and says whose.
- **MKT-15 MUST** be marked as editorial wherever it sits beside listings, so a featured package reads as a choice
  rather than a ranking.
- **MKT-16 MUST NOT** sell placement. Supporter status, a tip or a Ko-fi membership never buys a story, a Featured
  slot or a position in a list. If that ever changes, the page says so where it is read.
- **MKT-17 MUST** use McCal's own words ([REL-25](releases.md)), the same as release notes and Ko-fi posts.
- **MKT-18 MUST** correct in place: a story that turns out to be wrong is edited with a note saying what changed, not
  quietly rewritten.

## Where Folio is today

- Folio's own sources are signed, their key is pinned on first use, and a changed key stops updates until the user
  agrees ([ADR 0001](../adr/0001-repo-format.md), [0002](../adr/0002-signing.md)). MKT-4 extends that shape to a
  repository Folio didn't sign.
- Packages are declarative, with no downloaded code ([ADR 0004](../adr/0004-declarative-first.md)), and `:market` has
  Jazzer fuzz targets for every parser plus a weekly CI job.
- `format-v1.md` already promises the markdown subset in words, and `depiction.schema.json` already constrains image
  paths. Nothing enforces the subset itself yet, in either the app or `folio-pkg`.
- Folio installs an APK only when the user turns that on, checks it against the checksum its source signed, and says
  plainly that it cannot vouch for the app itself (Settings › Market › Installing apps).
- No bridge exists yet, and no editorial exists yet. These rules are written before the code, which is the point.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | Enforce the markdown subset: a parser test in `:market` and a check in `folio-pkg`, both against the same list | M |
| 2 | A bridge adapter written from the published spec, with its own fuzz target and a pinned fingerprint per repository | L |
| 3 | Signer display and the signer-changed stop for bridged APKs | M |
| 4 | Editorial: byline, date, the editorial mark, and where corrections appear | M |
| 5 | Whether a story written with an AI's help must say so is McCal's call. The rule that covered AI-made packages was removed on 23 Sep ([#107](https://github.com/McCal-Codes/folio/pull/107)), so this would be a new rule, not an existing one. The manifest's `aiAssisted` field and the Market showing it are unaffected | S |
