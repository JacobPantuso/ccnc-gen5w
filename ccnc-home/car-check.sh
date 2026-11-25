#!/bin/bash
# One-shot on-car check for ccNC Home. Run from the laptop with adb connected to the head unit.
# Installs the APK, starts it in diagnostics mode, streams its log, and collects what's needed
# to verify weather, the unit setting and the layout. Results go to car-check-<time>/.
# Read-only on the unit apart from the app install (and its /data/local/tmp scratch files).
set -uo pipefail
cd "$(dirname "$0")"
OUT="car-check-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

echo "== unit"
adb get-state || { echo "adb not connected"; exit 1; }
for p in ro.build.display.id ro.mobis.targetdir ro.sf.lcd_density sys.boot_completed; do
  echo "$p=$(adb shell getprop "$p" | tr -d '\r')"
done | tee "$OUT/props.txt"
adb shell wm size | tr -d '\r' | tee -a "$OUT/props.txt"

echo "== install"
if ! ./install.sh 2>&1 | tee "$OUT/install.txt"; then
  echo "!! install failed (see above): stopping"; exit 1
fi

echo "== log stream (logcat holds only ~10 s on these units)"
adb logcat -c
adb logcat -v time -s ccNCHome:V AndroidRuntime:E > "$OUT/ccnchome.log" 2>&1 &
LOGPID=$!
sleep 1

adb shell am force-stop dev.ccnc.home
adb shell am start -n dev.ccnc.home/.HomeActivity --ez diag true
echo "waiting 20 s for the weather service to answer..."
sleep 20

echo "== weather unit setting (tmu_info)"
adb shell content query --uri content://com.hkmc.telematics.service.util.db.VehicleSettingProvider/tmu_info \
  2>&1 | tr -d '\r' > "$OUT/tmu_info.txt"
grep -o "TempUnit=[0-9]*" "$OUT/tmu_info.txt" | sort -u

echo "== screenshot"
adb shell screencap -p /data/local/tmp/ccnc.png && adb pull /data/local/tmp/ccnc.png "$OUT/home.png" >/dev/null \
  && adb shell rm /data/local/tmp/ccnc.png

echo "== launchable apps (what the cards are built from)"
adb shell dumpsys package r 2>/dev/null | tr -d '\r' > "$OUT/dumpsys-package-r.txt"
adb shell pm list packages -f 2>/dev/null | tr -d '\r' > "$OUT/packages.txt"

kill "$LOGPID" 2>/dev/null
echo
echo "== summary"
grep -E "weather:|diag: newGui" "$OUT/ccnchome.log" | grep -v "diag: table" || echo "(no weather lines)"
echo "condition table lines: $(grep -c 'diag: table' "$OUT/ccnchome.log")"
grep -E "FATAL|AndroidRuntime" "$OUT/ccnchome.log" && echo "!! crash above"
echo "Saved to $OUT/"
