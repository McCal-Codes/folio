# 0006: Folio Standards are the build contract

- **Status:** proposed, 2026-09-22

## Context

Folio's design and engineering rules were spread across the README, CONTRIBUTING.md, architecture.md, review notes
and code comments. Each new feature risked inventing its own spacing, springs, breakpoints or state handling: there
are about 1,630 inline `.dp` values, around 40 hand-picked springs beside `FolioMotion`, and breakpoints of 560, 600,
650, 700 and 920 written as bare numbers.

AI coding agents now write code for contributors too. Features and design need the product's direction, and a
launcher runs under every app on the phone, so code nobody fully understands is a bigger risk than usual.

## Decision

- **`docs/standards/` is the contract** for design and code: eleven standards with numbered MUST / SHOULD / MAY rules,
  each with the current state and the gaps.
- **Rules marked "(new code)" apply as code is written or rewritten.** Nobody has to fix the whole tree first.
- **Breaking a MUST needs a recorded exception:** a comment at the site, and an ADR if it lasts.
- **Outside AI agents only test for and fix bugs.** Anything else an AI helps make that ships to users (themes, tweaks,
  packages) is labelled AI-assisted.

## Consequences

- **Good:** reviews can cite a rule instead of a preference. New surfaces share one visual and motion vocabulary. The
  Gaps tables give a ranked list of clean-up work.
- **Bad:** the standards have to be kept current as code lands, or they turn into fiction. Some MUSTs (tokens, haptics,
  keyboard) describe code that doesn't exist yet, so early changes carry the cost of adding it.
- **Bad:** restricting AI agents turns away some well-meant feature work, which now has to start as an issue.
