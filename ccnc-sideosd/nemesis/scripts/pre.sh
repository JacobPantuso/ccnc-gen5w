#!/system/bin/sh
# Refuses to install unless the file on the unit is exactly the stock one we patched (or already ours).
. ./scripts/common.sh
m=$(md5_of "$F")
[ "$m" = "$STOCK_MD5" ] && exit 0
[ "$m" = "$PATCHED_MD5" ] && exit 0
log "FAIL: $F is not the file this package was made for ($m). Nothing was changed."
exit 1
