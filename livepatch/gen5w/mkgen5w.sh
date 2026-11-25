#!/bin/bash
# Builds the single ccNC for Gen5W LivePatch package (DRAFT until NEMESIS_MIN is released and tested on a car).
#   ./mkgen5w.sh            -> dist/ccnc.gen5w-<V>-nemesis-package.zip (Package Store) and -nemesis.zip (USB stick)
#   REBUILD=1 ./mkgen5w.sh  rebuilds every app (car build: targetSdk 19) first
set -euo pipefail
V=${V:-2.0.0}
NEMESIS_MIN=${NEMESIS_MIN:-1.17.1}   # the fixed 1.17 re-release; set to its real version before publishing
H=$(cd "$(dirname "$0")" && pwd); R=$(cd "$H/../.." && pwd)
APPS="settings climate media weather phone radio voicememo profile ev navi home"
if [ "${REBUILD:-}" = 1 ]; then for a in $APPS; do (cd "$R/ccnc-$a" && ./build.sh >/dev/null); done; fi
# the two on-unit tools (dex jars), if not built yet
[ -s "$R/livepatch/tool/out/ccnc-lp.jar" ] || SKIP_TEST=1 "$R/livepatch/tool/build.sh" >/dev/null
[ -s "$R/ccnc-welcome/build/patch.jar" ] || "$R/ccnc-welcome/build-patch.sh" >/dev/null
W=$(mktemp -d); P=$W/ccnc.gen5w; trap 'rm -rf "$W"' EXIT
mkdir -p "$P/files/apks"
cp -R "$H/template/scripts" "$H/template/bin" "$P/"
cp "$R/ccnc-common/verify/verify.sh" "$R/ccnc-common/verify/devtools.sh" "$P/scripts/"
sed -e "s/@VERSION@/$V/g" -e "s/@NEMESIS_MIN@/$NEMESIS_MIN/g" "$H/template/NEMESIS.data.in" > "$P/NEMESIS.data"
sed -i '' "s/@NEMESIS_MIN@/$NEMESIS_MIN/g" "$P/scripts/pre.sh"
for a in $APPS; do
    f="$R/ccnc-$a/build/ccnc-$a.apk"; [ -s "$f" ] || { echo "missing $f (REBUILD=1)"; exit 1; }
    cp "$f" "$P/files/apks/$a.apk"
done
cp "$R/livepatch/tool/out/ccnc-lp.jar" "$R/ccnc-welcome/build/patch.jar" "$P/files/"
cp "$R/ccnc-welcome/video/welcome_hyundai_avnt.mp4" "$P/files/welcome_hyundai.mp4"
cp "$R/ccnc-welcome/video/welcome_kia_avnt.mp4" "$P/files/welcome_kia.mp4"
chmod 755 "$P"/bin/* "$P"/scripts/*.sh
[ $(wc -c < "$P/NEMESIS.data") -gt 0 ]
mkdir -p "$H/dist"
OUT=$H/dist/ccnc.gen5w-$V-nemesis-package.zip; rm -f "$OUT"
(cd "$P" && zip -qrX "$OUT" .)
# USB stick: NEMESIS list bundle with the package in packages/
S=$W/stick; mkdir -p "$S/packages"; cp "$OUT" "$S/packages/ccnc.gen5w.zip"
cat > "$S/NEMESIS.data" <<EOS
[nemesis]
version=1.1
type=list

[package.0]
path=packages/ccnc.gen5w.zip
name=ccNC for Gen5W
short-desc=ccNC-style home screen and apps for Gen5W, all in one.
published=$(date -u +%F)
EOS
STICK=$H/dist/ccnc.gen5w-$V-nemesis.zip; rm -f "$STICK"
(cd "$S" && zip -qrX "$STICK" .)
# for the NEMESIS checker / inspection
rm -rf "$H/dist/tree"; cp -R "$P" "$H/dist/tree"
ls -l "$OUT" "$STICK"
