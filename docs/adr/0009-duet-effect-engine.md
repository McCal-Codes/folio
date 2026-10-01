# 0009: Duet is the fold animation, and its looks are ported only from licensed code

- **Status:** proposed, 2026-09-28

## Context

Folio's fold animation lives in `FoldTransition.kt` behind a Settings switch. The roadmap already planned to turn it
into **Duet**, a tweak based on Duo Fold Live (H11 in the 0.6.8 plan). On 28 Sep 2026 McCal brought a proposal called
"Fold FX", a community package inspired by marcoazeem/duo-open, with presets such as Natural, Glass and Deep.

A dozen projects are doing the same effect. Most are standalone apps (accessibility overlays, Shizuku helpers, live
wallpapers). They were checked on 28 Sep 2026 for license, lineage and what Folio could actually use:

| Project | License | Decision |
|---|---|---|
| Ant-lib/hingewave | MIT | Port its perspective projection, blur floor and darkening model (own design doc, not derived from Atomicx7). |
| iamkeeler/FoldFX | MIT | Port its effect shape: one class per look plus one catalog both the picker and the renderer read. |
| joeconsorti/duo-fold-live | MIT | Port Classic Glass (its projection; its binomial blur ghosts on a live screen, so Folio's disk blur stands in) and the fold-only/unfold-only idea now; the Samsung wallpaper angle for every-degree tracking in 0.6.9. |
| marcoazeem/duo-open | MIT, but its shader is not | Ideas only (moving side, frost and darkening controls). Its AGSL is a port of Atomicx7/Duo-animation. |
| Atomicx7/Duo-animation | none | Avoid. No license means all rights reserved. |
| ServerReset/duo-open | MIT on a fork of the above | Avoid, same shader. |
| chuspeeism/iphone-duo | MIT | Already the reference for Folio's current look. |
| Folduo, Z-Fold-Duo-TEST, android-also-could-fold, DuoFoldWallpaper | MIT | Reference only; standalone apps that add nothing a launcher needs. |

## Decision

1. **Merge, don't fork a second tweak.** Fold FX is not a separate package. Its ideas become Duet's styles and options.
   The name is dropped because iamkeeler/FoldFX already uses it.
2. **Duet's looks are a catalog of `DuetStyle`s** in `com.mccal.folio.duet`: numbers over one shader (`DuetShader`),
   in one list (`DuetStyles.all`) that the picker, the renderer and the package validator all read. The launcher sees
   only `FoldTransitionHost`, so a look can be added, changed or removed without touching the launcher. Hingewave's
   and Duo Fold Live's projections are two modes of the shader; a look is not a class.
3. **Code is ported only from MIT sources pinned to a commit** and listed in `third_party/duet/PROVENANCE.md`, with an
   SPDX line and an upstream header in each file that carries ported code (`DuetShader.kt`). `DuetOptions.kt` takes
   only FoldFX's idea of one catalog, no code, and says so in its KDoc. Nothing is ported from a project whose shader lineage leads
   to Atomicx7.
4. **Only renderer math comes in.** No services, overlays, accessibility, capture, reflection, hidden APIs or network.
5. **Packages carry options, not code.** `tweaks.json` gains an optional `options` object per tweak, validated per
   tweak id and clamped on apply, and removing the package restores the previous options. The capability is
   `tweaks.foldTransition`.
6. **Existing installs keep today's animation.** Folio's earlier fold is the Duo style. A save from before Duet has no
   `duet` key and decodes to Duo; new installs start on iPhone Duo, which is the default for `DuetOptions()`.
7. **Options are for settings, a package kind is for files.** A tweak whose choice is a number or a name from a fixed
   list takes `options` (Duet). A tweak whose variants are things you add takes its own kind (Flipbook's `pageEffect`).
   Both are described in `docs/sdk/format-v1.md`.

## Consequences

- Hingewave's blur pyramid needs a still bitmap; Duet blurs live content through a `RenderEffect`, so the pyramid is
  not ported. Deep uses hingewave's projection with Folio's existing live blur.
- Every new style must pass the Fold8 120 Hz check in `docs/standards/performance.md` (PRF-12) before it ships.
- Adding a style is one class and one catalog entry, which is also the path for later community looks.
