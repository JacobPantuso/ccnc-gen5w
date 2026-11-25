#!/system/bin/sh
# Checks the package is whole and the unit's file is the stock one (or ours, from an earlier install).
. ./scripts/common.sh

for f in files/patch.jar files/welcome_hyundai.mp4; do
    [ -f "$f" ] || { log "FAIL: $f missing from the package"; exit 1; }
done
[ -f "$APP" ] || { log "FAIL: $APP not found: this isn't the unit this package is for"; exit 1; }
[ -x /system/bin/app_process ] || { log "FAIL: no app_process"; exit 1; }
if [ -f "$BK/stock.apk" ]; then
    [ "$(size_of "$BK/stock.apk")" = "$STOCK_SIZE" ] || { log "FAIL: the saved backup isn't the stock file"; exit 1; }
else
    s=$(size_of "$APP")
    [ "$s" = "$STOCK_SIZE" ] || { log "FAIL: WelcomeGoodbye.apk is $s bytes, not the stock $STOCK_SIZE: it was changed by something else, so nothing was touched"; exit 1; }
fi
log "preinstall ok"
exit 0
