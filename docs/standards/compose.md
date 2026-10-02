# Compose standard (`CMP`)

How Folio's Kotlin and Compose code is written. It follows Android's recommended architecture (UI and data layers,
unidirectional data flow, lifecycle-aware collection), sized for a one-person, one-module app rather than a large team.

## The shape

```
Activity / Service
      ↓ owns
LauncherModel (AndroidViewModel)  ←  system sources (packages, widgets, status, hinge)
      ↓ StateFlow<LauncherState>
screen composable        (collects once, lifecycle-aware)
      ↓ plain data + lambdas
stateless composables    (no model, no Context lookups for data)
      ↑ events (lambdas)
model functions          → persist, update state
```

## Rules

### Layers

- **CMP-1 MUST** keep durable state and its changes in the model layer (`LauncherModel` or a focused controller such
  as `WidgetController`), exposed as an immutable `StateFlow` ([STA-1](state-data.md)).
- **CMP-2 SHOULD** pass plain data and lambdas into composables, not `LauncherModel` itself, below the screen level
  (new code). A reusable composable must be callable from a test or preview with fake data.
- **CMP-3 MUST NOT** read `model.state.value` inside composition; collect it and pass it down.
- **CMP-4 SHOULD** keep Android framework calls (`PackageManager`, `getSharedPreferences`, intents) out of reusable
  presentation composables; do them in the model, a controller, or an effect at the screen level.
- **CMP-5 MUST** isolate vendor and version workarounds in one named place with a version guard and a fallback
  (`DiscoverBounds.kt` is the model), never scattered through screens.

### State in composition

- **CMP-6 MUST** hoist state that matters beyond one composable; keep only truly local UI state (a pressed flag, a
  text field draft) in `remember`.
- **CMP-7 MUST** use `rememberSaveable` for UI state the user would miss after rotation, fold or process death (an open
  folder, a Settings page, a search query).
- **CMP-8 MUST NOT** write to preferences, files or the model during composition. Writes happen in event handlers or
  effects.
- **CMP-9 MUST NOT** do I/O during composition, including an uncached `getSharedPreferences(...)` read; wrap it in
  `remember` or move it to the model.
- **CMP-10 SHOULD** derive rather than duplicate: compute presentation values from state (`derivedStateOf` for
  fast-changing inputs) instead of storing a second copy that can drift.
- **CMP-11 MUST** collect flows with `collectAsStateWithLifecycle` in UI (new code).

### Effects and threads

- **CMP-12 MUST** key effects on what they depend on, and use `rememberUpdatedState` for callbacks captured by
  long-lived effects.
- **CMP-13 MUST NOT** use `GlobalScope` or `runBlocking`. Hand-made scopes use a `SupervisorJob` and are cancelled
  with their owner.
- **CMP-14 MUST** do disk and network work on `Dispatchers.IO`, heavy computation on `Default`.
- **CMP-15 SHOULD** prefer coroutines over `Handler.postDelayed` in new code.

### Structure

- **CMP-16 SHOULD** keep composables small and named for what they show. A file over about 800 lines, or a
  composable over about 150, is a sign to split.
- **CMP-17 MUST** give lazy lists and pagers stable keys ([PRF-5](performance.md)).
- **CMP-18 SHOULD** mark UI state classes `@Immutable` when all their properties are, so Compose can skip them.
- **CMP-19 MUST NOT** add mutable global state (a top-level `var`, a singleton holding a model). Where a service needs
  the model, go through one documented bridge.

### Style

- **CMP-20 MUST** follow the Kotlin official style (`kotlin.code.style=official`) and the surrounding code's comment
  density and naming.
- **CMP-21 MUST** keep inherited `Duo*` names (`DuoApplication`, `Theme.Duo`); renaming them is churn.
- **CMP-22 SHOULD** log failures instead of swallowing them: a `runCatching` that drops the error sends it to
  `Diagnostics` or `CrashLog`.

## Where Folio is today

Good:

- One `AndroidViewModel` with a `StateFlow` (`LauncherModel.kt:293-307`), a 300 ms debounced save, I/O on
  `Dispatchers.IO`.
- No `GlobalScope`, no `runBlocking`, no writes found inside composable bodies.
- Pagers and most lists are keyed.
- Vendor workarounds are guarded (`DiscoverBounds`, `FoldBridgeActivity`).

Not yet:

- Size: `CustomizationSheet.kt` 2,251 lines, `LauncherScreen.kt` 1,596, `MarketScreen.kt` 1,556, `LauncherModel.kt`
  1,430. `LauncherScreen()` takes about 30 parameters, and the root collects about 76 state fields.
- `model.state.value` read in composition at `LauncherScreen.kt:207` and `:479`.
- Uncached prefs read in composition at `CustomizationSheet.kt:538`.
- Globals: `FolioSettingsBridge.liveModel` (`FolioTiles.kt:19`), plus mutable globals in `FolioActions.kt:35`,
  `CustomizationSheet.kt:52`, `Appearance.kt:75`.
- 26 `collectAsState()`; 4 `derivedStateOf`; 5 `@Stable`, 3 `@Immutable`; 0 `@Preview`.
- 25 `Handler(` and 11 `postDelayed`; about 308 `runCatching` with ~19 that log.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | Split `LauncherState` into Home, Appearance, Status and Settings slices so a status change doesn't recompose Home | L |
| 2 | Split `LauncherScreen()` and `CustomizationSheet.kt` by surface (one file per Settings page group) | L |
| 3 | ~~The `state.value` reads~~ (not a problem: both are in an event handler and an effect, where reading the current value is right). Left: the uncached prefs read in `CustomizationSheet.kt`, which is open in other pull requests | S |
| 4 | Document `FolioSettingsBridge` as the one bridge; remove or document the other mutable globals | M |
| 5 | ~~`.editorconfig`~~ (done). A formatter that enforces style is McCal's call: turned on today it would rewrite most of the codebase in one commit | S |
| 6 | Send swallowed failures to `Diagnostics` | M |
| 7 | Extract the pure layout code into `:core:layout` (it's already JVM-tested) | M |
