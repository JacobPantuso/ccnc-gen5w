#!/system/bin/sh
. ./scripts/common.sh
for a in $APPS; do
    [ -f "files/${a%%:*}.apk" ] || { log "FAIL: files/${a%%:*}.apk missing from the package"; exit 1; }
done
[ -x /system/bin/pm ] || { log "FAIL: no /system/bin/pm on this unit"; exit 1; }
log "preinstall ok"
exit 0
