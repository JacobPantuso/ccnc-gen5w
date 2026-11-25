#!/bin/bash
# DRAFT. Builds tool/out/ccnc-lp.jar (classes.dex for the unit's dalvikvm) and runs the offline checks.
# Host only: never pushes anything anywhere.
set -euo pipefail
cd "$(dirname "$0")"
BT=${BT:-$HOME/Library/Android/sdk/build-tools/35.0.0}
rm -rf out && mkdir -p out/classes
javac --release 8 -d out/classes src/ccnc/lp/DexPatch.java
LIB=$(ls -d $HOME/Library/Android/sdk/platforms/android-*/android.jar 2>/dev/null | tail -1 || true)
"$BT/d8" --release --min-api 17 ${LIB:+--lib "$LIB"} --output out/ccnc-lp.jar $(find out/classes -name '*.class')
echo "built out/ccnc-lp.jar"
[ -n "${SKIP_TEST:-}" ] || [ ! -x ./offline-test.sh ] || ./offline-test.sh   # needs stock files pulled from a unit
