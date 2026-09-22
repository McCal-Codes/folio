# Contributing

Thanks for helping with Folio! Bug reports, ideas, fixes and themes are all welcome.

## Before you start

- **Bugs:** use Settings › **Report a Bug** in Folio or the [bug form](https://github.com/McCal-Codes/folio/issues/new/choose).
  Include the Folio version, phone, Android version, folded or unfolded, and the steps.
- **Bigger changes:** open an issue first for new features or changes to dock geometry, fold layout or Google integration.
- **Security problems:** report privately, see [SECURITY.md](SECURITY.md).
- **Themes:** see [themes/README.md](themes/README.md).
- Everyone follows the [Code of Conduct](CODE_OF_CONDUCT.md).

## Trying the Market, without paying for it

The Market is held back for supporters until 0.7.0 ships. That is a head start, not a paywall, and it must never
get in the way of helping:

- **Every theme and tweak the Market hands out is already in Settings.** Nothing that has shipped moves behind a
  code, ever. Without the store you are missing the store, not a feature.
- **A build you make yourself always has it.** Any Folio Dev build shows the Market with no code at all - that is
  what `MarketFeature.isDevBuild` means, and there is a test holding it open:

  ```bash
  ./gradlew :app:assembleFast   # installs as "Folio Dev", beside a release Folio
  ```

- **If you would rather not build it, ask.** Codes are free for anyone testing or contributing, and there is
  nothing to prove first. Open an issue or say so on an existing one.
- When 0.7.0 is released the store is everyone's, and the gate stops existing.

## Making changes

Keep changes focused. In the PR, describe what's different for someone using Folio, and how you checked it.

Read the [code map](docs/architecture.md) for ownership, persistence and gesture constraints, and the
[Folio Standards](docs/standards/README.md) for the rules a change follows (cite rule IDs like `ADP-1` in the PR). The [user guide](docs/user-guide.md)
and [troubleshooting guide](docs/troubleshooting.md) describe the behavior changes should preserve.

Folio builds with Java 17:

```bash
./gradlew :app:testDebugUnitTest :market:testDebugUnitTest :app:lintDebug :market:lintDebug :app:assembleDebug
```

CI runs the same command on every pull request. Use disposable emulators for instrumentation tests. Fixtures that change
Home selection, profiles, widgets or settings must restore them; never use a personal phone as a fixture.

Preserve one-page-per-swipe behavior, native widget scrolling and long-press pickup, placements, widget bindings and
Home-page retention. Keep access optional and explain it where it's used. Layouts follow screen size, not device
checks. Tests should reproduce failures or protect meaningful behavior.

## Using AI tools

If an AI agent helps with your contribution, it may only **test for and fix bugs**: reproduce a problem, write a test
that shows it, and make the smallest fix. New features, UI changes, refactors and translations need a person. Say in the
pull request which tool you used and what it did. Themes, tweaks and Market packages made with AI are welcome,
labelled "AI-assisted". The full rules are in [AI contributions](docs/standards/ai-contributions.md).

## Translating Folio

Folio's text lives in `app/src/main/res/values/strings.xml`. Everything in that file can be translated; text still
written into Kotlin can't, and moving more of it across is work in progress — see the update map.

To add a language:

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`, where `<code>` is the
   BCP 47 tag Android uses: `values-fr`, `values-pt-rBR`, `values-ar`.
2. Translate the text between the tags. Leave the `name` attributes alone — they're how the app finds each line.
3. Keep `%1$s`, `%1$d` and `\n` exactly as they appear; they are values Folio fills in and line breaks.
4. Keep apostrophes escaped as `\'`, or the build fails.
5. Leave product names as they are: Folio, Ko-fi, Dynamic Island, Spotlight, Control Center, Notification Center.
6. Don't translate a line you aren't sure about — leave it out and English is used for it.

The build picks the language up on its own: AGP generates the locale list from the `values-*` folders, so a new
folder is all it takes for Folio to appear in Android 13's per-app language picker (Settings › Apps › Folio ›
Language).

Right-to-left languages are welcome, but Folio hasn't been checked in RTL yet, so expect to find layout problems —
please report them rather than working around them in the translation.

## What not to commit

Signing keys, passwords, SDK paths, personal layouts, account information, or copied application code. No GPL code and
nothing that needs root. Check screenshots for notifications, contacts and account names.

Contributions use the project's MIT license. Keep notices for third-party material and say where it came from.
