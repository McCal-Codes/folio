# Privacy and permissions standard (`PRV`)

One of Folio's strongest decisions is already in [PERMISSIONS.md](../../PERMISSIONS.md) and
[PRIVACY.md](../../PRIVACY.md): access is optional, explained where it's used, and Home works with none of it. This
standard keeps every new capability to that.

## The questions every new capability answers

Put the answers in the PR and in PERMISSIONS.md before it ships:

1. Why does Folio need it?
2. Is it optional? What still works without it?
3. When is it asked for, and from which screen?
4. What happens if it's refused, or taken away later?
5. Does any data leave the phone? To whom, how often, and what's sent?
6. Can a narrower API do the job?

## Rules

### Access

- **PRV-1 MUST** keep Home working with every optional permission and special access refused.
- **PRV-2 MUST** ask in context, at the moment a feature needs it, from the screen that uses it, with a sentence
  saying why (the Spotlight contacts row and the calendar card are the model). **MUST NOT** ask at first launch for
  things the user hasn't touched.
- **PRV-3 MUST** handle a refusal and a later revocation gracefully: the feature explains itself and offers the way
  back, it doesn't crash or nag.
- **PRV-4 MUST** list every permission and special access in PERMISSIONS.md with the file that uses it and what
  happens without it, in the same PR that adds it.
- **PRV-5 MUST** use the narrowest API: coarse location over fine, a system picker over storage access, a query over
  `QUERY_ALL_PACKAGES`.
- **PRV-6 MUST** keep `<queries>` entries minimal and explained; package visibility decides what Folio can see.

### Data

- **PRV-7 MUST** keep user data on the phone. No analytics, no ads, no tracking, no accounts, no automatic crash
  upload.
- **PRV-8 MUST** only contact the network for something the user turned on or asked for (updates, the roadmap, Market
  sources, search), with a plain `User-Agent: Folio`, limited frequency and caching (ETags). Background refresh is off
  by default.
- **PRV-9 MUST** list every host Folio contacts in PRIVACY.md.
- **PRV-10 MUST NOT** persist notification content; hold it in memory only.
- **PRV-11 MUST** keep cleartext traffic blocked (`networkSecurityConfig`); only the dev variant may reach localhost.
- **PRV-12 MUST** let the user see and share diagnostics themselves; nothing is sent for them.

### Packages and code

- **PRV-13 MUST NOT** download or run executable code: no DEX, JAR or native libraries ([ADR 0004](../adr/0004-declarative-first.md)).
  Market packages are data applied through Folio's own APIs.
- **PRV-14 MUST** verify Market sources by signature and pinned key ([ADR 0001](../adr/0001-repo-format.md),
  [0002](../adr/0002-signing.md)).
- **PRV-15 MUST** treat imported files (themes, layouts, packages) as untrusted input: validate, bound sizes, and fuzz
  the parser.
- **PRV-16 MUST** accept widget pin and other system requests only from the system.

### Tiers

- **PRV-17 MUST** make every feature work without root. Root, Shizuku or ADB grants may only add optional extras, and
  only in the separate System Bridge tier, clearly labelled and off by default.
- **PRV-18 MUST** keep the accessibility service, notification listener and similar powerful services to what their
  PERMISSIONS.md row says, and explain them plainly, including why some banking apps react to accessibility services.

## Where Folio is today

Good:

- 14 manifest permissions, each in PERMISSIONS.md with its file and fallback.
- Runtime requests use `ActivityResultContracts.RequestPermission` from the screen that needs them; special access
  opens the matching Settings page; Settings lists each permission's purpose (`CustomizationSheet.kt:1136`).
- No analytics or crash SDKs; `CrashLog` is local (last 5 reports) and shared by hand.
- Network only for updates, the roadmap and added Market sources; cleartext blocked; `allowBackup="false"`.
- Market packages are declarative, signed and pinned; `:market` has Jazzer fuzz targets and a weekly fuzz job.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | ~~Fuzz `ThemeImportActivity`'s parser~~ (done: `ThemeImportFuzzTest`, 5,000 damaged files plus hostile ones; every answer is a drawable theme or nothing) | S |
| 2 | ~~`PinWidgetActivity`: it already takes only a request `LauncherApps.getPinItemRequest` returns and that reports itself valid. What is left is a forged request from another app carrying its own binder; the fix is to re-check the widget provider or shortcut against `AppWidgetManager` / `LauncherApps` before placing it. Security work, so not rushed into a small batch~~ (done: a widget is placed only once `AppWidgetManager` reports the accepted id bound to the provider the request named, a shortcut only once `LauncherApps` lists it as pinned, and a provider that isn't installed is turned away first; `PinTrustTest`) | M |
| 3 | ~~A plain note about banking apps and the accessibility service~~ (done, in PERMISSIONS.md) | S |
