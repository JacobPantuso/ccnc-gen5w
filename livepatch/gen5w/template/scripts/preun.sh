#!/system/bin/sh
# Uninstall. LivePatch keeps the patched files mounted until the restart, and while it does, the car's climate and
# seat buttons open ccNC Climate and the stock volume strip is off (ccNC Home shows its own). So ccNC Climate and
# ccNC Home stay until then: Home removes both and /data/ccnc at the next boot (UninstallCleanup, uninstall-pending).
# Everything else goes now: the other apps, the listener, the developer-menu check and our HMG-API stock copies.
# On an update (NEMESIS stages the new version first) nothing is removed, so the new install keeps the owner's
# layout and theme.
. ./scripts/common.sh
STAGE=/data/local/tmp/nemesis/stage/$ID
caller=$(cat /proc/$PPID/cmdline 2>/dev/null)
case $caller in
    *uninstall*) ;;
    *) if grep -q "^id=$ID\$" "$STAGE/NEMESIS.data" 2>/dev/null; then log "update: keeping the apps"; exit 0; fi ;;
esac
listener_off
for a in $APPS; do
    case ${a#*:} in dev.ccnc.climate|dev.ccnc.home) ;; *) pm uninstall "${a#*:}" >/dev/null 2>&1 ;; esac
done
for f in $HMG_FILES; do
    m=$HS_MARKS/${f##*/}.md5; hs=/data/hmgapi/stock$f
    [ -f "$m" ] || continue
    [ "$(md5_of "$hs")" = "$(cat "$m")" ] && rm -f "$hs"
    rm -f "$m"
done
rm -rf "$CCNC_DIR/livepatch" "$HS_MARKS" "$CCNC_DIR/verify.sh"
# nemesis-d stays: Home uses it to finish the job after the restart, then deletes /data/ccnc.
mkdir -p "$CCNC_DIR" && echo "dev.ccnc.climate dev.ccnc.home" > "$PENDING" && chmod 644 "$PENDING"
if ! installed dev.ccnc.home; then rm -rf "$CCNC_DIR"; fi
log "removed; restart the head unit: the stock files come back and ccNC Climate and Home are removed then"
toast "ccNC removed. Restart the head unit now to finish (the climate buttons need the restart)."
exit 0
