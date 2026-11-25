#!/system/bin/sh
# Shared by the ccNC Home NEMESIS scripts (full root, cwd = the package dir).
# KitKat toolbox: no head/tail/cut/tr; keep to plain sh builtins and toolbox.
export PATH=/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
umask 022

ID=ccnc.home
PKG=dev.ccnc.home
APK=files/ccnc-home.apk
TMP_APK=/data/local/tmp/ccnc-home.apk

log() { echo "ccNC Home: $1"; }

toast() {
    [ "$(getprop sys.boot_completed)" = 1 ] || return 0
    am broadcast -a com.hkmc.intent.action.TOAST -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true >/dev/null 2>&1
}

# pm needs Android up. Wait up to ~3 minutes (a stick can be in the port at boot).
wait_android() {
    n=0
    while [ "$(getprop sys.boot_completed)" != 1 ]; do
        n=$((n + 1))
        [ $n -gt 90 ] && return 1
        sleep 2
    done
    return 0
}

installed() {
    pm path "$PKG" 2>/dev/null | grep -q "^package:"
}

# This unit's package manager only accepts OEM-allowlisted signers while
# sys.boot_completed=1, so drop it to 0 for the pm call only (as NEMESIS does),
# and always put it back.
pm_install() {
    cp "$APK" "$TMP_APK" || return 1
    chmod 644 "$TMP_APK"
    setprop sys.boot_completed 0
    r=$(pm install -r "$TMP_APK" 2>&1)
    setprop sys.boot_completed 1
    rm -f "$TMP_APK"
    echo "$r"
    case $r in *Success*) return 0 ;; esac
    return 1
}

# ccNC Home's notification listener (MediaNotificationBlocker): removes the Gen5W media popups and the system's
# "Media off" notice. Android only runs listeners named in secure enabled_notification_listeners (":"-separated),
# so ours is added to whatever is there and removed again on uninstall; other listeners are kept.
LISTENER=dev.ccnc.home/dev.ccnc.home.MediaNotificationBlocker
listener_on() {
    cur=$(settings get secure enabled_notification_listeners 2>/dev/null)
    [ "$cur" = "null" ] && cur=""
    case ":$cur:" in *":$LISTENER:"*) return 0;; esac
    if [ -z "$cur" ]; then new=$LISTENER; else new="$cur:$LISTENER"; fi
    settings put secure enabled_notification_listeners "$new" && log "notification listener on"
}
listener_off() {
    cur=$(settings get secure enabled_notification_listeners 2>/dev/null)
    case ":$cur:" in *":$LISTENER:"*) ;; *) return 0;; esac
    new=""
    old_ifs=$IFS; IFS=:
    for l in $cur; do [ -n "$l" ] && [ "$l" != "$LISTENER" ] && new="${new:+$new:}$l"; done
    IFS=$old_ifs
    settings put secure enabled_notification_listeners "$new" && log "notification listener off"
}
