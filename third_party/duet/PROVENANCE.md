# Duet: where its code comes from

Duet's styles take code from four MIT projects (one of them through another). Only renderer math and a code shape came in: no services, overlays,
accessibility, screen capture, reflection, hidden APIs or network. Each is pinned to the commit it was read at.
Decision and alternatives: [ADR 0009](../../docs/adr/0009-duet-effect-engine.md).

| Upstream | Commit | License | Taken | Where in Folio | Changed |
|---|---|---|---|---|---|
| [Ant-lib/hingewave](https://github.com/Ant-lib/hingewave) | `06833f508354` | MIT, Copyright (c) 2026 Ant-lib ([LICENSE-hingewave](LICENSE-hingewave)) | The perspective projection (eye, tilted pane, rays that miss go black) and the blur floor from `android/.../render/FoldShader.kt` (`core/shader/hingewave.glsl`); the eye distance and blur floor defaults from `core/effect.json` | `app/src/main/java/com/mccal/folio/duet/DuetShader.kt`, `DuetStyles.DEEP` | Samples Folio's live screen through its existing 24-tap blur instead of hingewave's five-level still-picture pyramid; the band is Folio's moving half from `foldGeometry`; tilt comes from Folio's fold progress `m`, capped at 60° |
| [joeconsorti/duo-fold-live](https://github.com/joeconsorti/duo-fold-live) | `8ca4371bf4c8` (v3.5.2) | MIT, Copyright (c) 2026 Duo Fold Live contributors, with upstream notices ([LICENSE-duo-fold-live](LICENSE-duo-fold-live)) | Classic Glass: the eye-at-z=40 projection from `ClassicGlassShader.kt` (itself adapted from chuspeeism/iphone-duo, MIT); the inner 1.25× blur; the Fold-Only / Unfold-Only idea from `AnimationModePolicy.java` | `DuetShader.kt` (`classic`), `DuetStyles.CLASSIC`, `DuetDirection` | Runs on Folio's live screen instead of DFL's captured mip levels; no window reveal, stretch, seam blend, reflection or AA settings; tilt scaled by Duet's Tilt slider; direction comes from Folio's own fold timeline instead of DFL's angle anchor |
| [chuspeeism/iphone-duo](https://github.com/chuspeeism/iphone-duo) | read through Duo Fold Live at `8ca4371bf4c8` | MIT, Copyright (c) 2026 jadon7 ([LICENSE-iphone-duo](LICENSE-iphone-duo)) | The projection and shade model behind Classic Glass and the iPhone Duo style, as Duo Fold Live adapted it | `DuetShader.kt` (`classic`), `DuetStyles.IPHONE` | As for Duo Fold Live above |
| [iamkeeler/FoldFX](https://github.com/iamkeeler/FoldFX) | `d0128c14dfd5` | MIT, Copyright (c) 2026 Gary Keeler ([LICENSE-FoldFX](LICENSE-FoldFX)) | The catalog shape from `overlay/effects/EffectCatalog.kt`: one list that the picker and the renderer both read, so they can't disagree | `app/src/main/java/com/mccal/folio/duet/DuetOptions.kt` (`DuetStyles`) | Looks are numbers over one shader, not one class each, and the Market's `TweakOptions.DUET` is checked against the same list in `DuetTest` |

## Looked at, not taken

| Project | Why not |
|---|---|
| marcoazeem/duo-open (MIT) | Its shader, `duo_unfold.agsl`, is a port of Atomicx7/Duo-animation, which has no license. Ideas only: moving side, frost and darkening controls. |
| Atomicx7/Duo-animation | No license, so all rights are reserved. |
| ServerReset/duo-open | A fork of duo-open, same shader. |
| joeconsorti/duo-fold-live: the Samsung hinge angle over Shizuku (`AngleParser`), live cover preview, capture | Planned for 0.6.9 with Enhanced Fold Tracking, not in this change. |
| FoldFX's Book Fold, Fade and Page Turn effects | Canvas overlays drawn over the screen; Folio's shader already does the fold, and Minimal covers the fade. |

## Updating

Read the upstream at a new commit, diff it against the one above, and change this table in the same commit as the
code. Never copy from a moving branch.
