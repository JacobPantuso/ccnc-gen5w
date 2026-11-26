#!/bin/bash
# Run at the car with adb connected and the ccNC packages installed (ccnc-home/install.sh, or the
# stick). Opens every ccNC app in turn, takes a screenshot of each and saves the logs that settle the
# open questions (service access, units, encodings) to ccnc-verify-<time>/. Needs no root.
set -u
cd "$(dirname "$0")"
OUT=ccnc-verify-$(date +%Y%m%d-%H%M%S); mkdir -p "$OUT"
adb get-state >/dev/null 2>&1 || { echo "no adb device"; exit 1; }
TAGS="ccncClimate:V ccncRadio:V ccncEv:V ccncProfile:V ccncMemo:V ccncWeather:V ccncNavi:V ccncDA:V ccncTheme:V ccncPhone:V ccncMedia:V ccNCHome:V"
adb logcat -c
adb logcat -v time $TAGS '*:S' > "$OUT/ccnc.log" 2>&1 &
LOG=$!
adb shell am broadcast -a com.hkmc.request.widgetupdate >/dev/null 2>&1
adb shell am broadcast -a com.hkmc.intent.action.request_weather_update >/dev/null 2>&1
for a in home:HomeActivity settings:MainActivity climate:MainActivity media:MainActivity weather:MainActivity \
         phone:MainActivity radio:MainActivity voicememo:MainActivity profile:MainActivity ev:MainActivity navi:MainActivity; do
  app=${a%%:*}; act=${a#*:}
  echo "== $app"
  adb shell am start -n dev.ccnc.$app/.$act > "$OUT/$app.start.txt" 2>&1
  sleep 4
  adb shell screencap -p /data/local/tmp/v.png && adb pull /data/local/tmp/v.png "$OUT/$app.png" >/dev/null 2>&1
done
adb shell am start -n dev.ccnc.home/.HomeActivity >/dev/null 2>&1
sleep 2
kill $LOG 2>/dev/null
adb shell pm list packages | grep ccnc > "$OUT/packages.txt"
{
  echo "ccNC verification $(date)"
  for tag in ccncClimate ccncRadio ccncEv ccncProfile ccncMemo ccncWeather ccncNavi ccncDA ccncTheme ccncPhone ccncMedia; do
    echo; echo "--- $tag"; grep " $tag" "$OUT/ccnc.log" | sed -n 1,12p
  done
} > "$OUT/REPORT.txt"
echo "done: $OUT/REPORT.txt and the screenshots"
