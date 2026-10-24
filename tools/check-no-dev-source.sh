#!/usr/bin/env bash
# Fails if a release APK carries any part of the Folio Dev debug source.
#
#   ./gradlew :app:assembleRelease && bash tools/check-no-dev-source.sh [path/to/app-release.apk]
#
# The debug source (src/devsource, T34 in docs/sdk/threat-model.md) belongs to the Folio Dev build types only. A release
# gets a same-named no-op from src/nodevsource, and R8 removes what is left. This looks inside the built file, which is the
# only proof that counts: a source-set mistake would not show in any unit test.
#
# It looks in the dex files for the real code's class names, the worker's /dev/ addresses, the publisher key's file name and
# the two public keys; in the resources for the dev page's strings; and for the build info the Folio Dev builds carry.
set -uo pipefail
cd "$(dirname "$0")/.."

apk="${1:-}"
if [ -z "$apk" ]; then
  apk=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | head -n 1)
fi
if [ -z "$apk" ] || [ ! -f "$apk" ]; then
  echo "No release APK to check. Build one first: ./gradlew :app:assembleRelease"
  exit 2
fi

# Split so this file does not contain the exact strings it searches for.
markers=(
  "DevSource""Ui" "DevSource""Keys" "DevSource""Page" "DevRout""ing" "DevInstall""Check"
  "/dev/""apk/" "/dev/""file/" "/dev/""sums/"
  "folio-dev-""publisher" "updates-""dev"
  "ESqpQhSJ""Cbk" "EUczgMGn""LEEX"
)
found=0

dex_list=$(unzip -Z1 "$apk" | grep -E '^classes[0-9]*\.dex$')
for dex in $dex_list; do
  for marker in "${markers[@]}"; do
    count=$(unzip -p "$apk" "$dex" | LC_ALL=C grep -a -c -F -- "$marker")
    if [ "${count:-0}" -gt 0 ]; then
      echo "$dex contains '$marker'"
      found=1
    fi
  done
done

if unzip -p "$apk" resources.arsc 2>/dev/null | LC_ALL=C grep -a -q -F -- "dev_source_"; then
  echo "resources.arsc has the dev source strings"
  found=1
fi
if unzip -p "$apk" resources.arsc 2>/dev/null | LC_ALL=C grep -a -q -F -- "Folio Dev builds"; then
  echo "resources.arsc has the dev source name"
  found=1
fi
if unzip -Z1 "$apk" | grep -E -q '(^|/)(dev-build\.json|builds\.json)$'; then
  echo "the APK carries dev build info"
  found=1
fi
if unzip -Z1 "$apk" | grep -q -i 'publisher.*\.pem$'; then
  echo "the APK carries a publisher key file"
  found=1
fi

if [ "$found" -eq 0 ]; then
  echo "No Folio Dev debug source in $apk."
else
  echo
  echo "The release build must not carry the debug source. Check app/build.gradle.kts: src/devsource belongs to the"
  echo "debug and fast source sets only, and src/nodevsource to release only."
  exit 1
fi
