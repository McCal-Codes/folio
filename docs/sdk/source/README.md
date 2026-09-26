# The Folio source

This is Folio's own source: the themes and tweaks built into the app, written in the package format so the Market
treats them like anything else. It's real content, generated from the app's own `TweakFeatures` and theme presets, and
it doubles as the worked example for authors and as the parsers' test data.

```
index.json                 the package list, with a copy of every manifest
revoked.json               nothing is revoked; the list exists so the format is always there
assets/                    images the index and depictions point at
packages/<name>/
  manifest.json            what the package is and what it may change
  depiction.json           its page in the Market
  tweaks.json              for tweak packages: which built-in tweak to turn on
```

## What's here

| Package | Section | Configures |
|---|---|---|
| Cabinet | tweaks | `tweaks.appPanels` |
| Harborline | tweaks | `tweaks.dockMagnify` |
| Roll Call | tweaks | `tweaks.notificationAppRow` |
| Palette | tweaks | `tweaks.tintNotifications` |
| Colored Albums | tweaks | `tweaks.tintMedia` |
| Flipbook | tweaks | `tweaks.pageEffects` |
| Classic, Dark, Tinted and Clear themes | themes | `theme` |

These ship inside the app, so their index entries have no `url`, `sha256` or `size`: there's nothing to download.
A hosted source (the Community one, or your own) fills those in, and everything else is identical.

## What the publishing job adds

`entry.json`, `entry.json.sig`, `revoked.json.sig` and `key.pub` are produced when the source is published, so the
signing key never sits in this repository (Phase 8). Until then the files here are the content, not a signed source, and
Folio's built-in copy is used directly.

## Images

`assets/home-clear.webp` is a real capture of the Clear theme. The other packages have no screenshots yet: their pages
are text until there are real captures of each tweak, rather than a picture that doesn't show the feature.
