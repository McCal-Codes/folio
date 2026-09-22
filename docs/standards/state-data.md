# State and data standard (`STA`)

Folio has many kinds of state: installed apps, layout, widgets, appearance, Focus modes, tweaks, Market packages,
system status, and a lot of short-lived interaction state. The rule that holds them together is **one owner per piece
of state**, with every change flowing through that owner.

## State map

Every new piece of state gets a row here (or in its feature's KDoc) before it's built.

| State | Owner | Persistence | Survives |
|---|---|---|---|
| Drag position, magnification, press | The composable doing it | None | Nothing; cancelled on any interruption |
| Open folder, open Settings page, search query | Screen UI (`rememberSaveable`) | Saved instance state | Rotation, fold, process death |
| Installed-app catalog | `LauncherModel` (from `PackageManager` / `LauncherApps`) | `app_catalog` prefs as a cache | Rebuilt from the system |
| Home layout, dock, folders, widget placements | `LauncherModel` | `launcher/state` JSON, schema 9, with migration backups | Everything; exported by Layout Backup |
| Widget bindings | Android (`AppWidgetHost`) + Folio's slot mapping | System + layout JSON | Reconnected on import, not copied |
| Appearance | `AppearanceStore` | `appearance` prefs | Everything; not in Layout Backup |
| Focus modes and rules | `FocusController` | `focus_rules` prefs | Everything |
| Market packages | Market repository | `filesDir/market` + records in layout JSON | Backup keeps records, reapplies changes |
| Hinge posture, window size | WindowManager (`LocalHinge`) | None | Recomputed |
| Battery, signal, ringer | `DeviceStatus` | None | Recomputed on start |
| Island activity | `Island` / `IslandEvents` | None | Recomputed |
| Crash reports | `CrashLog` | `filesDir/crashes`, last 5 | Until shared or rotated |

## Rules

### Ownership

- **STA-1 MUST** give every piece of state exactly one owner, and change it only through that owner. Other places
  read it or send events.
- **STA-2 MUST NOT** keep a second copy of durable state in a composable, a singleton or a second preference file.
- **STA-3 MUST** classify new state as transient, saveable, durable or system-backed, and put it in the table above.
- **STA-4 MUST NOT** let a missing app, a paused work profile or an unmounted package erase its placements. Missing is
  not deleted.

### Persistence

- **STA-5 MUST** version every saved format (`STATE_SCHEMA`, `LAYOUT_BACKUP_VERSION`, `"folioTheme": 1`) and migrate
  forward with a one-time backup of the old data before the first write.
- **STA-6 MUST** fail visibly: a layout that can't be read keeps a `state_damaged_backup` and tells the user, never
  silently resets.
- **STA-7 MUST** have a load test for each schema step (`LayoutLoadTest`) when the format changes.
- **STA-8 MUST NOT** load or parse large saved state on the main thread in new code.
- **STA-9 SHOULD** name preference files and keys as constants in one place per feature, not as repeated string
  literals.
- **STA-10 MAY** use `commit()` only where the process is about to die (crash, finish); comment why. Elsewhere use
  `apply()`.

### Backup and portability

- **STA-11 MUST** keep Layout Backup a portable description: widgets are reconnected, not copied; photos stay out;
  Market packages go in as records and reapply their changes.
- **STA-12 MUST** keep `android:allowBackup="false"` unless an ADR changes it; the user's own export is the backup.

### Identity

- **STA-13 MUST** include the Android profile in an app's identity, and use the address helpers in `HomeEditing.kt`
  for cells (negative indices are real addresses: the leading workspace and Discover).

## Where Folio is today

Good:

- A single `StateFlow<LauncherState>`, a JSON state with schema 9, one-time backups per migration step, a damaged
  backup, and `LayoutLoadTest`.
- Profile-aware identities; paused profiles keep placements.
- Layout Backup at version 3 with legacy migrations, Market records reapplied on restore.
- The `commit()` calls are all on process-death paths, each with its reason.

Not yet:

- 18 preference files, many named by string literal (`"folio"` 12 times), 68 `getSharedPreferences` calls in 30 files.
- The layout loads on the main thread during model init (`LauncherModel.kt:227-235`).
- `LauncherState` is one object with about 76 fields.
- Services reach the model through a global weak reference (`FolioTiles.kt:19`).

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | One `FolioPrefs` accessor with named files and keys | S |
| 2 | Load the layout off the main thread, with a placeholder frame (ties to the Doherty target in [PRF](performance.md)) | M |
| 3 | Consider DataStore for new settings (not a migration of the layout JSON, which works) | M |
| 4 | Split `LauncherState` (shared with CMP gap 1) | L |
| 5 | Load tests for schema < 2 and the `statusTop` fallback | S |
