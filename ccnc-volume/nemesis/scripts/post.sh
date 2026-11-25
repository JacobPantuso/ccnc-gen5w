#!/system/bin/sh
# Backs up the stock file (once, verified), installs the patched one, verifies. Any failure puts stock back.
. ./scripts/common.sh
mkdir -p "$BK"
if [ "$(md5_of "$BK/stock.odex")" != "$STOCK_MD5" ]; then
    if [ "$(md5_of "$F")" = "$STOCK_MD5" ]; then cp "$F" "$BK/stock.odex"; else cp files/automotive-service.stock.odex "$BK/stock.odex"; fi
    [ "$(md5_of "$BK/stock.odex")" = "$STOCK_MD5" ] || { rm -f "$BK/stock.odex"; log "FAIL: couldn't save the backup. Nothing was changed."; exit 1; }
fi
stock_to_usb "$BK/stock.odex"
hmgapi_stock_add "$BK/stock.odex"
[ "$(md5_of files/automotive-service.patched.odex)" = "$PATCHED_MD5" ] || { log "FAIL: package file is damaged. Nothing was changed."; exit 1; }
if put_odex files/automotive-service.patched.odex; then
    log "installed: the Gen5W volume strip is off from the next start"
    toast "ccNC volume installed. Restart the head unit to apply."
    exit 0
fi
log "FAIL: couldn't write $F; restoring stock"
put_odex "$BK/stock.odex" || log "FAIL: restore failed too; use the ccnc.volume.restore package"
exit 1
