# Worked examples

Packages written out in full, to read beside [format-v1.md](../format-v1.md). Nothing here ships. The built-in
source is [`source/`](../source/), and a package only moves into it when the Folio version in `app/build.gradle.kts`
is at least the package's `minFolio`, which `FolioVersionTest` enforces.

| Example | Shows |
|---|---|
| [`wallpaper-wooded-hilly-landscape/`](wallpaper-wooded-hilly-landscape/) | A `wallpaper` package, and how one carries its artist and its license under [DES-2b](../../standards/design.md) |
