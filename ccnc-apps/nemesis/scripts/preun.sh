#!/system/bin/sh
# Removes the apps. NEMESIS also runs this before an update (new package staged in
# /data/local/tmp/nemesis/stage/<id>): then keep them, the new postinstall pm install -r updates in place.
. ./scripts/common.sh
STAGE=/data/local/tmp/nemesis/stage/$ID
caller=$(cat /proc/$PPID/cmdline 2>/dev/null)
case $caller in
    *uninstall*) ;;
    *)
        if grep -q "^id=$ID\$" "$STAGE/NEMESIS.data" 2>/dev/null; then
            log "update: keeping the apps, the new package updates them"
            exit 0
        fi ;;
esac
fail=0
for a in $APPS; do
    p=${a#*:}
    pm path "$p" 2>/dev/null | grep -q "^package:" || continue
    r=$(pm uninstall "$p" 2>&1); echo "$r"
    case $r in *Success*) ;; *) fail=1 ;; esac
done
[ $fail = 0 ] && { toast "ccNC Apps removed"; exit 0; }
log "FAIL: pm uninstall didn't succeed for every app (output above)"
exit 1
