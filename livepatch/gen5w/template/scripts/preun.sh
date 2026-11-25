#!/system/bin/sh
# Uninstall: removes the apps (and their settings), the listener and the HMG-API stock copy it placed.
# LivePatch unmounts the patched files by itself (stock after the restart). On an update (NEMESIS stages the
# new version first) the apps stay, so the new postinstall's pm install -r keeps the owner's layout and theme.
. ./scripts/common.sh
STAGE=/data/local/tmp/nemesis/stage/$ID
caller=$(cat /proc/$PPID/cmdline 2>/dev/null)
case $caller in
    *uninstall*) ;;
    *) if grep -q "^id=$ID\$" "$STAGE/NEMESIS.data" 2>/dev/null; then log "update: keeping the apps"; exit 0; fi ;;
esac
listener_off
rm -rf /data/ccnc/livepatch
for a in $APPS; do pm uninstall "${a#*:}" >/dev/null 2>&1; done
if [ -f "$HS_MARK" ]; then
    [ "$(md5_of "$HS")" = "$(cat "$HS_MARK")" ] && rm -f "$HS"
    rm -f "$HS_MARK"
fi
log "removed; restart the head unit to bring the stock files back"
toast "ccNC removed. Restart the head unit."
exit 0
