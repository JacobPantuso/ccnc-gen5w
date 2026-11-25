#!/system/bin/sh
# Puts the stock file back (on uninstall; an update re-installs the patched one right after).
. ./scripts/common.sh
src="$BK/stock.odex"
[ "$(md5_of "$src")" = "$STOCK_MD5" ] || src=files/automotive-service.stock.odex
[ "$(md5_of "$src")" = "$STOCK_MD5" ] || { log "FAIL: no stock copy found; use the ccnc.volume.restore package"; exit 1; }
put_odex "$src" && { hmgapi_stock_remove; log "stock volume strip restored from the next start"; toast "ccNC volume removed. Restart the head unit."; exit 0; }
log "FAIL: couldn't restore $F"
exit 1
