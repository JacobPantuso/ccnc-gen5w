#!/system/bin/sh
# Puts the stock file back (on uninstall; an update re-installs the patched one right after).
. ./scripts/common.sh
src="$BK/stock.odex"
[ "$(md5_of "$src")" = "$STOCK_MD5" ] || src=files/HKMC_Climate.stock.odex
[ "$(md5_of "$src")" = "$STOCK_MD5" ] || { log "FAIL: no stock copy found; use the ccnc.climatebtn.restore package"; exit 1; }
put_odex "$src" && { log "stock climate/seat screens restored from the next start"; toast "Stock climate buttons restored. Restart the head unit."; exit 0; }
log "FAIL: couldn't restore $F"
exit 1
