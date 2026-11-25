#!/system/bin/sh
# Shared by the ccNC Volume scripts (full root, cwd = the package dir).
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022

ID=ccnc.volume
F=/system/framework/automotive-service.odex
# KVT49L (CEEV.CAN) stock and patched files. Anything else: don't touch it.
STOCK_MD5=61e2bc82f758b53797b0a7aa3980eae8
PATCHED_MD5=f426a78f829b935005995b4f998863ca
BK=/data/nemesis/backup/$ID
# HMG-API checks framework files against its car map and turns every hook off (wireless CarPlay
# included) when one differs, unless a stock copy is kept here. Only the volume panel changes.
HS=/data/hmgapi/stock/system/framework/automotive-service.odex

log() { echo "ccNC Volume: $1"; }

toast() {
    [ "$(getprop sys.boot_completed)" = 1 ] || return 0
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}

md5_of() { set -- $(md5 "$1" 2>/dev/null); echo "$1"; }

# Writes $1 over the framework odex as a new inode (cp + mv): the running system_server keeps
# its mapped copy, the new file is used from the next start. Verified before and after.
put_odex() {
    want=$(md5_of "$1")
    nemesis remount rw || return 1
    cp "$1" "$F.new" && chmod 644 "$F.new" && chown root:root "$F.new" 2>/dev/null
    r=$?
    [ $r -eq 0 ] && [ "$(md5_of "$F.new")" = "$want" ] && mv "$F.new" "$F"
    r=$?
    rm -f "$F.new"
    sync
    nemesis remount ro
    [ $r -eq 0 ] && [ "$(md5_of "$F")" = "$want" ]
}

# Copies the stock file to a USB stick too, so it can be put back even without /data.
stock_to_usb() {
    for u in /storage/usb0 /storage/usb1 /storage/usb2; do
        [ -d "$u" ] && { mkdir -p "$u/ccNC reports" && cp "$1" "$u/ccNC reports/automotive-service-stock.odex"; } 2>/dev/null
    done
}

# Puts the stock copy where HMG-API's map check looks (only when HMG-API is installed).
hmgapi_stock_add() {
    [ -d /data/hmgapi ] || return 0
    mkdir -p "${HS%/*}" && cp "$1" "$HS" && chmod 644 "$HS"
    [ "$(md5_of "$HS")" = "$STOCK_MD5" ] || { rm -f "$HS"; log "WARN: couldn't place HMG-API's stock copy"; return 1; }
    touch "$BK/hmgapi_stock_placed"
}

hmgapi_stock_remove() {
    [ -f "$BK/hmgapi_stock_placed" ] || return 0
    [ "$(md5_of "$HS")" = "$STOCK_MD5" ] && rm -f "$HS"
    rm -f "$BK/hmgapi_stock_placed"
}
