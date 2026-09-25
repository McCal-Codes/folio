# Testing standard (`TST`)

Folio already separates pure rules (JVM tests) from real Android behaviour (emulator tests). This standard keeps that
split and says what "done" means.

## The pyramid

```
        real devices         (fold, hinge timing, 120 Hz frames, vendor quirks)
      emulator integration   (app/src/androidTest: widgets, profiles, Discover, shade, Back)
    Compose UI and render    (Robolectric + compose test rule, screenshots)
  JVM rules                  (app/src/test, market tests: layout, gestures, sizing, migrations, parsing)
```

Push each check as low as it can go. A placement rule belongs in a JVM test, not an emulator test.

## Rules

### What gets tested

- **TST-1 MUST** keep layout, gesture decisions, sizing, migrations, parsing and state-to-visual mappings as pure
  functions with JVM tests.
- **TST-2 MUST** turn a bug into a regression test that fails without the fix, where that's possible. Say in the PR
  when it isn't and why.
- **TST-3 MUST** add window cases to `ScreenCoverageTest` / `ScreenMatrixTest` for layout changes
  ([ADP-6](adaptive-layout.md)).
- **TST-4 MUST** add a load test for every saved-format change ([STA-7](state-data.md)).
- **TST-5 MUST** test native widget, profile, Back and Discover behaviour on an emulator (`androidTest`), since JVM
  tests can't see them.
- **TST-6 SHOULD** test dynamic elements with injected time, not real delays ([DYN-20](dynamic-ui.md)).
- **TST-7 SHOULD** fuzz any parser of imported data.

### How

- **TST-8 MUST** use disposable emulators for instrumentation. Fixtures that change Home selection, profiles, widgets
  or settings restore them. Never a personal phone.
- **TST-9 MUST** test local builds on the phone as Folio Dev (the `fast` build), never over the installed Folio, and
  restore any setting a test changed.
- **TST-10 MUST** run the unit tests, not just a build. When only data files changed (`CHANGELOG.md`,
  `roadmap.json`), run with `--rerun-tasks`: those files aren't Gradle inputs, so a cached green can hide a failure.
- **TST-11 MUST NOT** delete or weaken a failing test to make CI pass; fix the code or explain in the PR why the test
  was wrong.

### Definition of done

A change is done when all of these hold, and the PR shows the evidence:

1. `./gradlew :app:testDebugUnitTest :market:testDebugUnitTest :app:lintDebug :market:lintDebug :app:assembleDebug`
   passes (the CI command).
2. New behaviour has a test at the lowest level that can hold it.
3. UI changes were shown as a Mockup Lab scene or screenshots before commit, on cover and inner windows.
4. It was run on a device or emulator and the affected flow was exercised, with logcat free of new errors.
5. The checklists of the standards it touches are ticked.

## Where Folio is today

Good:

- 89 JVM test files (~476 tests), Robolectric in 10, Compose render tests in 3; `:market` has 144 tests with JUnit 5
  and Jazzer fuzzing.
- 41 `androidTest` files (~120 tests): Home drag, pager gestures, widgets (picker, move, resize, process death),
  layout backup, work profiles, Discover, the shade, folders, setup.
- CI on every PR and push: secrets check, unit tests and lint for both modules, a debug build; a weekly fuzz job;
  SHA-pinned actions.
- Coverage tests over thousands of windows.

Not yet:

- No screenshot tests; no accessibility checks in UI tests.
- Instrumented tests don't run in CI.
- No lint baseline or config; 148 warnings, most `UseKtx`.
- CONTRIBUTING.md's command omits the `:market` tasks that CI runs.

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | Roborazzi screenshots at Mockup Lab window sizes, with `enableAccessibilityChecks()` | M |
| 2 | A CI emulator job for the `androidTest` suite | M |
| 3 | A lint baseline with warnings as errors for new code. McCal's call: it changes what fails CI for every open pull request, so it wants a quiet moment rather than the middle of 0.6.7 | S |
| 4 | ~~CONTRIBUTING.md's command matches CI~~ (done) | S |
