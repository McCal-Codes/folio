#!/usr/bin/env bash
# Builds a signed Folio release: dist/Folio-<version>/ with the APK, SHA256SUMS.txt, the signing certificate and
# the release's feature wall if there is one (FOLIO_WALL, or folio-marketing/walls/<version>/).
# Needs FOLIO_RELEASE_STORE_FILE (outside the repo), FOLIO_RELEASE_STORE_PASSWORD, FOLIO_RELEASE_KEY_ALIAS and
# FOLIO_RELEASE_KEY_PASSWORD in the environment. GitHub attaches the source code to every release on its own.
set -euo pipefail

repository_root=$(cd "$(dirname "$0")/.." && pwd -P)
version=$(sed -n 's/^val folioVersion = "\(.*\)"$/\1/p' "$repository_root/app/build.gradle.kts")
if [[ -z "$version" ]]; then
    echo "Could not read folioVersion from app/build.gradle.kts." >&2
    exit 1
fi
output_dir=${1:-"$repository_root/dist/Folio-$version"}

# REL-10, REL-15, REL-16 (docs/standards/releases.md): a version number means one build. Refuse to make a second one
# under a number that has already been published, and refuse to build a stable release whose notes still say they
# are unwritten. Set FOLIO_SKIP_RELEASE_CHECKS=1 to build anyway, for a test build that will never be published.
if [[ "${FOLIO_SKIP_RELEASE_CHECKS:-}" != 1 ]]; then
    if git -C "$repository_root" rev-parse --verify --quiet "refs/tags/v$version" >/dev/null; then
        echo "Tag v$version already exists, so this version has been built before." >&2
        echo "Move folioVersion on before building again (REL-16)." >&2
        exit 1
    fi
    released_version=${version%%-*}
    if ! grep -qE "^## \\[$released_version\\]" "$repository_root/CHANGELOG.md"; then
        echo "CHANGELOG.md has no section for $released_version. Write the notes before the build (REL-7)." >&2
        exit 1
    fi
    if [[ "$version" != *-* ]] && grep -qE "^## \\[$released_version\\] - Unreleased" "$repository_root/CHANGELOG.md"; then
        echo "CHANGELOG.md still says [$released_version] - Unreleased." >&2
        echo "A stable release is dated in the version-bump commit (REL-10). A beta may be built with it undated." >&2
        exit 1
    fi
    if [[ -n "$(git -C "$repository_root" status --porcelain)" ]]; then
        echo "The working tree has uncommitted changes, so nobody could rebuild this APK from a commit." >&2
        echo "Commit or stash them first, or set FOLIO_SKIP_RELEASE_CHECKS=1 for a build you will not publish." >&2
        exit 1
    fi
fi

for variable_name in FOLIO_RELEASE_STORE_FILE FOLIO_RELEASE_STORE_PASSWORD FOLIO_RELEASE_KEY_ALIAS FOLIO_RELEASE_KEY_PASSWORD; do
    if [[ -z "${!variable_name:-}" ]]; then
        echo "Missing required release signing variable: $variable_name" >&2
        exit 1
    fi
done
if [[ ! -f "$FOLIO_RELEASE_STORE_FILE" ]]; then
    echo "FOLIO_RELEASE_STORE_FILE does not point to a file." >&2
    exit 1
fi
store_directory=$(cd "$(dirname "$FOLIO_RELEASE_STORE_FILE")" && pwd -P)
store_file="$store_directory/$(basename "$FOLIO_RELEASE_STORE_FILE")"
case "$store_file" in
    "$repository_root"/*)
        echo "The release keystore must be stored outside the repository." >&2
        exit 1
        ;;
esac
if [[ "$output_dir" != /* ]]; then
    output_dir="$PWD/$output_dir"
fi
if [[ -e "$output_dir" ]]; then
    echo "Refusing to replace existing release output: $output_dir" >&2
    exit 1
fi

# apksigner ships with the Android SDK build tools; use the newest installed version.
sdk_dir=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z "$sdk_dir" && -f "$repository_root/local.properties" ]]; then
    sdk_dir=$(sed -n 's/^sdk\.dir=//p' "$repository_root/local.properties")
fi
apksigner=$(ls -d "$sdk_dir"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)
if [[ -z "$apksigner" ]]; then
    echo "apksigner not found. Set ANDROID_HOME to your Android SDK." >&2
    exit 1
fi

"$repository_root/scripts/gradle.sh" :app:assembleRelease
apk_source="$repository_root/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$apk_source" ]]; then
    echo "Signed release APK was not produced at the expected path." >&2
    exit 1
fi

output_parent=$(dirname "$output_dir")
mkdir -p "$output_parent"
staging_dir=$(mktemp -d "$output_parent/.folio-release.XXXXXX")
cleanup() { rm -rf "$staging_dir"; }
trap cleanup EXIT

package_dir="$staging_dir/$(basename "$output_dir")"
mkdir -p "$package_dir"
apk_name="Folio-$version.apk"
cp -p "$apk_source" "$package_dir/$apk_name"

# Fails if the APK isn't properly signed; the certificate digest lets people check updates come from the same key.
"$apksigner" verify --print-certs "$package_dir/$apk_name" > "$package_dir/signing-certificate.txt"

if command -v sha256sum >/dev/null 2>&1; then
    (cd "$package_dir" && sha256sum "$apk_name" > SHA256SUMS.txt)
else
    (cd "$package_dir" && shasum -a 256 "$apk_name" > SHA256SUMS.txt)
fi

# The release's feature wall, if one has been made. It travels with the build because REL-30 says a published
# picture comes from the release it describes, and because tools/announce-release.mjs uploads whatever asset here
# has "wall" in its name. Set FOLIO_WALL to point at one directly, or keep them in folio-marketing/walls/<version>/.
wall_source=${FOLIO_WALL:-}
if [[ -z "$wall_source" ]]; then
    walls_dir=${FOLIO_MARKETING_DIR:-"$repository_root/../folio-marketing"}/walls/$version
    if [[ -d "$walls_dir" ]]; then
        wall_source=$(find "$walls_dir" -maxdepth 1 -type f \
            \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \) | sort | head -1)
    fi
fi
if [[ -n "$wall_source" && -f "$wall_source" ]]; then
    wall_name="Folio-$version-wall.${wall_source##*.}"
    cp -p "$wall_source" "$package_dir/$wall_name"
    wall_bytes=$(wc -c < "$package_dir/$wall_name" | tr -d ' ')
    echo "Feature wall: $wall_name ($((wall_bytes / 1024)) KB), from $wall_source"
    # Discord takes 10 MB on an unboosted server, so a wall past 8 is one the release post will leave behind.
    if (( wall_bytes > 8 * 1024 * 1024 )); then
        echo "  Warning: over 8 MB, so the Discord announcement will post without it." >&2
    fi
else
    echo "No feature wall for $version, so the release will carry none (looked in ${FOLIO_WALL:-$walls_dir})."
fi

mv "$package_dir" "$output_dir"
trap - EXIT
rm -rf "$staging_dir"
echo "Signed release created at $output_dir"
grep "SHA-256" "$output_dir/signing-certificate.txt" || true
