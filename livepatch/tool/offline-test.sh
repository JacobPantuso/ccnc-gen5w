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
# Outputs on 250818 that have run on the car (ccnc.gen5w 2.0.2)
GOLD_VOL=601edc082cfe33c541e6f1dac452cef3 GOLD_RSH=e4ff67afee9e87bf725e00c831346cb2 GOLD_CLI=753c509b564a44e79080c32218879843
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
J="java -cp out/classes ccnc.lp.DexPatch"
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

# 4. lp-ccnc's exit mapping and records, with a stand-in for the unit's dalvikvm
LP=../gen5w/template/bin/lp-ccnc
mkdir -p $T/pkg/files && touch $T/pkg/files/ccnc-lp.jar && echo x > $T/in
for c in "0:0:applied" "10:10:applied" "1:10:unsupported" "3:1:error" "137:1:error"; do
  rc=${c%%:*}; rest=${c#*:}; want=${rest%%:*}; mark=${rest#*:}
  printf '#!/bin/sh\nfor a; do o=$a; done\n[ %s = 0 ] && echo out > "$o"\nexit %s\n' $rc $rc > $T/dvm; chmod +x $T/dvm
  rm -rf $T/lp
  got=$(cd $T/pkg && BOOTCLASSPATH=x DALVIKVM=$T/dvm CCNC_LP_DIR=$T/lp sh "$OLDPWD/$LP" volume $T/in $T/out >/dev/null 2>&1; echo $?)
  [ "$got" = $want ] && [ "$(cat $T/lp/volume 2>/dev/null)" = $mark ] && ok "lp-ccnc: tool exit $rc -> $want ($mark)" || bad "lp-ccnc: tool exit $rc gave $got ($(cat $T/lp/volume 2>/dev/null))"
done

# 5. every other app/framework odex of the unit: no mode may match anything it shouldn't
if [ -d "$UNIT" ]; then
  n=0
  for f in "$UNIT"/app/app/*.odex "$UNIT"/priv-app/priv-app/*.odex; do
    [ -f "$f" ] || continue
    b=$(basename "$f" .odex)
    for m in volume sideosd climate; do
      r=$(run $m "$f" $T/y); rm -f $T/y
      case "$m:$b:$r" in volume:*:1|sideosd:RightScreenHome:0|sideosd:RightScreenHome:10|sideosd:*:1|climate:HKMC_Climate:0|climate:HKMC_Climate:10|climate:*:1) ;; *) bad "$m on $b gave $r";; esac
    done
    n=$((n+1))
  done
  ok "scanned $n app odex files for false matches"
  for f in "$UNIT"/framework/*.odex; do
    b=$(basename "$f" .odex); r=$(run volume "$f" $T/y); rm -f $T/y
    case "$b:$r" in automotive-service:0|automotive-service:10|*:1) ;; *) bad "volume on framework $b gave $r";; esac
  done
  ok "framework odex: volume fits automotive-service only"
else
  skip "no unit image at $UNIT: false-match scan"
fi
exit $fail
