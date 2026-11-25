#!/system/bin/sh
# Shared by the ccNC Climate Buttons scripts (full root, cwd = the package dir).
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022

ID=ccnc.climatebtn
F=/system/app/HKMC_Climate.odex
# The stock file on this unit (not in HMG-API's file list) and our patched one. Anything else: don't touch it.
STOCK_MD5=5742c2482f5ae4aa9126a0f27283abe3
PATCHED_MD5=364fe54acec3998d06ad53c44e7056d7
BK=/data/nemesis/backup/$ID

log() { echo "ccNC Climate Buttons: $1"; }

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
        [ -d "$u" ] && { mkdir -p "$u/ccNC reports" && cp "$1" "$u/ccNC reports/HKMC_Climate-before-ccnc.odex"; } 2>/dev/null
    done
}

