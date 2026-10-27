#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# A JAVA_HOME that isn't a folder counts as unset. On Windows, JDK 17 comes first because CI runs on it.
if [ ! -d "${JAVA_HOME:-}" ]; then
    for jdk in '/Applications/Android Studio.app/Contents/jbr/Contents/Home' '/c/Program Files/Eclipse Adoptium'/jdk-17* \
        '/c/Program Files/Android/Android Studio/jbr'; do
        if [ -d "$jdk" ]; then export JAVA_HOME="$jdk"; break; fi
    done
fi
exec ./gradlew "$@"
