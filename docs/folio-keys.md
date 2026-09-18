# Folio Keys

A keyboard for the rest of Folio. **In design as of 2026-09-18 — no code exists.** This file is the part of that design
worth keeping in the repository: what has been decided, and the rules an implementation has to follow.

## What it is

A separate app, not a tweak: Android requires a keyboard to be its own input method service, so Folio Keys can't live
inside the launcher. What *can* be tweak-shaped is everything around it — themes, layouts and key-action sets, as the
same signed declarative Market packages Folio already uses.

It reads the window rather than the phone model: on a book fold it splits around the crease so no key sits on the
hinge, unfolded it splits with the middle holding what thumbs can't reach, and on a short window it gets out of the
way. It asks for no internet permission, which is the one promise the system can enforce on the user's behalf.

## Where the pieces are

| | |
|---|---|
| Mockup, 40 screens | `docs/mockups/lab/scenes/keys.js` — **not in git** (the lab folder is excluded) |
| Every screen at once | `docs/mockups/lab/keys-sheet.html` — not in git |
| Feature wall | `docs/mockups/lab/keys-wall.html` — not in git |
| Icon | `docs/mockups/lab/keys-icon.svg` (6b), published as `docs/sdk/source/assets/icons/folio-keys.png` |
| Market listing | `docs/sdk/source/packages/folio-keys/` — tracked |
| Supporter card and roadmap | on `main` (0.6.5): `SupporterSettings.kt`, `app/src/main/assets/roadmap.json` |

## Decided

- **Icon:** 6b, the keyboard bent at the crease, shallower fold.
- **Two key styles**, both checked at every window size: Folio (iOS) and Material (Google, using Material 3 baseline roles).
- **Gated for supporters** by the `keys` scope a Ko-fi code already carries (`BetaCodes.SCOPE_KEYS`). In 0.6.5 that
  shows a status card in Settings › Supporter; there is nothing to install.
- **Typing engine:** reuse AOSP LatinIME (Apache-2.0). HeliBoard is GPL-3.0, so study only; FUTO's licence rules it out.
- **Mistype tracking is a headline feature**, not a hidden one: the words you fix most, the keys you miss, and the offer
  to make a rule in the suggestion strip where the fixing happens.
- **Swearing** is never corrected into another word, and the filter is off by default.
- **Market listing** sets `minFolio: 0.8.0`, so no released Folio can pretend it is installable.

## Before any of it is written

The first move is not UI — it is a typing-engine spike: AOSP LatinIME's dictionary and suggestion engine behind a bare
QWERTY, typed on for a week. Everything distinctive here is comparatively easy and none of it rescues mediocre
correction. If the spike disappoints, Folio Keys becomes a theme and layout layer over an existing open keyboard
instead of a new one.

---

# Folio Keys — practices checklist

For the concept in `scenes/keys.js`. Each line says where the rule comes from, because that decides how much room
there is to disagree with it:

- **Platform** — an Android API or requirement. Not optional.
- **Convention** — what Gboard, SwiftKey, Samsung Keyboard, HeliBoard and FlorisBoard all do. Break it deliberately or not at all.
- **Folio** — our own choice, and the place where "better than Gboard" has to earn itself.

Anything marked **open** is not settled and should not be treated as decided. API levels are from memory and must be
checked against the docs before any of this is written.

## The service

| | Practice | Status in the mockup |
|---|---|---|
| Platform | `InputMethodService`, declared with `BIND_INPUT_METHOD` and an `input_method.xml` listing one subtype per language | n/a (no code) |
| Platform | A language is a **subtype with its own layout**, not a translated caption on a QWERTY key. RTL languages get RTL layouts | the lab skips the pseudo-language inside the key grid for exactly this reason |
| Platform | `onEvaluateFullscreenMode`: in a window too short for the app and the keyboard, go fullscreen with an extracted field | drawn — the keyboard falls back to the extract field under ~130 dp of room |
| Platform | `onComputeInsets`: a floating or one-handed keyboard must report its real touchable region, or it eats touches on the app behind it | **open** — nothing to show in a mockup, but it is the bug that makes floating keyboards feel broken |
| Platform | The globe key uses `switchToNextInputMethod`, and is only shown when `shouldOfferSwitchingToNextInputMethod` says so | drawn as always-present; **open** |
| Platform | `finishComposingText` when the field or app goes away | n/a |

## The window

| | Practice | Status |
|---|---|---|
| Platform | Draw inside the IME insets, and keep clear of the gesture bar and display cutouts | the board pads by the gesture inset and the side safe area; checked on every window |
| Convention | Keep the keyboard under about half the window | capped at 46% of a tall window, 55% of a short one, never over 360 dp |
| Folio | Shape comes from the window, never the model: split needs ≥600 dp, one-handed ≥348 dp so the rail fits, otherwise full | drawn, and checked at all 21 screen sizes |
| Folio | A book fold splits the keyboard around the crease — keys, strip, panels and the number pad all keep off the hinge | drawn; the checks fail the scene if anything lands on the fold |
| Convention | One-handed, floating, split and resize all exist, and one-handed carries a rail to move it back | drawn |

## The keys

| | Practice | Status |
|---|---|---|
| Platform | 24 dp is the hard floor for a touch target (WCAG 2.5.8); Android's own guidance is 48 dp | keys never go under 25 dp; the lab exempts the key grid from the 48 dp *warning* only, never from the 24 dp failure |
| Convention | Key labels are sized from key height (AOSP `keyLabelSize` is a percentage of the key), not from the system font scale | done — and *Appearance › Key text size* exists so it isn't a silent decision |
| Convention | Print the long-press alternate in the key's corner | drawn — number hints on the top row, the mapped action on gesture keys |
| Convention | Backspace: hold to repeat, swipe left to take a word | drawn in Gestures |
| Convention | Space: swipe to move the cursor | drawn, with Marker-style full-keyboard cursor as an option |
| Convention | The action key follows `imeOptions` — Go, Search, Send, Done | drawn per field type |
| Folio | Word keys shrink or become a glyph rather than overflow; the Android style always uses the enter glyph | done |
| Folio | Key gestures are per key and shown on the keycap, so nothing is hidden | drawn |

## The field decides

| | Practice | Status |
|---|---|---|
| Platform | `EditorInfo.inputType` picks the layout: email, URL, number, phone, password | drawn for email, website, number, password |
| Platform | In a password field: no learning, no suggestions, no autocorrect, no glide, no clipboard suggestions, no key preview | drawn, and an invariant fails the scene if suggestions ever appear there |
| Platform | Honour `IME_FLAG_NO_PERSONALIZED_LEARNING` whatever the user's own settings say | stated on the Privacy page |
| Platform | Inline autofill (Android 11+) — the keyboard shows the password manager's chips without seeing what is in them | drawn |
| Folio | Per-app profiles from `EditorInfo.packageName`, so they need no extra permission | drawn |

## Correcting

| | Practice | Status |
|---|---|---|
| Convention | Score a near miss by key distance, not letters alone; handle substitution, omission, insertion and transposition | stated in Typing › What it corrects with |
| Convention | Correct only when the candidate beats what was literally typed by a margin | **open** — the threshold is the whole game |
| Convention | Backspace right after a correction puts back what you typed, and the literal stays visible in the strip | drawn |
| Folio | The proximity model is built from the layout in use, so a custom or split layout corrects as well as QWERTY | stated; **open** until there is code |
| Folio | Learning is per profile and can be off for one app without being off everywhere | drawn on the Termux profile |

## Feel

| | Practice | Status |
|---|---|---|
| Platform | Haptics through `HapticFeedbackConstants.KEYBOARD_TAP` / `KEYBOARD_RELEASE`, and off when the system's touch vibration is off | *Appearance › Haptics* |
| Platform | Key sound through the system keypress effects, and off when the system's keyboard sound is off | **open** |
| Platform | Respect Reduce Motion: no popup animation when it's on | checked by the lab on every screen |
| Convention | Key preview on press, and never in a password field | *Appearance › Key preview on press* |

## Reachable

| | Practice | Status |
|---|---|---|
| Platform | Every key has a TalkBack name; two controls never read the same | checked on every screen |
| Platform | Contrast: AA for every label, in both themes and both key styles | checked on every screen, 280 windows × 2 styles |
| Platform | Nothing under 11 pt | checked |
| Folio | The settings pages follow the system font scale all the way to 200%, even though the keys don't | checked at 200% with the long-text pseudo-language and RTL |

## Trust

| | Practice | Status |
|---|---|---|
| Folio | No `INTERNET` permission. This is the one claim the OS can enforce for us | drawn on Privacy and on the turn-it-on screen |
| Folio | Clipboard history is local, expires on its own, and never takes from a password field | drawn |
| Folio | Incognito says so on the keyboard itself rather than expecting trust | drawn |
| Folio | Market packages are declarative: a theme, a layout, or actions from the fixed list of 19. No code, no network, no filesystem | drawn, and an invariant fails the scene if a screen names an action outside the list |

## Where "better than Gboard" actually is

Not autocorrect quality — that is years of data. It is the things Gboard has chosen not to do:

1. No internet permission at all.
2. Per-app profiles: theme, shelf, layout, gestures and learning, per app.
3. Per-key gestures, shown on the keycap.
4. An action shelf that is pinned and configurable instead of hidden behind one button.
5. A layout editor, and layouts as shareable signed packages.
6. Fold behaviour that reads the window and the crease.
7. One gesture engine arbitrating cursor, key actions, glide and shelf — the thing the jailbreak tweaks never had.
8. Everything above, checked at every window size before it ships.
