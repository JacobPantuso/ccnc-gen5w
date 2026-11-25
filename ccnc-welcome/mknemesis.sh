#!/bin/bash
# Builds dist/ccnc.welcome-<v>-nemesis.zip (USB stick) and -nemesis-package.zip.
set -euo pipefail
cd "$(dirname "$0")"
V=1.3
./build-patch.sh >/dev/null
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
zipdir() { (cd "$1" && find . -type f -exec touch -t 202601010000 {} + && find . -type f | sed 's#^\./##' | sort | zip -q -X -D "$2" -@); }
N=$W/pkg; mkdir -p "$N/files"
sed "s/@VERSION@/$V/g" nemesis/NEMESIS.data.in > "$N/NEMESIS.data"
python3 - "$N/NEMESIS.data" <<'PY'
import re, sys
lim = {"id": 64, "name": 64, "short-desc": 64, "detailed-desc": 256}
for line in open(sys.argv[1]):
    m = re.match(r'([a-z-]+)=(.*)', line.strip())
    if m and m.group(1) in lim and len(m.group(2).strip('"')) > lim[m.group(1)]:
        sys.exit("%s is %d characters, NEMESIS allows %d" % (m.group(1), len(m.group(2).strip('"')), lim[m.group(1)]))
PY
cp -r nemesis/scripts "$N/"
cp build/patch.jar "$N/files/"
cp video/welcome_hyundai_avnt.mp4 "$N/files/welcome_hyundai.mp4"
cp video/welcome_kia_avnt.mp4 "$N/files/welcome_kia.mp4"
mkdir -p dist; P=$PWD/dist/ccnc.welcome-$V-nemesis-package.zip; S=$PWD/dist/ccnc.welcome-$V-nemesis.zip
rm -f "$P" "$S"; zipdir "$N" "$P"
U=$W/stick; mkdir -p "$U/packages"; cp "$P" "$U/packages/ccnc.welcome.zip"
printf '[nemesis]\nversion=1.0\ntype=install\npath=packages/ccnc.welcome.zip\n' > "$U/NEMESIS.data"
zipdir "$U" "$S"; echo "$S"; echo "$P"
