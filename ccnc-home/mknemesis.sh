#!/bin/bash
# Builds the ccNC Home NEMESIS package. Output in dist/:
#   ccnc.home-<v>-nemesis.zip          unzip onto a USB stick (NEMESIS.data + packages/)
#   ccnc.home-<v>-nemesis-package.zip  the package itself (nemesis install <zip>)
set -euo pipefail
cd "$(dirname "$0")"

# Car build only: never ship the preview build (fake weather service, newer target SDK).
env -u PREVIEW -u TARGET_SDK ./build.sh >/dev/null
BT="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools"
BT="$BT/$(ls "$BT" | sort -V | tail -1)"
DEX=$("$BT/dexdump" build/classes.dex)
if [[ "$DEX" == *FakeWeather* ]]; then
  echo "refusing to package: build contains preview code"; exit 1
fi
BADGING=$("$BT/aapt2" dump badging build/ccnc-home.apk)
[[ "$BADGING" == *"targetSdkVersion:'19'"* ]] || { echo "refusing to package: APK doesn't target SDK 19"; exit 1; }
V=$(grep -o 'android:versionName="[^"]*"' src/main/AndroidManifest.xml | cut -d'"' -f2)
[[ "$V" =~ ^[0-9]+(\.[0-9]+)*$ ]] || { echo "versionName $V isn't dotted numbers (NEMESIS package-version)"; exit 1; }

W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
fill() { sed "s/@VERSION@/$V/g" "$1" > "$2"; }
# Deterministic zip: fixed dates, sorted names, no extra attributes.
zipdir() { (cd "$1" && find . -type f -exec touch -t 202601010000 {} + && find . -type f | sed 's#^\./##' | sort | zip -q -X -D "$2" -@); }

N=$W/pkg
mkdir -p "$N/files"
fill nemesis/NEMESIS.data.in "$N/NEMESIS.data"
# NEMESIS's metadata limits (meta.c): short-desc 64, detailed-desc 256, id/name 64.
python3 - "$N/NEMESIS.data" <<'PY'
import re, sys
lim = {"id": 64, "name": 64, "short-desc": 64, "detailed-desc": 256}
for line in open(sys.argv[1]):
    m = re.match(r'([a-z-]+)=(.*)', line.strip())
    if m and m.group(1) in lim:
        v = m.group(2).strip('"')
        if len(v) > lim[m.group(1)]:
            sys.exit("%s is %d characters, NEMESIS allows %d" % (m.group(1), len(v), lim[m.group(1)]))
PY
cp -r nemesis/scripts nemesis/bin "$N/"
cp ../ccnc-common/verify/verify.sh "$N/scripts/verify.sh"
cp ../ccnc-common/verify/devtools.sh "$N/scripts/devtools.sh"
chmod 755 "$N/scripts/verify.sh" "$N/scripts/devtools.sh"
cp build/ccnc-home.apk "$N/files/ccnc-home.apk"
chmod 755 "$N"/scripts/*.sh "$N"/bin/*.sh

mkdir -p dist
P=$PWD/dist/ccnc.home-$V-nemesis-package.zip
S=$PWD/dist/ccnc.home-$V-nemesis.zip
rm -f "$P" "$S"
zipdir "$N" "$P"

U=$W/stick
mkdir -p "$U/packages"
cp "$P" "$U/packages/ccnc.home.zip"
printf '[nemesis]\nversion=1.0\ntype=install\npath=packages/ccnc.home.zip\n' > "$U/NEMESIS.data"
fill nemesis/README.txt.in "$U/README.txt"
zipdir "$U" "$S"

echo "$S"
echo "$P"
