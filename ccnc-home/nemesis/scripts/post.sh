#!/system/bin/sh
# NEMESIS postinstall (install and update alike): installs the app with pm.
# Only adds /data/app/dev.ccnc.home-*.apk; nothing in /system is touched.
# pm install -r keeps the app's settings (layout, hidden cards, theme) on an update.
. ./scripts/common.sh

log "postinstall"
if ! wait_android; then
    log "FAIL: Android didn't finish booting, so the app couldn't be installed. Install the package again once the home screen is up."
    exit 1
fi
if ! pm_install; then
    log "FAIL: pm install didn't succeed (output above). Nothing else changed."
    toast "ccNC Home couldn't be installed: see NEMESIS's log"
    exit 1
fi
log "installed. Open it from NEMESIS Manager (Launch) or its app icon; long-press the date bar for themes."
listener_on
toast "ccNC Home installed"
# No automatic check run: the check is in the developer menu (tap the clock 7 times > Run checks).
[ -f ./scripts/devtools.sh ] && sh ./scripts/devtools.sh
exit 0
