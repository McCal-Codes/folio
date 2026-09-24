# Folio Package Format v1

Status: **draft**, first shipped in Folio 0.6.6. Fields marked *(0.7.x)* are reserved: parsers accept them, but Folio doesn't act on them yet.

This page is the reference for packages and sources. The machine-readable versions are the JSON Schemas in
[`schema/v1/`](schema/v1/). Folio's parser and the `folio-pkg` tool are both tested against these schemas.

## Principles

- **Declarative first.** A package is data: JSON, images, and (optionally) a sandboxed script. Folio never downloads or loads DEX, JAR or native code. See [ADR 0004](../adr/0004-declarative-first.md).
- **Signed sources, verified files.** A source signs its entry file, the entry file pins the index's hash, and the index pins every package's hash. See [ADR 0001](../adr/0001-repo-format.md).
- **Permissions are the source of truth.** Everything a package can do maps to a permission it declares. Folio shows those permissions before install, builds the privacy label from them, and enforces them at runtime.
- **Local-first.** Folio only goes online for sources the user adds.

## Package: `.foliopkg`

A `.foliopkg` file is a zip archive:

```
manifest.json      required
depiction.json     optional, the package page
assets/            optional, png, webp or jpg
script.js          optional, only when kind includes "script"
```

Limits:
- **Size:** at most 20 MB compressed and 50 MB uncompressed.
- **Entries:** at most 500.
- **Names:** relative paths with `/` separators; no `..`, no absolute paths, no symlinks.
- **Rejected on sight:** anything that breaks these rules, plus files with extensions other than `.json`, `.png`, `.webp`, `.jpg`, `.jpeg` and `.js`.

### `manifest.json`

| Field | Type | Notes |
|---|---|---|
| `$schema` | string | Optional. `https://folio.mccal.dev/schema/v1/manifest.schema.json` gives editors autocomplete. |
| `format` | integer | Always `1`. |
| `id` | string | Reverse-DNS, lowercase: `dev.maya.sunset-icons`. Unique across all sources. |
| `name` | text | Display name, at most 40 characters. |
| `version` | string | dpkg ordering: `[epoch:]upstream[-revision]`; `~` sorts before release (`1.0~beta1` < `1.0`). |
| `author` | object | `{ "name": text, "url"?: https URL }`. |
| `minFolio` | string | Oldest Folio version it supports, e.g. `0.7.0`. |
| `section` | enum | `themes`, `tweaks`, `layouts`, `wallpapers`, `scripts`. |
| `kind` | array of enum | One or more of the kinds below. |
| `permissions` | array of enum | See [Permissions](#permissions). Empty means "appearance only". |
| `screens` | array of enum | `cover`, `inner`. Both if omitted. |
| `depends` | array of string | Package ids, optionally `id (>= 1.2)`. |
| `conflicts` | array of string | Same syntax. |
| `icon` | path | `assets/icons/cabinet.png`, square, at least 180 px. Resolved against the **source**, not the package (see Pictures). |
| `depiction` | path | Usually `depiction.json`. |
| `license` | string | SPDX id, e.g. `MIT`. GPL code isn't accepted in the Community source. A license with no SPDX id takes SPDX's own `LicenseRef-` form: `LicenseRef-Unsplash`, `LicenseRef-Pexels`. Public domain is `CC0-1.0` when the holder dedicated it and `CC-PDDC` for a work whose rights have lapsed. Required on a `wallpaper` package, which must also name the artist in `author` (see Wallpapers). |
| `description` | text | One or two sentences, shown under the name. |
| `aiAssisted` | object | Set when something helped make the package: `{ "tools": [1 to 5 tool names, 60 characters each], "note"?: text }`. The note says what it did. Folio 0.6.6 skips it; 0.6.7 shows it on the package page and the install sheet. |
| `provides` *(later)* | array of enum | `iconPack`, `wallpapers`, `widgets`, `folioTheme`: what an external app brings. |
| `via` *(later)* | array of object | Required with kind `externalApp`: `{ "store": "playStore"\|"fdroid", "id": … }` or `{ "store": "obtainium", "repoUrl": … }`. |
| `requires` | object | `{ "features": [capability ids] }`: the Folio capabilities this package configures. See [Capabilities](#capabilities). |

**Text values:** any *text* field is either a plain string, or an object of language tags with an `en` fallback:
`{ "en": "Sunset Icons", "es": "Iconos Atardecer" }`. Tags look like `en`, `es` or `pt-BR`, there are at most 64 of them, and
two tags that differ only in case are refused. Folio shows the exact tag if it has it, then the bare language, then any
regional variant of it, then `en`. Text is at most 400 characters, except `name` (40) and a `markdown` block (4,000).

**Where Folio records the origin:** `folio-source`, `file`, `play-icon-pack` or `launcher-import` is stored with the
installed package, not in the manifest. Authors never set it, and a manifest that includes it has that field skipped.

**Limits Folio enforces while reading:** `manifest.json` is at most 64 KB and `depiction.json` at most 256 KB; JSON nests
at most 32 levels; `depends` and `conflicts` list at most 32 entries each. Files must be strict JSON (RFC 8259):
comments, unquoted keys, single quotes, trailing commas, trailing text and duplicate keys are all refused.

**Kinds:**

| Kind | Payload | Applied through |
|---|---|---|
| `theme` | `theme.json` in the same format as `themes/*.json` (`"folioTheme": 1`) | the existing theme importer |
| `layoutPreset` | `layout.json`, a subset of `LayoutPreset` | the layout model, with undo |
| `wallpaper` | images in `assets/` | the wallpaper picker |
| `iconPackLink` | `iconpack.json`: `{ "format": 1, "package": "com.example.icons" }` | the ADW/Nova icon-pack lookup |
| `tweakBundle` | `tweaks.json`: built-in tweak ids and their options | `installTweak` and feature scopes |
| `settingsSchema` *(0.7.x)* | `settings.json` (see its schema) | Folio's settings renderer |
| `script` *(0.7.x)* | `script.js` | the script sandbox |
| `externalApp` *(later)* | a `via` list: `playStore`, `fdroid`, `obtainium` | Get opens the store, then Apply |

#### Wallpapers

A wallpaper package is **one piece of art**, so the fields a package already has are the credit Folio needs, and
nothing was added to the format for this:

| Field | Carries |
|---|---|
| `name` | The work's title, as its holding collection or its photographer writes it |
| `author` | The artist. `name` is the person, `url` links their page at the museum, on Unsplash, or their own site |
| `license` | The license the art is under, not the license of the package files. Required |
| `description` | The date and the holding collection, e.g. "1857. Brooklyn Museum." |
| `depiction` | The full credit: artist, title, date, collection, and the URL the file came from |

Folio shows the artist and the license wherever the wallpaper is offered, so a package missing either is refused
rather than listed without a credit. This is [DES-2b](../standards/design.md): credit is not a license, and an image
with no author has no license to give. A package that gathers several pictures by different artists cannot state one
artist or one license, so it is not a wallpaper package: ship one per artwork.

The image itself goes under `assets/` with an image extension. Folio picks the first image it finds there, so a
package with one picture needs no naming convention and a package with several is guessing.

### `depiction.json`

This is the package page. It's a list of blocks, and Folio draws every block with its own components.

```json
{
  "format": 1,
  "tint": "#D85A30",
  "blocks": [
    { "type": "hero", "image": "assets/hero.webp" },
    { "type": "screenshots", "images": ["assets/s1.webp", "assets/s2.webp"] },
    { "type": "markdown", "text": "Warm, rounded icons for **2,400** apps." },
    { "type": "featureList", "items": ["Themed icon fallback", "Cover and inner screens"] },
    { "type": "changelog", "entries": [{ "version": "1.2.0", "date": "2026-09-14", "notes": "180 new icons." }] },
    { "type": "link", "title": "Website", "url": "https://example.com" },
    { "type": "donation", "url": "https://ko-fi.com/example" }
  ]
}
```

Block types:
- `hero`
- `screenshots`
- `markdown`: a safe subset; no HTML or images. Blank lines separate paragraphs, lines starting `- ` are
  bullets, `**bold**` and `*italic*` are emphasis, and `[words](https://…)` is a link. Anything else -
  a heading, a table, an unclosed `*`, a link to anything but https - is drawn exactly as it was typed,
  so a stray marker shows up as a marker instead of swallowing a sentence.
- `featureList`
- `compatibility`
- `changelog`
- `link`: https only.
- `donation`: https only.

Folio adds its own rows for privacy, source, "Built from" and Report.

## Installing

Folio reads a package fully before anything on the Home screen changes, and applies it in one go:

1. **Size and checksum** against the index entry, before the file is opened.
2. **Open** the zip in memory. Nothing is written to disk, so a name inside a package can't reach the file system;
   names must be relative with no `..`, and only `.json`, `.png`, `.webp`, `.jpg`, `.jpeg` and `.js` files are kept.
   At most 500 files, 20 MB packed and 50 MB unpacked.
3. **Read** the manifest, the page and the payload for each kind.
4. **Check** that the id and version match what the source listed, that Folio has every capability the package needs,
   and that it doesn't replace something already installed.
5. **Apply** each change, keeping what it replaced. If one fails, the ones already applied are put back, so a package is
   never half applied.
6. **Record** what was applied, with the previous version kept until Undo is dismissed.

**Undo and Remove** walk the same list backwards, using what each change replaced. What a package changed is stored as
data, so both still work after a restart without the package file.

**If Folio crashes twice within a minute of a package being applied**, that package starts turned off, with its settings
kept. The store offers Try Again, Remove and Details, and everything else keeps working.

## What the store shows before you get a package

All of it comes from the manifest, never from anything the author wrote:

- **A one-line summary:** code (only a `script` package runs any, in the sandbox), network (never), personal data
  (never), and how many things it changes.
- **What it changes:** one line per permission, in Folio's words, from the table in [Permissions](#permissions). A
  package with no permissions says "How Folio looks, and nothing else".
- **What it can't reach:** your apps and their data, notifications, contacts and calendar, the network, and other apps'
  settings. Not promises: a package is data, so it has no way to reach any of them.
- **Where it came from:** built into Folio, or a source you added, with `provenance` and the checksum when the index
  carries them.

Nothing is applied until Get is pressed on that sheet.

## Source: static files

Host a source on any HTTPS server; GitHub Pages is the easy path. The layout:

```
key.pub             the source's public key (base64 SPKI), shown as a fingerprint when the source is added
entry.json          signed pointer to the index
entry.json.sig      detached signature over entry.json's exact bytes
index.json          repo info and package list
revoked.json        signed list of disabled packages (optional)
revoked.json.sig
packages/*.foliopkg
assets/             pictures the index, the manifests and the depictions point at
icon.png
```

### One id, two sources

A package id is its identity: settings, updates and Undo all hang off it, so the same id from two places is two
different things wearing one name. Folio never picks between them quietly.

- **A source using an id that belongs to a package inside Folio** is claiming to be that package. It stays listed,
  says so on its row, and cannot be installed.
- **Two sources the user added, using one id**, are both listed, and each says another source offers the name too.
  Which one they meant is theirs to decide.
- **Folio's own Featured banners only ever resolve to Folio's own packages**, so a source can't put itself in
  Folio's window by reusing an id.

### Who wrote it, and who is handing it over

These are two questions, and a source's signature only answers the second: "this list came from this source,
unchanged". An **author signature** answers the first, and travels with the package rather than the listing.

An index entry may carry one:

```json
{
  "id": "dev.maya.sunset-icons",
  "version": "1.2.0",
  "url": "packages/dev.maya.sunset-icons_1.2.0.foliopkg",
  "sha256": "…",
  "size": 148213,
  "signedBy": {
    "key": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE…",
    "signature": "MEUCIQ…"
  }
}
```

What is signed binds the package's identity to its bytes:

```
folio-pkg:<id>:<version>:<sha256 of the .foliopkg>
```

so a signature can't be moved onto another package, another version, or altered bytes. ECDSA P-256 with SHA-256,
base64 - the same as source signatures, and the same reader.

**Folio pins the key to the package id**, the way a phone pins an app's signing key. The first signed copy of
`dev.maya.sunset-icons` wins that name; after that:

| What arrives | What Folio does |
|---|---|
| Signed by the key it knows | Installs, and says **signed by its developer** |
| First signed copy ever seen | Installs, and remembers the key for that id |
| No signature, and none ever seen | Installs, and says **nobody signed this package** |
| Signed by a different key | **Refused** - somebody else already publishes under that name |
| Bytes don't match the signature | **Refused** - altered package, or altered listing |
| No signature, but signed copies were seen before | **Refused** - dropping the signature is how you'd get round it |

So a mirror can carry Sunset Icons and Folio still says **published by maya**; the same mirror cannot change a byte
of it, and cannot publish its own package under her name. Unsigned packages stay welcome - most first packages are -
and the store says so plainly rather than implying a guarantee it doesn't have.

#### A package shared as a file

A `.foliopkg` sent to someone directly has no index to carry a signature, so it carries its own, as
`signature.json` inside the archive:

```json
{
  "format": 1,
  "key": "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE…",
  "signature": "MEUCIQ…"
}
```

It can't sign the archive it lives in, and signing the zip's bytes would be worse than useless - rebuilding a zip
from the same files changes them. It signs the files:

```
folio-pkg-files:<id>:<version>:<sha256 of "<name> <sha256>\n" for every other file, sorted by name>
```

so changing any file in the package breaks it, while repacking the same files doesn't. The same pinning applies: a
file claiming an id that belongs to another key is refused whether it arrived from a source or through a share
sheet, and the confirm sheet says which of the two it is rather than assuming nobody signed it.

Both signatures are worth having. The one in the index means the store can say who wrote a package **before**
downloading it; the one inside the package means the file still proves itself when it arrives another way.

Signing is on the publisher's side: `tools/build.py --author-keys <folder>` in `folio-packages` signs each package
with `<folder>/<package-id>.pem`, inside the archive and in the index. An author key belongs to its author and
never goes in a repo.

### Pictures

Every picture a package names - its `icon`, and a depiction's `hero` and `screenshots` - is a **relative path on the
source's own host**, resolved against the source's address. `assets/icons/cabinet.png` on
`https://maya.example/folio/` is fetched from `https://maya.example/folio/assets/icons/cabinet.png`.

Two reasons it works that way rather than reading them out of the `.foliopkg`:

- the store shows a package's page **before** it is installed, and downloading a 20 MB package to show a thumbnail
  would be absurd;
- every byte then comes from the one host the user chose to trust, so listing a package can't make their phone call a
  third party. Folio fetches pictures through the same client as everything else: https only, size-capped, no cookies,
  and no redirect off https.

A publishing tool copies these files out of the package when it packs it, so an author keeps them in one place.
Folio's own source is inside the app, so it reads them straight from its assets and makes no request at all.

Folio's own source is in [`source/`](source/), with the app's real themes and tweaks; it's the worked example.

### `entry.json`

```json
{
  "format": 1,
  "keyId": "7F3A91C25B0ED418",
  "timestamp": 1789660320,
  "maxAge": 1209600,
  "index": { "path": "index.json", "sha256": "…", "size": 18342 }
}
```

The client accepts an entry only when all of these hold:
1. The signature verifies with the pinned key.
2. `timestamp` isn't older than the last one it accepted for this source (rollback protection).
3. `timestamp + maxAge` hasn't passed; `maxAge` is at most 30 days (freeze protection).
4. The downloaded index matches `size` and `sha256`.

Signatures are ECDSA P-256 with SHA-256, over the file's exact bytes, in base64 DER ([ADR 0002](../adr/0002-signing.md)).
The key is pinned the first time the source is added, and Folio won't follow a key change without asking.

**How often Folio asks:** a background refresh waits at least six hours and sends `If-None-Match`, so an unchanged
source answers 304 with no body. When the entry still pins the hash of the list Folio already has, the list isn't
downloaded at all. One refresh is at most five requests: `entry.json`, its signature, the index, `revoked.json` and its
signature. Folio never calls a repository API, which is what keeps it clear of GitHub's 60-requests-an-hour limit.

### `index.json`

```json
{
  "format": 1,
  "name": "Folio packages",
  "description": "Themes and tweaks reviewed by the Folio project.",
  "icon": "icon.png",
  "issuesUrl": "https://github.com/McCal-Codes/folio-packages/issues/new",
  "featured": [{ "package": "dev.maya.sunset-icons", "label": "Theme of the week" }],
  "packages": [
    {
      "id": "dev.maya.sunset-icons",
      "version": "1.2.0",
      "url": "packages/dev.maya.sunset-icons_1.2.0.foliopkg",
      "sha256": "…",
      "size": 1468211,
      "manifest": { "…": "copy of the package's manifest.json" },
      "provenance": { "repo": "maya/sunset-icons", "commit": "3f9c2a1", "workflow": "publish.yml" }
    }
  ]
}
```

- **Package URLs:** relative to the index, or absolute https (for example a GitHub Release asset URL). Folio never calls the GitHub API for each package.
- **`url`, `sha256` and `size` come together**, or not at all. A package built into Folio has none of them, because
  there's nothing to download; a hosted package needs all three, which `folio-pkg index` checks when it builds the list.
- **Consistency:** the manifest copy must match the manifest inside the downloaded file, or the install fails.

### `revoked.json`

```json
{ "format": 1, "timestamp": 1789660320, "packages": [{ "id": "dev.bad.pkg", "versions": ["*"], "reason": "Malware" }], "sources": [] }
```

It's signed the same way as `entry.json`. Revoked packages are turned off, and the user is told why. `"*"` in
`versions` means every version. An unsigned, broken or older revocation list is ignored rather than trusted, so a host
can't un-revoke something by serving an older list. A source can also be listed in a source the user already trusts,
which drops it before Folio calls it at all.

### Trust

- **Official key:** the Folio source's key is built into the app.
- **Other sources:** the key fingerprint is shown when the user adds the source, and then pinned. If a source's key changes, Folio stops trusting it until the user confirms.

## Links

| Link | Opens |
|---|---|
| `folio://source/<url-encoded https URL>` | the Add Source sheet, pre-filled |
| `folio://package/<id>` | the package page, if a source the user added lists it |
| a shared `.foliopkg` | the install sheet; unsigned files say "Unknown developer" |

## Capabilities

Every feature in Folio belongs to one of three classes:
- **Core:** Folio implements it (folders, dock, status, widgets, search, Focus).
- **Package:** a package configures a Core capability; Cabinet and themes are examples.
- **Integration:** it connects to something outside Folio (icon pack apps).

A package never adds behavior Folio doesn't already have. When you remove a package, the capability returns to its defaults.

`requires.features` lists the capabilities a package configures. Before installing, Folio checks that it has every one. If it doesn't, it shows "Needs a newer Folio" and doesn't install the package.

| Capability id | What Folio implements | Since |
|---|---|---|
| `theme` | Theme presets and theme files (`"folioTheme": 1`) | 0.4.0 |
| `home.layout` | Home grid, dock and layout presets | before 0.7.0 |
| `wallpaper` | Folio's wallpaper | 0.1.0 |
| `icons` | Icon style, shape and tint | before 0.7.0 |
| `icons.packs` | Icon packs found through the ADW/Nova/Lawnchair intents | by 0.4.0 |
| `tweaks.appPanels` | Cabinet (formerly App Panels) | 0.6.0 |
| `tweaks.dockMagnify` | Harborline (formerly Dock Magnification) | 0.6.0 |
| `tweaks.notificationAppRow` | Roll Call (formerly Notification App Row) | 0.6.0 |
| `tweaks.tintNotifications` | Palette (formerly Tinted Notifications) | 0.6.0 |
| `tweaks.tintMedia` | Colored Albums (formerly Album Art Colors) | 0.6.0 |
| `island.messages` | Messages in the Dynamic Island | 0.7.x |
| `focus.modes` | Switching Home Modes / Focus | 0.7.x |
| `settings.pages` | Settings pages drawn from `settings.json` | 0.7.x |
| `scripts` | The script sandbox | 0.7.x |

"Since" comes from CHANGELOG.md. "Before 0.7.0" means the feature exists today but the changelog doesn't record when it arrived. The 0.1.0 entry is inherited from DuoLauncher, and 0.7.x means planned.

**Packages never get Android permissions.** Folio holds its own permissions (for example Notification access). The permissions below are Folio's rules for what a package may change; they're what the privacy label lists.

## Permissions

| Permission | What it allows | Privacy label wording |
|---|---|---|
| `home.appearance` | Colors, materials, icon style | none (appearance only) |
| `home.layout` | Changing the Home grid and dock | Changes your Home layout |
| `icons` | Replacing app icons | none |
| `wallpaper` | Setting Folio's wallpaper | none |
| `tweaks` | Turning built-in tweaks on or off | Changes Folio tweaks |
| `island.messages` | Showing messages in the Dynamic Island | Shows island messages |
| `focus.switch` | Switching Home Modes / Focus | Switches Home Modes |
| `fold.state` | Reading whether the phone is folded | Reads fold state |
| `time` | Running on a schedule | Runs on a schedule |
| `apps.open` | Opening an app the user picked | Opens apps |

Scripts can only use actions whose permission they declare. New permissions come with a new format version.

## Versioning this format

- **Additive changes** (new optional fields, new kinds or block types) keep `format: 1`. Older Folio versions ignore what they don't know:
  - An unknown **field** is skipped, and the rest of the file is used.
  - An unknown **block** in a page is skipped, so the rest of the page still shows.
  - An unknown **kind, permission, section, screen or capability**, or a higher `format`, means the package needs a newer
    Folio. Folio won't install it rather than guess: those values decide what a package may change.
- **Breaking changes** use `format: 2`, with a new schema folder. Folio keeps reading v1.
- **Compatibility:** every v1 package in `market/src/test/resources/corpus/v1/` must keep installing in every future Folio version. They're real packed `.foliopkg` files, and `CompatibilityCorpusTest` installs, applies and removes each one on every run. Entries are added, never changed.
- **Folio's own source** (`source/`) is part of the test suite: its files are checked against these schemas and read by the parsers on every run.
