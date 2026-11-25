#!/bin/bash
# Builds build/patch.jar (classes.dex inside a jar, for app_process on a KitKat unit).
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
PLATFORM="$SDK/platforms/$(ls "$SDK/platforms" | sort -V | tail -1)/android.jar"
rm -rf build && mkdir -p build/classes
javac -source 8 -target 8 -Xlint:-options -bootclasspath "$PLATFORM" -d build/classes src/dev/ccnc/welcome/Patch.java
"$BT/d8" --min-api 19 --release --lib "$PLATFORM" --output build $(find build/classes -name '*.class')
(cd build && zip -q -X patch.jar classes.dex)
ls -l build/patch.jar
