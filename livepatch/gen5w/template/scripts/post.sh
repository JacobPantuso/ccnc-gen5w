#!/system/bin/sh
# After NEMESIS has committed the install (the apps went in at preinstall): Home's notification listener, the
# developer-menu tools, the developer menu's step records, and HMG-API's stock copies. Problems here are reported
# but don't fail the install: everything that matters is already in place. LivePatch mounts the patched files at
# the next restart.
. ./scripts/common.sh
listener_on && log "notification listener on"
[ -f ./scripts/devtools.sh ] && sh ./scripts/devtools.sh

# Step records for the developer menu: a step's tool writes its own, but when NEMESIS reuses a cached result the
# tool doesn't run. Fill in a missing one from LivePatch's status (built or mounted = applied).
st=$(nemesis livepatch status 2>/dev/null)
mkdir -p "$CCNC_DIR/livepatch" && chmod 755 "$CCNC_DIR" "$CCNC_DIR/livepatch"
for s in $STEPS; do
    n=${s%%:*}; t=${s#*:}
    [ -f "$CCNC_DIR/livepatch/$n" ] && continue
    case "$st" in
        *"$t mounted"*|*"$t farmed"*) echo applied > "$CCNC_DIR/livepatch/$n" ;;
        *) echo unsupported > "$CCNC_DIR/livepatch/$n" ;;
    esac
    chmod 644 "$CCNC_DIR/livepatch/$n"
done

# The start-up screen step's md5 record (livepatch/consent.md5): ccNC only shows its start-up chooser while the mounted
# SystemUI.odex has that md5. bin/lp-ccnc writes it, but not when NEMESIS reused a cached result: then run the tool
# again on the stock file (same input, same output) just for the record. No record = the car's own screen, as stock.
if [ "$(cat "$CCNC_DIR/livepatch/consent" 2>/dev/null)" = applied ] && [ ! -s "$CCNC_DIR/livepatch/consent.md5" ]; then
    tmp=/data/local/tmp/ccnc-consent.$$
    rm -f "$tmp" "$tmp.out"
    nemesis livepatch stock "$CONSENT_FILE" "$tmp" >/dev/null 2>&1 || { ! grep -q " $CONSENT_FILE " /proc/mounts && cp "$CONSENT_FILE" "$tmp"; }
    [ -s "$tmp" ] && sh ./bin/lp-ccnc consent "$tmp" "$tmp.out" >/dev/null 2>&1
    rm -f "$tmp" "$tmp.out"
    [ -s "$CCNC_DIR/livepatch/consent.md5" ] && log "start-up screen: md5 record written" || log "start-up screen: no md5 record; the car's own screen stays"
fi

# HMG-API reads these files by path, so it sees LivePatch's patched copies and would turn hooks off (all of them for
# the framework file, wireless CarPlay with it) unless the stock file is at /data/hmgapi/stock/<path>. The stock bytes
# come from `nemesis livepatch stock` (also while a patched copy is mounted), else from the file if nothing is mounted
# over it. A copy we placed (recorded in $HS_MARKS) is refreshed when the stock file changed (a firmware update); an
# identical unrecorded copy (2.0.0/2.0.1 didn't record theirs) is adopted unless HMG-API itself owns that file; any
# other copy is HMG-API's and left alone.
if [ -d /data/hmgapi ]; then
    mkdir -p "$HS_MARKS"
    hmg=$HMG_FILES
    for j in /data/hmgapi/state/files.json /data/hmgapi/maps/*/*/*/*/car.json; do
        [ -f "$j" ] && grep -q "\"$CONSENT_FILE\"" "$j" 2>/dev/null && { hmg="$hmg $CONSENT_FILE"; log "HMG-API's map lists $CONSENT_FILE"; break; }
    done
    for f in $hmg; do
        hs=/data/hmgapi/stock$f; m=$HS_MARKS/${f##*/}.md5; tmp=/data/local/tmp/ccnc-stock.$$
        rm -f "$tmp"
        nemesis livepatch stock "$f" "$tmp" >/dev/null 2>&1 || { ! grep -q " $f " /proc/mounts && cp "$f" "$tmp"; }
        want=$(md5_of "$tmp")
        if [ -z "$want" ]; then log "WARNING: no stock copy of $f for HMG-API"; rm -f "$tmp"; continue; fi
        if [ ! -f "$hs" ]; then
            mkdir -p "${hs%/*}" && chmod 644 "$tmp" && mv "$tmp" "$hs" && echo "$want" > "$m" && log "HMG-API stock copy: $f"
        elif [ "$(md5_of "$hs")" = "$want" ]; then
            if [ ! -f "$m" ] && ! grep -q "\"$f\"" /data/hmgapi/state/files.json 2>/dev/null; then echo "$want" > "$m"; log "HMG-API stock copy adopted: $f"; fi
        elif [ -f "$m" ]; then
            chmod 644 "$tmp" && mv "$tmp" "$hs" && echo "$want" > "$m" && log "HMG-API stock copy refreshed (stock changed): $f"
        else
            log "HMG-API keeps its own copy of $f; left as it is"
        fi
        rm -f "$tmp"
    done
fi
toast "ccNC installed. Restart the head unit to finish, then press Home and choose ccNC Home."
exit 0
