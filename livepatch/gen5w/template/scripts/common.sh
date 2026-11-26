#!/system/bin/sh
# Shared by the ccNC for Gen5W scripts (full root, cwd = the package dir). mksh + toolbox only.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022
ID=ccnc.gen5w
# file:package, Home last (it's the one people open first)
APPS="settings:dev.ccnc.settings climate:dev.ccnc.climate media:dev.ccnc.media weather:dev.ccnc.weather phone:dev.ccnc.phone radio:dev.ccnc.radio voicememo:dev.ccnc.voicememo profile:dev.ccnc.profile ev:dev.ccnc.ev navi:dev.ccnc.navi home:dev.ccnc.home"
LISTENER=dev.ccnc.home/dev.ccnc.home.MediaNotificationBlocker
# Files LivePatch changes that HMG-API's hook map checks by md5: HMG-API takes /data/hmgapi/stock/<path> as the
# stock file, so its hooks stay on. Our copies are recorded in $HS_MARKS (md5 per file) so uninstall removes only ours.
HMG_FILES="/system/framework/automotive-service.odex /system/app/RightScreenHome.odex"
HS_MARKS=/data/ccnc/hmgapi-stock

log() { echo "ccNC: $1"; }

toast() {
    [ "$(getprop sys.boot_completed)" = 1 ] || return 0
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}

wait_android() {
    n=0
    while [ "$(getprop sys.boot_completed)" != 1 ]; do
        n=$((n + 1)); [ $n -gt 90 ] && return 1; sleep 2
    done
    return 0
}

md5_of() { set -- $(md5 "$1" 2>/dev/null); echo "$1"; }

# This unit's package manager only accepts OEM-allowlisted signers while sys.boot_completed=1: drop it to 0 once
# around all the pm installs (as NEMESIS does) and always put it back, even if the script is cut off.
pm_begin() { trap 'setprop sys.boot_completed 1' EXIT; setprop sys.boot_completed 0; }
pm_end() { setprop sys.boot_completed 1; trap - EXIT; }
pm_install() {
    cp "files/apks/$1.apk" "/data/local/tmp/ccnc-$1.apk" || return 1
    chmod 644 "/data/local/tmp/ccnc-$1.apk"
    r=$(pm install -r "/data/local/tmp/ccnc-$1.apk" 2>&1)
    rm -f "/data/local/tmp/ccnc-$1.apk"
    echo "$r"
    case $r in *Success*) return 0 ;; esac
    case $r in *INCONSISTENT_CERTIFICATES*) log "$1: the installed copy is signed with another key (a build from another machine?)";; esac
    return 1
}
installed() { pm path "$1" 2>/dev/null | grep -q "^package:"; }

# Files of ours in /data/ccnc: tool results (livepatch/), the HMG-API stock-copy records (hmgapi-stock/), the
# developer-menu tools (verify.sh, nemesis-d) and, between an uninstall and the restart, uninstall-pending.
CCNC_DIR=/data/ccnc
PENDING=$CCNC_DIR/uninstall-pending
# step:target, for the developer menu's records
STEPS="volume:/system/framework/automotive-service.odex sideosd:/system/app/RightScreenHome.odex climate:/system/app/HKMC_Climate.odex welcome:/system/app/WelcomeGoodbye.apk"

# Home's notification listener (removes the Gen5W media popups and the boot "Media off" notice).
listener_on() {
    cur=$(settings get secure enabled_notification_listeners 2>/dev/null)
    [ "$cur" = "null" ] && cur=""
    case ":$cur:" in *":$LISTENER:"*) return 0;; esac
    if [ -z "$cur" ]; then new=$LISTENER; else new="$cur:$LISTENER"; fi
    settings put secure enabled_notification_listeners "$new"
}
listener_off() {
    cur=$(settings get secure enabled_notification_listeners 2>/dev/null)
    case ":$cur:" in *":$LISTENER:"*) ;; *) return 0;; esac
    new=""; old_ifs=$IFS; IFS=:
    for l in $cur; do [ -n "$l" ] && [ "$l" != "$LISTENER" ] && new="${new:+$new:}$l"; done
    IFS=$old_ifs
    settings put secure enabled_notification_listeners "$new"
}
