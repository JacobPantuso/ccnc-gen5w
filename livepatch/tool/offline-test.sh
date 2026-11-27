#!/bin/bash
# Offline checks of the patch tool (DexPatch) and its LivePatch wrapper (lp-ccnc). Host only; changes nothing.
# Needs the CEEV.CAN 250818 stock files, which aren't in the public repository (firmware belongs to Hyundai): put them
# under $CCNC_STOCK (default: this tree's ccnc-volume/files, ccnc-sideosd/files, ccnc-climatebtn/files); without them
# the file tests are skipped. $CCNC_UNIT (default ../offline/unit, next to this repository) adds a scan of every app and framework odex.
set -uo pipefail
cd "$(dirname "$0")"
G=$(cd ../.. && pwd)
VOL=${CCNC_STOCK:-$G/ccnc-volume/files}/automotive-service.stock.odex
RSH=${CCNC_STOCK:-$G/ccnc-sideosd/files}/RightScreenHome.stock.odex
CLI=${CCNC_STOCK:-$G/ccnc-climatebtn/files}/HKMC_Climate.stock.odex
UNIT=${CCNC_UNIT:-$G/../offline/unit/system}   # a unit image kept next to this repository
# Outputs on 250818: volume and climate have run on the car (ccnc.gen5w 2.0.2); sideosd adds the right-screen AV off view skip (2.0.3)
GOLD_VOL=601edc082cfe33c541e6f1dac452cef3 GOLD_RSH=3d2a5482c8beda0f39af8f0f1065d237 GOLD_CLI=753c509b564a44e79080c32218879843
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
J="java -cp ${CCNC_LP_CLASSES:-out/classes} ccnc.lp.DexPatch"   # CCNC_LP_CLASSES: classes compiled elsewhere (checks without a build)
fail=0
ok() { echo "PASS $1"; }
bad() { echo "FAIL $1"; fail=1; }
skip() { echo "SKIP $1"; }
run() { $J "$@" >"$T/log" 2>&1; echo $?; }
md5f() { python3 -c "import hashlib,sys;print(hashlib.md5(open(sys.argv[1],'rb').read()).hexdigest())" "$1" 2>/dev/null; }

if [ -f "$VOL" ] && [ -f "$RSH" ] && [ -f "$CLI" ]; then
  # 1. each mode on its stock file: the exact bytes that run on the car; a second run exits 10 and writes nothing
  for m in "volume $VOL $GOLD_VOL" "sideosd $RSH $GOLD_RSH" "climate $CLI $GOLD_CLI"; do
    set -- $m
    [ "$(run $1 $2 $T/$1.odex)" = 0 ] && [ "$(md5f $T/$1.odex)" = $3 ] && ok "$1 output = on-car bytes" || bad "$1 output"
    [ "$(run $1 $T/$1.odex $T/$1.2)" = 10 ] && [ ! -e $T/$1.2 ] && ok "$1 re-run exits 10" || bad "$1 re-run"
  done
  [ "$(run climate $CLI $T/c.odex)" = 0 ] && [ "$(grep -c -- '-> implicit' $T/log)" = 2 ] && ok "climate rewrites both launch sites" || bad "climate sites"
  # 2. wrong file for the mode: exit 1 (doesn't fit), nothing written
  [ "$(run volume $RSH $T/x)" = 1 ] && [ ! -e $T/x ] && ok "volume refuses RightScreenHome (1)" || bad "volume wrong file"
  [ "$(run climate $VOL $T/x)" = 1 ] && [ ! -e $T/x ] && ok "climate refuses automotive-service (1)" || bad "climate wrong file"
  [ "$(run sideosd $CLI $T/x)" = 1 ] && [ ! -e $T/x ] && ok "sideosd refuses HKMC_Climate (1)" || bad "sideosd wrong file"
  # 3. a truncated file is a tool error (3), not "doesn't fit"
  python3 -c "import sys;d=open(sys.argv[1],'rb').read();open(sys.argv[2],'wb').write(d[:len(d)//2])" "$VOL" $T/trunc.odex
  [ "$(run volume $T/trunc.odex $T/x)" = 3 ] && [ ! -e $T/x ] && ok "truncated input exits 3" || bad "truncated input ($(cat $T/log))"
else
  skip "stock odex files not found (set CCNC_STOCK): file tests"
fi

# 3b. consent (SystemUI's start-up profile screen): /system/priv-app/SystemUI.odex of CEEV.CAN 250818, from the unit
# image ($CCNC_UNIT) or $CCNC_STOCK/SystemUI.stock.odex. GOLD_CON is the tool's output on that file; it has NOT run on
# a car yet (private/systemui/DESIGN.txt has the on-car test plan).
SUI=$UNIT/priv-app/priv-app/SystemUI.odex
[ -f "$SUI" ] || SUI=${CCNC_STOCK:-$G/private/systemui}/SystemUI.stock.odex
STOCK_CON=942ff036a190a058980fde2829a9e11c GOLD_CON=d5128d82a2f0dda098fb9a8893e1da4e
if [ -f "$SUI" ] && [ "$(md5f "$SUI")" = $STOCK_CON ]; then
  [ "$(run check consent "$SUI")" = 0 ] && ok "consent: stock SystemUI is patchable" || bad "consent check ($(cat $T/log))"
  [ "$(run consent "$SUI" $T/con.odex)" = 0 ] && [ "$(md5f $T/con.odex)" = $GOLD_CON ] && ok "consent output = expected bytes" || bad "consent output ($(cat $T/log))"
  [ "$(run consent $T/con.odex $T/con.2)" = 10 ] && [ ! -e $T/con.2 ] && ok "consent re-run exits 10" || bad "consent re-run"
  # exactly these bytes change (file offsets): the dex adler32, the window type, message 1's log line -> the gate,
  # two switch targets, and the HIDE_CONSENT_SCREEN branch. Everything else, the size included, is the stock file.
  python3 - "$SUI" $T/con.odex <<'PY' && ok "consent changes only the expected 29 bytes" || bad "consent byte diff"
import sys
a = open(sys.argv[1], 'rb').read(); b = open(sys.argv[2], 'rb').read()
assert len(a) == len(b)
d = [i for i in range(len(a)) if a[i] != b[i]]
want = (list(range(0x30, 0x34)) + [0x329f0] + list(range(0x35bce, 0x35bdb)) + [0x35e68, 0x35e74]
        + list(range(0x37a6e, 0x37a75)) + [0x37a76, 0x37a77])
assert d == want, [hex(x) for x in d]
u = lambda o: b[o] | b[o + 1] << 8
assert u(0x329f0) == 2003                                                        # const/16 v5, TYPE_SYSTEM_ALERT
assert [u(0x35bce + 2 * i) for i in range(7)] == [0x1070, 0x09ff, 0x0009, 0x000a, 0x0038, 0xfff1, 0x0012]   # gate
assert [u(0x35bdc + 2 * i) for i in range(3)] == [0x1070, 0x09ef, 0x0009]        # confirm(), untouched
assert [u(0x37a6e + 2 * i) for i in range(5)] == [0x4112, 0x2071, 0x0a1b, 0x0010, 0x000e]   # const/4 v1,4; e(E,I); return-void
PY
  # a file with only one or two of the three edits is refused (1) and nothing is written
  python3 - "$SUI" $T/con.odex $T <<'PY'
import sys
a = bytearray(open(sys.argv[1], 'rb').read()); b = open(sys.argv[2], 'rb').read()
parts = {'type': [(0x329f0, 0x329f2)], 'gate': [(0x35bce, 0x35bdc), (0x35e68, 0x35e78)], 'hide': [(0x37a6e, 0x37a78)]}
for name, spans in parts.items():
    c = bytearray(a)
    for s, e in spans: c[s:e] = b[s:e]
    open(sys.argv[3] + '/part-' + name + '.odex', 'wb').write(c)      # stock + this edit only
    c = bytearray(b)
    for s, e in spans: c[s:e] = a[s:e]
    open(sys.argv[3] + '/miss-' + name + '.odex', 'wb').write(c)      # patched, this edit missing
PY
  for p in part-type part-gate part-hide miss-type miss-gate miss-hide; do
    [ "$(run consent $T/$p.odex $T/x)" = 1 ] && [ ! -e $T/x ] && ok "consent refuses a half-patched file ($p)" || bad "consent $p ($(cat $T/log))"
  done
  python3 -c "import sys;d=open(sys.argv[1],'rb').read();open(sys.argv[2],'wb').write(d[:len(d)//2])" "$SUI" $T/trunc2.odex
  [ "$(run consent $T/trunc2.odex $T/x)" = 3 ] && [ ! -e $T/x ] && ok "consent: truncated input exits 3" || bad "consent truncated ($(cat $T/log))"
  if [ -f "$VOL" ]; then
    [ "$(run consent "$VOL" $T/x)" = 1 ] && [ ! -e $T/x ] && ok "consent refuses automotive-service (1)" || bad "consent wrong file"
  fi
else
  skip "stock SystemUI.odex (250818) not found: consent file tests"
fi

# 4. lp-ccnc's exit mapping and records, with a stand-in for the unit's dalvikvm
LP=../gen5w/template/bin/lp-ccnc
mkdir -p $T/pkg/files && echo jar > $T/pkg/files/ccnc-lp.jar && echo x > $T/in
for c in "0:0:applied" "10:10:applied" "1:10:unsupported" "3:1:error" "137:1:error"; do
  rc=${c%%:*}; rest=${c#*:}; want=${rest%%:*}; mark=${rest#*:}
  printf '#!/bin/sh\nfor a; do o=$a; done\n[ %s = 0 ] && echo out > "$o"\nexit %s\n' $rc $rc > $T/dvm; chmod +x $T/dvm
  rm -rf $T/lp
  got=$(cd $T/pkg && BOOTCLASSPATH=x DALVIKVM=$T/dvm CCNC_LP_DIR=$T/lp sh "$OLDPWD/$LP" volume $T/in $T/out >/dev/null 2>&1; echo $?)
  [ "$got" = $want ] && [ "$(cat $T/lp/volume 2>/dev/null)" = $mark ] && ok "lp-ccnc: tool exit $rc -> $want ($mark)" || bad "lp-ccnc: tool exit $rc gave $got ($(cat $T/lp/volume 2>/dev/null))"
done

# 4b. consent's md5 record (what ccNC compares the mounted SystemUI.odex with): written with the output's md5 on 0,
# the input's on 10, removed on "doesn't fit" and on errors. The unit's md5 prints "<hash> <file>": stand-in below.
mkdir -p $T/bin && printf '#!/bin/sh\npython3 -c "import hashlib,sys;print(hashlib.md5(open(sys.argv[1],chr(114)+chr(98)).read()).hexdigest(), sys.argv[1])" "$1"\n' > $T/bin/md5 && chmod +x $T/bin/md5
for c in "0:0:out" "10:10:in" "1:10:none" "3:1:none"; do
  rc=${c%%:*}; rest=${c#*:}; want=${rest%%:*}; rec=${rest#*:}
  printf '#!/bin/sh\nfor a; do o=$a; done\n[ %s = 0 ] && echo out > "$o"\nexit %s\n' $rc $rc > $T/dvm; chmod +x $T/dvm
  rm -rf $T/lp; mkdir -p $T/lp; echo stale > $T/lp/consent.md5
  got=$(cd $T/pkg && PATH=$T/bin:$PATH BOOTCLASSPATH=x DALVIKVM=$T/dvm CCNC_LP_DIR=$T/lp sh "$OLDPWD/$LP" consent $T/in $T/out >/dev/null 2>&1; echo $?)
  case $rec in
    out)  exp=$(printf 'out\n' | python3 -c "import hashlib,sys;print(hashlib.md5(sys.stdin.buffer.read()).hexdigest())") ;;
    in)   exp=$(md5f $T/in) ;;
    none) exp= ;;
  esac
  [ "$got" = $want ] && [ "$(cat $T/lp/consent.md5 2>/dev/null)" = "$exp" ] && ok "lp-ccnc consent: tool exit $rc -> $want, md5 record ${rec}" || bad "lp-ccnc consent: tool exit $rc gave $got, record '$(cat $T/lp/consent.md5 2>/dev/null)'"
done

# 5. every other app/framework odex of the unit: no mode may match anything it shouldn't
if [ -d "$UNIT" ]; then
  n=0
  for f in "$UNIT"/app/app/*.odex "$UNIT"/priv-app/priv-app/*.odex; do
    [ -f "$f" ] || continue
    b=$(basename "$f" .odex)
    for m in volume sideosd climate consent; do
      r=$(run $m "$f" $T/y); rm -f $T/y
      case "$m:$b:$r" in consent:SystemUI:0|consent:*:1|volume:*:1|sideosd:RightScreenHome:0|sideosd:RightScreenHome:10|sideosd:*:1|climate:HKMC_Climate:0|climate:HKMC_Climate:10|climate:*:1) ;; *) bad "$m on $b gave $r";; esac
    done
    n=$((n+1))
  done
  ok "scanned $n app odex files for false matches"
  for f in "$UNIT"/framework/*.odex; do
    b=$(basename "$f" .odex); r=$(run volume "$f" $T/y); rm -f $T/y
    case "$b:$r" in automotive-service:0|automotive-service:10|*:1) ;; *) bad "volume on framework $b gave $r";; esac
  done
  ok "framework odex: volume fits automotive-service only"
  for f in "$UNIT"/framework/*.odex; do
    b=$(basename "$f" .odex); r=$(run consent "$f" $T/y); rm -f $T/y
    [ "$r" = 1 ] || bad "consent on framework $b gave $r"
  done
  ok "framework odex: consent fits none"
else
  skip "no unit image at $UNIT: false-match scan"
fi
exit $fail
