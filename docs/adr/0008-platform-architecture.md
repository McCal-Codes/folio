# 0008: Folio is a launcher first, then an extension platform, then an optional system bridge

- **Status:** proposed, 2026-09-28

## Context

Folio's features have been built one at a time: each tweak, the Dynamic Island, the Side Bar, StandBy, Market
packages and the fold animation each carry their own settings, their own notification handling and their own idea of
what the phone allows. That worked for a launcher. It does not work for what the Market is meant to become, where
packages from other people change Home, gestures and live information, and where some users will grant more access
(notification access, accessibility, Shizuku, root) than others.

Two things made the cost visible on 28 Sep 2026. Folio's backend audit found `LauncherState` at 116 fields and 407
`runCatching` calls that mostly swallow their failures, so a change anywhere touches everything and a failure anywhere
leaves no trace. And the list of ideas worth building (Icon Actions, Icon Stacks, Freeform Home, Smart Badges,
complications, Workspaces, Edge panels, control tiles, Notification Rules, Tweak Studio, Shizuku controls) would, built
the same way, become a dozen more separate systems.

Samsung's own direction points the same way: One UI 9 treats layouts, the cover screen, controls and continuity across
form factors as modular, adaptive surfaces, and Good Lock's most used modules are small, focused tools rather than one
large settings page.

## Decision

**Folio is built in four layers, each optional on top of the last: the launcher, the extension platform, a system
bridge the user chooses to grant, and a research lane for hooks.** Nothing in a lower layer depends on a higher one,
and every feature says which layer it needs.

### Six primitives in Folio's core

1. **Extension Runtime**: typed registries for actions, providers, renderers, surfaces and rules. Packages contribute
   entries; they never ship code (ADR 0004).
2. **Capability Broker**: knows what this phone, with the access this user granted, can actually do, and is the only
   path to anything beyond Folio's own screen.
3. **Activity Graph**: one model of live state (media, progress, calls, timers, notifications, device state) that
   every surface reads. The Dynamic Island becomes one renderer of it, not a subsystem.
4. **Adaptive Layout Engine**: one logical Home item with a presentation per context (folded, unfolded, landscape,
   desk posture, external display), so layouts move between screens deterministically.
5. **Snapshots and Safe Mode**: every change that alters Home is a transaction (snapshot, apply, health check, commit
   or roll back), and Safe Mode starts Folio with no packages, rules or elevated backends.
6. **Inspector**: an event log of what each gesture, rule, package and privileged action did, and why.

**The primitive belongs in Folio; the policy or presentation belongs in a package.** Gesture recognition is core, a
gesture mapping is a package. The page transform engine is core, a cylinder effect is a package. The complication
host is core, a GitHub complication is a package.

### Privilege tiers

| Tier | Backend | What it means |
|---|---|---|
| A0 | Standard Android (Home role, `LauncherApps`, `AppWidgetHost`, WindowManager) | Works for everyone |
| A1 | Notification access | Reads and manages notifications |
| A2 | Accessibility | Global gestures and actions |
| A3 | Shizuku (shell identity) | Selected elevated operations |
| A4 | Sui or root (root identity) | Root operations through the same broker |
| A5 | Hook module (Zygisk or LSPosed style) | Changes behavior inside other processes; a separate project, research only |
| X | None | Not promised |

A feature can use several tiers: Home gestures are A0, global gestures A2. Authority is not compatibility: root makes
an operation *allowed*, not *stable*, so each privileged operation is probed, executed, read back and can be rolled
back. Official LSPosed support currently ends at Android 14, so A5 is a research lane with no release number.

### Capabilities, not permissions

- Capabilities are semantic and scoped: `notification.metadata.read` is separate from `notification.content.read`,
  reads are separate from writes, and scopes name their targets (`package.state.write` for listed packages only).
- **Packages never inherit privilege.** A package asks the broker for an action by id. It never receives Shizuku, root,
  a shell, a raw Binder, the accessibility tree or raw notifications, and never learns which backend ran its request.
- Every privileged action lands in an audit ledger: when, which package, what, on what, through which backend, the
  result, and whether a person or a rule started it.
- Risk classes decide consent: **Observe** (grant once), **Reversible** (grant, log, Undo), **Disruptive**
  (confirmation or a scoped standing grant), **Destructive** (confirmation every time; never unattended).
- Backend loss (Shizuku stopped after a reboot, access revoked) is an ordinary state, shown in plain words, and rules
  that need it are skipped and logged rather than failing.

## Consequences

- **Order of work changes.** 0.6.9 becomes the Foundation release: the broker for A0 to A2 and the action registry,
  shipped together with Icon Actions as their first user, plus transactional apply, Safe Mode 2 and a basic Inspector.
  The Market opens to everyone (0.7.0) only once packages declare capabilities and can be quarantined. Shizuku comes
  after the broker, ledger and Inspector exist; root after that; hooks stay research.
- **Existing parts move into the primitives rather than being rewritten:** Market Safe Mode and Undo become the
  transaction system, Layout History becomes snapshots, the Diagnostics trail becomes the Inspector's store, the
  island's planned `FolioLiveEvent` becomes the Activity Graph, and the planned `FoldAngleProvider` becomes the
  Adaptive engine's hinge provider (public `TYPE_HINGE_ANGLE` first, a Samsung adapter only where measured).
- **Layouts change format once.** Columns, Edit Layout on Home, Icon Stacks, Freeform Home and layout profiles all
  build on one "Layout model v2" migration instead of one schema bump each.
- **Every feature gets a capability row** in the capability matrix before it is scheduled, with the tier it needs and
  whether it was verified on a real phone.
- **Cost:** the Foundation release has less to show than a tweak release, which is why each primitive ships with a
  visible feature that uses it.
