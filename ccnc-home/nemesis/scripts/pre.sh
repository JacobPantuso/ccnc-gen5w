#!/system/bin/sh
# NEMESIS preinstall: check the package is whole before anything changes.
. ./scripts/common.sh

[ -f "$APK" ] || { log "FAIL: $APK missing from the package"; exit 1; }
[ -x /system/bin/pm ] || { log "FAIL: no /system/bin/pm on this unit"; exit 1; }
log "preinstall ok"
exit 0
