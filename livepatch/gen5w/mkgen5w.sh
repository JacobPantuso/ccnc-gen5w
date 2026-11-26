#!/bin/bash
# Builds the ccNC for Gen5W release package. Always from a clean car build, so a stale or emulator APK can't slip in.
#   V=2.0.3 ./mkgen5w.sh   -> dist/ccnc.gen5w-<V>-nemesis-package.zip (Package Store), -nemesis.zip (USB stick),
#                             ccnc.gen5w-<V>.sha256
# Needs the release key keys/ccnc-apps.keystore (private; the same key every ccNC release since 1.0 is signed with,
# so updates install over the apps already on a car). CCNC_KEYSTORE / _PASS / _ALIAS override it.
# Checks every APK (car build: targetSdk 19, not debuggable, signed with that key), the descriptor and the scripts
# before zipping, and runs the patch tool's offline tests when the stock files are available.
set -euo pipefail
V=${V:?set the package version, e.g. V=2.0.3 ./mkgen5w.sh}
case $V in *[!0-9.]*|.*|*.) echo "V must be dotted numbers, got '$V'"; exit 1;; esac
NEMESIS_MIN=${NEMESIS_MIN:-1.17.1}
H=$(cd "$(dirname "$0")" && pwd); R=$(cd "$H/../.." && pwd)
APPS="settings climate media weather phone radio voicememo profile ev navi home"
KEYSTORE=${CCNC_KEYSTORE:-$R/keys/ccnc-apps.keystore}
KEYSTORE_PASS=${CCNC_KEYSTORE_PASS:-android}
KEYSTORE_ALIAS=${CCNC_KEYSTORE_ALIAS:-androiddebugkey}
CERT_SHA256=${CCNC_CERT_SHA256:-66b4aeed24b4dca6c0e15b87078049be8d206ec6ca507c6f5f1146407c227848}
[ -f "$KEYSTORE" ] || { echo "no release key at $KEYSTORE (see BUILDING.md)"; exit 1; }
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
AAPT=$(ls "$SDK"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1)
fail() { echo "mkgen5w: $*"; exit 1; }

# 1. clean car builds of every app and both on-unit tools (no emulator/debug/preview settings from the environment)
for a in $APPS; do
    echo "building ccnc-$a"
    (cd "$R/ccnc-$a" && env -u TARGET_SDK -u DEBUGGABLE -u PREVIEW \
        KEYSTORE="$KEYSTORE" KEYSTORE_PASS="$KEYSTORE_PASS" KEYSTORE_ALIAS="$KEYSTORE_ALIAS" ./build.sh >/dev/null)
done
"$R/livepatch/tool/build.sh" >/dev/null          # also runs the offline tests when the stock files are there
"$R/ccnc-welcome/build-patch.sh" >/dev/null

# 2. every APK must be a car build signed with the release key
for a in $APPS; do
    f="$R/ccnc-$a/build/ccnc-$a.apk"
    b=$("$AAPT" dump badging "$f")
    case $b in *"targetSdkVersion:'19'"*) ;; *) fail "$a is not a car build (targetSdk 19)";; esac
    case $b in *application-debuggable*) fail "$a is debuggable";; esac
    c=$("$BT/apksigner" verify --print-certs "$f" | grep -i 'SHA-256 digest' | head -1 | tr -d ' :' | tr 'A-F' 'a-f')
    case $c in *"$CERT_SHA256"*) ;; *) fail "$a isn't signed with the release key ($c)";; esac
done

# 3. assemble
W=$(mktemp -d); P=$W/ccnc.gen5w; trap 'rm -rf "$W"' EXIT
mkdir -p "$P/files/apks"
cp -R "$H/template/scripts" "$H/template/bin" "$P/"
cp "$R/ccnc-common/verify/verify.sh" "$R/ccnc-common/verify/devtools.sh" "$P/scripts/"
sed -e "s/@VERSION@/$V/g" -e "s/@NEMESIS_MIN@/$NEMESIS_MIN/g" "$H/template/NEMESIS.data.in" > "$P/NEMESIS.data"
for f in "$P"/scripts/*.sh; do sed "s/@NEMESIS_MIN@/$NEMESIS_MIN/g" "$f" > "$f.tmp" && mv "$f.tmp" "$f"; done
for a in $APPS; do cp "$R/ccnc-$a/build/ccnc-$a.apk" "$P/files/apks/$a.apk"; done
cp "$R/livepatch/tool/out/ccnc-lp.jar" "$R/ccnc-welcome/build/patch.jar" "$P/files/"
cp "$R/ccnc-welcome/video/welcome_hyundai_avnt.mp4" "$P/files/welcome_hyundai.mp4"
cp "$R/ccnc-welcome/video/welcome_kia_avnt.mp4" "$P/files/welcome_kia.mp4"
chmod 755 "$P"/bin/* "$P"/scripts/*.sh

# 4. package checks
python3 - "$P/NEMESIS.data" <<'PY'
import re, sys
s = open(sys.argv[1]).read()
d = re.search(r'detailed-desc="(.*?)"', s, re.S).group(1)
assert len(d) <= 256, "detailed-desc is %d chars (max 256)" % len(d)
assert '@' not in re.sub(r'(?m)^;.*$', '', s).replace('usb-handler@', ''), "unreplaced @placeholder@ in NEMESIS.data"
PY
grep -rl 'DRAFT\|UNTESTED' "$P/NEMESIS.data" "$P/scripts" "$P/bin" && fail "DRAFT/UNTESTED text in the package"
for f in "$P"/scripts/*.sh "$P"/bin/*; do sh -n "$f" || fail "syntax: $f"; done

# 5. zips + checksums
mkdir -p "$H/dist"
OUT=$H/dist/ccnc.gen5w-$V-nemesis-package.zip; rm -f "$OUT"
(cd "$P" && zip -qrX "$OUT" .)
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
(cd "$H/dist" && shasum -a 256 "${OUT##*/}" "${STICK##*/}" > "ccnc.gen5w-$V.sha256")
rm -rf "$H/dist/tree"; cp -R "$P" "$H/dist/tree"   # for the NEMESIS checker / inspection
ls -l "$OUT" "$STICK" "$H/dist/ccnc.gen5w-$V.sha256"
