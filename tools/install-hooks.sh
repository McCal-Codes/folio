#!/usr/bin/env bash
# Installs the pre-push check (tools/hooks/pre-push) for this clone and every worktree of it.
#
#   bash tools/install-hooks.sh            install; refuses to replace a pre-push hook you already have
#   bash tools/install-hooks.sh --force    replace it
#   bash tools/install-hooks.sh --remove   uninstall
#
# Git keeps hooks in the shared git directory, so this affects every session working in this clone: from the next push
# on, each one runs the release-rules and translation checks first. That is the point, and also why it is a step you
# take on purpose, not something a build does. The installed file is a two-line wrapper that runs the hook from
# whichever worktree is pushing, so a change to tools/hooks/pre-push takes effect without reinstalling.
set -euo pipefail
top=$(git rev-parse --show-toplevel)
common=$(git -C "$top" rev-parse --path-format=absolute --git-common-dir)
hook="$common/hooks/pre-push"
marker="# folio pre-push wrapper"

if [ "${1:-}" = "--remove" ]; then
    if [ -f "$hook" ] && grep -q "$marker" "$hook"; then rm "$hook"; echo "removed $hook"; else echo "no Folio pre-push hook installed"; fi
    exit 0
fi
if [ -e "$hook" ] && ! grep -q "$marker" "$hook" && [ "${1:-}" != "--force" ]; then
    echo "$hook already exists and is not ours. Use --force to replace it." >&2; exit 1
fi
mkdir -p "$common/hooks"
cat > "$hook" <<'EOF'
#!/usr/bin/env bash
# folio pre-push wrapper
top=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
[ -f "$top/tools/hooks/pre-push" ] && exec bash "$top/tools/hooks/pre-push" "$@"
exit 0
EOF
chmod +x "$hook"
echo "installed $hook"
echo "skip it once with FOLIO_SKIP_PREPUSH=1 git push; remove it with bash tools/install-hooks.sh --remove"
