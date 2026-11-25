#!/system/bin/sh
# Puts the stock file back (on uninstall; an update re-installs the patched one right after).
. ./scripts/common.sh
src="$BK/stock.odex"
[ "$(md5_of "$src")" = "$STOCK_MD5" ] || src=files/RightScreenHome.stock.odex
[ "$(md5_of "$src")" = "$STOCK_MD5" ] || { log "FAIL: no stock copy found; use the ccnc.sideosd.restore package"; exit 1; }
put_odex "$src" && { log "Gen5W side popups restored from the next start"; toast "Gen5W side popups restored. Restart the head unit."; exit 0; }
log "FAIL: couldn't restore $F"
exit 1
