#!/bin/bash
# Builds dist/ccnc.apps-<v>-nemesis.zip (USB stick) and -nemesis-package.zip from the three apps.
set -euo pipefail
cd "$(dirname "$0")"
V=1.13
BT="${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools"; BT="$BT/$(ls "$BT" | sort -V | tail -1)"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
N=$W/pkg; mkdir -p "$N/files"
for app in settings climate media weather phone radio voicememo profile ev navi; do
  env -u PREVIEW -u TARGET_SDK ../ccnc-$app/build.sh >/dev/null
  apk=../ccnc-$app/build/ccnc-$app.apk
  b=$("$BT/aapt2" dump badging "$apk")
  [[ "$b" == *"targetSdkVersion:'19'"* ]] || { echo "refusing: $app doesn't target SDK 19"; exit 1; }
  cp "$apk" "$N/files/$app.apk"
done
sed "s/@VERSION@/$V/g" nemesis/NEMESIS.data.in > "$N/NEMESIS.data"
python3 - "$N/NEMESIS.data" <<'PY'
import re, sys
lim = {"id": 64, "name": 64, "short-desc": 64, "detailed-desc": 256}
for line in open(sys.argv[1]):
    m = re.match(r'([a-z-]+)=(.*)', line.strip())
    if m and m.group(1) in lim and len(m.group(2).strip('"')) > lim[m.group(1)]:
        sys.exit("%s is %d characters, NEMESIS allows %d" % (m.group(1), len(m.group(2).strip('"')), lim[m.group(1)]))
PY
cp -r nemesis/scripts nemesis/bin "$N/"
cp ../ccnc-common/verify/verify.sh "$N/scripts/verify.sh"
cp ../ccnc-common/verify/devtools.sh "$N/scripts/devtools.sh"
chmod 755 "$N/scripts/verify.sh" "$N/scripts/devtools.sh"
zipdir() { (cd "$1" && find . -type f -exec touch -t 202601010000 {} + && find . -type f | sed 's#^\./##' | sort | zip -q -X -D "$2" -@); }
mkdir -p dist; P=$PWD/dist/ccnc.apps-$V-nemesis-package.zip; S=$PWD/dist/ccnc.apps-$V-nemesis.zip
rm -f "$P" "$S"; zipdir "$N" "$P"
U=$W/stick; mkdir -p "$U/packages"; cp "$P" "$U/packages/ccnc.apps.zip"
printf '[nemesis]\nversion=1.0\ntype=install\npath=packages/ccnc.apps.zip\n' > "$U/NEMESIS.data"
zipdir "$U" "$S"; echo "$S"; echo "$P"
