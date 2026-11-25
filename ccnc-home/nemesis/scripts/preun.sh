#!/system/bin/sh
# NEMESIS preuninstall: removes the app (and its settings).
# NEMESIS also runs this before an update, with the new package staged in
# /data/local/tmp/nemesis/stage/<id>: then keep the app, so the new postinstall's
# pm install -r updates it in place and the owner's layout and theme survive.
# Never skip when the caller is an uninstall: a stage left by an interrupted
# install mustn't turn an uninstall into nothing.
. ./scripts/common.sh

STAGE=/data/local/tmp/nemesis/stage/$ID
caller=$(cat /proc/$PPID/cmdline 2>/dev/null)
case $caller in
    *uninstall*) ;;
    *)
        if grep -q "^id=$ID\$" "$STAGE/NEMESIS.data" 2>/dev/null; then
            log "update: keeping the app, the new package updates it"
            exit 0
        fi ;;
esac

if ! installed; then
    log "app not installed: nothing to remove"
    exit 0
fi
listener_off
# If it was picked as the default home, going back to the stock home is automatic:
# the default is dropped with the app.
r=$(pm uninstall "$PKG" 2>&1)
echo "$r"
case $r in
    *Success*) log "removed" ; toast "ccNC Home removed"; exit 0 ;;
esac
log "FAIL: pm uninstall didn't succeed (output above): the package stays"
exit 1
