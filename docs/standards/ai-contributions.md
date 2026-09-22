# AI contributions standard (`AI`)

Folio welcomes help from people who use AI coding agents. This standard sets what those agents may do, and how
anything they make is labelled. It applies to every contribution from outside the maintainer: pull requests, issues,
themes, tweaks, Market packages, icon packs and wallpapers.

The short version:

> **Outside AI agents test for bugs and fix bugs. That's all.** Anything else an AI helps make (an add-on, a tweak,
> a theme, a package) is welcome in the Market, labelled as AI-assisted.

## Rules

### What an agent may do in this repository

- **AI-1 MUST** limit AI-agent work on Folio's code to:
  - **Bug testing:** reproducing a reported problem, writing a test that shows it, and filing a bug report with real
    reproduction steps.
  - **Bug fixing:** the smallest change that makes Folio behave as documented, with a test that failed before and
    passes after ([TST-2](testing.md)).
- **AI-2** A bug is Folio behaving differently from what the [user guide](../user-guide.md), the
  [changelog](../../CHANGELOG.md), these standards, or plain common sense (a crash, lost data, an unreachable control)
  says it should. A Gap item in a standard is not a bug unless it causes a problem someone can see.
- **AI-3 MUST NOT** use an AI agent for new features, UI or visual changes, redesigns, refactors, renames, dependency
  upgrades, translations, or rewriting docs. Open an issue describing the idea instead; the maintainer decides what
  gets built and how.
- **AI-4 MUST** keep one bug per pull request, with no unrelated clean-up in the diff.

### Disclosure

- **AI-5 MUST** say in the pull request or issue that an AI agent was used, name the tool, and say what it did
  (found the bug, wrote the test, wrote the fix). The pull request template has a checkbox for this.
- **AI-6 MUST** label anything an AI helped make that ships to users (a theme in `themes/`, a tweak, a Market package,
  an icon pack, a wallpaper, a layout preset) as **AI-assisted**, naming the tool:
  - a Market package sets `aiAssisted` in its manifest: `{ "tools": ["Claude"], "note": "Drafted the colours." }`
    ([format](../sdk/format-v1.md));
  - and also starts `description` with "AI-assisted (tool name).", because Folio 0.6.6 doesn't show the field
    (later versions show it on the package page and the install sheet; see Gaps);
  - a theme in `themes/` starts its description the same way;
  - the credits or README that travels with it says so too.
- **AI-7 MUST NOT** remove or reword an AI-assisted label when republishing, forking or updating someone else's
  package.
- **AI-8** An unlabelled contribution that turns out to be AI-made may be closed, delisted or removed from a source,
  whatever its quality.

### Responsibility

- **AI-9 MUST** have a person behind every contribution who has read the whole change, run it on a real device or an
  emulator, and can answer questions about it. "The agent wrote it" is not an answer in review.
- **AI-10 MUST** follow the same rules as everyone else: MIT licence, no GPL code, no copied application code, nothing
  that needs root, no secrets or personal data in the diff or screenshots ([CONTRIBUTING](../../CONTRIBUTING.md)).
- **AI-11 MUST NOT** run agents that open issues or pull requests on their own, file bugs nobody reproduced, or post
  review comments without a person reading them first.
- **AI-12 SHOULD** cite the rule IDs a fix restores ("fixes a DYN-15 breach: the stack kept rotating while hidden").

## Why

- Bug fixes have a clear right answer and a test that proves it. Features and design don't: they need the product's
  direction, which is the maintainer's call.
- A launcher sits under every app on the phone. Code that nobody fully understands is a bigger risk here than in most
  projects.
- People choosing a theme or tweak deserve to know how it was made, the same way they're told who made it.

## Checklist for a pull request that used an AI agent

- [ ] It fixes one bug, and says which
- [ ] It has a test that failed before the fix, or explains why a test isn't possible
- [ ] The PR names the tool and what it did
- [ ] I read every line and ran it myself
- [ ] No features, UI changes, refactors or translations

## Gaps

| # | Work | Size |
|---|---|---|
| 1 | Drop the `description` prefix rule once the release that shows `aiAssisted` (package page and install sheet) has replaced 0.6.6 on most phones | S |
| 2 | The same field in the community theme format (`themes/README.md`, `CommunityThemesTest`) | S |
| 3 | A "made with AI" filter in the Market | S |
