#!/system/bin/sh
# Shared by the ccNC Apps scripts (full root, cwd = the package dir).
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022

ID=ccnc.apps
# file:package
APPS="settings:dev.ccnc.settings climate:dev.ccnc.climate media:dev.ccnc.media weather:dev.ccnc.weather phone:dev.ccnc.phone radio:dev.ccnc.radio voicememo:dev.ccnc.voicememo profile:dev.ccnc.profile ev:dev.ccnc.ev navi:dev.ccnc.navi"

log() { echo "ccNC Apps: $1"; }

toast() {
    [ "$(getprop sys.boot_completed)" = 1 ] || return 0
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}

wait_android() {
    n=0
    while [ "$(getprop sys.boot_completed)" != 1 ]; do
        n=$((n + 1))
        [ $n -gt 90 ] && return 1
        sleep 2
    done
    return 0
}

# Only OEM-allowlisted signers pass while sys.boot_completed=1: drop it to 0 for the pm call
# only (as NEMESIS does) and always put it back.
pm_install() {
    cp "files/$1.apk" "/data/local/tmp/ccnc-$1.apk" || return 1
    chmod 644 "/data/local/tmp/ccnc-$1.apk"
    setprop sys.boot_completed 0
    r=$(pm install -r "/data/local/tmp/ccnc-$1.apk" 2>&1)
    setprop sys.boot_completed 1
    rm -f "/data/local/tmp/ccnc-$1.apk"
    echo "$r"
    case $r in *Success*) return 0 ;; esac
    return 1
}
