# 0004: Declarative first, no downloaded executable code

- **Status:** accepted, 2026-09-17

## Context

Jailbreak tweaks inject native code into apps. Folio can't, and shouldn't:
- It has no root.
- Google Play's Device and Network Abuse policy bans downloading DEX, JAR or .so files.
- A bad tweak must never be able to break Home.

Users still want community themes, layouts, tweak settings and some automation.

## Decision

- **Packages are data:** themes, layout presets, wallpapers, icon-pack links, tweak bundles and settings pages. Folio applies them through its own model APIs.
- **Automation uses a sandboxed interpreter** (QuickJS or LuaJ; see ADR 0003). The script has no network, file or reflection access, can only return actions it declared permissions for, and runs under CPU and memory caps.
  *30 Sep 2026: not built. The `script` kind is reserved and refused; a script is code, a higher trust class than data,
  and would need process isolation and its own store label before it could ship. (ADR 0003, cited above, was never
  written.)*
- **Folio never loads downloaded DEX, JAR or native code.**
- **Separate-APK extensions over IPC are left for a later decision.**

## Consequences

- **Good:** Play-policy safe; a bad package can at worst misconfigure Folio, and Safe Mode plus Undo fix that. Permissions can be explained and enforced. Packages are small, can be reviewed, and can be diffed.
- **Bad:** tweaks can only do what Folio exposes. Each new capability needs a Folio release, a new permission and documentation.
