#!/bin/bash
# Installs (or updates) the ccNC Home NEMESIS package over adb, the same way a USB
# stick or NEMESIS Manager would: `nemesis install [--update] <package zip>`.
# Uses a non-SUID copy of nemesis so the command goes through the NEMESIS daemon with
# full root (plain `nemesis root -c` from adb is a weak root on these units).
#   ./install.sh            build the package, then install it
#   ./install.sh <zip>      install a given *-nemesis-package.zip
set -euo pipefail
cd "$(dirname "$0")"

if [ $# -ge 1 ]; then
  PKGZIP=$1
else
  ./mknemesis.sh > /tmp/ccnc-mknemesis.out || { cat /tmp/ccnc-mknemesis.out; echo "package build failed"; exit 1; }
  PKGZIP=$(grep -- '-nemesis-package.zip$' /tmp/ccnc-mknemesis.out)
fi
[ -f "$PKGZIP" ] || { echo "no package at $PKGZIP"; exit 1; }

adb get-state >/dev/null
adb shell 'cp /system/xbin/nemesis /data/local/tmp/nemesis-d && chmod 755 /data/local/tmp/nemesis-d'
adb push "$PKGZIP" /data/local/tmp/ccnc.home.zip >/dev/null
if adb shell '[ -d /data/nemesis/apps/ccnc.home ] && echo yes' | grep -q yes; then UPD=--update; else UPD=; fi
echo "nemesis install $UPD ccnc.home"
adb shell "/data/local/tmp/nemesis-d root -c 'nemesis install $UPD /data/local/tmp/ccnc.home.zip'" | tr -d '\r'
adb shell rm -f /data/local/tmp/ccnc.home.zip
adb shell 'pm path dev.ccnc.home' | tr -d '\r' | grep -q '^package:' \
  || { echo "!! the app isn't installed after the package install: see NEMESIS's output above"; exit 1; }
echo "Installed. Uninstall: adb shell \"/data/local/tmp/nemesis-d root -c 'nemesis uninstall ccnc.home'\""
