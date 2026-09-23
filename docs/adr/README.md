# Architecture decision records

Short records of decisions that shape Folio, why they were made, and what they cost. New records get the next number, and old ones are never rewritten: a later record supersedes an earlier one.

| # | Decision | Status |
|---|---|---|
| [0001](0001-repo-format.md) | Market sources use a signed entry file that pins a hashed index (F-Droid style) | Accepted |
| [0002](0002-signing.md) | Sources are signed with ECDSA P-256 and their key is pinned on first use | Accepted |
| 0003 | Script engine (QuickJS or LuaJ) | Decided in Phase 9 |
| [0004](0004-declarative-first.md) | Packages are declarative first; no downloaded executable code | Accepted |
| [0005](0005-org-json.md) | Keep org.json for Market parsing | Accepted |
| [0006](0006-standards.md) | Folio Standards are the build contract; outside AI agents only test and fix bugs | Proposed |
| [0007](0007-release-trains.md) | One trunk, two audiences: features gated by the `beta` scope, betas are tags, and `versionCode` gets hotfix headroom | Proposed |
