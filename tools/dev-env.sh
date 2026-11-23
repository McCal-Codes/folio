#!/usr/bin/env bash
# Sets up this shell to build and test Folio: a Java 17 JAVA_HOME, ANDROID_HOME, adb and the emulator on PATH, and
# (in Git Bash) MSYS_NO_PATHCONV so adb does not rewrite /sdcard-style paths. Nothing is installed or written.
#
#   source tools/dev-env.sh            # export the settings into this shell
#   bash tools/dev-env.sh --check      # print what was found; exit 1 if Java 17 or the SDK is missing
#
# An existing JAVA_HOME or ANDROID_HOME that works is kept. Otherwise it looks in the usual places, and for the SDK
# at sdk.dir in local.properties first.

_folio_is_sourced=0
(return 0 2>/dev/null) && _folio_is_sourced=1

_folio_java_major() {
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1
}

_folio_find_java() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ "$(_folio_java_major "$JAVA_HOME")" = 17 ]; then
        printf '%s' "$JAVA_HOME"; return
    fi
    local dir found=""
    # A glob that matches nothing stays literal and fails the -x test. The last match in each pattern wins, so a
    # newer 17.x update is chosen over an older one.
    for dir in \
        "/c/Program Files/Eclipse Adoptium"/jdk-17* "/c/Program Files/Java"/jdk-17* "/c/Program Files/Microsoft"/jdk-17* \
        /usr/lib/jvm/*17* /Library/Java/JavaVirtualMachines/*17*/Contents/Home; do
        if [ -x "$dir/bin/java" ] && [ "$(_folio_java_major "$dir")" = 17 ]; then
            found=$dir
        fi
    done
    [ -n "$found" ] && printf '%s' "$found"
}

_folio_find_sdk() {
    local top props dir
    for dir in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        [ -n "$dir" ] && [ -d "$dir/platform-tools" ] && { printf '%s' "$dir"; return; }
    done
    top=$(git rev-parse --show-toplevel 2>/dev/null)
    props="$top/local.properties"
    if [ -f "$props" ]; then
        # local.properties escapes backslashes and colons (C\:\\Users\\me\\Sdk); turn that back into a path.
        dir=$(sed -n 's/^sdk.dir=//p' "$props" | head -1 | sed 's/\\\\/\//g; s/\\:/:/g; s/\r$//')
        [ -d "$dir/platform-tools" ] && { printf '%s' "$dir"; return; }
    fi
    for dir in "${LOCALAPPDATA:-}/Android/Sdk" "$HOME/AppData/Local/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        [ -d "$dir/platform-tools" ] && { printf '%s' "$dir"; return; }
    done
}

_folio_java=$(_folio_find_java)
_folio_sdk=$(_folio_find_sdk)

# In Git Bash a path has two spellings: /c/Users/me (what PATH needs) and C:/Users/me (what Gradle and the SDK
# tools print). cygpath converts; elsewhere the path is used as is.
_folio_unix() { if command -v cygpath >/dev/null 2>&1; then cygpath -u "$1"; else printf '%s' "$1"; fi; }
_folio_mixed() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }

# Puts a directory first on PATH, even if it is already on it further down (the java of another JDK may be ahead).
_folio_front() {
    local d=$1 rest="" p IFS=:
    for p in $PATH; do [ "$p" = "$d" ] || rest="${rest:+$rest:}$p"; done
    PATH="$d${rest:+:$rest}"
}

if [ -n "$_folio_java" ]; then
    export JAVA_HOME="$(_folio_mixed "$_folio_java")"
    _folio_front "$(_folio_unix "$_folio_java")/bin"
    export PATH
fi
if [ -n "$_folio_sdk" ]; then
    export ANDROID_HOME="$(_folio_mixed "$_folio_sdk")" ANDROID_SDK_ROOT="$(_folio_mixed "$_folio_sdk")"
    _folio_su=$(_folio_unix "$_folio_sdk")
    for _folio_p in cmdline-tools/latest/bin emulator platform-tools; do
        [ -d "$_folio_su/$_folio_p" ] && _folio_front "$_folio_su/$_folio_p"
    done
    export PATH
fi
case "$(uname -s 2>/dev/null)" in MINGW*|MSYS*) export MSYS_NO_PATHCONV=1 ;; esac

if [ "${1:-}" = "--check" ] || [ "$_folio_is_sourced" = 0 ]; then
    _folio_bad=0
    if [ -n "$_folio_java" ]; then echo "JAVA_HOME    $JAVA_HOME (Java 17; java on PATH: $(java -version 2>&1 | sed -n 's/.*version "\([0-9.]*\).*/\1/p' | head -1))"; else echo "JAVA_HOME    NOT FOUND: install a Java 17 JDK (Temurin 17)"; _folio_bad=1; fi
    if [ -n "$_folio_sdk" ]; then echo "ANDROID_HOME $ANDROID_HOME"; else echo "ANDROID_HOME NOT FOUND: set it or put sdk.dir in local.properties"; _folio_bad=1; fi
    for _folio_t in adb emulator; do
        if command -v "$_folio_t" >/dev/null 2>&1; then echo "$_folio_t$(printf '%*s' $((13 - ${#_folio_t})) '')$(command -v "$_folio_t")"; else echo "$_folio_t$(printf '%*s' $((13 - ${#_folio_t})) '')not on PATH (only matters if you use it)"; fi
    done
    [ "$_folio_is_sourced" = 0 ] && echo "(run as 'source tools/dev-env.sh' to apply this to your shell)"
    [ "$_folio_bad" = 0 ] || { [ "$_folio_is_sourced" = 1 ] && return 1 || exit 1; }
fi
unset _folio_java _folio_sdk _folio_p _folio_t _folio_bad _folio_su _folio_is_sourced
unset -f _folio_java_major _folio_find_java _folio_find_sdk _folio_unix _folio_mixed _folio_front
