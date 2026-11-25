#!/system/bin/sh
# ccNC theme pack scripts (shared by every pack; full root, cwd = the package dir). The pack itself is files only.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022
ID=$(cat files/PACK_ID)
APP=/data/data/dev.ccnc.home
DEST=$APP/files/themes/$ID

log() { echo "ccNC theme $ID: $1"; }

toast() {
    [ "$(getprop sys.boot_completed)" = 1 ] || return 0
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}

# ccNC Home's Linux user id (owner of the pack files, so Home can serve them to the other ccNC apps).
home_uid() { dumpsys package dev.ccnc.home 2>/dev/null | grep -m1 -o 'userId=[0-9]*' | cut -d= -f2; }
