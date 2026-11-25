#!/system/bin/sh
# Emergency restore: puts HKMC_Climate.odex back from this package. Works with Android down (NEMESIS.emergency).
. ./scripts/common.sh
[ "$(md5_of files/HKMC_Climate.stock.odex)" = "$STOCK_MD5" ] || { log "FAIL: package file is damaged"; exit 1; }
[ "$(md5_of "$F")" = "$STOCK_MD5" ] && { log "already stock"; exit 0; }
put_odex files/HKMC_Climate.stock.odex && { log "stock restored; restart the head unit"; exit 0; }
log "FAIL: couldn't restore $F"; exit 1
