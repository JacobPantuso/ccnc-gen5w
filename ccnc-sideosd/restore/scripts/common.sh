#!/system/bin/sh
# Shared by the ccNC Side Popups scripts (full root, cwd = the package dir).
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022

ID=ccnc.sideosd
F=/system/app/RightScreenHome.odex
# The file on this unit before ccNC (HMG-API keeps its own stock copy of it) and our patched one. Anything else: don't touch it.
STOCK_MD5=9b54104c21339e5c593d13081b5abd3c
PATCHED_MD5=0b04470678194ab7e5bf626de3738001
BK=/data/nemesis/backup/$ID

log() { echo "ccNC Side Popups: $1"; }

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
        [ -d "$u" ] && { mkdir -p "$u/ccNC reports" && cp "$1" "$u/ccNC reports/RightScreenHome-before-ccnc.odex"; } 2>/dev/null
    done
}

