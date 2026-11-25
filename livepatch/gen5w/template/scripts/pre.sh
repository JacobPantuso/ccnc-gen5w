#!/system/bin/sh
# Refuses to install where it could hurt the unit. NEMESIS's depends= already requires @NEMESIS_MIN@+ and no 1.x ccNC packages.
. ./scripts/common.sh
# The NEMESIS 1.17 LivePatch wrapper vs HMG-API's app_process wrapper bug: never with HMG-API on the affected builds.
v=$(nemesis version 2>/dev/null)
if [ -d /data/hmgapi ] || [ -e /system/bin/app_process.hmgapi-stock ]; then
    case "$v" in *1.17|*1.17.0|*"1.17 "*|*"1.17.0 "*) log "FAIL: NEMESIS $v + HMG-API is not safe for LivePatch. Update NEMESIS first. Nothing was changed."; exit 1;; esac
fi
wait_android || { log "FAIL: Android isn't up yet; install again once the home screen shows. Nothing was changed."; exit 1; }
exit 0
