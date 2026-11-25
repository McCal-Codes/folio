# In flight

Who is holding which files right now. Several sessions work on Folio at once, and the three-way edits to the
changelog, the roadmap and `strings.xml` that produced [the releases standard](standards/releases.md) all came from
nobody knowing what else was open (REL-6).

**Before editing a file more than one change is likely to touch, look here.** The list below is a courtesy, not a
lock: the real state is `gh pr list` and `git worktree list`. Keep it honest rather than complete.

## How to use it

- **Starting something that touches a shared file?** Add a row. Shared means `CHANGELOG.md`,
  `app/src/main/assets/roadmap.json`, `strings.xml`, `app/build.gradle.kts`, or a screen someone else is clearly in
  (`LauncherScreen.kt`, `CustomizationSheet.kt`, `MarketScreen.kt`, `LauncherModel.kt`, `MainActivity.kt`).
- **Finished, merged or abandoned?** Delete your row in that same pull request. A stale row is worse than no row.
- **Found a row that collides with your work?** Say so to that session before you start, or split the change so the
  overlap is one file rather than five.
- **A row is not a claim on a file.** Two changes to one file are fine when both know. What is not fine is finding
  out at merge time.

## Now

| Branch | What it changes | Files it holds |
|---|---|---|
| `simple-mode-combined` | Simple Mode preview behind the closed `SIMPLE_MODE` gate (beta scope sees it): the #372 and #431 designs in one, with people to call from the contact picker. Merge after the beta 2 build is cut so it ships in beta 3. | `CHANGELOG.md`, `strings.xml` (en, ko, zh), `SimpleHome.kt`, `SimpleSettings.kt`, `SimpleMode.kt`, this file |
| `release-070-beta2` | Bump to 0.7.0-beta.2: Dynamic Island app palette tint & rolling ticker, app shortcut menu palette tint, seamless unfolded two-page spacing, Nixie & Minimal StandBy clocks, interactive 4-card widget stack, dual-screen ambient lamp, left-screen widget stabilization. | `app/build.gradle.kts`, `CHANGELOG.md`, `docs/releases/0.7.0-beta.2.md`, this file |
| `icon-actions-picker` | Icon Actions picker for 0.7.0, behind `ICON_ACTIONS`: the catalog, the editor in Edit Icon and Settings › Gestures & Actions, menu rows, and the accessibility service description and PERMISSIONS.md paragraph (PRV-18). | `ActionCatalog.kt`, `IconActionsEditor.kt`, `IconActionsSettings.kt`, `IconEditor.kt`, `AppContextMenu.kt`, `LauncherScreen.kt`, `CustomizationSheet.kt`, `LauncherModel.kt` (`setAllIconActions`), `PERMISSIONS.md`, strings |
| cc-two-pane-editor | Control Center editor in two panes on wide windows (PR 664) | CHANGELOG.md, TopPanels.kt (ControlCenter's root and the gallery call only), CcControlGallery.kt, FeatureGate.kt, CcTwoPane.kt |
| beta4-cc-customize | Control Center and Home editing, Tips page, panel fold padding, crash reports (PR 656) | CHANGELOG.md, strings.xml (and values-b+ko, values-b+zh+Hans), app/build.gradle.kts, CustomizationSheet.kt, ControlCenterSettings.kt, FoldAvoidance.kt |
| better-bug-reports | Bug report form arrives with Screen picked and a short summary; Home state and Market packages in the full report; optional screenshots (PR not opened yet) | CHANGELOG.md, strings.xml (and values-b+ko, values-b+zh+Hans), what-to-test.json, BugReport.kt, Diagnostics.kt, HomeStateReport.kt, ReportAttach.kt, CustomizationSheet.kt (Help report dialog and Crash Reports card only), WhatToTestPage.kt, LauncherScreen.kt (one status hook), bug_report.yml |
| page-overview | Page overview: hide, show and reorder Home pages (PR 656) | CHANGELOG.md, strings.xml (and values-b+ko, values-b+zh+Hans), what-to-test.json, LauncherModel.kt, LauncherScreen.kt (dots, Edit menu, one overlay), LauncherActionSheet.kt, Focus.kt, new HomePages.kt and PageOverview.kt |

Dependabot's open bumps are left out: they touch only `gradle/libs` or `build.gradle.kts`, and
[REL-27](standards/releases.md) says when to take them.

## Worth knowing without a row

- **The Mockup Lab** (`docs/mockups/lab/`) is git-excluded from this repository and has its own private one,
  `folio-mockups`, cloned only inside the `folio-0.7.0` checkout. Commit lab work there the day it is made; its rules
  are in the lab's `STANDARDS.md` (`LAB-1` and on). The parity tests (`LabParityTest`, `LabSettingsParityTest`) skip
  unless Gradle's `folio.labDir` property points at the lab.
- **`docs/update-map.md`, `docs/plan.md` and `CLAUDE.md`** are excluded too, and have no history to fall back on: check
  the file times before you edit and back a file up first.
- **A squash merge changes patch ids**, so `git branch --merged` and `git cherry` both lie about whether a branch
  landed. Compare trees instead: `git merge-tree --write-tree origin/main <branch>` against `main`'s tree.
