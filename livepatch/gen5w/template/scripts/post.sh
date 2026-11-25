#!/system/bin/sh
# Installs the ccNC apps (pm; /system untouched), turns on Home's notification listener, installs the developer-menu
# tools, and keeps HMG-API's hooks on with the volume LivePatch (stock copy of automotive-service.odex for its
# md5 check). LivePatch itself mounts the patched files at the next restart.
. ./scripts/common.sh
wait_android || { log "FAIL: Android didn't finish booting; install again once the home screen is up."; exit 1; }
bad=""
for a in $APPS; do
    f=${a%%:*}
    if pm_install "$f"; then log "installed $f"; else log "FAIL: $f"; bad="$bad $f"; fi
done
[ -n "$bad" ] && { toast "ccNC couldn't install:$bad (see NEMESIS's log)"; exit 1; }
listener_on && log "notification listener on"
[ -f ./scripts/devtools.sh ] && sh ./scripts/devtools.sh
# HMG-API reads framework files by path, so after the restart it sees LivePatch's patched copy and would turn every
# hook off (wireless CarPlay with it) unless a stock copy is at /data/hmgapi/stock/<path>. The stock file is still
# in view until the restart, so copy it now (not if something is already mounted over it).
if [ -d /data/hmgapi ] && [ ! -f "$HS" ] && ! grep -q " $VOL " /proc/mounts; then
    mkdir -p "${HS%/*}" && cp "$VOL" "$HS.new" && chmod 644 "$HS.new" &&
        [ "$(md5_of "$HS.new")" = "$(md5_of "$VOL")" ] && mv "$HS.new" "$HS" && md5_of "$HS" > "$HS_MARK" &&
        log "HMG-API stock copy placed"
    rm -f "$HS.new"
fi
toast "ccNC installed. Restart the head unit to finish, then press Home and choose ccNC Home."
exit 0
