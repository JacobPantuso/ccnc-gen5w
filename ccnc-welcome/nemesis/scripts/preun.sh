#!/system/bin/sh
# Puts the stock apk back. NEMESIS also runs this before an update (new package staged in
# /data/local/tmp/nemesis/stage/<id>): then keep ours, the new postinstall repatches it.
. ./scripts/common.sh

STAGE=/data/local/tmp/nemesis/stage/$ID
caller=$(cat /proc/$PPID/cmdline 2>/dev/null)
case $caller in
    *uninstall*) ;;
    *)
        if grep -q "^id=$ID\$" "$STAGE/NEMESIS.data" 2>/dev/null; then
            log "update: keeping the video, the new package repatches it"
            exit 0
        fi ;;
esac

if [ ! -f "$BK/stock.apk" ]; then
    log "no backup: nothing to restore (the file was never changed by this package)"
    exit 0
fi
[ "$(size_of "$BK/stock.apk")" = "$STOCK_SIZE" ] || { log "FAIL: the backup isn't the stock file, so it's left alone"; exit 1; }
install_apk "$BK/stock.apk" || { log "FAIL: couldn't restore the stock apk (backup kept in $BK)"; exit 1; }
log "stock startup video restored"
toast "Stock startup video restored."
exit 0
