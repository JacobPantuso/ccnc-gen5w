#!/bin/bash
# Builds dist/ccnc.sideosd-<v>-nemesis-package.zip (+ stick zip) and the emergency restore package.
set -euo pipefail
cd "$(dirname "$0")"
V=1.0
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
zipdir() { (cd "$1" && find . -type f -exec touch -t 202601010000 {} + && find . -type f | sed 's#^\./##' | sort | zip -q -X -D "$2" -@); }
mkdir -p dist
build() { # id srcdir files...
    id=$1; src=$2; shift 2
    N=$W/$id; mkdir -p "$N/files"
    sed "s/@VERSION@/$V/g" "$src/NEMESIS.data.in" > "$N/NEMESIS.data"
    cp -r "$src/scripts" "$N/"
    for f in "$@"; do cp "files/$f" "$N/files/"; done
    P=$PWD/dist/$id-$V-nemesis-package.zip; S=$PWD/dist/$id-$V-nemesis.zip
    rm -f "$P" "$S"; zipdir "$N" "$P"
    U=$W/stick-$id; mkdir -p "$U/packages"; cp "$P" "$U/packages/$id.zip"
    printf '[nemesis]\nversion=1.0\ntype=install\npath=packages/%s.zip\n' "$id" > "$U/NEMESIS.data"
    zipdir "$U" "$S"; echo "$P"; echo "$S"
}
build ccnc.sideosd nemesis RightScreenHome.patched.odex RightScreenHome.stock.odex
build ccnc.sideosd.restore restore RightScreenHome.stock.odex
