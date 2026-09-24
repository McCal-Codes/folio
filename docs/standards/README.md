# Folio Standards

[architecture.md](../architecture.md) says how Folio works. These standards say how Folio should be built. When you
add or change anything, the change follows these rules, or it records why it doesn't.

They are written from the code as it is on 22 Sep 2026 (0.6.6), checked against current Android, Compose, Apple HIG
and WCAG guidance. Each standard has three parts:

1. **Rules**, numbered so a PR or review can cite them (`ADP-3`, `DYN-7`).
2. **Where Folio is today**: measured facts with file references, good and bad.
3. **Gaps**: what it takes to meet the rules, ranked, with a size (S / M / L).

## The governing principle

> **Folio adapts its behaviour without fragmenting its identity.**
>
> Folio is the same launcher on a flip cover, a phone, a foldable, a tablet and a resizable window. Layout may change
> a lot as available space, posture, input or system capability changes. The interaction model, the state, the words
> and the visual language stay the same.
>
> Behaviour may depend on a device only when it reflects a real capability or a verified hardware fact. Device names,
> manufacturers and model checks never stand in for measuring the window or detecting a capability.

Three smaller principles follow from it and show up throughout:

- **State decides, motion explains.** Meaningful state drives dynamic UI. Animation shows the change and never owns it.
- **Adaptive state decides the destination, motion is the journey.** Every layout is correct with animations off.
- **Everything works without root, without optional permissions and without the network.** Those only add.

## Normative words

As in [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119):

| Word | Meaning |
|---|---|
| **MUST** / **MUST NOT** | Required. Breaking it needs a recorded exception (below). |
| **SHOULD** / **SHOULD NOT** | Expected. You may deviate for a reason you can state in the PR. |
| **MAY** | Allowed, your call. |

A rule marked **(new code)** applies to new and rewritten code now, and to existing code as its Gap item is done.
Nobody has to fix the whole tree before shipping a feature.

## The standards

| Standard | Prefix | Owns |
|---|---|---|
| [Design](design.md) | `DES` | Visual language, tokens, components, where iOS inspiration ends |
| [Dynamic UI](dynamic-ui.md) | `DYN` | Live, animated and context-driven elements, motion tokens, the D1 to D5 classes |
| [Interaction](interaction.md) | `INT` | Gestures, priority, cancellation, haptics, keyboard and pointer |
| [Adaptive layout](adaptive-layout.md) | `ADP` | Window size, folds and hinges, insets, multi-window |
| [Compose](compose.md) | `CMP` | How Folio's Kotlin and Compose code is written |
| [State and data](state-data.md) | `STA` | Who owns each piece of state, persistence, migrations |
| [Performance](performance.md) | `PRF` | Critical journeys, budgets, measuring |
| [Accessibility](accessibility.md) | `A11Y` | TalkBack, targets, contrast, text size, Reduce Motion, RTL |
| [Privacy and permissions](privacy-permissions.md) | `PRV` | Access, network, data, the System Bridge tier |
| [Testing](testing.md) | `TST` | What gets tested where, and what "done" means |
| [Releases](releases.md) | `REL` | Branches, the changelog, version numbers, betas, and what goes out beside a build |
| [Market](market.md) | `MKT` | Bridges to other repositories, what a package page may contain, editorial |

Where standards overlap, the more specific one wins: Dynamic UI over Design for motion, Accessibility over Design for
contrast and size, Privacy over everything for what leaves the phone, and Privacy over Market for anything a package
or a bridge can reach. Releases owns anything about how a change reaches a phone.

## What "Folio quality" means

A change is Folio quality when:

1. It works on every window in the support envelope ([ADP](adaptive-layout.md)), folded and unfolded, both
   orientations, with a hinge half open.
2. It uses Folio's tokens and components, so it looks like it was always there ([DES](design.md)).
3. Its state has one owner, and it survives rotation, folding and process death where that matters ([STA](state-data.md)).
4. Its motion can be interrupted and it is correct with animations off ([DYN](dynamic-ui.md)).
5. TalkBack can reach and operate it, and it holds up at 200% text ([A11Y](accessibility.md)).
6. It works when optional access is refused and explains access where it asks ([PRV](privacy-permissions.md)).
7. It has a test that fails without it, where a test is possible ([TST](testing.md)).
8. UI changes were shown as a Mockup Lab scene or screenshots before they were committed.

## Exceptions

Breaking a MUST is sometimes right: a vendor bug, a platform gap, an audited workaround. When you do:

- Put a comment at the site starting `Standards exception (ADP-2):` with the reason and the condition for removing it.
- If it shapes more than one file or is meant to last, write an ADR in [docs/adr](../adr/README.md).
- List it in the standard's **Recorded exceptions** section.

`CameraArea.kt`'s SM-F971 camera rectangle and `DiscoverBounds.kt`'s Window Extensions 8 to 10 guard are the model:
narrow, versioned, and they fall back to normal behaviour.

## Changing a standard

Standards change by PR like code. A change that loosens a MUST, or adds a new one, also gets an ADR. Keep "Where
Folio is today" current when a Gap item lands, the same way the update map is kept current.

Read this file, then only the standards your change touches. Cite rule IDs in commit messages and PR descriptions when
a rule shaped the change ("keeps ADP-1: branches on window width, not model"). Don't mark work done until the
standard's checklist is met, and report any rule you couldn't meet instead of skipping it quietly.

## Sources

- Android: [adaptive apps](https://developer.android.com/develop/ui/compose/layouts/adaptive),
  [window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes),
  [foldables](https://developer.android.com/develop/ui/compose/layouts/adaptive/foldables/make-your-app-fold-aware),
  [app architecture](https://developer.android.com/topic/architecture),
  [Compose performance](https://developer.android.com/develop/ui/compose/performance),
  [core app quality](https://developer.android.com/docs/quality-guidelines/core-app-quality),
  [large-screen quality](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality).
- Apple: [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines) (motion,
  accessibility, layout, materials).
- W3C: [WCAG 2.2](https://www.w3.org/TR/WCAG22/) for contrast and target size.
- [Laws of UX](https://lawsofux.com), as applied in the 17 to 19 Sep 2026 review.
