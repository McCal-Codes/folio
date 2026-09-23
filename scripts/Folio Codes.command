#!/bin/sh
# Double-click this to open the supporter code admin page (scripts/code-admin.py).
#
# Put a copy wherever it suits: the Desktop, or the Dock (drag it to the right-hand side, next to the Trash).
# A copy anywhere still runs the script from this checkout, so it stays up to date with the repository.
#   ln -s ~/dev/duo-fold-launcher/scripts/"Folio Codes.command" ~/Desktop/
#
# macOS may ask once whether you're sure you want to open it, because it didn't come from the App Store.
cd "$(dirname "$0")/.." || exit 1
echo "Folio supporter codes"
echo "Ctrl-C, or close this window, to stop the page."
echo
python3 scripts/code-admin.py "$@"
status=$?
# A window that vanishes takes the reason with it: on a failure, hold it open until it's been read.
if [ $status -ne 0 ] && [ $status -ne 130 ]; then
  echo
  echo "The page stopped (exit $status). Press Return to close this window."
  read -r _
fi
