#!/system/bin/sh
# ccNC on-car verification. Runs as root at the end of a ccNC package's NEMESIS postinstall
# (cwd = the package dir), or by hand: sh scripts/verify.sh
#
# Opens every installed ccNC app in turn, takes a screenshot of each, collects the apps' log lines
# (the raw values the car reports: climate, radio, EV, driver assistance, vehicle and display settings,
# weather, charger...) and writes REPORT.txt, ccnc.log and the screenshots to
#   <each USB stick>/ccNC reports/run-N/      and      /data/local/tmp/ccnc-verify/
# Only model-level facts are collected (no VIN, serial, phone numbers or contacts). The screenshots show
# what the apps show, so a connected phone's name can appear on the welcome page.
#
# Takes about a minute and puts ccNC apps on the screen: install while parked.
# Skip it: put an empty file "ccNC reports/NO_VERIFY" on the stick.
#
# KitKat toolbox: no head/tail/cut/tr, no date +format, so only plain sh, ls, cp, cat, grep, sleep.

export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
USBS="${CCNC_USBS:-/storage/usb0 /storage/usb1 /storage/usb2}"
WORK=/data/local/tmp/ccnc-verify
TAGS="ccncClimate:V ccncRadio:V ccncEv:V ccncProfile:V ccncMemo:V ccncWeather:V ccncNavi:V ccncDA:V ccncVehicle:V ccncDisplay:V ccncTheme:V ccncPhone:V ccncMedia:V ccNCHome:V"
WAIT=5

log() { echo "ccNC verify: $1"; }

for d in $USBS; do
    [ -e "$d/ccNC reports/NO_VERIFY" ] && { log "skipped (NO_VERIFY on the stick)"; exit 0; }
done
[ "$(getprop sys.boot_completed)" = 1 ] || { log "Android isn't up; skipped"; exit 0; }

rm -rf "$WORK"
mkdir -p "$WORK" || { log "can't create $WORK"; exit 0; }

toast() {
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}

installed() { pm path "$1" 2>/dev/null | grep -q "^package:"; }

# open <name> <package> <component> [am extras...]
open() {
    name=$1; pkg=$2; comp=$3
    shift 3
    installed "$pkg" || { echo "== $name: not installed" >> "$WORK/ccnc.log"; return; }
    echo "== $name" >> "$WORK/ccnc.log"
    logcat -c
    am start -n "$comp" "$@" >/dev/null 2>&1
    sleep $WAIT
    # Something else (NEMESIS' install screen) can sit on top: bring the app forward again and retry.
    n=0
    while [ $n -lt 3 ]; do
        dumpsys window 2>/dev/null | grep mCurrentFocus | grep -q "$pkg" && break
        echo "  ($name: $pkg not in front, retry $n)" >> "$WORK/ccnc.log"
        am start -n "$comp" "$@" >/dev/null 2>&1
        sleep 4
        n=$((n + 1))
    done
    screencap -p "$WORK/$name.png" 2>/dev/null
    logcat -d -v time $TAGS '*:S' >> "$WORK/ccnc.log" 2>&1
}

# Keep the system's own log before the per-app captures clear it (logcat -c below): welcome/micom/mode
# service lines, package-manager lines, and the whole buffer as a file for anything else.
logcat -d -v time > "$WORK/system-log-before.txt" 2>&1
grep -i "welcome\|goodbye\|micom\|\.mp4\|PackageManager\|ModeManager\|DATC\|HEV\|UserProfile" "$WORK/system-log-before.txt" > "$WORK/system-services.txt" 2>/dev/null
dmesg 2>/dev/null | grep -i "welcome\|splash\|bootanim" > "$WORK/dmesg-boot.txt" 2>/dev/null

log "starting: opening each ccNC app for a few seconds"
toast "ccNC check running: please wait about a minute"

# Ask the unit's services to send fresh data before the apps look.
am broadcast -a com.hkmc.intent.action.request_weather_update >/dev/null 2>&1
am broadcast -a com.hkmc.request.widgetupdate >/dev/null 2>&1

open home       dev.ccnc.home       dev.ccnc.home/.HomeActivity --ez diag true
open settings   dev.ccnc.settings   dev.ccnc.settings/.MainActivity
open driverassist dev.ccnc.settings dev.ccnc.settings/.DriverAssistActivity
WAIT=15
open vehicle    dev.ccnc.settings   dev.ccnc.settings/.VehicleSettingsActivity --ez dump true
WAIT=5
open display    dev.ccnc.settings   dev.ccnc.settings/.DisplayActivity
open climate    dev.ccnc.climate    dev.ccnc.climate/.MainActivity
open media      dev.ccnc.media      dev.ccnc.media/.MainActivity
open weather    dev.ccnc.weather    dev.ccnc.weather/.MainActivity
open phone      dev.ccnc.phone      dev.ccnc.phone/.MainActivity
open radio      dev.ccnc.radio      dev.ccnc.radio/.MainActivity
open voicememo  dev.ccnc.voicememo  dev.ccnc.voicememo/.MainActivity
open profile    dev.ccnc.profile    dev.ccnc.profile/.MainActivity
open ev         dev.ccnc.ev         dev.ccnc.ev/.MainActivity
open navi       dev.ccnc.navi       dev.ccnc.navi/.MainActivity

# Back to the ccNC home (or leave the screen as it is if it isn't installed).
installed dev.ccnc.home && am start -n dev.ccnc.home/.HomeActivity >/dev/null 2>&1

{
    echo "ccNC verification report"
    date
    echo
    echo "unit"
    echo "  product:   $(getprop ro.product.name)"
    echo "  brand:     $(getprop ro.product.brand)"
    echo "  onebin:    $(getprop ro.mobis.targetdir)"
    echo "  build:     $(getprop ro.build.display.id)"
    echo "  android:   $(getprop ro.build.version.release)"
    echo
    echo "installed ccNC packages"
    pm list packages 2>/dev/null | grep ccnc
    echo
    echo "startup video (WelcomeGoodbye.apk)"
    ls -l /system/app/WelcomeGoodbye.apk 2>/dev/null
    echo "stock apk video entries (size name):"
    cat /data/local/tmp/ccnc-welcome-entries.txt 2>/dev/null
    echo "welcome/goodbye lines from the system log taken before the check (full log: system-log-before.txt):"
    grep -i "welcome\|goodbye\|\.mp4" "$WORK/system-log-before.txt" 2>/dev/null | grep -v ccnc | while read l; do echo "  $l"; done
    echo
    echo "log lines from the apps (raw values the car reported), per app:"
    cat "$WORK/ccnc.log"
} > "$WORK/REPORT.txt"

saved=""
for d in $USBS; do
    [ -d "$d" ] || continue
    dest="$d/ccNC reports"
    mkdir -p "$dest" 2>/dev/null || continue
    n=1
    while [ -e "$dest/run-$n" ]; do n=$((n + 1)); done
    mkdir "$dest/run-$n" 2>/dev/null || continue
    if cp "$WORK"/* "$dest/run-$n/" 2>/dev/null; then saved="$saved $dest/run-$n"; fi
done

if [ -n "$saved" ]; then
    log "saved to:$saved"
    toast "ccNC check done: report saved to the USB stick"
else
    log "no USB stick to write to; the report is in $WORK (adb pull it)"
    toast "ccNC check done: no USB stick found, report kept on the unit"
fi
exit 0
