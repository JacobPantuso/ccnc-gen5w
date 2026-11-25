#!/bin/bash
# Builds every ccNC NEMESIS package and one USB-stick zip that lists them all (Package Store,
# NEMESIS 1.16 type=list): dist/ccnc-all-nemesis.zip. Unzip onto the root of a stick.
set -euo pipefail
cd "$(dirname "$0")"
# Package Store order. System patches last; their restore packages go to restore/ (not listed, for emergencies).
PK=(ccnc-apps ccnc-home ccnc-climatebtn ccnc-sideosd ccnc-volume ccnc-welcome)
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
mkdir -p "$W/stick/packages" "$W/stick/restore" dist
ini() { unzip -p "$1" NEMESIS.data | grep "^$2=" | sed -n 1p | sed 's/^[^=]*=//; s/^"\(.*\)"$/\1/'; }
{ printf '[nemesis]\nversion=1.1\ntype=list\n'; } > "$W/stick/NEMESIS.data"
i=0
add() { # package zip -> listed in the Package Store
  local pkg=$1 id
  id=$(ini "$pkg" id)
  cp "$pkg" "$W/stick/packages/$id.zip"
  printf '\n[package.%d]\npath=packages/%s.zip\nname=%s\nshort-desc=%s\npublished=%s\n' \
    "$i" "$id" "$(ini "$pkg" name)" "$(ini "$pkg" short-desc)" "$(date -u +%F)" >> "$W/stick/NEMESIS.data"
  echo "  $id $(ini "$pkg" package-version)"
  i=$((i + 1))
}
for d in "${PK[@]}"; do
  out=$("$d/mknemesis.sh" 2>&1 | grep -v "^Note" || true)
  pkg=$(echo "$out" | grep -e '-nemesis-package.zip$' | grep -v '\.restore-' | tail -1)
  [ -f "$pkg" ] || { echo "$d: no package built"; echo "$out"; exit 1; }
  add "$pkg"
  r=$(echo "$out" | grep -e '\.restore-.*-nemesis.zip$' | tail -1 || true)
  [ -n "$r" ] && [ -f "$r" ] && cp "$r" "$W/stick/restore/" && echo "    restore: $(basename "$r")"
done
# Example community theme pack (ccnc-themekit).
t=$(ccnc-themekit/mktheme.sh ccnc-themekit/examples/sample-ember | grep -e '-nemesis-package.zip$' | tail -1)
[ -f "$t" ] && add "$t"
cat > "$W/stick/README.txt" <<'TXT'
ccNC for Gen5W: unzip this onto the root of a USB stick, plug it into the head unit, and pick
packages from NEMESIS Manager's Package Store. Install parked, in this order:

  1. ccNC Apps          Settings, Climate, Media, EV, Weather, Phone, Radio, Voice Memo, Profile,
                        Navigation. Apps only: /system is not changed.
  2. ccNC Home          the launcher. After installing, press Home and choose "ccNC Home" (Always).
  3. ccNC Climate Buttons   (optional, system patch) the car's climate and seat buttons open ccNC
                        Climate. Needs ccNC Apps. Restart the head unit afterwards.
  4. ccNC Side Popups Off   (optional, system patch) hides the Gen5W right-side popups. Restart.
  5. ccNC Volume        (optional, system patch) hides the Gen5W volume strip; ccNC shows its own. Restart.
  6. ccNC Startup Video (optional) ccNC's welcome video. Restart.
  7. Sample Ember       example ccNC theme pack: pick it in Settings > Screen Theme > ccNC Theme.

The system patches (3-5) are made for head-unit firmware CEEV.CAN 250818 only and refuse any other
file. Each backs up the stock file and puts it back on uninstall. If the head unit doesn't come back
after one, use the matching package in restore/ with NEMESIS.emergency.

DEVELOPER MENU: tap the clock in the top bar of any ccNC app 7 times. It has Reboot, Run checks
(opens every ccNC app for a few seconds, then saves REPORT.txt, ccnc.log and screenshots to
"ccNC reports/run-N/" on this stick), Status, CPU usage and Compatibility. Installing no longer runs
the check by itself.

Every package can be uninstalled from NEMESIS Manager. Climate is view only unless you turn on
"Vehicle control" inside it (long-press the home icon at the top left).
TXT
(cd "$W/stick" && find . -type f -exec touch -t 202601010000 {} + && find . -type f | sed 's#^\./##' | sort | zip -q -X -D "$OLDPWD/dist/ccnc-all-nemesis.zip" -@)
echo "dist/ccnc-all-nemesis.zip"
