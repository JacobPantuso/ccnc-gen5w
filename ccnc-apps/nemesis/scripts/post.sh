#!/system/bin/sh
# Installs each app with pm. Only adds /data/app/dev.ccnc.*; nothing in /system is touched.
. ./scripts/common.sh
log "postinstall"
wait_android || { log "FAIL: Android didn't finish booting; install the package again once the home screen is up."; exit 1; }
bad=""
for a in $APPS; do
    f=${a%%:*}
    if pm_install "$f"; then log "installed $f"; else log "FAIL: $f"; bad="$bad $f"; fi
done
if [ -n "$bad" ]; then
    toast "ccNC Apps: couldn't install:$bad (see NEMESIS's log)"
    exit 1
fi
toast "ccNC Apps installed"
# No automatic check run: the check is in the developer menu (tap the clock 7 times > Run checks).
[ -f ./scripts/devtools.sh ] && sh ./scripts/devtools.sh
exit 0
