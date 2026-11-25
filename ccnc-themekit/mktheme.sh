#!/bin/bash
# Builds a ccNC theme pack into a NEMESIS package (+ USB-stick zip): ./mktheme.sh <pack-folder>
# Output: dist/ccnc.theme.<id>-<version>-nemesis-package.zip (Package Store) and ...-nemesis.zip (unzip onto a stick).
set -euo pipefail
K="$(cd "$(dirname "$0")" && pwd)"
P="$(cd "$1" && pwd)"
python3 -I "$K/validate.py" "$P"
field() { python3 -I -c "import json,sys; v=json.load(open(sys.argv[1])).get(sys.argv[2]) or ''; print(str(v).replace('\"','').replace('\n',' '))" "$P/theme.json" "$1"; }
ID=$(field id); V=$(field version); NAME=$(field name); AUTHOR=$(field author); DESC=$(field description)
[ -n "$DESC" ] || DESC="$NAME, a ccNC theme by $AUTHOR"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
N=$W/pkg; mkdir -p "$N/files/theme"
sed -e "s|@ID@|$ID|g" -e "s|@VERSION@|$V|g" -e "s|@NAME@|$NAME|g" -e "s|@AUTHOR@|$AUTHOR|g" -e "s|@DESC@|$DESC|g" \
    "$K/nemesis/NEMESIS.data.in" > "$N/NEMESIS.data"
cp -r "$K/nemesis/scripts" "$N/"
# Only what theme.json references goes in.
python3 -I - "$P" "$N/files/theme" <<'PY'
import json, os, shutil, sys
src, dst = sys.argv[1], sys.argv[2]
t = json.load(open(os.path.join(src, 'theme.json'), encoding='utf-8'))
shutil.copy(os.path.join(src, 'theme.json'), dst)
for k in ('wallpaper', 'boot_video', 'shutdown_video'):
    if t.get(k): shutil.copy(os.path.join(src, t[k]), os.path.join(dst, t[k]))
if t.get('icons') and os.path.isdir(os.path.join(src, t['icons'])):
    os.makedirs(os.path.join(dst, t['icons']))
    for f in os.listdir(os.path.join(src, t['icons'])):
        if f.endswith('.png'): shutil.copy(os.path.join(src, t['icons'], f), os.path.join(dst, t['icons'], f))
PY
printf '%s' "$ID" > "$N/files/PACK_ID"
zipdir() { (cd "$1" && find . -type f -exec touch -t 202601010000 {} + && find . -type f | sed 's#^\./##' | sort | zip -q -X -D "$2" -@); }
mkdir -p "$K/dist"
PK="$K/dist/ccnc.theme.$ID-$V-nemesis-package.zip"; ST="$K/dist/ccnc.theme.$ID-$V-nemesis.zip"
rm -f "$PK" "$ST"; zipdir "$N" "$PK"
U=$W/stick; mkdir -p "$U/packages"; cp "$PK" "$U/packages/ccnc.theme.$ID.zip"
printf '[nemesis]\nversion=1.0\ntype=install\npath=packages/ccnc.theme.%s.zip\n' "$ID" > "$U/NEMESIS.data"
zipdir "$U" "$ST"
echo "$PK"; echo "$ST"
