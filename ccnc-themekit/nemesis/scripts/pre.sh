#!/system/bin/sh
. ./scripts/common.sh
case "$ID" in ""|*[!a-z0-9_-]*) log "FAIL: bad pack id"; exit 1;; esac
[ -n "$(home_uid)" ] && [ -d "$APP" ] || { log "FAIL: install ccNC Home first. Nothing was changed."; exit 1; }
exit 0
