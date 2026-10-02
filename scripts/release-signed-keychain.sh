#!/usr/bin/env bash
# Signs a Folio release with the passwords kept in the macOS Keychain, so they are never typed into a shell, written in
# a file, left in shell history or shown to anyone. It sets the four FOLIO_RELEASE_* variables and runs
# scripts/release-signed.sh, so every check in that script (REL-10, REL-15, REL-16) still applies, and passes its
# arguments on to it.
#
# One-time setup, in your own terminal (each command asks for the password, hidden):
#   security add-generic-password -a folio -s folio-release-store -w
#   security add-generic-password -a folio -s folio-release-key -w
#
# Defaults, each overridable from the environment:
#   FOLIO_RELEASE_STORE_FILE  ~/folio-release.jks   (outside the repository; release-signed.sh refuses one inside it)
#   FOLIO_RELEASE_KEY_ALIAS   folio
#   FOLIO_KEYCHAIN_ACCOUNT    folio                  (the -a above)
#   FOLIO_KEYCHAIN_STORE_ITEM folio-release-store    (the -s of the keystore password)
#   FOLIO_KEYCHAIN_KEY_ITEM   folio-release-key      (the -s of the key password)
#
# Needs macOS (`security`). On CI the four variables come from encrypted secrets and release-signed.sh is run directly.
set -euo pipefail

if ! command -v security >/dev/null 2>&1; then
    echo "This helper reads the macOS Keychain, and \`security\` is not on this machine." >&2
    echo "Set the four FOLIO_RELEASE_* variables yourself and run scripts/release-signed.sh." >&2
    exit 1
fi

script_directory=$(cd "$(dirname "$0")" && pwd -P)
account=${FOLIO_KEYCHAIN_ACCOUNT:-folio}
store_item=${FOLIO_KEYCHAIN_STORE_ITEM:-folio-release-store}
key_item=${FOLIO_KEYCHAIN_KEY_ITEM:-folio-release-key}

# Reads one password. `security` exits 44 for an item that is not there, which is the usual failure, so say how to
# create it. Any other failure (a locked Keychain, a prompt you cancelled) is not a missing item, and re-adding one
# that exists would only fail, so it gets its own message. The value goes into a variable and nowhere else: it is
# never echoed, never put on a command line.
keychain_password() {
    local item=$1 value status=0
    value=$(security find-generic-password -a "$account" -s "$item" -w 2>/dev/null) || status=$?
    if [[ $status -eq 44 ]]; then
        echo "No Keychain item \"$item\" for account \"$account\". Create it (it asks for the password, hidden):" >&2
        echo "  security add-generic-password -a $account -s $item -w" >&2
        exit 1
    elif [[ $status -ne 0 || -z "$value" ]]; then
        echo "Could not read the Keychain item \"$item\" for account \"$account\" (exit $status, or an empty password)." >&2
        echo "Unlock the login Keychain, allow the prompt if it appears, or set the item again with \`security add-generic-password -U\`." >&2
        exit 1
    fi
    printf '%s' "$value"
}

# A trace would print the two assignments below with the passwords in them, so tracing stays off from here on.
set +x

FOLIO_RELEASE_STORE_FILE=${FOLIO_RELEASE_STORE_FILE:-$HOME/folio-release.jks}
# A quoted or exported "~/x" arrives with its tilde unexpanded.
FOLIO_RELEASE_STORE_FILE=${FOLIO_RELEASE_STORE_FILE/#\~/$HOME}
FOLIO_RELEASE_KEY_ALIAS=${FOLIO_RELEASE_KEY_ALIAS:-folio}
if [[ ! -f "$FOLIO_RELEASE_STORE_FILE" ]]; then
    echo "The release keystore is not at $FOLIO_RELEASE_STORE_FILE. Set FOLIO_RELEASE_STORE_FILE to where it is." >&2
    exit 1
fi
FOLIO_RELEASE_STORE_PASSWORD=$(keychain_password "$store_item")
FOLIO_RELEASE_KEY_PASSWORD=$(keychain_password "$key_item")
export FOLIO_RELEASE_STORE_FILE FOLIO_RELEASE_KEY_ALIAS FOLIO_RELEASE_STORE_PASSWORD FOLIO_RELEASE_KEY_PASSWORD

exec "$script_directory/release-signed.sh" "$@"
